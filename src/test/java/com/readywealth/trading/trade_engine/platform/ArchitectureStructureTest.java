package com.readywealth.trading.trade_engine.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureStructureTest {

    @Test
    void businessContextsFollowCanonicalTemplate() {
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

        for (String context : contexts) {
            Path ctx = root.resolve(context);
            assertTrue(Files.isDirectory(ctx), "Missing context folder: " + context);
            assertTrue(Files.exists(ctx.resolve("README.md")), "Missing README.md for context: " + context);
            assertTrue(Files.exists(ctx.resolve("package-info.java")), "Missing package-info.java for context: " + context);

            assertTrue(Files.isDirectory(ctx.resolve("application")), "Missing application layer for context: " + context);
            assertTrue(Files.isDirectory(ctx.resolve("domain")), "Missing domain layer for context: " + context);
            assertTrue(Files.isDirectory(ctx.resolve("infrastructure")), "Missing infrastructure layer for context: " + context);
        }
    }

    @Test
    void expectedApiFoldersExistForContextsThatExposeTransports() {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "readywealth", "trading", "trade_engine");
        List<String> required = List.of(
                "auth/api/http",
                "engine/api/http",
                "execution/api/http",
                "execution/api/ws",
                "marketdata/api/http",
                "marketdata/api/ws",
                "session/api/http"
        );

        for (String folder : required) {
            assertTrue(Files.isDirectory(root.resolve(folder)), "Missing transport folder: " + folder);
        }
    }

    @Test
    void supportContextsArePresent() {
        Path root = Path.of(System.getProperty("user.dir"), "src", "main", "java", "com", "readywealth", "trading", "trade_engine");

        assertTrue(Files.isDirectory(root.resolve("platform/config")), "Missing platform/config");
        assertTrue(Files.isDirectory(root.resolve("platform/web")), "Missing platform/web");
        assertTrue(Files.exists(root.resolve("platform/README.md")), "Missing platform README");
        assertTrue(Files.exists(root.resolve("platform/package-info.java")), "Missing platform package-info");

        assertTrue(Files.isDirectory(root.resolve("shared")), "Missing shared context");
        assertTrue(Files.exists(root.resolve("shared/README.md")), "Missing shared README");
        assertTrue(Files.exists(root.resolve("shared/package-info.java")), "Missing shared package-info");
    }
}
