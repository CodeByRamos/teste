package app.platform.hardware;

import java.util.List;

/**
 * Appearance data used only to draw a component (3D models, images). Engines never read it.
 * Every field is optional; which ones apply depends on the category.
 *
 * @param colors       colour names from the source, most prominent first (e.g. "BLACK", "WHITE")
 * @param lighting     lighting kind ("None", "RGB", "ARGB", ...)
 * @param cooling      graphics card cooler description ("2 Fans", "Blower", "Water Cooled", ...)
 * @param fanCount     fans on the product (graphics card cooler, CPU cooler)
 * @param fanSizeMm    fan diameter (CPU cooler)
 * @param heatSpreader memory module has a heat spreader
 * @param rgb          memory module has RGB lighting
 * @param heightMm     memory module height, or case height
 * @param widthMm      case width
 * @param depthMm      case depth
 * @param psuShroud    case has a power-supply shroud
 */
public record VisualTraits(
        List<String> colors,
        String lighting,
        String cooling,
        Integer fanCount,
        Integer fanSizeMm,
        Boolean heatSpreader,
        Boolean rgb,
        Double heightMm,
        Double widthMm,
        Double depthMm,
        Boolean psuShroud) {

    public static final VisualTraits NONE = new VisualTraits(List.of(), null, null, null, null, null, null, null, null, null, null);

    public VisualTraits {
        colors = colors == null ? List.of() : List.copyOf(colors);
    }

    public String primaryColor() {
        return colors.isEmpty() ? null : colors.getFirst();
    }

    public boolean hasLighting() {
        return lighting != null && !lighting.equalsIgnoreCase("None");
    }
}
