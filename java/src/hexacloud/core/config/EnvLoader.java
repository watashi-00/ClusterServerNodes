package hexacloud.core.config;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import hexacloud.core.utils.DebugUtils;

public class EnvLoader {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = EnvLoader.class.getClassLoader().getResourceAsStream("hexacloud.properties")) {
            if(input != null) {
                properties.load(input);
                DebugUtils.log("EnvLoader: Loaded configurations from classpath resource 'hexacloud.properties'");
            }
        } catch(IOException e) {
        }

        if(properties.isEmpty()) {
            try (InputStream input = new FileInputStream("resources/hexacloud.properties")) {
                properties.load(input);
                DebugUtils.log("EnvLoader: Loaded configurations from local file 'resources/hexacloud.properties'");
            } catch(IOException e) {
                try (InputStream input = new FileInputStream("hexacloud.properties")) {
                    properties.load(input);
                    DebugUtils.log("EnvLoader: Loaded configurations from local file 'hexacloud.properties'");
                } catch(IOException ex) {
                    DebugUtils.log("EnvLoader: No 'hexacloud.properties' found in resources/ or CWD. Using system environment variables and defaults.");
                }
            }
        }
    }

    public static String get(String clusterName, String propertyName, String defaultValue) {
        String key = "cluster." + clusterName + "." + propertyName;
        String value = properties.getProperty(key);
        if(value != null) return value.trim();

        String envKey = ("cluster_" + clusterName + "_" + propertyName).toUpperCase().replace("-", "_");
        value = System.getenv(envKey);
        if(value != null) return value.trim();

        String defaultKey = "cluster.default." + propertyName;
        value = properties.getProperty(defaultKey);
        if(value != null) return value.trim();

        String defaultEnvKey = ("cluster_default_" + propertyName).toUpperCase();
        value = System.getenv(defaultEnvKey);
        if(value != null) return value.trim();

        return defaultValue;
    }

    public static boolean getBoolean(String clusterName, String propertyName, boolean defaultValue) {
        String value = get(clusterName, propertyName, null);
        if(value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    public static int getInt(String clusterName, String propertyName, int defaultValue) {
        String value = get(clusterName, propertyName, null);
        if(value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch(NumberFormatException e) {
            return defaultValue;
        }
    }
}
