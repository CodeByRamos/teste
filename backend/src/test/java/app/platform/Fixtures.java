package app.platform;

import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogVersion;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.HardwareComponent;
import app.platform.infra.opendb.OpenDbRecordMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Real OpenDB records (see src/test/resources/opendb-fixture/NOTICE.md) mapped to the domain. */
public final class Fixtures {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Fixtures() {
    }

    public static Path directory() {
        try {
            return Path.of(Fixtures.class.getResource("/opendb-fixture").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<HardwareComponent> components() {
        OpenDbRecordMapper mapper = new OpenDbRecordMapper();
        List<HardwareComponent> components = new ArrayList<>();
        for (Map.Entry<String, ComponentCategory> entry : OpenDbRecordMapper.DIRECTORIES.entrySet()) {
            try (Stream<Path> files = Files.list(directory().resolve(entry.getKey()))) {
                for (Path file : files.toList()) {
                    mapper.map(entry.getValue(), JSON.readTree(Files.readString(file)))
                            .ifPresent(record -> components.add(record.component()));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return components;
    }

    @SuppressWarnings("unchecked")
    public static <T extends HardwareComponent> T one(Class<T> type) {
        return (T) components().stream().filter(type::isInstance).findFirst().orElseThrow();
    }

    public static Catalog catalog() {
        return new Catalog(components(), CatalogVersion.NONE);
    }
}
