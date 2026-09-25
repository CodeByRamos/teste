package app.platform.intake;

import app.platform.recommendation.TargetResolution;
import app.platform.recommendation.UseCase;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic reading of a free-text request such as "Quero um PC de até 6 mil para jogar e programar".
 *
 * <p>It extracts only what it can recognize with certainty and asks about the rest. It is a stand-in
 * for the future AI layer and keeps the same contract: interpretation produces a draft request that
 * the person confirms; nothing here decides compatibility or prices.
 */
public final class NeedsInterpreter {

    public record Interpretation(
            BigDecimal budgetBrl,
            Set<UseCase> useCases,
            TargetResolution resolution,
            boolean mentionsOwnedParts,
            List<String> understood,
            List<String> questions) {
    }

    private static final String AMOUNT = "(\\d{1,3}(?:\\.\\d{3})+|\\d+(?:,\\d+)?)";

    /**
     * A number counts as money only with a currency marker ("R$ 5.000", "6 mil", "5k", "4000 reais") or
     * after a budget word ("até 5000"). This keeps model numbers such as "RTX 4060" out of the budget.
     */
    private static final List<Pattern> BUDGET_PATTERNS = List.of(
            Pattern.compile("r\\$\\s*" + AMOUNT + "(\\s*(?:mil|k))?\\b"),
            Pattern.compile("\\b" + AMOUNT + "\\s*(mil|k|reais|conto)\\b"),
            Pattern.compile("\\b(?:ate|orcamento(?: de)?|gastar|investir|budget)\\s+(?:de\\s+)?" + AMOUNT
                    + "\\b(?!\\s*(?:gb|tb|w|hz|fps|p)\\b)"));

    private static final Map<UseCase, Pattern> USE_CASES = new LinkedHashMap<>();

    static {
        USE_CASES.put(UseCase.GAMING_COMPETITIVE, Pattern.compile(
                "\\b(valorant|cs ?2|counter[- ]strike|csgo|fortnite|lol|league of legends|dota|overwatch|apex|warzone|competitiv\\w*|fps alto)\\b"));
        USE_CASES.put(UseCase.GAMING_AAA, Pattern.compile(
                "\\b(gta|cyberpunk|red dead|elden ring|hogwarts|starfield|black myth|aaa|jogos? pesados?|lancamentos?|ultra|ray ?tracing)\\b"));
        USE_CASES.put(UseCase.PROGRAMMING, Pattern.compile("\\b(program\\w*|codar|codigo|desenvolv\\w*|dev|ide|vs ?code|intellij|compilar)\\b"));
        USE_CASES.put(UseCase.CONTAINERS_VMS, Pattern.compile("\\b(docker|container\\w*|kubernetes|k8s|maquinas? virtu\\w*|vms?|virtualiza\\w*|wsl)\\b"));
        USE_CASES.put(UseCase.VIDEO_EDITING, Pattern.compile("\\b(edit\\w* (de )?videos?|edicao|premiere|davinci|after effects|final cut|render\\w*|youtube)\\b"));
        USE_CASES.put(UseCase.STREAMING, Pattern.compile("\\b(lives?|stream\\w*|transmit\\w*|twitch|obs)\\b"));
        USE_CASES.put(UseCase.OFFICE_STUDY, Pattern.compile("\\b(estud\\w*|faculdade|escola|escritorio|office|excel|word|planilh\\w*|trabalh\\w*|home office|navegar|internet)\\b"));
    }

    private static final Pattern GENERIC_GAMING = Pattern.compile("\\b(jog\\w*|games?|gamer)\\b");
    private static final Pattern OWNED_PARTS = Pattern.compile("\\b(ja tenho|tenho uma?|aproveitar|reaproveitar|minha placa|meu processador)\\b");
    private static final Pattern RES_4K = Pattern.compile("\\b(4k|2160p?)\\b");
    private static final Pattern RES_QHD = Pattern.compile("\\b(1440p?|2k|qhd)\\b");
    private static final Pattern RES_FHD = Pattern.compile("\\b(1080p?|full ?hd|fhd)\\b");
    private static final Pattern UNRELEASED_OR_UNSPECIFIED = Pattern.compile("\\b(gta ?(6|vi))\\b");

    private NeedsInterpreter() {
    }

    public static Interpretation interpret(String text) {
        String folded = fold(text == null ? "" : text);
        List<String> understood = new ArrayList<>();
        List<String> questions = new ArrayList<>();

        BigDecimal budget = budget(folded);
        if (budget != null) {
            understood.add("Orçamento de até R$ " + String.format(Locale.of("pt", "BR"), "%,.0f", budget));
        } else {
            questions.add("Quanto você pretende investir, mais ou menos?");
        }

        Set<UseCase> uses = EnumSet.noneOf(UseCase.class);
        USE_CASES.forEach((useCase, pattern) -> {
            if (pattern.matcher(folded).find()) {
                uses.add(useCase);
            }
        });
        if (GENERIC_GAMING.matcher(folded).find() && uses.stream().noneMatch(UseCase::isGaming)) {
            uses.add(UseCase.GAMING_AAA);
            questions.add("Que tipo de jogo você mais joga? Jogos competitivos (como Valorant) pedem menos que lançamentos pesados.");
        }
        uses.forEach(useCase -> understood.add("Uso: " + useCase.label().toLowerCase(Locale.ROOT)));
        if (uses.isEmpty()) {
            questions.add("O que você quer fazer com o computador? Por exemplo: jogar, programar, editar vídeos ou estudar.");
        }

        if (UNRELEASED_OR_UNSPECIFIED.matcher(folded).find()) {
            understood.add("Ainda não há requisitos oficiais de GTA VI para PC na nossa base, então consideramos jogos pesados em geral");
        }

        TargetResolution resolution = null;
        if (RES_4K.matcher(folded).find()) {
            resolution = TargetResolution.UHD_4K;
        } else if (RES_QHD.matcher(folded).find()) {
            resolution = TargetResolution.QHD;
        } else if (RES_FHD.matcher(folded).find()) {
            resolution = TargetResolution.FULL_HD;
        }
        if (resolution != null) {
            understood.add("Resolução: " + resolution.label());
        }

        boolean owned = OWNED_PARTS.matcher(folded).find();
        if (owned) {
            questions.add("Quais peças você já tem e quer aproveitar? Você pode escolhê-las na próxima etapa.");
        }
        return new Interpretation(budget, uses, resolution, owned, understood, questions);
    }

    static BigDecimal budget(String folded) {
        BigDecimal best = null;
        for (Pattern pattern : BUDGET_PATTERNS) {
            Matcher matcher = pattern.matcher(folded);
            while (matcher.find()) {
                // "5.000" uses dots as thousands separators; "1,5 mil" uses a decimal comma.
                BigDecimal value = new BigDecimal(matcher.group(1).replace(".", "").replace(',', '.'));
                String unit = matcher.groupCount() >= 2 && matcher.group(2) != null ? matcher.group(2).trim() : "";
                if (unit.equals("mil") || unit.equals("k")) {
                    value = value.multiply(BigDecimal.valueOf(1000));
                }
                boolean plausible = value.compareTo(new BigDecimal("500")) >= 0 && value.compareTo(new BigDecimal("200000")) <= 0;
                if (plausible && (best == null || value.compareTo(best) > 0)) {
                    best = value;
                }
            }
        }
        return best;
    }

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
