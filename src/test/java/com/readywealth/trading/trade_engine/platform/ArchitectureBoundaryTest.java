package com.readywealth.trading.trade_engine.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureBoundaryTest {

    @Test
    void newDomainPackagesDoNotImportFrameworks() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "readywealth", "trading", "trade_engine");
        List<Path> domainRoots = List.of(
                root.resolve("auth/domain"),
                root.resolve("marketdata/domain"),
                root.resolve("session/domain"),
                root.resolve("strategy/domain"),
                root.resolve("execution/domain"),
                root.resolve("alerts/domain")
        );

        List<String> forbiddenPrefixes = List.of(
                "import org.springframework",
                "import jakarta.",
                "import com.zerodhatech",
                "import org.ta4j",
                "import org.apache.kafka",
                "import org.springframework.data.redis"
        );

        List<String> violations = new ArrayList<>();

        for (Path domainRoot : domainRoots) {
            if (!Files.exists(domainRoot)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(domainRoot)) {
                walk.filter(path -> path.toString().endsWith(".java"))
                        .forEach(javaFile -> {
                            try {
                                List<String> lines = Files.readAllLines(javaFile);
                                for (String line : lines) {
                                    String trimmed = line.trim();
                                    for (String prefix : forbiddenPrefixes) {
                                        if (trimmed.startsWith(prefix)) {
                                            violations.add(javaFile + " -> " + trimmed);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                violations.add(javaFile + " -> " + e.getMessage());
                            }
                        });
            }
        }

        assertTrue(violations.isEmpty(), () -> "Forbidden domain imports found:\n" + String.join("\n", violations));
    }
}
