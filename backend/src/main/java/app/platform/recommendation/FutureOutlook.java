package app.platform.recommendation;

import app.platform.hardware.ComponentCategory;

import java.util.List;

/**
 * How much room a build leaves to evolve part by part, without replacing the others.
 *
 * <p>Derived only from the current catalog and the compatibility rules: every example part exists in
 * the catalog and was checked against the build. It never predicts future launches.
 *
 * @param summary one plain-language sentence for the whole build
 */
public record FutureOutlook(String summary, List<Aspect> aspects) {

    public static final String DISCLAIMER =
            "Baseado nas peças que já existem no catálogo e nas regras de compatibilidade. "
                    + "Não prevemos lançamentos futuros; ganhos de desempenho são estimados pelas especificações.";

    public FutureOutlook {
        aspects = List.copyOf(aspects);
    }

    /** GOOD: can evolve without touching other parts. PARTIAL: small room. LIMITED: evolving needs other parts replaced. */
    public enum Level {
        GOOD,
        PARTIAL,
        LIMITED
    }

    /**
     * One dimension of the outlook (processor, graphics card, memory, storage…).
     *
     * @param headline        the conclusion in one sentence
     * @param explanation     why, in plain language, naming the example part when there is one
     * @param technicalDetail values behind the conclusion, for people who want the specifics
     */
    public record Aspect(
            String id,
            String title,
            Level level,
            String headline,
            String explanation,
            String technicalDetail,
            List<ComponentCategory> involves) {
        public Aspect {
            involves = List.copyOf(involves);
        }
    }
}
