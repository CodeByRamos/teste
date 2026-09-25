package app.platform.infra.opendb;

import app.platform.hardware.ComponentCategory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A pinned OpenDB checkout produced by {@code scripts/fetch-opendb.sh}: category folders, the upstream
 * LICENSE.txt, and a SNAPSHOT.json describing the exact commit.
 */
public record OpenDbSnapshot(Path directory, String commit, String repository, String committedAt, String license) {

    public static final String LICENSE_NAME = "Open Data Commons Attribution License (ODC-By) v1.0";
    public static final String LICENSE_URL = "https://opendatacommons.org/licenses/by/1-0/";

    private static final Map<ComponentCategory, String> DIRECTORY_BY_CATEGORY = OpenDbRecordMapper.DIRECTORIES.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    public static OpenDbSnapshot open(Path directory, JsonMapper json) {
        Path descriptor = directory.resolve("SNAPSHOT.json");
        if (!Files.isRegularFile(descriptor)) {
            throw new IllegalStateException("Not an OpenDB snapshot (missing SNAPSHOT.json): " + directory.toAbsolutePath()
                    + ". Run scripts/fetch-opendb.sh first.");
        }
        if (!Files.isRegularFile(directory.resolve("LICENSE.txt"))) {
            throw new IllegalStateException("OpenDB snapshot without LICENSE.txt: " + directory.toAbsolutePath()
                    + ". The ODC-By license notice must be kept with the data.");
        }
        try {
            JsonNode node = json.readTree(Files.readString(descriptor));
            return new OpenDbSnapshot(directory,
                    node.path("commit").stringValue(),
                    node.path("repository").stringValue(),
                    node.path("committedAt").stringValue(null),
                    node.path("license").stringValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path categoryDirectory(String name) {
        return directory.resolve(name);
    }

    /** Public URL of one record at the pinned commit, for attribution and "where did this come from". */
    public static String recordUrl(String repository, String commit, ComponentCategory category, String externalId) {
        return repository + "/blob/" + commit + "/open-db/" + DIRECTORY_BY_CATEGORY.get(category) + "/" + externalId + ".json";
    }
}
