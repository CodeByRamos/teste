package app.platform.recommendation;

/** What the person wants to do with the computer, in their words rather than hardware terms. */
public enum UseCase {
    GAMING_COMPETITIVE("Jogos competitivos", "jogos como Valorant, CS2, Fortnite e LoL, com muitos quadros por segundo"),
    GAMING_AAA("Jogos pesados", "lançamentos com gráficos avançados"),
    PROGRAMMING("Programação", "editores, compiladores e várias ferramentas abertas ao mesmo tempo"),
    CONTAINERS_VMS("Docker e máquinas virtuais", "rodar vários ambientes isolados ao mesmo tempo"),
    VIDEO_EDITING("Edição de vídeo", "cortar, aplicar efeitos e exportar vídeos"),
    STREAMING("Fazer lives", "jogar ou trabalhar enquanto transmite ao vivo"),
    OFFICE_STUDY("Estudo e trabalho", "navegador, documentos, planilhas e reuniões");

    private final String label;
    private final String description;

    UseCase(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean isGaming() {
        return this == GAMING_COMPETITIVE || this == GAMING_AAA;
    }
}
