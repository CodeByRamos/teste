package app.platform.visualization;

import java.util.List;
import java.util.Map;

/**
 * Which base 3D model draws a component, and the parameters that adapt it to the real product.
 *
 * <p>{@code params} carries only values known for this component; the renderer fills the rest from the base
 * model's defaults (see frontend/public/3d-models/catalog.json). Every default the resolver itself had to
 * assume is listed in {@code assumptions}, in plain language.
 *
 * @param family     layout family used by the renderer (e.g. "gpu", "air-cooler", "aio-radiator")
 * @param color      colour name from the data source ("BLACK", "WHITE"...), or null
 * @param confidence "measured" when the key dimensions come from the component's data, "estimated" otherwise
 */
public record ModelSpec(
        String modelId,
        String family,
        Map<String, Object> params,
        String color,
        boolean rgb,
        String confidence,
        List<String> assumptions) {

    public ModelSpec {
        params = Map.copyOf(params);
        assumptions = List.copyOf(assumptions);
    }
}
