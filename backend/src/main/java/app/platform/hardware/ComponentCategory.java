package app.platform.hardware;

/**
 * Categories of parts the platform assembles into a desktop PC.
 * Labels are user-facing (pt-BR) and intentionally jargon-free.
 */
public enum ComponentCategory {
    CPU("Processador"),
    GPU("Placa de vídeo"),
    MOTHERBOARD("Placa-mãe"),
    MEMORY("Memória RAM"),
    STORAGE("Armazenamento"),
    POWER_SUPPLY("Fonte de alimentação"),
    CASE("Gabinete"),
    CPU_COOLER("Cooler do processador");

    private final String label;

    ComponentCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
