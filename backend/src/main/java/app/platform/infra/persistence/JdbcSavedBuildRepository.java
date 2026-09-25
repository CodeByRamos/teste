package app.platform.infra.persistence;

import app.platform.builds.SavedBuild;
import app.platform.builds.SavedBuildRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSavedBuildRepository implements SavedBuildRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcSavedBuildRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void save(SavedBuild build) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    insert into saved_build (id, created_at, title, request, total_brl, prices_are_examples, engine_version, catalog_version, view)
                    values (?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, cast(? as jsonb))
                    """,
                    build.id(), Timestamp.from(build.createdAt()), build.title(), build.requestJson(), build.totalBrl(),
                    build.pricesAreExamples(), build.engineVersion(), build.catalogVersion(), build.viewJson());
            List<SavedBuild.SavedItem> items = build.items();
            for (int position = 0; position < items.size(); position++) {
                SavedBuild.SavedItem item = items.get(position);
                jdbc.update("""
                        insert into saved_build_item (build_id, position, component_id, category, source, external_id, owned, price_brl, price_kind)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        build.id(), position, item.componentId(), item.category(), item.source(), item.externalId(),
                        item.owned(), item.priceBrl(), item.priceKind());
            }
        });
    }

    @Override
    public Optional<SavedBuild> find(UUID id) {
        List<SavedBuild.SavedItem> items = jdbc.query("""
                select component_id, category, source, external_id, owned, price_brl, price_kind
                from saved_build_item where build_id = ? order by position
                """, (row, index) -> new SavedBuild.SavedItem(row.getObject(1, UUID.class), row.getString(2), row.getString(3),
                row.getString(4), row.getBoolean(5), row.getBigDecimal(6), row.getString(7)), id);
        return jdbc.query("""
                select id, created_at, title, request::text, total_brl, prices_are_examples, engine_version, catalog_version, view::text
                from saved_build where id = ?
                """, (row, index) -> new SavedBuild(row.getObject(1, UUID.class), row.getTimestamp(2).toInstant(),
                row.getString(3), row.getString(4), items, row.getBigDecimal(5), row.getBoolean(6), row.getString(7),
                row.getString(8), row.getString(9)), id).stream().findFirst();
    }
}
