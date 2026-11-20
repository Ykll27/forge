package forge.ptbr.translator;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Memória de tradução minerada dos textos oficiais. Cada categoria guarda pares
 * "modelo inglês → modelo português" (ver {@link Slots}) com a contagem de cartas
 * oficiais em que o par apareceu; na consulta vence a forma mais frequente.
 *
 * <p>Depois de {@link #freeze()} só o melhor par de cada chave fica em memória,
 * o que reduz o consumo de RAM no celular.</p>
 */
public final class TranslationMemory {
    /** Categorias de entrada. */
    public enum Kind {
        /** Frase inteira de efeito ("Compre um card."). */
        SENTENCE,
        /** Cabeça de gatilho/condição com vírgula final ("Quando CARDNAME entrar no campo de batalha,"). */
        HEAD,
        /** Custo inteiro ou item de custo ("{T}", "Sacrifique CARDNAME"). */
        COST,
        /** Palavra-chave, em minúsculas ("voar", "proteção contra o preto"). */
        KEYWORD,
        /** Palavra de habilidade antes do travessão ("Queda de Terreno"). */
        ABILITY_WORD,
        /** Texto de lembrete, sem parênteses. */
        REMINDER,
        /** Parte principal da linha de tipo ("Legendary Creature" → "Criatura Lendária"). */
        TYPE,
        /** Subtipo ("Elf" → "Elfo"). */
        SUBTYPE,
        /** Nome de ficha predefinida ("Treasure" → "Tesouro"). */
        TOKEN,
        /** Nome de marcador ("oil" → "óleo", usado como "marcador de óleo"). */
        COUNTER
    }

    private final EnumMap<Kind, Map<String, Map<String, Integer>>> counts = new EnumMap<>(Kind.class);
    private final EnumMap<Kind, Map<String, String>> best = new EnumMap<>(Kind.class);
    private boolean frozen;

    public TranslationMemory() {
        for (Kind k : Kind.values()) {
            counts.put(k, new HashMap<>());
            best.put(k, new HashMap<>());
        }
    }

    public void add(Kind kind, String en, String pt) {
        add(kind, en, pt, 1);
    }

    public void add(Kind kind, String en, String pt, int weight) {
        if (frozen) {
            throw new IllegalStateException("memória congelada");
        }
        if (en == null || pt == null || en.trim().isEmpty() || pt.trim().isEmpty()) {
            return;
        }
        counts.get(kind).computeIfAbsent(en, k -> new HashMap<>()).merge(pt, weight, Integer::sum);
    }

    /** Escolhe a melhor tradução de cada chave e descarta as contagens. */
    public TranslationMemory freeze() {
        if (frozen) {
            return this;
        }
        for (Kind kind : Kind.values()) {
            Map<String, String> target = best.get(kind);
            for (Map.Entry<String, Map<String, Integer>> e : counts.get(kind).entrySet()) {
                String chosen = null;
                int max = -1;
                for (Map.Entry<String, Integer> c : e.getValue().entrySet()) {
                    // desempate determinístico: maior contagem, depois ordem alfabética
                    if (c.getValue() > max || (c.getValue() == max && c.getKey().compareTo(chosen) < 0)) {
                        chosen = c.getKey();
                        max = c.getValue();
                    }
                }
                target.put(e.getKey(), chosen);
            }
            counts.get(kind).clear();
        }
        frozen = true;
        return this;
    }

    public String lookup(Kind kind, String en) {
        if (!frozen) {
            throw new IllegalStateException("chame freeze() antes de consultar");
        }
        return best.get(kind).get(en);
    }

    public int size(Kind kind) {
        return frozen ? best.get(kind).size() : counts.get(kind).size();
    }

    public int size() {
        int n = 0;
        for (Kind k : Kind.values()) {
            n += size(k);
        }
        return n;
    }

    /** Permite ajustes manuais depois de congelar (glossário curado, correções). */
    public void put(Kind kind, String en, String pt) {
        if (!frozen) {
            throw new IllegalStateException("chame freeze() antes");
        }
        best.get(kind).put(en, pt);
    }

    /** Serializa (gzip, TSV {@code tipo\tinglês\tportuguês}). Só para memória congelada. */
    public void write(OutputStream out) throws IOException {
        freeze();
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8))) {
            for (Kind kind : Kind.values()) {
                for (Map.Entry<String, String> e : best.get(kind).entrySet()) {
                    w.write(kind.name());
                    w.write('\t');
                    w.write(e.getKey());
                    w.write('\t');
                    w.write(e.getValue());
                    w.write('\n');
                }
            }
        }
    }

    public static TranslationMemory read(InputStream in) throws IOException {
        TranslationMemory tm = new TranslationMemory().freeze();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length == 3) {
                    tm.best.get(Kind.valueOf(p[0])).put(p[1], p[2]);
                }
            }
        }
        return tm;
    }
}
