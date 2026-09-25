package app.platform.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepts the {@code postgresql://user:password@host:port/database} URL that hosts such as Railway and Heroku
 * provide in {@code DATABASE_URL}, and turns it into the JDBC URL and credentials Spring expects.
 * A {@code jdbc:} URL is left alone, so AWS RDS style configuration keeps working.
 */
public final class DatabaseUrl {

    private DatabaseUrl() {
    }

    /** Spring properties to set, or an empty map when {@code DATABASE_URL} is absent or already a JDBC URL. */
    public static Map<String, String> springProperties(Map<String, String> environment) {
        String url = environment.get("DATABASE_URL");
        if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            return Map.of();
        }
        URI uri = URI.create(url);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("DATABASE_URL has no host");
        }
        Map<String, String> properties = new LinkedHashMap<>();
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (uri.getRawQuery() != null) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        properties.put("spring.datasource.url", jdbc.toString());
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !environment.containsKey("DATABASE_USERNAME")) {
            int colon = userInfo.indexOf(':');
            properties.put("spring.datasource.username", decode(colon < 0 ? userInfo : userInfo.substring(0, colon)));
            if (colon >= 0) {
                properties.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return properties;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
