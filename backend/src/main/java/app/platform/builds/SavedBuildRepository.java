package app.platform.builds;

import java.util.Optional;
import java.util.UUID;

public interface SavedBuildRepository {

    void save(SavedBuild build);

    Optional<SavedBuild> find(UUID id);
}
