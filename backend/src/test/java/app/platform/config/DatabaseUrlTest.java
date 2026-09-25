package app.platform.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlTest {

    @Test
    void hostStyleUrlBecomesJdbcUrlAndCredentials() {
        Map<String, String> properties = DatabaseUrl.springProperties(Map.of(
                "DATABASE_URL", "postgresql://postgres:s3cr%40t@postgres.railway.internal:5432/railway?sslmode=disable"));

        assertThat(properties).containsExactly(
                Map.entry("spring.datasource.url", "jdbc:postgresql://postgres.railway.internal:5432/railway?sslmode=disable"),
                Map.entry("spring.datasource.username", "postgres"),
                Map.entry("spring.datasource.password", "s3cr@t"));
    }

    @Test
    void jdbcUrlAndMissingUrlAreLeftAlone() {
        assertThat(DatabaseUrl.springProperties(Map.of("DATABASE_URL", "jdbc:postgresql://db:5432/app"))).isEmpty();
        assertThat(DatabaseUrl.springProperties(Map.of())).isEmpty();
    }

    @Test
    void explicitUsernameWins() {
        Map<String, String> properties = DatabaseUrl.springProperties(Map.of(
                "DATABASE_URL", "postgres://u:p@db/app", "DATABASE_USERNAME", "other"));

        assertThat(properties).containsOnlyKeys("spring.datasource.url");
    }
}
