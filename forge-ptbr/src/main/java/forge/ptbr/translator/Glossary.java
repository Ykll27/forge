package forge.ptbr.translator;
import forge.ptbr.translator.TranslationMemory.Kind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Glossário curado, complementar à terminologia minerada. Formato TSV:
 * {@code TIPO<TAB>inglês<TAB>português<TAB>status[<TAB>nota]}, com status
 * {@code oficial} (conferido em material oficial) ou {@code comunidade}
 * (mecânica sem termo oficial em português; proposta revisável).
 *
 * <p>Entradas do glossário só são usadas quando a memória oficial não tem a chave,
 * de modo que um termo minerado de carta impressa sempre vence.</p>
 */
public final class Glossary {
    public static final String RESOURCE = "/forge/ptbr/glossario.tsv";

    private final EnumMap<Kind, Map<String, String>> terms = new EnumMap<>(Kind.class);
    private final EnumMap<Kind, Map<String, Boolean>> official = new EnumMap<>(Kind.class);

    public Glossary() {
        for (Kind k : Kind.values()) {
            terms.put(k, new HashMap<>());
            official.put(k, new HashMap<>());
        }
    }

    public static Glossary empty() {
        return new Glossary();
    }

    /** Glossário embutido no módulo. */
    public static Glossary bundled() {
        Glossary g = new Glossary();
        try (InputStream in = Glossary.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                g.load(in);
            }
        } catch (IOException e) {
            // glossário opcional: sem ele, só a terminologia minerada é usada
        }
        return g;
    }

    /** Acrescenta entradas de um arquivo (por exemplo, o glossário editado pelo usuário). */
    public Glossary loadFile(Path file) throws IOException {
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                load(in);
            }
        }
        return this;
    }

    public Glossary load(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\t");
            if (p.length < 3) {
                continue;
            }
            Kind kind;
            try {
                kind = Kind.valueOf(p[0].trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            put(kind, p[1].trim(), p[2].trim(), p.length > 3 && "oficial".equalsIgnoreCase(p[3].trim()));
        }
        return this;
    }

    public void put(Kind kind, String en, String pt, boolean isOfficial) {
        terms.get(kind).put(en, pt);
        official.get(kind).put(en, isOfficial);
    }

    public String lookup(Kind kind, String en) {
        return terms.get(kind).get(en);
    }

    public boolean isOfficial(Kind kind, String en) {
        return Boolean.TRUE.equals(official.get(kind).get(en));
    }

    public int size() {
        int n = 0;
        for (Map<String, String> m : terms.values()) {
            n += m.size();
        }
        return n;
    }
}
