package app.platform.api;

import app.platform.catalog.Catalog;
import app.platform.catalog.CatalogHolder;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.HardwareComponent;
import app.platform.infra.persistence.JdbcCatalogRepository;
import app.platform.intake.NeedsInterpreter;
import app.platform.pricing.PriceService;
import app.platform.recommendation.BuildRequest;
import app.platform.recommendation.ComponentExplainer;
import app.platform.recommendation.TargetResolution;
import app.platform.recommendation.UseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Validated
class CatalogController {

    private final CatalogHolder catalogs;
    private final PriceService prices;
    private final JdbcCatalogRepository repository;
    private final JsonMapper json;

    CatalogController(CatalogHolder catalogs, PriceService prices, JdbcCatalogRepository repository, JsonMapper json) {
        this.catalogs = catalogs;
        this.prices = prices;
        this.repository = repository;
        this.json = json;
    }

    @GetMapping("/catalog/search")
    List<ApiViews.SearchResult> search(
            @RequestParam(required = false) ComponentCategory category,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "12") @Min(1) @Max(30) int limit) {
        Catalog catalog = catalogs.current();
        return catalog.search(category, q, limit).stream().map(this::summary).toList();
    }

    @GetMapping("/catalog/components/{id}")
    ResponseEntity<ApiViews.SearchResult> component(@PathVariable UUID id) {
        return catalogs.current().find(id).map(this::summary).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Reads a free-text request. Deterministic: extracts only what it recognizes and asks about the rest. */
    @PostMapping("/intake/interpret")
    ApiViews.Interpretation interpret(@Valid @RequestBody ApiRequests.Interpret body) {
        NeedsInterpreter.Interpretation result = NeedsInterpreter.interpret(body.text());
        return new ApiViews.Interpretation(
                result.budgetBrl(),
                result.useCases().stream().sorted().map(use -> new ApiViews.Labeled(use.name(), use.label())).toList(),
                result.resolution() == null ? null : new ApiViews.Labeled(result.resolution().name(), result.resolution().label()),
                result.mentionsOwnedParts(),
                result.understood(),
                result.questions());
    }

    /** Choices the questionnaire offers, so labels live in one place. */
    @GetMapping("/options")
    ApiViews.Options options() {
        return new ApiViews.Options(
                Arrays.stream(UseCase.values()).map(use -> new ApiViews.Option(use.name(), use.label(), use.description())).toList(),
                Arrays.stream(TargetResolution.values()).map(res -> new ApiViews.Labeled(res.name(), res.label())).toList(),
                Arrays.stream(ComponentCategory.values()).map(cat -> new ApiViews.Labeled(cat.name(), cat.label())).toList(),
                BuildRequest.MIN_BUDGET, BuildRequest.MAX_BUDGET);
    }

    /** Where the hardware data comes from, its license, and how complete it is. */
    @GetMapping(value = "/meta/data-sources", produces = MediaType.APPLICATION_JSON_VALUE)
    String dataSources() {
        Catalog catalog = catalogs.current();
        ObjectNode document = json.createObjectNode();
        document.set("hardware", json.valueToTree(ViewMapper.dataSource(catalog.version())));
        document.put("components", catalog.size());
        document.put("attribution", "Contém informações do BuildCores OpenDB, disponibilizado sob a "
                + "Open Data Commons Attribution License (ODC-By) v1.0.");
        repository.latestQualitySummary().ifPresent(summary -> document.set("quality", json.readTree(summary)));
        document.put("pricesDisclaimer", ViewMapper.PRICE_DISCLAIMER);
        return json.writeValueAsString(document);
    }

    private ApiViews.SearchResult summary(HardwareComponent component) {
        var info = component.info();
        List<ApiViews.Spec> highlights = ViewMapper.specs(ComponentExplainer.specs(component)).stream().limit(3).toList();
        return new ApiViews.SearchResult(info.id(), info.name(), info.manufacturer(), info.category().name(), info.category().label(),
                info.releaseYear(), ViewMapper.price(prices.bestOffer(component).orElse(null)), highlights, info.quality().score());
    }
}
