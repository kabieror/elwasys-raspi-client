package org.kabieror.elwasys.raspiclient.ui.small;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import org.kabieror.elwasys.common.*;
import org.kabieror.elwasys.raspiclient.application.ActionContainer;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.executions.FhemException;
import org.kabieror.elwasys.raspiclient.io.CardDetectedEvent;
import org.kabieror.elwasys.raspiclient.ui.AbstractMainFormController;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.small.components.ProgramListItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dieser Controller steuert die Geräteauswahl.
 *
 * @author Oliver Kabierschke
 */
@SuppressWarnings("serial")
public class MainFormController extends AbstractMainFormController {

    /**
     * Aktuell laufende Aufgaben zur Aktualisierung von Einträgen der
     * Geräte-Liste
     */
    private final Map<Execution, ScheduledFuture<?>> runningUpdateTasks;
    private final MainFormStateManager stateManager;
    /**
     * Service, der regelmäßig Aufgaben ausführt (Update der Geräte-Liste)
     */
    ScheduledExecutorService updateService;
    /**
     * Ausgewählte Daten des Benutzers
     */
    Device selectedDevice;
    Program selectedProgram;
    User registeredUser;
    @FXML
    Pane startupPane;
    @FXML
    Pane devicePane;
    @FXML
    Pane infoPane;
    @FXML
    Pane confirmAbortionPane;
    @FXML
    Pane programPane;
    @FXML
    Pane confirmationPane;
    @FXML
    Pane doorOpenPane;
    @FXML
    Pane errorPane;
    @FXML
    Label start_versionLabel;
    /**
     * Gerätefelder
     */
    @FXML
    Pane device1container;
    @FXML
    Label device1detailLabel;
    @FXML
    Pane device2container;
    @FXML
    Label device2detailLabel;
    @FXML
    Pane device3container;
    @FXML
    Label device3detailLabel;
    @FXML
    Pane device4container;
    @FXML
    Label device4detailLabel;
    /**
     * Liste aller Programme zum ausgewählten Gerät
     */
    @FXML
    ListView<Program> programList;
    ObservableList<Program> programListData;
    /**
     * Seite: Info
     */
    @FXML
    Button info_buttonCancel;
    @FXML
    Button info_buttonAbortProgram;
    @FXML
    Label info_labelDevice;
    @FXML
    Label info_labelUser;
    @FXML
    Label info_labelRemaining;
    @FXML
    Label info_labelEndTime;
    /**
     * Seite: Abbruch bestätigen
     */
    @FXML
    Button abort_buttonYes;
    @FXML
    Button abort_buttonNo;
    /**
     * Seite: Programmauswahl
     */
    @FXML
    Label program_labelDevice;
    @FXML
    Button program_buttonCancel;
    @FXML
    Button program_buttonForward;
    /**
     * Seite: Bestätigung
     */
    @FXML
    Label confirmation_labelDevice;
    @FXML
    Label confirmation_userIcon;
    @FXML
    Label confirmation_username;
    @FXML
    Label confirmation_credit;
    @FXML
    Label confirmation_cost;
    @FXML
    Label confirmation_errorMessage;
    @FXML
    Label confirmation_remainingCredit;
    @FXML
    Button confirmation_buttonCancel;
    @FXML
    Button confirmation_buttonStart;
    @FXML
    Button confirmation_buttonDoor;
    /**
     * Seite: Tür öffnen
     */
    @FXML
    Button door_buttonDone;
    /**
     * Seite: Fehler
     */
    @FXML
    Label error_title;
    @FXML
    Label error_detail;
    @FXML
    Button error_buttonCancel;
    @FXML
    Button error_buttonRetry;
    /**
     * Seite: Bitte warten
     */
    @FXML
    Pane waitPane;
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private Device[] devices = new Device[4];
    /**
     * Service, der die Warte-Seite anzeigt.
     */
    private ScheduledExecutorService waitPaneService;
    private ScheduledFuture<?> runningWaitPaneDelay;
    /**
     * Die Aktion, die fehlgeschlagen ist und wiederholt werden kann
     */
    private Runnable retryAction;

    /**
     * Konstruktor
     */
    public MainFormController() {
        // Registriere beim Manager
        ElwaManager.instance.setMainFormController(this);

        this.updateService = Executors.newScheduledThreadPool(4);
        this.waitPaneService = Executors.newSingleThreadScheduledExecutor();

        this.runningUpdateTasks = new HashMap<>();

        // Zustandsmanager initiieren
        this.stateManager = new MainFormStateManager(this);
    }

    /**
     * Wird aufgerufen, sobald das Hauptformular geladen ist
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        /**
         * Event-Handler installieren
         */
        this.installEventHandler();

        // ElwaManager initiieren
        final ActionContainer actionContainer = new ActionContainer();
        final Runnable startupRunnable = () -> {
            Platform.runLater(
                    () -> MainFormController.this.stateManager.gotoState(MainFormState.STARTUP));
            try {
                // Startup Delay
                Thread.sleep(ElwaManager.instance.getConfigurationManager().getStartupDelay()
                        .getSeconds() * 1000);
            } catch (final InterruptedException e1) {
                this.logger.warn("Interrupted while performing startup delay.");
            }
            final Logger logger = LoggerFactory.getLogger(this.getClass());
            logger.info("Initializing components");

            if (super.tryInitiate(actionContainer)) {
                Platform.runLater(() -> MainFormController.this.stateManager
                        .gotoState(MainFormState.SELECT_DEVICE));
            }
        };
        actionContainer.setAction(() -> {
            final Thread startupThread = new Thread(startupRunnable);
            startupThread.start();
        });
        actionContainer.getAction().run();
    }

    /**
     * Initiiert den Controller. Setzt voraus, dass der ElwaManager bereits
     * initiiert wurde
     */
    @Override
    public void initiate() {
        super.initiate();

        Platform.runLater(this::initializeComponents);

        // Update der Geräte planen
        final Runnable updateDevices = () -> {
            if (ElwaManager.instance.getExecutionManager().getRunningExecutions().size() > 0) {
                // Kein Update, wenn eine Ausführung läuft.
                return;
            }
            // Update objects
            for (final Device d : this.devices) {
                if (d == null) {
                    continue;
                }
                try {
                    d.update();
                } catch (final Exception e) {
                    this.logger.error("Error while updating device.", e);
                }
            }
        };
        this.backlightManager.listenToLightOnEvent(updateDevices::run);
        this.updateService.scheduleAtFixedRate(updateDevices, 90, 60, TimeUnit.SECONDS);
    }

    /**
     * Fügt erforderliche Event-Handler zu den Komponenten hinzu
     */
    private void installEventHandler() {
        this.info_buttonCancel
                .setOnAction(e -> this.stateManager.gotoState(MainFormState.SELECT_DEVICE));
        this.info_buttonAbortProgram.setOnAction(
                e -> this.stateManager.gotoState(MainFormState.CONFIRM_PROGRAM_ABORTION));

        this.abort_buttonNo
                .setOnAction(e -> this.stateManager.gotoState(MainFormState.DEVICE_INFO));
        this.abort_buttonYes.setOnAction(e -> {
            this.beginWait();
            final ActionContainer actionContainer = new ActionContainer();
            actionContainer.setAction(() -> {
                final Thread t = new Thread(() -> {
                    try {
                        ElwaManager.instance.getExecutionManager()
                                .abortExecution(ElwaManager.instance.getExecutionManager()
                                        .getRunningExecution(this.selectedDevice));
                        this.stateManager.gotoState(MainFormState.SELECT_DEVICE);
                    } catch (final Exception ex) {
                        this.logger.error("Could not abort the running execution.", ex);
                        Platform.runLater(() -> this.displayError("Interner Fehler",
                                ex.getLocalizedMessage(), actionContainer, false));
                    } finally {
                        Platform.runLater(this::endWait);
                    }
                });
                this.beginWait();
                t.start();
            });
            actionContainer.getAction().run();
        });

        this.program_buttonCancel
                .setOnAction(e -> this.stateManager.gotoState(MainFormState.SELECT_DEVICE));
        this.program_buttonForward.setOnAction(e -> {
            this.selectedProgram = this.programList.getSelectionModel().getSelectedItem();

            if (this.selectedProgram != null) {
                this.stateManager.gotoState(MainFormState.CONFIRMATION_WAIT_FOR_CARD);
            }
        });

        this.confirmation_buttonCancel.setOnAction(e -> {
            if (this.selectedDevice.getPrograms().size() == 1) {
                // Überspringe Programmauswahl, wenn nur eines verfügbar ist
                this.stateManager.gotoState(MainFormState.SELECT_DEVICE);
            } else {
                this.stateManager.gotoState(MainFormState.PROGRAM_SELECTED);
            }
        });
        this.confirmation_buttonDoor.setOnAction(e -> {
            // Tür öffnen
            this.beginWait();
            final Execution ex = Execution.getOfflineExecution(this.selectedDevice,
                    Program.getDoorOpenProgram(), User.getAnonymous());
            final ActionContainer actionContainer = new ActionContainer();
            actionContainer.setAction(() -> {
                final Thread actionThread = new Thread(() -> {
                    try {
                        ElwaManager.instance.getExecutionManager().startExecution(ex);
                        Platform.runLater(
                                () -> this.stateManager.gotoState(MainFormState.OPEN_DOOR));
                    } catch (final SQLException e1) {
                        Platform.runLater(() -> {
                            this.logger.error("The execution could not be started.", e1);
                            this.displayError("Datenbankfehler", e1.getLocalizedMessage(),
                                    actionContainer, true);
                        });
                    } catch (final IOException e1) {
                        Platform.runLater(() -> {
                            this.logger.error("The execution could not be started.", e1);
                            this.displayError("Kommunikationsfehler", e1.getLocalizedMessage(),
                                    actionContainer, true);
                        });
                    } catch (final InterruptedException e1) {
                        this.logger.error("The execution could not be started.", e1);
                        Platform.runLater(() -> this
                                .displayError("Interner Fehler", "Das Starten der Ausführung wurde unterbrochen.",
                                        true));
                    } catch (FhemException e1) {
                        this.logger.error("Communication with FHEM-Server failed.", e);
                        Platform.runLater(() -> this.displayError("Kommunikationsfehler",
                                e1.getLocalizedMessage() + "\n" + e1.getCause().getLocalizedMessage(), actionContainer,
                                true));
                    } finally {
                        Platform.runLater(this::endWait);
                    }
                });
                this.beginWait();
                actionThread.start();
            });
            actionContainer.getAction().run();
        });
        this.confirmation_buttonStart.setOnAction(e -> {
            // Starte Programmausführung
            final ActionContainer actionContainer = new ActionContainer();
            actionContainer.setAction(() -> {
                final Thread actionThread = new Thread(() -> {
                    assert this.registeredUser != null;
                    assert this.selectedProgram != null;
                    assert this.selectedDevice != null;
                    final Execution ex;
                    try {
                        ex = ElwaManager.instance.getDataRetriever().newExecution(
                                this.registeredUser, this.selectedProgram, this.selectedDevice);
                    } catch (final SQLException e1) {
                        this.logger.error("The execution cannot be created.", e1);
                        Platform.runLater(() -> {
                            this.displayError("Datenbankfehler", e1.getLocalizedMessage(),
                                    actionContainer, true);
                            this.endWait();
                        });
                        return;
                    }
                    try {
                        ElwaManager.instance.getExecutionManager().startExecution(ex);
                    } catch (final SQLException e1) {
                        this.logger.error("The execution could not be started.", e1);
                        Platform.runLater(() -> {
                            this.displayError("Datenbankfehler", e1.getLocalizedMessage(),
                                    actionContainer, true);
                            this.endWait();
                        });
                        return;
                    } catch (final IOException e1) {
                        this.logger.error("The execution could not be started.", e1);
                        Platform.runLater(() -> {
                            this.displayError("Kommunikationsfehler", e1.getLocalizedMessage(),
                                    actionContainer, true);
                            this.endWait();
                        });
                    } catch (final InterruptedException e1) {
                        this.logger.error("The execution could not be started.", e1);
                        Platform.runLater(() -> {
                            this.displayError("Interner Fehler",
                                    "Das Starten der Ausführung wurde unterbrochen.", true);
                            this.endWait();
                        });
                    } catch (FhemException e1) {
                        this.logger.error("Communication with FHEM-Server failed.", e);
                        Platform.runLater(() -> this.displayError("Kommunikationsfehler",
                                e1.getLocalizedMessage() + "\n" + e1.getCause().getLocalizedMessage(), actionContainer,
                                true));
                    }

                    // Gerätelisten-Eintrag benachrichtigen,
                    // dass er sich aktualisieren muss
                    Platform.runLater(() -> {
                        try {
                            this.updateDevicePane(this.selectedDevice);
                        } catch (final Exception e1) {
                            this.logger.error("Unable to update device pane", e1);
                        }
                    });

                    // Regelmäßige Aktualisierung starten
                    final ScheduledFuture<?> future =
                            this.updateService.scheduleAtFixedRate(new Runnable() {
                                private final Logger logger = LoggerFactory.getLogger(this.getClass());
                                private Device d;
                                private MainFormController c;

                                Runnable init(Device d, MainFormController c) {
                                    this.d = d;
                                    this.c = c;
                                    return this;
                                }

                                @Override
                                public void run() {
                                    Platform.runLater(() -> {
                                        try {
                                            this.c.updateDevicePane(this.d);
                                        } catch (final Exception e) {
                                            this.logger.error("Unable to update device pane", e);
                                        }
                                    });
                                }
                            }.init(this.selectedDevice, this), 1, 1, TimeUnit.SECONDS);
                    this.runningUpdateTasks.put(ex, future);

                    Platform.runLater(() -> {
                        this.stateManager.gotoState(MainFormState.SELECT_DEVICE);
                        this.endWait();
                    });
                });
                this.beginWait();
                actionThread.start();
            });
            actionContainer.getAction().run();
        });
        this.door_buttonDone.setOnAction(e -> {
            final ActionContainer ac = new ActionContainer();
            ac.setAction(() -> {
                final Thread actionThread = new Thread(() -> {
                    ElwaManager.instance.getExecutionManager().abortExecution(ElwaManager.instance.getExecutionManager()
                                    .getRunningExecution(this.selectedDevice));
                    this.stateManager.gotoState(MainFormState.SELECT_DEVICE);
                    this.endWait();
                });
                this.beginWait();
                actionThread.start();
            });
            ac.getAction().run();
        });
        this.error_buttonCancel.setOnAction(e -> this.stateManager.resetAfterError());
        this.error_buttonRetry.setOnAction(e -> {
            this.stateManager.gotoStateBeforeError();
            this.retryAction.run();
        });
    }

    /**
     * Initialisiert die Komponenten
     */
    private void initializeComponents() {
        // 1. Gerätekacheln initialisieren.
        // Zellenfabrik erstellen
        this.device1container.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            this.selectedDevice = this.devices[0];
            this.onDeviceSelected();
        });
        this.device2container.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            this.selectedDevice = this.devices[1];
            this.onDeviceSelected();
        });
        this.device3container.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            this.selectedDevice = this.devices[2];
            this.onDeviceSelected();
        });
        this.device4container.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            this.selectedDevice = this.devices[3];
            this.onDeviceSelected();
        });

        // Geräte-Kacheln mit Daten befüllen
        try {
            final Location location = ElwaManager.instance.getDataRetriever()
                    .getLocation(ElwaManager.instance.getConfigurationManager().getLocationName());
            this.devices = ElwaManager.instance.getDataRetriever().getDevicesToDisplayXs(location);
            for (int i = 0; i < 4; i++) {
                this.updateDevicePane(i);
            }
        } catch (final SQLException e1) {
            this.logger.error("Error during loading data from the database.", e1);
            this.displayError("Datenbankfehler", e1.getLocalizedMessage(),
                    new ActionContainer(() -> Platform.runLater(this::initializeComponents)),
                    false);
        }

        // 2. Programmliste initialisieren
        // Zellenfabrik erstellen
        this.programList.setCellFactory(c -> new ProgramListItem());

        // Auf Änderungen der Auswahl reagieren
        this.programList.getSelectionModel().selectedItemProperty().addListener(e -> {
            if (this.programList.getSelectionModel().getSelectedItem() != null) {
                this.stateManager.gotoState(MainFormState.PROGRAM_SELECTED);
            }
        });

        // Programmliste erstellen
        this.programListData = FXCollections.observableArrayList();

        this.programList.setItems(this.programListData);

    }

    /**
     * Gibt den aktuellen Zustand des Haupt-Fensters zurück.
     *
     * @return Den aktuellen Zustand des Hauptfensters.
     */
    public MainFormState getMainFormState() {
        return this.stateManager.getState();
    }

    /**
     * Gibt die Nachricht des aktuellen Fehlers zurück.
     *
     * @return Die Nachricht des aktuellen Fehlers.
     */
    public String getCurrentErrorMessage() {
        if (this.stateManager.getState() != MainFormState.ERROR
                && this.stateManager.getState() != MainFormState.ERROR_RETRYABLE) {
            return "";
        } else {
            return this.error_title.getText();
        }
    }

    /**
     * Führt notwendige Zustandsänderungen nach der Auswahl eines Geräts durch
     */
    private void onDeviceSelected() {
        if (this.selectedDevice == null || !this.selectedDevice.isEnabled()) {
            return;
        }
        if (ElwaManager.instance.getExecutionManager()
                .getRunningExecution(this.selectedDevice) != null) {
            // Info-Seite anzeigen
            this.stateManager.gotoState(MainFormState.DEVICE_INFO);
        } else {
            if (this.selectedDevice.getPrograms().size() == 1) {
                // Überspringe Programmauswahl, wenn nur eines verfügbar ist
                this.selectedProgram = this.selectedDevice.getPrograms().get(0);
                this.stateManager.gotoState(MainFormState.CONFIRMATION_WAIT_FOR_CARD);
            } else {
                this.stateManager.gotoState(MainFormState.SELECT_PROGRAM);
            }
        }
    }

    /**
     * Aktualisiert ein Gerät auf der Startseite
     *
     * @param d Das zu aktualisierende Gerät
     * @throws SQLException
     */
    private void updateDevicePane(Device d) throws SQLException {
        for (int i = 0; i < 4; i++) {
            if (d.equals(this.devices[i])) {
                this.updateDevicePane(i);
            }
        }
    }

    /**
     * Aktualisiert ein Gerät auf der Startseite
     *
     * @param i Der nullbasierte Index des zu aktualisierenden Geräts
     * @throws SQLException
     */
    private void updateDevicePane(int i) throws SQLException {
        this.logger.trace("Update device " + i);
        final Device device = this.devices[i];
        Pane container;
        Label detailLabel;
        switch (i) {
            case 0:
                container = this.device1container;
                detailLabel = this.device1detailLabel;
                break;
            case 1:
                container = this.device2container;
                detailLabel = this.device2detailLabel;
                break;
            case 2:
                container = this.device3container;
                detailLabel = this.device3detailLabel;
                break;
            case 3:
                container = this.device4container;
                detailLabel = this.device4detailLabel;
                break;
            default:
                this.logger.trace("Index " + i + " is out of bounds.");
                return;
        }

        // Style zurücksetzen
        container.getStyleClass().removeAll("disabled", "occupied");
        detailLabel.setText("");

        if (device == null || !device.isEnabled()) {
            this.logger.trace("Device " + i + " is disabled");
            container.getStyleClass().add("disabled");
        } else if (ElwaManager.instance.getExecutionManager()
                .getRunningExecution(device) != null) {
            this.logger.trace("Device " + i + " is occupied");
            container.getStyleClass().add("occupied");
            detailLabel.setText(ElwaManager.instance.getExecutionManager()
                    .getRunningExecution(device).getUser().getName());
        } else {
            this.logger.trace("Device " + i + " is normal");
            final User lastUser = ElwaManager.instance.getDataRetriever().getLastUser(device);
            detailLabel.setText(lastUser == null ? "" : lastUser.getName());
        }
    }

    /**
     * Zeigt einen Fehler an
     *
     * @param title  Titel
     * @param detail Details
     */
    private void displayError(String title, String detail, boolean backOptionEnabled) {
        this.error_buttonCancel.setDisable(!backOptionEnabled);
        this.error_title.setText(title);
        this.error_detail.setText(
                detail + "\nBitte Log-Datei prüfen, um mehr über diesen Fehler zu erfahren.");
        this.stateManager.gotoState(MainFormState.ERROR);
    }

    /**
     * Zeigt einen Fehler an und bietet das Wiederholen der Aktion an
     *
     * @param title             Titel
     * @param detail            Details
     * @param retryAction       Die wiederholbare Aktion
     * @param backOptionEnabled Ob der Zurück-Button aktiv sein soll
     */
    public void displayError(String title, String detail, ActionContainer retryAction,
                             boolean backOptionEnabled) {
        this.error_buttonCancel.setDisable(!backOptionEnabled);
        this.error_title.setText(title);
        this.error_detail.setText(
                detail + "\nBitte Log-Datei prüfen, um mehr über diesen Fehler zu erfahren.");
        this.retryAction = () -> {
            this.stateManager.gotoStateBeforeError();
            retryAction.getAction().run();
        };
        this.stateManager.gotoState(MainFormState.ERROR_RETRYABLE);
    }

    /**
     * Shows the wait panel
     */
    private void beginWait() {
        if (this.runningWaitPaneDelay != null && !this.runningWaitPaneDelay.isDone()) {
            this.runningWaitPaneDelay.cancel(false);
        }
        this.runningWaitPaneDelay = this.waitPaneService
                .schedule(() -> this.waitPane.setVisible(true), 200, TimeUnit.MILLISECONDS);
    }

    /**
     * Hides the wait panel
     */
    private void endWait() {
        if (this.runningWaitPaneDelay != null && !this.runningWaitPaneDelay.isDone()) {
            this.runningWaitPaneDelay.cancel(false);
        }
        this.waitPane.setVisible(false);
    }

    /**
     * Beende das Aktualisieren eines Eintrags, wenn Ausführung zuende
     */
    @Override
    public void onExecutionFinished(Execution e) {
        Platform.runLater(() -> {
            final ActionContainer actionContainer = new ActionContainer();
            actionContainer.setAction(() -> {
                if (this.runningUpdateTasks.containsKey(e)) {
                    this.runningUpdateTasks.get(e).cancel(false);
                    try {
                        this.updateDevicePane(e.getDevice());
                    } catch (final Exception e1) {
                        this.logger
                                .error("Unable to update device pane after execution terminated");
                        this.displayError("Datenbankfehler", e1.getLocalizedMessage(),
                                actionContainer, false);
                    }
                }
                // Gehe zu Geräteauswahl wenn betroffenes Gerät ausgewählt ist
                if (this.selectedDevice != null && this.selectedDevice.equals(e.getDevice())) {
                    this.stateManager.gotoState(MainFormState.SELECT_DEVICE);
                }
            });
            actionContainer.getAction().run();
        });
    }

    @Override
    public void onExecutionFailed(Execution execution, Exception exception) {
        Platform.runLater(() -> {
            final ActionContainer ac = new ActionContainer();
            ac.setAction(() -> {
                final Thread actionThread = new Thread(() -> {
                    try {
                        ElwaManager.instance.getExecutionManager()
                                .retryFinishExecution(execution);
                    } catch (final SQLException e) {
                        this.logger.error("Could not finish the execution " + execution.getId(), e);
                        this.displayError("Datenbankfehler", e.getLocalizedMessage(), ac, false);
                    } catch (final IOException e) {
                        this.logger.error("Could not finish the execution " + execution.getId(), e);
                        this.displayError("Kommunikationsfehler", e.getLocalizedMessage(), ac,
                                false);
                    } catch (final Exception e) {
                        this.logger.error("Could not finish the execution " + execution.getId(), e);
                        this.displayError("Interner Fehler", e.getLocalizedMessage(), ac, false);
                    } finally {
                        this.endWait();
                    }
                });
                this.beginWait();
                actionThread.start();
            });
            this.logger.error("Could not finish the execution " + execution.getId(), exception);
            if (exception instanceof SQLException) {
                this.displayError("Datenbankfehler", exception.getLocalizedMessage(), ac, false);
            } else if (exception instanceof IOException) {
                this.displayError("Kommunikationsfehler", exception.getLocalizedMessage(), ac,
                        false);
            } else {
                this.displayError("Interner Fehler", exception.getLocalizedMessage(), ac, false);
            }
        });
    }

    /**
     * Wird aufgerufen, sobald das Haupfenster geschlossen werden soll
     */
    @Override
    public void onClose(boolean restart) {
        this.updateService.shutdownNow();
        this.waitPaneService.shutdownNow();
        if (restart) {
            this.updateService = Executors.newScheduledThreadPool(4);
            this.waitPaneService = Executors.newSingleThreadScheduledExecutor();
        }
    }

    /**
     * Wird aufgerufen, sobald eine Karte erkannt wird
     */
    @Override
    public void onCardDetected(CardDetectedEvent e) {
        if (this.stateManager.getState().equals(MainFormState.CONFIRMATION_WAIT_FOR_CARD)
                || this.stateManager.getState()
                .equals(MainFormState.CONFIRMATION_CREDIT_INSUFFICENT)
                || this.stateManager.getState().equals(MainFormState.CONFIRMATION_CARD_UNKNOWN)
                || this.stateManager.getState().equals(MainFormState.CONFIRMATION_USER_BLOCKED)) {
            // Suche den zur Karte passenden Benutzer
            final ActionContainer actionContainer = new ActionContainer();
            actionContainer.setAction(() -> {
                final Runnable searchUserRunnable = () -> {
                    try {
                        this.registeredUser = ElwaManager.instance.getDataRetriever()
                                .getUserByCardId(e.getCardId());
                    } catch (final SQLException e1) {
                        this.logger.error("SQLException while looking up user.", e1);
                        this.registeredUser = null;
                        Platform.runLater(() -> {
                            this.displayError("Datenbankfehler", e1.getLocalizedMessage(),
                                    actionContainer, true);
                            this.endWait();
                        });
                        return;
                    }
                    if (this.registeredUser != null) {
                        if (this.registeredUser.isBlocked()) {
                            // Benutzer gesperrt
                            Platform.runLater(() -> this.stateManager
                                    .gotoState(MainFormState.CONFIRMATION_USER_BLOCKED));
                        } else if (this.registeredUser.canAfford(this.selectedProgram
                                .getPrice(this.selectedProgram.getMaxDuration(), this.registeredUser))) {
                            // Guthaben reicht aus
                            Platform.runLater(() -> this.stateManager
                                    .gotoState(MainFormState.CONFIRMATION_READY));
                        } else {
                            // Guthaben reicht nicht aus
                            Platform.runLater(() -> this.stateManager
                                    .gotoState(MainFormState.CONFIRMATION_CREDIT_INSUFFICENT));

                        }
                    } else {
                        // Kein Benutzer zur Id gefunden.
                        this.logger
                                .warn("There is no user associated to card " + e.getCardId() + ".");
                        Platform.runLater(() -> this.stateManager
                                .gotoState(MainFormState.CONFIRMATION_CARD_UNKNOWN));
                    }

                    Platform.runLater(this::endWait);
                };

                Platform.runLater(this::beginWait);

                final Thread searchUser = new Thread(searchUserRunnable);
                searchUser.setName("CardDetectedThread");
                searchUser.start();
            });

            actionContainer.getAction().run();

        }
    }
}
