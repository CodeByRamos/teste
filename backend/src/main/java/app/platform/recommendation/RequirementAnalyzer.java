package app.platform.recommendation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns use cases into technical requirements.
 *
 * <p>These are product rules (v1), not facts from a data source: they encode general guidance on what
 * each kind of use demands. They live in one table so they can be reviewed and tuned in one place.
 */
public final class RequirementAnalyzer {

    /** Requirements of one use case. */
    record Needs(
            boolean dedicatedGpu,
            double gpuWeight,
            double cpuMultiWeight,
            double cpuGamingWeight,
            int threads,
            int minRam,
            int targetRam,
            int minStorage,
            int targetStorage,
            int vram,
            double enoughPerformance,
            String reason) {
    }

    private static final Map<UseCase, Needs> TABLE = new EnumMap<>(Map.of(
            UseCase.OFFICE_STUDY, new Needs(false, 0.0, 0.6, 0.2, 4, 8, 16, 256, 500, 0, 0.35,
                    "Estudo e trabalho: o vídeo integrado do processador dá conta, e 16 GB de memória evitam lentidão com muitas abas abertas."),
            UseCase.PROGRAMMING, new Needs(false, 0.1, 0.8, 0.2, 8, 16, 32, 500, 1000, 0, 0.8,
                    "Programação: um processador com vários núcleos compila mais rápido, e memória de sobra mantém editor, navegador e ferramentas abertos juntos."),
            UseCase.CONTAINERS_VMS, new Needs(false, 0.05, 1.0, 0.1, 12, 32, 64, 500, 1000, 0, 1.0,
                    "Docker e máquinas virtuais: cada ambiente reserva memória e núcleos, então 32 GB ou mais e muitos núcleos fazem diferença."),
            UseCase.VIDEO_EDITING, new Needs(true, 0.5, 0.9, 0.1, 12, 32, 32, 1000, 2000, 8, 1.0,
                    "Edição de vídeo: exportar usa todos os núcleos do processador, a placa de vídeo acelera efeitos, e vídeos ocupam muito espaço."),
            UseCase.STREAMING, new Needs(true, 0.6, 0.7, 0.4, 12, 16, 32, 1000, 1000, 8, 1.0,
                    "Lives: a placa de vídeo codifica a transmissão enquanto o processador roda o jogo ou programa ao mesmo tempo."),
            UseCase.GAMING_COMPETITIVE, new Needs(true, 0.8, 0.2, 0.6, 6, 16, 16, 500, 1000, 6, 1.0,
                    "Jogos competitivos: pedem muitos quadros por segundo, o que depende de um processador rápido e de uma boa placa de vídeo."),
            UseCase.GAMING_AAA, new Needs(true, 1.0, 0.2, 0.4, 8, 16, 32, 1000, 1000, 8, 1.0,
                    "Jogos pesados: a placa de vídeo é a peça mais importante, e jogos novos ocupam bastante espaço.")));

    /** How much a use case counts: the main use fully, others partially. Without a main use, all count the same. */
    private static final double PRIMARY_FACTOR = 1.0;
    private static final double SECONDARY_FACTOR = 0.6;
    private static final double UNRANKED_FACTOR = 0.85;

    private RequirementAnalyzer() {
    }

    public static RequirementProfile analyze(BuildRequest request) {
        boolean dedicatedGpu = false;
        double gpuWeight = 0, multiWeight = 0, gamingWeight = 0;
        int threads = 0, minRam = 0, targetRam = 0, minStorage = 0, targetStorage = 0, vram = 0;
        double enough = 0;
        List<String> reasons = new ArrayList<>();

        for (UseCase useCase : request.useCases()) {
            Needs needs = TABLE.get(useCase);
            boolean main = request.primaryUse() == null || useCase == request.primaryUse();
            double factor = request.primaryUse() == null ? UNRANKED_FACTOR : main ? PRIMARY_FACTOR : SECONDARY_FACTOR;
            dedicatedGpu |= needs.dedicatedGpu();
            gpuWeight = Math.max(gpuWeight, needs.gpuWeight() * factor);
            multiWeight = Math.max(multiWeight, needs.cpuMultiWeight() * factor);
            gamingWeight = Math.max(gamingWeight, needs.cpuGamingWeight() * factor);
            threads = Math.max(threads, needs.threads());
            minRam = Math.max(minRam, needs.minRam());
            minStorage = Math.max(minStorage, needs.minStorage());
            // "Nice to have" amounts follow the main use; secondary uses only raise the minimums.
            if (main) {
                targetRam = Math.max(targetRam, needs.targetRam());
                targetStorage = Math.max(targetStorage, needs.targetStorage());
            }
            vram = Math.max(vram, needs.vram());
            enough = Math.max(enough, needs.enoughPerformance());
            reasons.add(needs.reason());
        }

        if (request.includesGaming()) {
            int resolutionVram = switch (request.resolution()) {
                case FULL_HD -> 8;
                case QHD -> 12;
                case UHD_4K -> 16;
            };
            if (request.useCases().contains(UseCase.GAMING_AAA)) {
                vram = Math.max(vram, resolutionVram);
            }
            if (request.resolution() != TargetResolution.FULL_HD) {
                gpuWeight *= 1.15;
                reasons.add("Jogar em " + request.resolution().label() + " exige mais da placa de vídeo, então ela recebe prioridade maior no orçamento.");
            }
        }

        boolean gamingPriority = request.primaryUse() != null ? request.primaryUse().isGaming() : request.includesGaming();
        return new RequirementProfile(dedicatedGpu, gamingPriority, gpuWeight, multiWeight, gamingWeight, threads,
                minRam, Math.max(minRam, targetRam), minStorage, Math.max(minStorage, targetStorage), vram, enough, reasons);
    }
}
