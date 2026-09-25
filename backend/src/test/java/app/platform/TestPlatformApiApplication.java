package app.platform;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local development launcher: starts a real PostgreSQL (embedded binaries, no Docker needed), points the
 * API at it and ingests the pinned OpenDB snapshot on first run. Data persists in {@code backend/.local}.
 *
 * <p>Run with {@code ./mvnw spring-boot:test-run}. Production uses {@link PlatformApiApplication} with a managed
 * PostgreSQL (e.g. AWS RDS) configured through environment variables.
 */
public class TestPlatformApiApplication {

    private static final int DEV_DATABASE_PORT = 54329;

    public static void main(String[] args) throws IOException {
        Path dataDirectory = Path.of(".local", "postgres").toAbsolutePath();
        Files.createDirectories(dataDirectory);
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(dataDirectory)
                .setCleanDataDirectory(false)
                .setPort(DEV_DATABASE_PORT)
                // After an unclean stop PostgreSQL replays its log before accepting connections.
                .setPGStartupWait(java.time.Duration.ofSeconds(90))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // shutting down anyway
            }
        }));

        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("platform.opendb.ingest-on-startup", "true");
        System.setProperty("platform.rate-limit.trusted-proxies", "127.0.0.1,0:0:0:0:0:0:0:1");
        currentSnapshot().ifPresent(dir -> System.setProperty("platform.opendb.snapshot-dir", dir.toString()));

        PlatformApiApplication.main(args);
    }

    /** data/opendb/CURRENT holds the commit fetched by scripts/fetch-opendb.sh. */
    private static java.util.Optional<Path> currentSnapshot() throws IOException {
        Path root = Path.of("..", "data", "opendb").toAbsolutePath().normalize();
        Path current = root.resolve("CURRENT");
        if (!Files.isRegularFile(current)) {
            System.err.println("No OpenDB snapshot found. Run scripts/fetch-opendb.sh from the repository root.");
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(root.resolve(Files.readString(current).trim()));
    }
}
