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

    /**
     * Scans the classpath for classes that implement the specified interface and are not abstract/interface.
     */
    public static java.util.List<Class<?>> scanClasspathForImplementations(Class<?> interfaceClass) {
        java.util.List<Class<?>> implementations = new java.util.ArrayList<>();
        String classpath = System.getProperty("java.class.path");
        String pathSeparator = System.getProperty("path.separator");
        String[] entries = classpath.split(pathSeparator);

        for (String entry : entries) {
            File file = new File(entry);
            if (!file.exists()) continue;

            if (file.isDirectory()) {
                scanDirectoryForClasses(file, "", interfaceClass, implementations);
            } else if (file.isFile() && file.getName().endsWith(".jar")) {
                scanJarForClasses(file, interfaceClass, implementations);
            }
        }
        return implementations;
    }

    private static void scanDirectoryForClasses(File directory, String packageName, Class<?> interfaceClass, java.util.List<Class<?>> implementations) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                if (name.equals(".git") || name.equals("target") || name.equals("build") || 
                    name.equals("bin") || name.equals(".idea") || name.equals(".gradle") || 
                    name.equals(".gemini")) {
                    continue;
                }
                String subPackage = packageName.isEmpty() ? name : packageName + "." + name;
                scanDirectoryForClasses(file, subPackage, interfaceClass, implementations);
            } else if (name.endsWith(".class")) {
                String className = packageName.isEmpty() ? 
                    name.substring(0, name.length() - 6) : 
                    packageName + "." + name.substring(0, name.length() - 6);
                tryLoadClass(className, interfaceClass, implementations);
            }
        }
    }

    private static void scanJarForClasses(File jarFile, Class<?> interfaceClass, java.util.List<Class<?>> implementations) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    String className = name.replace("/", ".").substring(0, name.length() - 6);
                    tryLoadClass(className, interfaceClass, implementations);
                }
            }
        } catch (Exception e) {
            // Ignore jar reading errors
        }
    }

    private static void tryLoadClass(String className, Class<?> interfaceClass, java.util.List<Class<?>> implementations) {
        try {
            Class<?> clazz = Class.forName(className);
            if (interfaceClass.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                implementations.add(clazz);
            }
        } catch (Throwable t) {
            // Ignore classes that cannot be loaded
        }
    }
}
