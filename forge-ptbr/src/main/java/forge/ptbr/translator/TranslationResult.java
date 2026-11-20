package forge.ptbr.translator;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de uma tradução automática.
 *
 * @param text         texto em português, linhas separadas pelo marcador literal {@code \n}
 * @param segments     número de frases/palavras-chave avaliadas
 * @param translated   quantas delas foram traduzidas (as demais ficaram em inglês)
 * @param untranslated trechos que ficaram em inglês, para diagnóstico e revisão
 * @param version      versão do tradutor que produziu o texto (invalida o cache quando muda)
 */
public final class TranslationResult {
    private final String text;
    private final int segments;
    private final int translated;
    private final List<String> untranslated;
    private final int version;

    public TranslationResult(String text, int segments, int translated, List<String> untranslated, int version) {
        untranslated = untranslated == null ? Collections.emptyList() : List.copyOf(untranslated);
        this.text = text;
        this.segments = segments;
        this.translated = translated;
        this.untranslated = untranslated;
        this.version = version;
    }

    public String text() {
        return text;
    }

    public int segments() {
        return segments;
    }

    public int translated() {
        return translated;
    }

    public List<String> untranslated() {
        return untranslated;
    }

    public int version() {
        return version;
    }

    /** Fração traduzida, de 0 a 1. Texto vazio conta como totalmente traduzido. */
    public double coverage() {
        return segments == 0 ? 1.0 : (double) translated / segments;
    }

    public boolean isComplete() {
        return translated == segments;
    }
}
