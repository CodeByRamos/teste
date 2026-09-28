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
            boolean planUpgrades,
            List<String> understood,
            List<String> questions,
            boolean modelAssisted) {
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
    private static final Pattern PLAN_UPGRADES = Pattern.compile(
            "\\b(upgrades?|melhorar (ele |o pc |aos poucos )?(depois|no futuro|com o tempo)|aos poucos|futuro|evoluir|trocar (pecas )?depois)\\b");
    private static final Pattern RES_4K = Pattern.compile("\\b(4k|2160p?)\\b");
    private static final Pattern RES_QHD = Pattern.compile("\\b(1440p?|2k|qhd)\\b");
    private static final Pattern RES_FHD = Pattern.compile("\\b(1080p?|full ?hd|fhd)\\b");
    private static final Pattern UNRELEASED_OR_UNSPECIFIED = Pattern.compile("\\b(gta ?(6|vi))\\b");

    private NeedsInterpreter() {
    }

    /**
     * What was recognized in the text, before it is described to the person.
     *
     * @param gamingGuessed games were mentioned without saying which kind, so heavy games were assumed
     */
    private record Facts(BigDecimal budget, Set<UseCase> uses, boolean gamingGuessed, boolean mentionsGta,
                         TargetResolution resolution, boolean planUpgrades, boolean owned) {
    }

    /** Rules only: deterministic, no external service. */
    public static Interpretation interpret(String text) {
        return describe(facts(fold(text == null ? "" : text)), false);
    }

    /**
     * Combines the rules with a language model's reading of the same text. The rules win where they found
     * something; the model fills gaps. A model budget is only accepted when the text contains a number
     * (in digits or words), so an invented amount never gets through. Everything shown to the person is
     * still written here, from structured fields, never by the model.
     */
    public static Interpretation combine(String text, ModelReading reading) {
        String folded = fold(text == null ? "" : text);
        Facts rules = facts(folded);

        BigDecimal budget = rules.budget();
        if (budget == null && reading.budgetBrl() != null && mentionsANumber(folded)
                && reading.budgetBrl().compareTo(new BigDecimal("500")) >= 0
                && reading.budgetBrl().compareTo(new BigDecimal("200000")) <= 0) {
            budget = reading.budgetBrl().setScale(0, java.math.RoundingMode.HALF_UP);
        }

        Set<UseCase> uses = EnumSet.noneOf(UseCase.class);
        uses.addAll(rules.uses());
        boolean gamingGuessed = rules.gamingGuessed();
        boolean modelKnowsTheGames = reading.useCases().stream().anyMatch(UseCase::isGaming);
        if (gamingGuessed && modelKnowsTheGames) {
            // The rules only guessed "heavy games"; the model read which kind the person meant.
            uses.removeIf(UseCase::isGaming);
            gamingGuessed = false;
        }
        uses.addAll(reading.useCases());

        TargetResolution resolution = rules.resolution() != null ? rules.resolution() : reading.resolution();
        return describe(new Facts(budget, uses, gamingGuessed, rules.mentionsGta(), resolution,
                rules.planUpgrades() || reading.planUpgrades(), rules.owned() || reading.mentionsOwnedParts()), true);
    }

    private static Facts facts(String folded) {
        Set<UseCase> uses = EnumSet.noneOf(UseCase.class);
        USE_CASES.forEach((useCase, pattern) -> {
            if (pattern.matcher(folded).find()) {
                uses.add(useCase);
            }
        });
        boolean gamingGuessed = false;
        if (GENERIC_GAMING.matcher(folded).find() && uses.stream().noneMatch(UseCase::isGaming)) {
            uses.add(UseCase.GAMING_AAA);
            gamingGuessed = true;
        }
        TargetResolution resolution = null;
        if (RES_4K.matcher(folded).find()) {
            resolution = TargetResolution.UHD_4K;
        } else if (RES_QHD.matcher(folded).find()) {
            resolution = TargetResolution.QHD;
        } else if (RES_FHD.matcher(folded).find()) {
            resolution = TargetResolution.FULL_HD;
        }
        return new Facts(budget(folded), uses, gamingGuessed, UNRELEASED_OR_UNSPECIFIED.matcher(folded).find(), resolution,
                PLAN_UPGRADES.matcher(folded).find(), OWNED_PARTS.matcher(folded).find());
    }

    private static Interpretation describe(Facts facts, boolean modelAssisted) {
        List<String> understood = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        if (facts.budget() != null) {
            understood.add("Orçamento de até R$ " + String.format(Locale.of("pt", "BR"), "%,.0f", facts.budget()));
        } else {
            questions.add("Quanto você pretende investir, mais ou menos?");
        }
        if (facts.gamingGuessed()) {
            questions.add("Que tipo de jogo você mais joga? Jogos competitivos (como Valorant) pedem menos que lançamentos pesados.");
        }
        facts.uses().forEach(useCase -> understood.add("Uso: " + useCase.label().toLowerCase(Locale.ROOT)));
        if (facts.uses().isEmpty()) {
            questions.add("O que você quer fazer com o computador? Por exemplo: jogar, programar, editar vídeos ou estudar.");
        }
        if (facts.mentionsGta()) {
            understood.add("Ainda não há requisitos oficiais de GTA VI para PC na nossa base, então consideramos jogos pesados em geral");
        }
        if (facts.resolution() != null) {
            understood.add("Resolução: " + facts.resolution().label());
        }
        if (facts.planUpgrades()) {
            understood.add("Quer poder melhorar o PC depois, peça por peça");
        }
        if (facts.owned()) {
            questions.add("Quais peças você já tem e quer aproveitar? Você pode escolhê-las na próxima etapa.");
        }
        return new Interpretation(facts.budget(), facts.uses(), facts.resolution(), facts.owned(), facts.planUpgrades(),
                understood, questions, modelAssisted);
    }

    /** Digits, or the words people use for amounts ("cinco mil", "quinhentos reais"). Not "um"/"uma": they are articles. */
    private static final Pattern NUMBER = Pattern.compile(
            "\\d|\\b(mil|cem|cento|duzentos|trezentos|quatrocentos|quinhentos|seiscentos|setecentos|oitocentos|novecentos"
                    + "|reais|conto|contos|pila)\\b");

    /** A budget needs a number in the text, written in digits or words. */
    static boolean mentionsANumber(String folded) {
        return NUMBER.matcher(folded).find();
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
