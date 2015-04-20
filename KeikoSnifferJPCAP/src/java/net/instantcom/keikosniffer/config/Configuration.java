package net.instantcom.keikosniffer.config;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class Configuration {

    private static final Log log = LogFactory.getLog(Configuration.class);

    private static final File LINUX_CONFIG = new File("/opt/keiko-sniffer/conf/keiko-sniffer.conf");
    private static final File WINDOWS_CONFIG = new File("../conf/keiko-sniffer.conf");
    private static final File DEVELOPMENT_CONFIG = new File("src/java/keikosniffer_devel.conf");

    private static final Configuration instance = new Configuration();

    private Configuration() {
        reload();
    }

    public static Configuration getInstance() {
        return instance;
    }

    public boolean isDevelopment() {
        return DEVELOPMENT_CONFIG.exists();
    }

    public boolean isLinuxPlatform() {
        return LINUX_CONFIG.exists();
    }

    public boolean isWindowsPlatform() {
        return WINDOWS_CONFIG.exists();
    }

    public File getConfigurationFile() {
        return isDevelopment() ? DEVELOPMENT_CONFIG : LINUX_CONFIG;
    }

    /**
     * Reloads configuration.
     */
    public void reload() {
        try {
            Properties properties = new Properties();
            properties.load(new FileReader(getConfigurationFile()));
            this.properties = properties;
        } catch (Exception e) {
            log.error("can't load configuration", e);
        }
    }

    public String getString(String key, String defaultValue) {
        String s = properties.getProperty(key);
        if (null == s || "".equals(s)) {
            return defaultValue;
        }
        return s.trim();
    }

    public int getInt(String key, int defaultValue) {
        String s = properties.getProperty(key);
        if (null == s || "".equals(s)) {
            return defaultValue;
        }
        return Integer.parseInt(s.trim());
    }

    public long getLong(String key, long defaultValue) {
        String s = properties.getProperty(key);
        if (null == s || "".equals(s)) {
            return defaultValue;
        }
        return Long.parseLong(s.trim());
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String s = properties.getProperty(key);
        if (null == s || "".equals(s)) {
            return defaultValue;
        }
        s = s.trim();
        return "true".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s) || "1".equals(s);
    }

    private Properties properties;

}
