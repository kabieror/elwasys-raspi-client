package org.kabieror.elwasys.raspiclient.configuration;

import org.kabieror.elwasys.common.ConfigurationManager;
import org.kabieror.elwasys.common.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Diese Klasse verwaltet die Konfiguration
 *
 * @author Oliver Kabierschke
 */
public class WashguardConfiguration extends ConfigurationManager {
    private static final String DS = System.getProperty("file.separator");
    private static final String FILE_NAME = System.getProperty("user.dir") + DS + "elwasys.properties";
    private static final String DEFAULTS_FILE_NAME =
            "/org/kabieror/elwasys/raspiclient/resources/defaultconfig.properties";

    private final File uidFile = new File(System.getProperty("user.dir") + DS + ".client-uid");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private String uid = null;

    /**
     * Constructor
     *
     * @throws Exception
     */
    public WashguardConfiguration() throws Exception {
        super();
    }

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    public InputStream getDefaultsFileStream() {
        return WashguardConfiguration.class.getResourceAsStream(DEFAULTS_FILE_NAME);
    }

    /**
     * Gibt die Adresse, unter welcher der zu verwendende FHEM-Server erreichbar
     * ist.
     *
     * @return Die Adresse, unter welcher der zu verwendende FHEM-Server erreichbar ist.
     */
    public String getFhemConnectionString() {
        return this.props.getProperty("fhem.server");
    }

    /**
     * Gibt den TCP-Port zurück, auf welchem der FHEM-Server hört.
     *
     * @return Der TCP-Port, auf welchem der FHEM-Server hört.
     */
    public int getFhemPort() {
        return Integer.parseInt(this.props.getProperty("fhem.port"));
    }

    /**
     * Gibt den Name des Standorts des Waschwächters zurück (z.B. Waschküche1)
     *
     * @return Den Namen des Standortes des Waschwächters
     */
    public String getLocationName() {
        return this.props.getProperty("location");
    }

    /**
     * Gibt die Zeit bis zum Abdunkeln des Displays zurück
     *
     * @return Die Zeit bis zum Abdunkeln des Displays
     */
    public Duration getDisplayTimeout() {
        long secs;
        try {
            secs = Long.parseLong(this.props.getProperty("displayTimeout"));
        } catch (final NumberFormatException e) {
            this.logger.warn("The configuration value displayTimeout has an invalid format. Using 60 seconds instead.");
            return Duration.ofSeconds(60);
        }
        return Duration.ofSeconds(secs);
    }

    /**
     * Gibt die Zeit zurück, für die der Startbildschirm auf jeden Fall
     * angezeigt wird
     *
     * @return Die Zeit, die der Startbildschirm auf jeden Fall angezeigt wird
     */
    public Duration getStartupDelay() {
        long secs;
        try {
            secs = Long.parseLong(this.props.getProperty("startupDelay"));
        } catch (final NumberFormatException e) {
            this.logger.warn("The configuration value startupDelay has an invalid format. Using 2 seconds instead.");
            return Duration.ofSeconds(2);
        }
        return Duration.ofSeconds(secs);
    }

    /**
     * Der API-Token dieser Anwendung bei Pushover.
     *
     * @return Den API-Token dieser Anwendung bei Pushover.
     */
    public String getPushoverApiToken() {
        return "abgQotPcAEUncZEF9AsFy3T2M36jQ7";
    }

    public String getIonicApiToken() {
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiJkNzg1Yzg2Mi1hYzhmLTRiYTgtYjZmMi1mZDgwMGQzZDU3ZTUifQ.B0a4pnx_n7V2vR_Vxkv7urE2FCrvUG0-Glt0lhn-7Po";
    }

    /**
     * Die IP-Adresse, über welche der Wartungsserver kontaktiert werden soll.
     *
     * @return Die IP-Adresse des Wartungsservers.
     */
    public String getMaintenanceServer() {
        final String ip = this.props.getProperty("maintenance.server");
        if (ip == null || ip.isEmpty()) {
            return "";
        } else if (!ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            this.logger.warn("The configuration value 'maintenance.server' has an invalid format and cannot be used.");
            return "";
        }
        return ip;
    }

    /**
     * Der Port, auf welchem der Wartungsserver erreichbar ist.
     *
     * @return Der Port, auf welchem der Wartungsserver erreichbar ist.
     */
    public int getMaintenancePort() {
        int port;
        try {
            port = Integer.parseInt(this.props.getProperty("maintenance.port"));
        } catch (final NumberFormatException e) {
            this.logger.warn("The configuration valid maintenance.port has an invalid format. Using the default 3591 " +
                    "instead.");
            return 3591;
        }
        return port;
    }

    /**
     * Die Zeit nach der letzten Aktion eines Benutzers, nach der der angemeldete Benutzer automatisch abgemeldet werden
     * soll.
     */
    public int getAutoLogoutSeconds() {
        int time;
        try {
            time = Integer.parseInt(this.props.getProperty("sessionTimeout"));
        } catch (final NumberFormatException e) {
            this.logger
                    .warn("The configuration value 'sessionTimeout' has an invalid format. Using the default value " +
                            "instead.");
            return 20;
        }
        return time;
    }

    /**
     * Gibt die eindeutige ID dieses Clients zurück.
     *
     * @return Die eindeutige ID dieses Clients.
     */
    public String getUid() {
        if (this.uid == null) {
            if (this.uidFile.exists()) {
                try {
                    this.uid = Files.readAllLines(this.uidFile.toPath()).get(0).trim();
                    this.logger.info("Using the previously stored uid " + this.uid);
                } catch (final IOException e) {
                    this.logger.error("Could not read the uid file " + this.uidFile.getPath());
                    this.uid = Utilities.generateUid();
                }
            } else {
                this.uid = Utilities.generateUid();
                this.logger.info("Generated uid " + this.uid);
                try {
                    Files.write(this.uidFile.toPath(), this.uid.getBytes(), StandardOpenOption.CREATE);
                } catch (final IOException e) {
                    this.logger.error("Could not write the uid to the file " + this.uidFile.getPath());
                }
            }
        }
        return this.uid;
    }
}
