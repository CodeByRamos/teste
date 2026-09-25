package app.platform.api;

import app.platform.builds.BuildAssembler;
import app.platform.builds.BuildResult;
import app.platform.builds.SavedBuild;
import app.platform.builds.SavedBuildRepository;
import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogHolder;
import app.platform.recommendation.BuildRequest;
import app.platform.recommendation.RecommendationEngine;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class BuildController {

    private final CatalogHolder catalogs;
    private final BuildAssembler assembler;
    private final SavedBuildRepository savedBuilds;
    private final JsonMapper json;

    BuildController(CatalogHolder catalogs, BuildAssembler assembler, SavedBuildRepository savedBuilds, JsonMapper json) {
        this.catalogs = catalogs;
        this.assembler = assembler;
        this.savedBuilds = savedBuilds;
        this.json = json;
    }

    /** Recommends a complete build for the person's needs. */
    @PostMapping("/recommendations")
    ApiViews.Build recommend(@Valid @RequestBody ApiRequests.Needs needs) {
        return ViewMapper.build(assembler.recommend(catalogs.current(), needs.toDomain()));
    }

    /** Re-evaluates a set of parts (after a swap, or parts entered by the person). */
    @PostMapping("/builds/evaluate")
    ApiViews.Build evaluate(@Valid @RequestBody ApiRequests.Evaluate body) {
        BuildRequest request = body.needs() == null ? null : body.needs().toDomain();
        return ViewMapper.build(assembler.evaluate(catalogs.current(), request, body.componentIds(), owned(body.ownedComponentIds())));
    }

    /**
     * Saves a build. The server recomputes prices and compatibility from the part ids; nothing the client
     * sends about prices is trusted.
     */
    @PostMapping("/builds")
    ResponseEntity<ApiViews.Saved> save(@Valid @RequestBody ApiRequests.Save body) {
        Catalog catalog = catalogs.current();
        BuildRequest request = body.needs() == null ? null : body.needs().toDomain();
        BuildResult result = assembler.evaluate(catalog, request, body.componentIds(), owned(body.ownedComponentIds()));
        ApiViews.Build view = ViewMapper.build(result);

        UUID id = UUID.randomUUID();
        List<SavedBuild.SavedItem> items = result.items().stream().map(item -> new SavedBuild.SavedItem(
                item.component().id(), item.component().category().name(), item.component().info().source().source(),
                item.component().info().source().externalId(), item.owned(),
                item.offer() == null ? null : item.offer().priceBrl(),
                item.offer() == null ? null : item.offer().kind().name())).toList();
        String title = body.title() == null || body.title().isBlank() ? null : body.title().strip();
        savedBuilds.save(new SavedBuild(id, Instant.now(), title,
                request == null ? null : json.writeValueAsString(ViewMapper.needs(request)),
                items, result.totalBrl(), result.pricesAreExamples(), RecommendationEngine.VERSION,
                catalog.version().source() + "@" + catalog.version().sourceVersion(),
                json.writeValueAsString(view)));
        return ResponseEntity.status(201).body(new ApiViews.Saved(id));
    }

    /** Returns a saved build exactly as it was shown when saved. */
    @GetMapping(value = "/builds/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> saved(@PathVariable UUID id) {
        return savedBuilds.find(id)
                .map(build -> {
                    ObjectNode document = json.createObjectNode();
                    document.put("id", build.id().toString());
                    document.put("savedAt", build.createdAt().toString());
                    document.put("title", build.title());
                    document.set("build", json.readTree(build.viewJson()));
                    return ResponseEntity.ok(json.writeValueAsString(document));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Set<UUID> owned(List<UUID> ids) {
        return ids == null ? Set.of() : new HashSet<>(ids);
    }
}
