package app.platform.recommendation;

import app.platform.compatibility.BuildParts;
import app.platform.compatibility.PowerEstimate;
import app.platform.compatibility.PowerEstimator;
import app.platform.hardware.ComponentCategory;
import app.platform.hardware.Cpu;
import app.platform.hardware.CpuCooler;
import app.platform.hardware.Gpu;
import app.platform.hardware.HardwareComponent;
import app.platform.hardware.Memory;
import app.platform.hardware.Motherboard;
import app.platform.hardware.PcCase;
import app.platform.hardware.PowerSupply;
import app.platform.hardware.Storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Plain-language explanations, layered from "what is it" to technical specifications.
 * Every sentence is built from structured data; nothing is invented when a value is unknown.
 */
public final class ComponentExplainer {

    public record Spec(String label, String value) {
    }

    public record Explanation(String whatItIs, String whyItMatters, String reason, List<Spec> specs) {
    }

    private static final String UNKNOWN = "Não informado";

    private ComponentExplainer() {
    }

    /**
     * @param engineChoice true when the recommendation engine picked this part; only then do reasons claim it was
     *                     the best option for the budget (after a manual swap that would not be true)
     */
    public static Explanation explain(HardwareComponent component, BuildParts parts, RequirementProfile profile,
                                      BuildRequest request, boolean owned, boolean engineChoice) {
        String reason;
        if (owned && request == null) {
            reason = "Peça informada por você.";
        } else if (owned) {
            reason = "Você já tem esta peça, então ela foi aproveitada e não entra no total.";
        } else if (request == null) {
            reason = "Peça escolhida por você.";
        } else {
            reason = reason(component, parts, profile, request, engineChoice);
        }
        return new Explanation(whatItIs(component.category()), whyItMatters(component.category()), reason, specs(component));
    }

    public static String whatItIs(ComponentCategory category) {
        return switch (category) {
            case CPU -> "O cérebro do computador: executa os programas e coordena todas as outras peças.";
            case GPU -> "Gera as imagens que aparecem no monitor.";
            case MOTHERBOARD -> "A base onde todas as peças se conectam.";
            case MEMORY -> "A memória de trabalho: guarda o que está aberto agora.";
            case STORAGE -> "Onde ficam guardados o sistema, os programas, os jogos e seus arquivos.";
            case POWER_SUPPLY -> "Converte a energia da tomada e alimenta todas as peças com segurança.";
            case CASE -> "A estrutura que protege as peças e organiza a circulação de ar.";
            case CPU_COOLER -> "Retira o calor do processador para mantê-lo em temperatura segura.";
        };
    }

    public static String whyItMatters(ComponentCategory category) {
        return switch (category) {
            case CPU -> "Define a rapidez geral do computador: abrir programas, compilar, exportar vídeos e, em jogos, quantos quadros por segundo são possíveis.";
            case GPU -> "Em jogos, é a peça que mais influencia a qualidade gráfica e a fluidez. Na edição de vídeo e em lives, acelera efeitos e a codificação.";
            case MOTHERBOARD -> "Determina quais peças são compatíveis entre si e quais upgrades serão possíveis no futuro.";
            case MEMORY -> "Com pouca memória, o computador fica lento ou trava quando muitas coisas estão abertas ao mesmo tempo.";
            case STORAGE -> "Um SSD faz o computador ligar e abrir programas muito mais rápido que um HD tradicional.";
            case POWER_SUPPLY -> "Uma fonte fraca ou de baixa qualidade pode desligar o computador sob carga ou até danificar peças.";
            case CASE -> "Precisa comportar todas as peças com folga e deixar o ar circular para nada esquentar demais.";
            case CPU_COOLER -> "Sem refrigeração adequada, o processador reduz a própria velocidade para não superaquecer.";
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Reasons
    // ---------------------------------------------------------------------------------------------

    private static String reason(HardwareComponent component, BuildParts parts, RequirementProfile profile, BuildRequest request,
                                 boolean engineChoice) {
        return switch (component) {
            case Cpu cpu -> cpuReason(cpu, profile, engineChoice);
            case Gpu gpu -> gpuReason(gpu, request, engineChoice);
            case Motherboard board -> boardReason(board, parts, engineChoice);
            case Memory memory -> memoryReason(memory, request);
            case Storage storage -> storageReason(storage, request);
            case PowerSupply psu -> psuReason(psu, parts);
            case PcCase pcCase -> caseReason(pcCase, parts, engineChoice);
            case CpuCooler cooler -> coolerReason(cooler, parts);
        };
    }

    private static String cpuReason(Cpu cpu, RequirementProfile profile, boolean engineChoice) {
        List<String> strengths = new ArrayList<>();
        if (profile.cpuMultiThreadWeight() >= 0.6 && cpu.cores() != null && cpu.threads() != null) {
            strengths.add("tem " + cpu.cores() + " núcleos e " + cpu.threads() + " threads para compilar, exportar e rodar vários programas ao mesmo tempo");
        }
        if (profile.cpuGamingWeight() >= 0.5 && cpu.boostClockGhz() != null) {
            String gaming = "chega a " + ghz(cpu.boostClockGhz()) + ", o que ajuda a manter muitos quadros por segundo em jogos";
            if (cpu.l3CacheMb() != null && cpu.l3CacheMb() >= 64) {
                gaming += ", e tem cache grande (" + number(cpu.l3CacheMb()) + " MB), que faz bastante diferença em jogos";
            }
            strengths.add(gaming);
        }
        if (strengths.isEmpty() && cpu.cores() != null) {
            strengths.add("tem " + cpu.cores() + " núcleos, suficientes para o uso do dia a dia com folga");
        }
        String text = (engineChoice ? "Escolhemos este processador porque " : "Este processador ") + String.join("; ", strengths) + "."
                + (engineChoice ? " Foi o melhor equilíbrio entre desempenho e preço dentro do orçamento." : "");
        if (RecommendationEngine.stockCoolerIsEnough(cpu)) {
            text += " Ele já vem com cooler na caixa.";
        }
        return text;
    }

    private static String gpuReason(Gpu gpu, BuildRequest request, boolean engineChoice) {
        List<String> uses = new ArrayList<>();
        if (request.useCases().contains(UseCase.GAMING_AAA)) {
            uses.add("jogos pesados em " + request.resolution().label());
        }
        if (request.useCases().contains(UseCase.GAMING_COMPETITIVE)) {
            uses.add("jogos competitivos com muitos quadros por segundo");
        }
        if (request.useCases().contains(UseCase.VIDEO_EDITING)) {
            uses.add("acelerar efeitos e exportação na edição de vídeo");
        }
        if (request.useCases().contains(UseCase.STREAMING)) {
            uses.add("transmitir ao vivo usando o codificador de vídeo da própria placa");
        }
        String vram = gpu.vramGb() == null ? "" : "Com " + gpu.vramGb() + " GB de memória de vídeo, ";
        String purpose = uses.isEmpty() ? "atende ao uso informado" : "atende a " + joinNatural(uses);
        return vram + (vram.isEmpty() ? capitalize(purpose) : purpose) + "."
                + (engineChoice ? " Foi a placa com melhor desempenho estimado que coube no orçamento junto com as outras peças." : "");
    }

    private static String boardReason(Motherboard board, BuildParts parts, boolean engineChoice) {
        StringBuilder text = new StringBuilder("Tem o encaixe certo para o processador (" + board.socket() + ")");
        if (board.ramType() != null) {
            text.append(" e usa memória ").append(board.ramType());
        }
        text.append('.');
        if (board.m2Slots() != null && !board.driveM2Slots().isEmpty()) {
            text.append(" Oferece ").append(board.driveM2Slots().size()).append(board.driveM2Slots().size() == 1 ? " encaixe" : " encaixes")
                    .append(" M.2 para SSDs rápidos.");
        }
        if (engineChoice) {
            text.append(" Escolhemos a opção mais em conta que atende a tudo isso — pagar mais na placa-mãe raramente aumenta o desempenho.");
        }
        if (!board.hasWifi()) {
            text.append(" Ela não tem Wi-Fi: use cabo de rede ou um adaptador Wi-Fi USB.");
        }
        return text.toString();
    }

    private static String memoryReason(Memory memory, BuildRequest request) {
        StringBuilder text = new StringBuilder();
        text.append(memory.totalCapacityGb()).append(" GB");
        if (memory.modules() != null && memory.modules() == 2) {
            text.append(" em dois pentes iguais, que trabalham juntos (dual channel) e deixam a memória mais rápida.");
        } else {
            text.append('.');
        }
        Set<UseCase> uses = request.useCases();
        if (uses.contains(UseCase.CONTAINERS_VMS)) {
            text.append(" Docker e máquinas virtuais reservam memória para cada ambiente, então quantidade aqui faz diferença.");
        } else if (uses.contains(UseCase.VIDEO_EDITING)) {
            text.append(" Edição de vídeo usa bastante memória, principalmente em vídeos longos ou em alta resolução.");
        } else if (memory.totalCapacityGb() != null && memory.totalCapacityGb() >= 32) {
            text.append(" Dá folga para jogos, navegador e outros programas abertos ao mesmo tempo.");
        } else {
            text.append(" É a quantidade recomendada hoje para o uso informado.");
        }
        return text.toString();
    }

    private static String storageReason(Storage storage, BuildRequest request) {
        String space = storage.capacityGb() == null ? "" : " São " + capacity(storage.capacityGb()) + " de espaço";
        String fill = request.includesGaming()
                ? ", o bastante para o sistema e vários jogos (jogos novos costumam passar de 100 GB)."
                : request.useCases().contains(UseCase.VIDEO_EDITING)
                        ? " para o sistema, programas e projetos. Para guardar muitos vídeos, um segundo disco pode vir depois."
                        : ", suficiente para o sistema, programas e seus arquivos.";
        return "SSD NVMe: o computador liga em segundos e programas abrem rápido." + (space.isEmpty() ? "" : space + fill);
    }

    private static String psuReason(PowerSupply psu, BuildParts parts) {
        PowerEstimate power = PowerEstimator.estimate(parts);
        StringBuilder text = new StringBuilder();
        text.append("Tem ").append(psu.wattage()).append(" W para um consumo estimado de ").append(power.estimatedLoadWatts())
                .append(" W, com folga para picos de energia.");
        if (psu.efficiencyRating() != null) {
            text.append(" A certificação ").append(psu.efficiencyRating()).append(" indica que ela desperdiça pouca energia em calor.");
        }
        return text.toString();
    }

    private static String caseReason(PcCase pcCase, BuildParts parts, boolean engineChoice) {
        List<String> fits = new ArrayList<>();
        if (parts.motherboard() != null && parts.motherboard().formFactor() != null) {
            fits.add("a placa-mãe (" + parts.motherboard().formFactor() + ")");
        }
        if (parts.gpu() != null && parts.gpu().lengthMm() != null && pcCase.maxGpuLengthMm() != null) {
            fits.add("a placa de vídeo de " + parts.gpu().lengthMm() + " mm (cabe até " + pcCase.maxGpuLengthMm() + " mm)");
        }
        if (parts.cooler() != null && parts.cooler().heightMm() != null && pcCase.maxCoolerHeightMm() != null) {
            fits.add("o cooler de " + parts.cooler().heightMm() + " mm (cabe até " + pcCase.maxCoolerHeightMm() + " mm)");
        }
        if (fits.isEmpty()) {
            return engineChoice ? "Gabinete mais em conta que comporta as peças escolhidas." : "Gabinete escolhido para a configuração.";
        }
        return "Comporta " + joinNatural(fits) + "." + (engineChoice ? " É a opção mais em conta que atende a isso." : "");
    }

    private static String coolerReason(CpuCooler cooler, BuildParts parts) {
        StringBuilder text = new StringBuilder("Prende no encaixe do processador");
        if (parts.cpu() != null && parts.cpu().powerBudgetWatts() != null) {
            text.append(", que pode consumir até ").append(parts.cpu().powerBudgetWatts()).append(" W sob carga");
        }
        text.append('.');
        if (cooler.heightMm() != null) {
            text.append(" Com ").append(cooler.heightMm()).append(" mm de altura, tem tamanho adequado para esse consumo e cabe no gabinete escolhido.");
        }
        return text.toString();
    }

    // ---------------------------------------------------------------------------------------------
    // Specifications
    // ---------------------------------------------------------------------------------------------

    public static List<Spec> specs(HardwareComponent component) {
        List<Spec> specs = new ArrayList<>();
        switch (component) {
            case Cpu cpu -> {
                specs.add(new Spec("Núcleos e threads", cpu.cores() == null ? UNKNOWN
                        : coresText(cpu) + " · " + orUnknown(cpu.threads()) + " threads"));
                specs.add(new Spec("Frequência máxima", cpu.boostClockGhz() == null ? UNKNOWN : ghz(cpu.boostClockGhz())));
                specs.add(new Spec("Cache L3", cpu.l3CacheMb() == null ? UNKNOWN : number(cpu.l3CacheMb()) + " MB"));
                specs.add(new Spec("Encaixe (socket)", orUnknown(cpu.socket())));
                specs.add(new Spec("Consumo", cpu.tdpWatts() == null ? UNKNOWN : cpu.tdpWatts() + " W"
                        + (cpu.maxPowerWatts() != null && cpu.maxPowerWatts() > cpu.tdpWatts() ? " (até " + cpu.maxPowerWatts() + " W)" : "")));
                specs.add(new Spec("Vídeo integrado", cpu.integratedGraphics() == null ? UNKNOWN
                        : cpu.integratedGraphics() ? "Sim" + (cpu.integratedGraphicsModel() == null ? "" : " (" + cpu.integratedGraphicsModel() + ")") : "Não"));
                specs.add(new Spec("Cooler na caixa", yesNo(cpu.includesCooler())));
                specs.add(new Spec("Memória suportada", cpu.memoryTypes().isEmpty() ? UNKNOWN : String.join(", ", cpu.memoryTypes().stream().sorted().toList())));
            }
            case Gpu gpu -> {
                specs.add(new Spec("Chip", orUnknown(gpu.chipset())));
                specs.add(new Spec("Memória de vídeo", gpu.vramGb() == null ? UNKNOWN : gpu.vramGb() + " GB" + (gpu.memoryType() == null ? "" : " " + gpu.memoryType())));
                specs.add(new Spec("Comprimento", gpu.lengthMm() == null ? UNKNOWN : gpu.lengthMm() + " mm"));
                specs.add(new Spec("Consumo", gpu.tdpWatts() == null ? UNKNOWN : gpu.tdpWatts() + " W"));
                specs.add(new Spec("Conectores de energia", gpu.powerConnectors() == null ? UNKNOWN : connectorsText(gpu.powerConnectors())));
                specs.add(new Spec("Espessura", gpu.slotWidth() == null ? UNKNOWN : number(gpu.slotWidth()) + " slots"));
            }
            case Motherboard board -> {
                specs.add(new Spec("Encaixe (socket)", orUnknown(board.socket())));
                specs.add(new Spec("Chipset", orUnknown(board.chipset())));
                specs.add(new Spec("Tamanho", orUnknown(board.formFactor())));
                specs.add(new Spec("Memória", board.ramType() == null ? UNKNOWN : board.ramType() + " · " + orUnknown(board.memorySlots())
                        + " encaixes · até " + orUnknown(board.maxMemoryGb()) + " GB"));
                specs.add(new Spec("Encaixes M.2", board.m2Slots() == null ? UNKNOWN : String.valueOf(board.driveM2Slots().size())));
                specs.add(new Spec("Portas SATA", orUnknown(board.sataPorts())));
                specs.add(new Spec("Wi-Fi", board.wireless() == null ? UNKNOWN : board.hasWifi() ? board.wireless() : "Não"));
            }
            case Memory memory -> {
                specs.add(new Spec("Capacidade", memory.totalCapacityGb() == null ? UNKNOWN : memory.totalCapacityGb() + " GB"
                        + (memory.modules() != null && memory.moduleCapacityGb() != null ? " (" + memory.modules() + " × " + memory.moduleCapacityGb() + " GB)" : "")));
                specs.add(new Spec("Tipo", orUnknown(memory.ramType())));
                specs.add(new Spec("Velocidade", memory.speedMts() == null ? UNKNOWN : memory.speedMts() + " MT/s"));
                specs.add(new Spec("Latência", memory.casLatency() == null ? UNKNOWN : "CL" + memory.casLatency()));
            }
            case Storage storage -> {
                specs.add(new Spec("Tipo", storage.storageType() == null ? UNKNOWN
                        : storage.storageType() + (Boolean.TRUE.equals(storage.nvme()) ? " NVMe" : "")));
                specs.add(new Spec("Capacidade", storage.capacityGb() == null ? UNKNOWN : capacity(storage.capacityGb())));
                specs.add(new Spec("Formato", orUnknown(storage.formFactor())));
                specs.add(new Spec("Interface", orUnknown(storage.interfaceName())));
            }
            case PowerSupply psu -> {
                specs.add(new Spec("Potência", psu.wattage() == null ? UNKNOWN : psu.wattage() + " W"));
                specs.add(new Spec("Eficiência", orUnknown(psu.efficiencyRating())));
                specs.add(new Spec("Cabos modulares", psu.modular() == null ? UNKNOWN : switch (psu.modular()) {
                    case "Full" -> "Todos";
                    case "Semi-Modular" -> "Parcialmente";
                    case "Non-Modular" -> "Não";
                    default -> psu.modular();
                }));
                specs.add(new Spec("Formato", orUnknown(psu.formFactor())));
            }
            case PcCase pcCase -> {
                specs.add(new Spec("Formato", orUnknown(pcCase.formFactor())));
                specs.add(new Spec("Placas-mãe aceitas", pcCase.supportedMotherboardFormFactors().isEmpty() ? UNKNOWN
                        : String.join(", ", pcCase.supportedMotherboardFormFactors().stream().sorted().toList())));
                specs.add(new Spec("Placa de vídeo até", pcCase.maxGpuLengthMm() == null ? UNKNOWN : pcCase.maxGpuLengthMm() + " mm"));
                specs.add(new Spec("Cooler até", pcCase.maxCoolerHeightMm() == null ? UNKNOWN : pcCase.maxCoolerHeightMm() + " mm"));
                specs.add(new Spec("Lateral transparente", yesNo(pcCase.transparentSidePanel())));
            }
            case CpuCooler cooler -> {
                specs.add(new Spec("Tipo", cooler.waterCooled() == null ? UNKNOWN : cooler.isAirCooler() ? "Ar"
                        : "Líquido" + (cooler.radiatorSizeMm() == null ? "" : " (radiador de " + cooler.radiatorSizeMm() + " mm)")));
                specs.add(new Spec("Altura", cooler.heightMm() == null ? UNKNOWN : cooler.heightMm() + " mm"));
                specs.add(new Spec("Ventoinhas", orUnknown(cooler.fanCount())));
                specs.add(new Spec("Encaixes compatíveis", cooler.sockets().isEmpty() ? UNKNOWN : cooler.sockets().size() + " tipos"));
            }
        }
        return specs;
    }

    private static String coresText(Cpu cpu) {
        if (cpu.effectiveEfficiencyCores() > 0) {
            return cpu.cores() + " núcleos (" + cpu.effectivePerformanceCores() + " de desempenho + " + cpu.effectiveEfficiencyCores() + " de eficiência)";
        }
        return cpu.cores() + " núcleos";
    }

    private static String connectorsText(Gpu.PowerConnectors connectors) {
        StringJoiner joiner = new StringJoiner(" + ");
        if (connectors.highPower16Pin() > 0) {
            joiner.add(connectors.highPower16Pin() + " × 16 pinos");
        }
        if (connectors.eightPin() > 0) {
            joiner.add(connectors.eightPin() + " × 8 pinos");
        }
        if (connectors.sixPin() > 0) {
            joiner.add(connectors.sixPin() + " × 6 pinos");
        }
        return joiner.length() == 0 ? "Nenhum (energia pelo encaixe)" : joiner.toString();
    }

    static String capacity(int gb) {
        if (gb >= 1000) {
            return gb % 1000 == 0 ? gb / 1000 + " TB" : String.format(Locale.of("pt", "BR"), "%.1f TB", gb / 1000.0);
        }
        return gb + " GB";
    }

    private static String ghz(double value) {
        return String.format(Locale.of("pt", "BR"), "%.1f GHz", value);
    }

    private static String number(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.of("pt", "BR"), "%.1f", value);
    }

    private static String yesNo(Boolean value) {
        return value == null ? UNKNOWN : value ? "Sim" : "Não";
    }

    private static String orUnknown(Object value) {
        return value == null ? UNKNOWN : value.toString();
    }

    private static String joinNatural(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " e " + items.getLast();
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
