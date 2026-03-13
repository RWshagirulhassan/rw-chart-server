package com.readywealth.trading.trade_engine.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitecturePackagePathConsistencyTest {

    @Test
    void javaPackageDeclarationsMatchDirectoryPath() throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        List<String> violations = new ArrayList<>();

        validateSourceRoot(projectRoot.resolve("src/main/java"), violations);
        validateSourceRoot(projectRoot.resolve("src/test/java"), violations);

        assertTrue(violations.isEmpty(), () -> "Package/path mismatches found:\n" + String.join("\n", violations));
    }

    private static void validateSourceRoot(Path sourceRoot, List<String> violations) throws IOException {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(path -> path.toString().endsWith(".java"))
                    .forEach(javaFile -> validateJavaFile(sourceRoot, javaFile, violations));
        }
    }

    private static void validateJavaFile(Path sourceRoot, Path javaFile, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(javaFile);
            String packageLine = lines.stream()
                    .map(String::trim)
                    .filter(line -> line.startsWith("package "))
                    .findFirst()
                    .orElse(null);

            if (packageLine == null || !packageLine.endsWith(";")) {
                violations.add(javaFile + " -> missing package declaration");
                return;
            }

            String declaredPackage = packageLine
                    .substring("package ".length(), packageLine.length() - 1)
                    .trim();

            String expectedPackage = sourceRoot.relativize(javaFile.getParent())
                    .toString()
                    .replace('/', '.')
                    .replace('\\', '.');

            if (!declaredPackage.equals(expectedPackage)) {
                violations.add(javaFile + " -> declared=" + declaredPackage + ", expected=" + expectedPackage);
            }
        } catch (IOException e) {
            violations.add(javaFile + " -> " + e.getMessage());
        }
    }
}
