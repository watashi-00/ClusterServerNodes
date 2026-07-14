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
                DebugUtils.info("EnvLoader: Loaded configurations from classpath resource 'hexacloud.properties'");
            }
        } catch(IOException e) {
            // Ignore classpath loading error and try local files
            DebugUtils.log("EnvLoader: Failed to load from classpath. " + e.getMessage());
        }

        // 2. Try loading from resources/ folder, java/resources/ or CWD
        if(properties.isEmpty()) {
            String[] possiblePaths = {
                "resources/hexacloud.properties",
                "java/resources/hexacloud.properties",
                "hexacloud.properties"
            };

            boolean loaded = false;

            for(String path : possiblePaths) {
                try(InputStream input = new FileInputStream(path)) {
                    properties.load(input);
                    DebugUtils.info("EnvLoader: Loaded configurations from local file '" + path + "'");
                    loaded = true;
                    break;
                } catch(IOException ex) {
                    DebugUtils.log("EnvLoader: File not found or unreadable at '" + path + "'");
                }
            }

            if (!loaded) {
                DebugUtils.error("EnvLoader: No 'hexacloud.properties' found. Using system environment variables and defaults.");
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
