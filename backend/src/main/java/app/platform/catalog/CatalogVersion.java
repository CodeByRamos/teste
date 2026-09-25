package app.platform.catalog;

import java.time.Instant;

/**
 * Which source snapshot the catalog was built from, so every answer can be traced back to data.
 *
 * @param sourceVersion upstream revision (for OpenDB, the git commit)
 */
public record CatalogVersion(String source, String sourceVersion, String sourceUrl, String license, Instant ingestedAt) {

    public static final CatalogVersion NONE = new CatalogVersion("none", "none", null, null, null);
}
