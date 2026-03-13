package com.readywealth.trading.trade_engine.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureNoLegacyFoldersTest {

    @Test
    void businessContextsDoNotUseLegacyRootFolders() throws IOException {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "readywealth", "trading", "trade_engine");

        List<String> contexts = List.of(
                "alerts",
                "auth",
                "engine",
                "execution",
                "marketdata",
                "session",
                "strategy"
        );
        Set<String> allowedRootFolders = Set.of("api", "application", "domain", "infrastructure");
        Set<String> forbiddenLegacyFolders = Set.of(
                "controller",
                "service",
                "store",
                "model",
                "config",
                "websocket",
                "ticks",
                "runtime",
                "aggregation"
        );

        List<String> violations = new ArrayList<>();

        for (String context : contexts) {
            Path ctx = root.resolve(context);
            if (!Files.isDirectory(ctx)) {
                continue;
            }
            try (Stream<Path> children = Files.list(ctx)) {
                children.filter(Files::isDirectory).forEach(child -> {
                    String name = child.getFileName().toString();
                    if (forbiddenLegacyFolders.contains(name)) {
                        violations.add(context + " -> forbidden root folder: " + name);
                        return;
                    }
                    if (!allowedRootFolders.contains(name)) {
                        violations.add(context + " -> non-canonical root folder: " + name);
                    }
                });
            }
        }

        assertTrue(violations.isEmpty(), () -> "Legacy/non-canonical root folders found:\n" + String.join("\n", violations));
    }
}
