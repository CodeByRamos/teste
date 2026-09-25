package app.platform.infra.persistence;

import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogVersion;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads the active catalog from PostgreSQL into the in-memory {@link Catalog}. */
public final class JdbcCatalogRepository {

    private static final Map<ComponentCategory, Class<? extends HardwareComponent>> TYPES = Map.of(
            ComponentCategory.CPU, Cpu.class,
            ComponentCategory.GPU, Gpu.class,
            ComponentCategory.MOTHERBOARD, Motherboard.class,
            ComponentCategory.MEMORY, Memory.class,
            ComponentCategory.STORAGE, Storage.class,
            ComponentCategory.POWER_SUPPLY, PowerSupply.class,
            ComponentCategory.CASE, PcCase.class,
            ComponentCategory.CPU_COOLER, CpuCooler.class);

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public JdbcCatalogRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Catalog loadActive() {
        List<HardwareComponent> components = new ArrayList<>();
        jdbc.query("select category, specs::text from hardware_component where status = 'active'", row -> {
            ComponentCategory category = ComponentCategory.valueOf(row.getString(1));
            components.add(json.readValue(row.getString(2), TYPES.get(category)));
        });
        return new Catalog(components, latestVersion().orElse(CatalogVersion.NONE));
    }

    public Optional<CatalogVersion> latestVersion() {
        return jdbc.query("""
                select source, source_version, source_url, license, ingested_at
                from source_snapshot order by ingested_at desc limit 1
                """, (row, index) -> new CatalogVersion(row.getString(1), row.getString(2), row.getString(3),
                row.getString(4), row.getObject(5, Timestamp.class).toInstant())).stream().findFirst();
    }

    /** Data-quality summary computed at ingestion for the latest snapshot, as JSON. */
    public Optional<String> latestQualitySummary() {
        return jdbc.query("select quality_summary::text from source_snapshot order by ingested_at desc limit 1",
                (row, index) -> row.getString(1)).stream().findFirst();
    }
}
