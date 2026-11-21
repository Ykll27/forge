package forge.ptbr.translator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduções revisadas de cartas inteiras sem texto oficial em português (o recurso
 * {@code revisadas.txt}). Foram escritas a partir do Oracle com a terminologia dos textos
 * oficiais, com contexto que as regras não têm (gênero dos pronomes, mecânicas novas), e têm
 * precedência sobre a tradução por memória e regras. Continuam sendo tradução automática:
 * só exibição, e nunca substituem um texto oficial ou uma correção do jogador.
 *
 * <p>Formato: {@code nome em inglês|texto}, com {@code \n} literal entre as linhas, como nos
 * arquivos de tradução do Forge; linhas com {@code #} são comentários.</p>
 */
public final class RevisedTexts {
    static final String RESOURCE = "/forge/ptbr/revisadas.txt";

    private static volatile RevisedTexts bundled;

    private final Map<String, String> texts;

    private RevisedTexts(Map<String, String> texts) {
        this.texts = Collections.unmodifiableMap(texts);
    }

    /** As traduções que vêm com o módulo (carregadas uma vez). */
    public static RevisedTexts bundled() {
        RevisedTexts r = bundled;
        if (r == null) {
            synchronized (RevisedTexts.class) {
                r = bundled;
                if (r == null) {
                    r = load();
                    bundled = r;
                }
            }
        }
        return r;
    }

    private static RevisedTexts load() {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = RevisedTexts.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int bar = line.indexOf('|');
                    if (bar <= 0 || bar == line.length() - 1) {
                        continue;
                    }
                    map.put(line.substring(0, bar), line.substring(bar + 1));
                }
            }
        } catch (IOException e) {
            // recurso opcional: sem ele, valem só a memória e as regras
        }
        return new RevisedTexts(map);
    }

    /** Texto revisado da face (com {@code \n} literal entre linhas), ou null. */
    public String get(String englishName) {
        return englishName == null ? null : texts.get(englishName);
    }

    public int size() {
        return texts.size();
    }

    /** Nomes das faces com texto revisado. */
    public java.util.Set<String> names() {
        return texts.keySet();
    }
}
