package net.instantcom.keiko.config;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public final class Configuration {

    private static final Log log = LogFactory.getLog(Configuration.class);
    private static final Configuration instance = new Configuration();

    public static final int DEFAULT_PORT = 6881;

    private Configuration() {
        reload();
    }

    public static Configuration getInstance() {
        return instance;
    }

    /**
     * Reloads configuration.
     */
    public void reload() {
        try {
            Properties properties = new Properties();
            properties.load(new FileReader(new File("src/conf/keiko.conf")));
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
        return "true".equalsIgnoreCase(s) || "y".equalsIgnoreCase(s)
            || "1".equals(s);
    }

    private Properties properties;

}
