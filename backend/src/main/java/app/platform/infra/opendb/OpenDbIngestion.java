package app.platform.infra.opendb;

import app.platform.hardware.ComponentCategory;
import app.platform.hardware.DataIssue;
import app.platform.hardware.HardwareComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Loads a pinned OpenDB snapshot into PostgreSQL.
 *
 * <p>Stores, per record: a normalized projection used by the engines ({@code specs}), the original JSON
 * untouched ({@code raw}), data-quality findings, and product identifiers for matching store listings.
 * Records absent from the new snapshot are marked {@code removed_upstream}, never deleted, so saved builds
 * that reference them stay readable. Re-ingesting the same commit is a no-op.
 */
public final class OpenDbIngestion {

    private static final Logger log = LoggerFactory.getLogger(OpenDbIngestion.class);
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final JsonMapper json;
    private final OpenDbRecordMapper mapper = new OpenDbRecordMapper();

    public OpenDbIngestion(JdbcTemplate jdbc, TransactionTemplate transactions, JsonMapper json) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.json = json;
    }

    public record Summary(String commit, boolean skipped, int records, int rejected, Map<String, CategoryQuality> categories) {
    }

    public record CategoryQuality(int records, int rejected, double averageQuality, Map<String, Map<String, Integer>> issues) {
    }

    private record Parsed(ComponentCategory category, String externalId, String raw, OpenDbRecordMapper.MappedRecord mapped) {
    }

    public boolean alreadyIngested(OpenDbSnapshot snapshot) {
        Integer count = jdbc.queryForObject(
                "select count(*) from source_snapshot where source = ? and source_version = ? and mapper_version = ?",
                Integer.class, OpenDbRecordMapper.SOURCE, snapshot.commit(), OpenDbRecordMapper.VERSION);
        return count != null && count > 0;
    }

    public Summary ingest(OpenDbSnapshot snapshot) {
        if (alreadyIngested(snapshot)) {
            log.info("OpenDB snapshot {} already ingested; skipping", snapshot.commit());
            return new Summary(snapshot.commit(), true, 0, 0, Map.of());
        }
        long started = System.nanoTime();
        ConcurrentLinkedQueue<Parsed> parsed = new ConcurrentLinkedQueue<>();
        Map<ComponentCategory, AtomicInteger> rejected = new TreeMap<>();
        OpenDbRecordMapper.DIRECTORIES.forEach((directory, category) -> {
            rejected.put(category, new AtomicInteger());
            List<Path> files = list(snapshot.categoryDirectory(directory));
            files.parallelStream().forEach(file -> {
                Optional<Parsed> record = parse(category, file);
                record.ifPresentOrElse(parsed::add, () -> rejected.get(category).incrementAndGet());
            });
        });

        Map<String, CategoryQuality> quality = qualitySummary(new ArrayList<>(parsed), rejected);
        int rejectedTotal = rejected.values().stream().mapToInt(AtomicInteger::get).sum();

        transactions.executeWithoutResult(status -> {
            Long snapshotId = jdbc.queryForObject("""
                    insert into source_snapshot (source, source_version, source_url, license, committed_at, record_count,
                        quality_summary, mapper_version)
                    values (?, ?, ?, ?, cast(? as timestamptz), ?, cast(? as jsonb), ?)
                    returning id
                    """, Long.class,
                    OpenDbRecordMapper.SOURCE, snapshot.commit(), snapshot.repository(), snapshot.license(),
                    snapshot.committedAt(), parsed.size(), json.writeValueAsString(quality), OpenDbRecordMapper.VERSION);
            upsertComponents(new ArrayList<>(parsed), snapshotId);
            int removed = jdbc.update("""
                    update hardware_component set status = 'removed_upstream'
                    where source = ? and snapshot_id <> ? and status = 'active'
                    """, OpenDbRecordMapper.SOURCE, snapshotId);
            long identifiersStarted = System.nanoTime();
            replaceIdentifiers(new ArrayList<>(parsed));
            log.info("OpenDB identifiers replaced in {} ms", (System.nanoTime() - identifiersStarted) / 1_000_000);
            log.info("OpenDB {}: {} records stored, {} rejected, {} marked removed upstream",
                    snapshot.commit(), parsed.size(), rejectedTotal, removed);
        });
        log.info("OpenDB ingestion finished in {} s", (System.nanoTime() - started) / 1_000_000_000);
        return new Summary(snapshot.commit(), false, parsed.size(), rejectedTotal, quality);
    }

    private Optional<Parsed> parse(ComponentCategory category, Path file) {
        String externalId = file.getFileName().toString().replaceFirst("\\.json$", "");
        try {
            String raw = Files.readString(file);
            JsonNode node = json.readTree(raw);
            Optional<OpenDbRecordMapper.MappedRecord> mapped = mapper.map(category, node);
            if (mapped.isEmpty()) {
                log.warn("OpenDB record {} rejected: missing id or name", file);
                return Optional.empty();
            }
            if (!mapped.get().component().info().source().externalId().equals(externalId)) {
                log.warn("OpenDB record {} rejected: opendb_id does not match file name", file);
                return Optional.empty();
            }
            return Optional.of(new Parsed(category, externalId, raw, mapped.get()));
        } catch (IOException | RuntimeException e) {
            log.warn("OpenDB record {} rejected: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Loads records into a temporary staging table, then applies set-based changes: rewrite only rows whose content
     * changed (large JSON documents are not rewritten when identical), touch the snapshot reference of unchanged rows,
     * and insert new ones.
     */
    private void upsertComponents(List<Parsed> records, long snapshotId) {
        long started = System.nanoTime();
        jdbc.execute("""
                create temporary table staging_component (
                    id uuid, source text, external_id text, category text, name text, manufacturer text, series text,
                    variant text, release_year integer, specs jsonb, raw jsonb, quality_score real, quality_issues jsonb
                ) on commit drop
                """);
        String insert = """
                insert into staging_component (id, source, external_id, category, name, manufacturer, series, variant,
                    release_year, specs, raw, quality_score, quality_issues)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, cast(? as jsonb))
                """;
        for (int from = 0; from < records.size(); from += BATCH_SIZE) {
            List<Parsed> batch = records.subList(from, Math.min(records.size(), from + BATCH_SIZE));
            jdbc.batchUpdate(insert, batch, batch.size(), (statement, record) -> {
                HardwareComponent component = record.mapped().component();
                var info = component.info();
                statement.setObject(1, info.id());
                statement.setString(2, info.source().source());
                statement.setString(3, info.source().externalId());
                statement.setString(4, info.category().name());
                statement.setString(5, info.name());
                statement.setString(6, info.manufacturer());
                statement.setString(7, info.series());
                statement.setString(8, info.variant());
                if (info.releaseYear() == null) {
                    statement.setNull(9, Types.INTEGER);
                } else {
                    statement.setInt(9, info.releaseYear());
                }
                statement.setString(10, json.writeValueAsString(component));
                statement.setString(11, record.raw());
                statement.setDouble(12, info.quality().score());
                statement.setString(13, json.writeValueAsString(info.quality().issues()));
            });
        }
        jdbc.execute("create index on staging_component (source, external_id)");
        jdbc.execute("analyze staging_component");
        long staged = System.nanoTime();

        int changed = jdbc.update("""
                update hardware_component h set
                    category = s.category, name = s.name, manufacturer = s.manufacturer, series = s.series,
                    variant = s.variant, release_year = s.release_year, specs = s.specs, raw = s.raw,
                    quality_score = s.quality_score, quality_issues = s.quality_issues, status = 'active',
                    snapshot_id = ?, last_seen_at = now()
                from staging_component s
                where h.source = s.source and h.external_id = s.external_id
                  and (h.specs <> s.specs or h.raw <> s.raw or h.quality_issues <> s.quality_issues
                       or h.name is distinct from s.name or h.category <> s.category or h.status <> 'active')
                """, snapshotId);
        int unchanged = jdbc.update("""
                update hardware_component h set snapshot_id = ?, last_seen_at = now()
                from staging_component s
                where h.source = s.source and h.external_id = s.external_id and h.snapshot_id <> ?
                """, snapshotId, snapshotId);
        int inserted = jdbc.update("""
                insert into hardware_component (id, source, external_id, category, name, manufacturer, series, variant,
                    release_year, specs, raw, quality_score, quality_issues, status, snapshot_id)
                select s.id, s.source, s.external_id, s.category, s.name, s.manufacturer, s.series, s.variant,
                    s.release_year, s.specs, s.raw, s.quality_score, s.quality_issues, 'active', ?
                from staging_component s
                where not exists (select 1 from hardware_component h where h.source = s.source and h.external_id = s.external_id)
                """, snapshotId);
        log.info("OpenDB upsert: {} staged in {} ms; {} changed, {} unchanged, {} new in {} ms", records.size(),
                (staged - started) / 1_000_000, changed, unchanged, inserted, (System.nanoTime() - staged) / 1_000_000);
    }

    private void replaceIdentifiers(List<Parsed> records) {
        jdbc.update("delete from component_identifier where component_id in (select id from hardware_component where source = ?)",
                OpenDbRecordMapper.SOURCE);
        List<Object[]> rows = new ArrayList<>();
        for (Parsed record : records) {
            record.mapped().identifiers().stream().distinct().forEach(identifier -> rows.add(new Object[]{
                    record.mapped().component().id(), identifier.type(), identifier.value()}));
        }
        for (int from = 0; from < rows.size(); from += BATCH_SIZE * 4) {
            jdbc.batchUpdate("insert into component_identifier (component_id, type, value) values (?, ?, ?) on conflict do nothing",
                    rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE * 4)));
        }
    }

    private static Map<String, CategoryQuality> qualitySummary(List<Parsed> records, Map<ComponentCategory, AtomicInteger> rejected) {
        Map<String, CategoryQuality> summary = new TreeMap<>();
        for (ComponentCategory category : ComponentCategory.values()) {
            List<Parsed> ofCategory = records.stream().filter(record -> record.category() == category).toList();
            Map<String, Map<String, Integer>> issues = new TreeMap<>();
            double qualityTotal = 0;
            for (Parsed record : ofCategory) {
                var quality = record.mapped().component().info().quality();
                qualityTotal += quality.score();
                for (DataIssue issue : quality.issues()) {
                    issues.computeIfAbsent(issue.field(), key -> new TreeMap<>()).merge(issue.kind().name(), 1, Integer::sum);
                }
            }
            double average = ofCategory.isEmpty() ? 0 : Math.round(qualityTotal / ofCategory.size() * 1000) / 1000.0;
            summary.put(category.name(), new CategoryQuality(ofCategory.size(), rejected.get(category).get(), average, issues));
        }
        return summary;
    }

    private static List<Path> list(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.toString().endsWith(".json")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read OpenDB category directory " + directory, e);
        }
    }
}
