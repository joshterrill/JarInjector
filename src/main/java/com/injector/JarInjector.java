package com.injector;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarInjector {

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.out.println("Usage: java -jar jar-injector.jar <input-jar> <output-jar> [class-to-modify]");
            System.out.println("Or: java -jar jar-injector.jar <input-jar> -dShowClasses");
            return;
        }

        String inputJarPath = args[0];

        if (args.length == 2 && args[1].equals("-dShowClasses")) {
            // Print all classes and methods
            showClassesAndMethods(inputJarPath);
            return;
        }

        String outputJarPath = args[1];
        String classNameToModify = (args.length == 3) ? args[2] : null;

        File tempDir = new File("temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        // Extract the JAR file and log all classes
        extractJar(inputJarPath, tempDir);

        // Determine the main class if no class is specified
        if (classNameToModify == null) {
            classNameToModify = getMainClassFromJar(inputJarPath);
            if (classNameToModify == null) {
                System.err.println("No main class found in the JAR manifest and no class specified.");
                return;
            }
        }

        // Modify the specified class
        modifyClass(tempDir, classNameToModify);

        // Repackage the JAR file
        createJar(tempDir, outputJarPath);

        // Clean up temporary files
        deleteDirectory(tempDir);
    }

    private static void extractJar(String jarPath, File destDir) throws IOException {
        JarFile jarFile = new JarFile(jarPath);
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            File entryFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                entryFile.mkdirs();
            } else {
                entryFile.getParentFile().mkdirs();
                try (InputStream is = jarFile.getInputStream(entry);
                     FileOutputStream os = new FileOutputStream(entryFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
        }
        jarFile.close();
    }

    private static String getMainClassFromJar(String jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                return manifest.getMainAttributes().getValue("Main-Class");
            }
        }
        return null;
    }

    private static void modifyClass(File tempDir, String className) throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(tempDir.getPath()); // Add the directory to the class pool
        CtClass ctClass = pool.get(className);

        // Check if main method exists, if not add one
        boolean hasMainMethod = false;
        try {
            CtMethod mainMethod = ctClass.getDeclaredMethod("main");
            hasMainMethod = mainMethod != null;
        } catch (javassist.NotFoundException e) {
            // Method not found, so add it
            CtMethod mainMethod = CtMethod.make("public static void main(String[] args) { System.out.println(\"Main method added\"); }", ctClass);
            ctClass.addMethod(mainMethod);
        }

        // Modify the init method or main method if init is not available
        try {
            CtMethod method = ctClass.getDeclaredMethod("init");
            method.insertBefore("{ System.out.println(\"Injected code before init\"); }");
        } catch (javassist.NotFoundException e) {
            // If init method is not found, inject into the main method
            CtMethod mainMethod = ctClass.getDeclaredMethod("main");
            mainMethod.insertBefore("{ System.out.println(\"Injected code before main\"); }");
        }

        ctClass.writeFile(tempDir.getPath());
    }

    private static void createJar(File sourceDir, String jarPath) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath))) {
            addFilesToJar(sourceDir, sourceDir, jos);
        }
    }

    private static void addFilesToJar(File sourceDir, File currentDir, JarOutputStream jos) throws IOException {
        for (File file : currentDir.listFiles()) {
            if (file.isDirectory()) {
                addFilesToJar(sourceDir, file, jos);
            } else {
                String entryName = sourceDir.toURI().relativize(file.toURI()).getPath();
                jos.putNextEntry(new JarEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                }
                jos.closeEntry();
            }
        }
    }

    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                deleteDirectory(file);
            }
        }
        dir.delete();
    }

    private static void showClassesAndMethods(String jarPath) throws Exception {
        File tempDir = new File("temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        // Extract the JAR file to the temporary directory
        extractJar(jarPath, tempDir);

        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(tempDir.getPath()); // Add the directory to the class pool

        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            System.out.println("Classes and methods defined in the JAR:");
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    System.out.println("Class: " + className);
                    CtClass ctClass = pool.get(className);
                    for (CtMethod method : ctClass.getDeclaredMethods()) {
                        System.out.println("  Method: " + method.getName());
                    }
                }
            }
        }

        // Clean up temporary files
        deleteDirectory(tempDir);
    }
}
