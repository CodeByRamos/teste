package app.platform.api;

import app.platform.recommendation.BuildRequest;
import app.platform.recommendation.TargetResolution;
import app.platform.recommendation.UseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request bodies. All input is validated here; prices are never accepted from clients. */
final class ApiRequests {

    private ApiRequests() {
    }

    record Needs(
            @NotNull @DecimalMin("1500") @DecimalMax("100000") BigDecimal budgetBrl,
            @NotEmpty @Size(max = 7) Set<@NotNull UseCase> useCases,
            UseCase primaryUse,
            TargetResolution resolution,
            @Size(max = 12) List<@NotNull UUID> ownedComponentIds) {

        BuildRequest toDomain() {
            return new BuildRequest(budgetBrl, useCases, primaryUse != null && useCases.contains(primaryUse) ? primaryUse : null,
                    resolution, ownedComponentIds);
        }
    }

    record Evaluate(
            @Valid Needs needs,
            @NotEmpty @Size(max = 12) List<@NotNull UUID> componentIds,
            @Size(max = 12) List<@NotNull UUID> ownedComponentIds) {
    }

    record Save(
            @Valid Needs needs,
            @NotEmpty @Size(max = 12) List<@NotNull UUID> componentIds,
            @Size(max = 12) List<@NotNull UUID> ownedComponentIds,
            @Size(max = 80) String title) {
    }

    record Interpret(@NotBlank @Size(max = 1000) String text) {
    }
}
