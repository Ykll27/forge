package forge.ptbr.model;

/**
 * Origem de um texto exibido em português. A ordem do enum é a ordem de prioridade:
 * uma origem posterior substitui uma anterior quando as duas têm o mesmo campo.
 */
public enum TextSource {
    /** Gerado pelo tradutor de efeitos. Nunca substitui texto oficial. */
    AUTOMATIC("Tradução automática", false),
    /** Texto oficial distribuído junto com o Forge (res/languages/cardnames-pt-BR.txt). */
    OFFICIAL_BUNDLED("Texto oficial", true),
    /** Texto oficial baixado do Scryfall (impressão em português mais recente). */
    OFFICIAL_DOWNLOADED("Texto oficial (Scryfall)", true),
    /** Correção feita pelo próprio jogador. */
    USER("Corrigido por você", false);

    private final String label;
    private final boolean official;

    TextSource(String label, boolean official) {
        this.label = label;
        this.official = official;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOfficial() {
        return official;
    }
}
