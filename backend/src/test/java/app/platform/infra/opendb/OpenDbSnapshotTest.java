package app.platform.infra.opendb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDbSnapshotTest {

    /** scripts/fetch-opendb.sh records an unknown commit date as null when the GitHub API is unavailable. */
    @Test
    void unknownCommitDateIsAccepted(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("LICENSE.txt"), "ODC-By");
        Files.writeString(directory.resolve("SNAPSHOT.json"), """
                {"source": "buildcores-opendb", "repository": "https://github.com/buildcores/buildcores-open-db",
                 "commit": "abc123", "committedAt": null, "license": "ODC-By-1.0", "categories": ["CPU"]}
                """);

        OpenDbSnapshot snapshot = OpenDbSnapshot.open(directory, JsonMapper.builder().build());

        assertThat(snapshot.commit()).isEqualTo("abc123");
        assertThat(snapshot.committedAt()).isNull();
    }
}
