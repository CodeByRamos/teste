package app.platform.catalog;

import java.util.concurrent.atomic.AtomicReference;

/** The catalog currently served. Swapped atomically after an ingestion so requests never see a partial catalog. */
public final class CatalogHolder {

    private final AtomicReference<Catalog> current = new AtomicReference<>(Catalog.empty());

    public Catalog current() {
        return current.get();
    }

    public void replace(Catalog catalog) {
        current.set(catalog);
    }
}
