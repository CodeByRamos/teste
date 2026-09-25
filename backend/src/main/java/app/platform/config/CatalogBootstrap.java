package app.platform.config;

import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogHolder;
import app.platform.infra.opendb.OpenDbIngestion;
import app.platform.infra.opendb.OpenDbSnapshot;
import app.platform.infra.persistence.JdbcCatalogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * At startup: optionally ingests the configured OpenDB snapshot (skipped when that commit is already stored),
 * then loads the active catalog into memory.
 */
@Component
class CatalogBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogBootstrap.class);

    private final PlatformProperties properties;
    private final OpenDbIngestion ingestion;
    private final JdbcCatalogRepository repository;
    private final CatalogHolder holder;

    CatalogBootstrap(PlatformProperties properties, OpenDbIngestion ingestion, JdbcCatalogRepository repository, CatalogHolder holder) {
        this.properties = properties;
        this.ingestion = ingestion;
        this.repository = repository;
        this.holder = holder;
    }

    @Override
    public void run(ApplicationArguments args) {
        PlatformProperties.OpenDb opendb = properties.opendb();
        if (opendb.ingestOnStartup() && opendb.snapshotDir() != null) {
            OpenDbSnapshot snapshot = OpenDbSnapshot.open(opendb.snapshotDir(), PlatformConfiguration.storageJson());
            OpenDbIngestion.Summary summary = ingestion.ingest(snapshot);
            if (!summary.skipped()) {
                log.info("Ingested OpenDB {}: {} records, {} rejected", summary.commit(), summary.records(), summary.rejected());
            }
        }
        long started = System.currentTimeMillis();
        Catalog catalog = repository.loadActive();
        holder.replace(catalog);
        log.info("Catalog loaded: {} components from {} {} in {} ms", catalog.size(), catalog.version().source(),
                catalog.version().sourceVersion(), System.currentTimeMillis() - started);
        if (catalog.size() == 0) {
            log.warn("Catalog is empty. Fetch a snapshot with scripts/fetch-opendb.sh and set platform.opendb.snapshot-dir.");
        }
    }
}
