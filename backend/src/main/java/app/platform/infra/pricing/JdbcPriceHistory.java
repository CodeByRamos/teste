package app.platform.infra.pricing;

import app.platform.pricing.PriceHistory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class JdbcPriceHistory implements PriceHistory {

    private final JdbcTemplate jdbc;

    public JdbcPriceHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LowestPrice> lowestSince(UUID componentId, Instant since) {
        return jdbc.query("""
                select price_brl, store_name, observed_at from price_observation
                where component_id = ? and observed_at >= ?
                order by price_brl, observed_at desc
                limit 1
                """, (row, index) -> new LowestPrice(row.getBigDecimal(1), row.getString(2), row.getTimestamp(3).toInstant()),
                componentId, Timestamp.from(since)).stream().findFirst();
    }

    @Override
    public java.util.List<DailyLow> dailyLows(UUID componentId, Instant since) {
        return jdbc.query("""
                select (observed_at at time zone 'America/Sao_Paulo')::date as day, store_name, min(price_brl)
                from price_observation
                where component_id = ? and observed_at >= ?
                group by 1, 2
                order by 1, 2
                """, (row, index) -> new DailyLow(row.getObject(1, java.time.LocalDate.class), row.getString(2), row.getBigDecimal(3)),
                componentId, Timestamp.from(since));
    }
}
