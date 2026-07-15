package hexacloud.core.utils;

import java.io.File;

/**
 * Utility class for path resolution and directory traversal helper methods.
 */
public class PathUtils {

    private PathUtils() {}

    /**
     * Recursively searches for a directory with the given name starting from the base directory.
     * Skips build and metadata folders for performance optimization.
     */
    public static File findDirectory(File current, String targetName) {
        if (current == null || !current.exists()) {
            return null;
        }

        File direct = new File(current, targetName);
        if (direct.isDirectory()) {
            return direct;
        }

        File[] children = current.listFiles(File::isDirectory);
        if (children != null) {
            for (File child : children) {
                String name = child.getName();
                if (name.equals(".git") || name.equals("target") || name.equals("build") || 
                    name.equals("bin") || name.equals(".idea") || name.equals(".gradle") || 
                    name.equals(".gemini")) {
                    continue;
                }
                File res = findDirectory(child, targetName);
                if (res != null) {
                    return res;
                }
            }
        }
        return null;
    }

    /**
     * Finds the resources directory in the workspace tree, walking up to parent directories if not found locally.
     */
    public static File findResourcesDir() {
        File resourcesDir = findDirectory(new File("."), "resources");
        if (resourcesDir == null) {
            File parent = new File(".").getAbsoluteFile();
            for (int i = 0; i < 3; i++) {
                parent = parent.getParentFile();
                if (parent == null) break;
                resourcesDir = findDirectory(parent, "resources");
                if (resourcesDir != null) break;
            }
        }
        return resourcesDir;
    }
}
