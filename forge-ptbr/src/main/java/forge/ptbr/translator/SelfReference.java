package forge.ptbr.translator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Referências de uma carta a si mesma. No texto antigo a carta se refere a si pelo nome
 * ("Quando Elfos de Llanowar entrar..."); a partir de 2024 o inglês usa "this creature".
 * Para que a memória de tradução sirva às duas épocas, ambas viram {@link #PLACEHOLDER}
 * antes da busca, e na volta cada ocorrência é reescrita na forma portuguesa adequada.
 */
public final class SelfReference {
    public static final String PLACEHOLDER = "CARDNAME";
    /** Delimitam uma frase que ficou em inglês: lá dentro a auto-referência volta à forma original. */
    static final char UNTRANSLATED_START = '\u0001';
    static final char UNTRANSLATED_END = '\u0002';

    /** "this creature" → "esta criatura", por tipo. Termos de tipo oficiais em PT. */
    private static final Map<String, String> THIS_FORMS = new LinkedHashMap<>();

    static {
        THIS_FORMS.put("creature", "esta criatura");
        THIS_FORMS.put("enchantment", "este encantamento");
        THIS_FORMS.put("artifact", "este artefato");
        THIS_FORMS.put("land", "este terreno");
        THIS_FORMS.put("spell", "esta mágica");
        THIS_FORMS.put("permanent", "este permanente");
        THIS_FORMS.put("Vehicle", "este Veículo");
        THIS_FORMS.put("Equipment", "este Equipamento");
        THIS_FORMS.put("Aura", "esta Aura");
        THIS_FORMS.put("card", "este card");
        THIS_FORMS.put("token", "esta ficha");
        THIS_FORMS.put("planeswalker", "este planeswalker");
        THIS_FORMS.put("battle", "esta batalha");
        THIS_FORMS.put("Saga", "esta Saga");
        THIS_FORMS.put("Class", "esta Classe");
        THIS_FORMS.put("Case", "este Caso");
        THIS_FORMS.put("Room", "esta Sala");
        THIS_FORMS.put("Spacecraft", "esta Espaçonave");
        THIS_FORMS.put("Planet", "este Planeta");
        THIS_FORMS.put("Siege", "este Cerco");
        THIS_FORMS.put("Attraction", "esta Atração");
        THIS_FORMS.put("Contraption", "esta Engenhoca");
    }

    private static final Pattern THIS_PATTERN = Pattern.compile(
            "\\b([Tt]his) (" + String.join("|", THIS_FORMS.keySet()) + ")\\b");

    private SelfReference() {
    }

    /** Resultado da normalização: texto com marcador + formas originais, em ordem. */
    public static final class Normalized {
        private final String text;
        private final List<String> originals;

        public Normalized(String text, List<String> originals) {
            this.text = text;
            this.originals = originals;
        }

        public String text() {
            return text;
        }

        public List<String> originals() {
            return originals;
        }
        }

    /** Apelido de lendárias: "Ty Lee, Artful Acrobat" → "Ty Lee". */
    public static String shortName(String name) {
        if (name == null) {
            return null;
        }
        int comma = name.indexOf(", ");
        if (comma > 0) {
            return name.substring(0, comma);
        }
        // "Primo the Unbounded" não tem vírgula; "X of the Y" mantém o nome inteiro
        return null;
    }

    /**
     * Substitui o nome, o apelido e "this creature"/"this spell"... por {@link #PLACEHOLDER},
     * e "enters the battlefield" por "enters" (redação de 2024), de modo que texto antigo
     * e novo produzam a mesma chave.
     */
    public static Normalized normalizeEnglish(String text, String name) {
        List<Hit> hits = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            collect(hits, text, Pattern.compile(Pattern.quote(name)));
            String shortName = shortName(name);
            if (shortName != null && shortName.length() >= 3) {
                collect(hits, text, Pattern.compile("\\b" + Pattern.quote(shortName) + "\\b(?!,)"));
            }
            // cartas divididas e de duas faces trazem às vezes só a primeira palavra
        }
        Matcher m = THIS_PATTERN.matcher(text);
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(), m.group()));
        }
        hits.sort((a, b) -> a.start != b.start ? Integer.compare(a.start, b.start) : Integer.compare(b.end, a.end));
        StringBuilder out = new StringBuilder();
        List<String> originals = new ArrayList<>();
        int pos = 0;
        for (Hit h : hits) {
            if (h.start < pos) {
                continue; // sobreposto (o apelido dentro do nome completo)
            }
            out.append(text, pos, h.start).append(PLACEHOLDER);
            originals.add(h.text);
            pos = h.end;
        }
        out.append(text.substring(pos));
        String normalized = out.toString().replace("enters the battlefield", "enters");
        return new Normalized(normalized, originals);
    }

    private static final Pattern PT_THIS_PATTERN;

    static {
        StringBuilder alt = new StringBuilder();
        for (String form : THIS_FORMS.values()) {
            alt.append(alt.length() == 0 ? "" : "|").append(Pattern.quote(form));
        }
        PT_THIS_PATTERN = Pattern.compile("(?<![\\p{L}])(" + alt + ")(?![\\p{L}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * Substitui o nome português (e o apelido) e as formas "esta criatura", "esta Saga"...
     * por {@link #PLACEHOLDER}, simetricamente a {@link #normalizeEnglish}.
     */
    public static String normalizePortuguese(String text, String ptName) {
        text = PT_THIS_PATTERN.matcher(text).replaceAll(PLACEHOLDER);
        if (ptName == null || ptName.isEmpty()) {
            return text;
        }
        // a capitalização do nome no texto nem sempre é a do título ("Sangue Para O Deus...")
        String out = Pattern.compile(Pattern.quote(ptName), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text).replaceAll(PLACEHOLDER);
        String shortName = shortName(ptName);
        if (shortName != null && shortName.length() >= 3) {
            out = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(shortName) + "(?![\\p{L},])", Pattern.UNICODE_CASE)
                    .matcher(out).replaceAll(PLACEHOLDER);
        }
        return out;
    }

    /**
     * Reescreve as ocorrências de {@link #PLACEHOLDER} no texto traduzido.
     * Cada ocorrência usa a forma original correspondente: "this creature" vira "esta criatura";
     * o nome vira o nome em português (ou o inglês, se não houver nome oficial).
     */
    public static String render(String translated, List<String> originals, String displayName) {
        return render(translated, originals, displayName, null);
    }

    /**
     * Como {@link #render(String, List, String)}, sabendo o nome inglês: quando o original usava
     * o apelido ("Thrun"), o português também usa o apelido do nome traduzido.
     */
    public static String render(String translated, List<String> originals, String displayName, String englishName) {
        if (!translated.contains(PLACEHOLDER)) {
            return stripMarkers(translated);
        }
        StringBuilder out = new StringBuilder();
        int pos = 0;
        int idx = 0;
        int at;
        while ((at = translated.indexOf(PLACEHOLDER, pos)) >= 0) {
            out.append(translated, pos, at);
            String original = originals.isEmpty() ? null
                    : originals.get(Math.min(idx, originals.size() - 1));
            if (insideUntranslated(translated, at)) {
                out.append(original == null ? displayName : original);
                pos = at + PLACEHOLDER.length();
                idx++;
                continue;
            }
            String replacement = portugueseFor(original, displayName);
            String enShort = shortName(englishName);
            if (original != null && original.equals(enShort)) {
                String ptShort = shortName(displayName);
                replacement = ptShort != null ? ptShort : displayName;
            }
            boolean sentenceStart = at == 0 || endsSentence(translated, at);
            out.append(sentenceStart ? MtgText.capitalize(replacement) : replacement);
            pos = at + PLACEHOLDER.length();
            idx++;
        }
        out.append(translated.substring(pos));
        return stripMarkers(out.toString());
    }

    private static boolean insideUntranslated(String s, int at) {
        int open = s.lastIndexOf(UNTRANSLATED_START, at);
        int close = s.lastIndexOf(UNTRANSLATED_END, at);
        return open > close;
    }

    static String stripMarkers(String s) {
        if (s.indexOf(UNTRANSLATED_START) < 0) {
            return s;
        }
        return s.replace(String.valueOf(UNTRANSLATED_START), "").replace(String.valueOf(UNTRANSLATED_END), "");
    }

    private static String portugueseFor(String original, String displayName) {
        if (original != null) {
            Matcher m = THIS_PATTERN.matcher(original);
            if (m.matches()) {
                return THIS_FORMS.get(m.group(2));
            }
        }
        return displayName;
    }

    private static boolean endsSentence(String s, int at) {
        int i = at - 1;
        while (i >= 0 && s.charAt(i) == ' ') {
            i--;
        }
        if (i < 0) {
            return true;
        }
        char c = s.charAt(i);
        return c == '.' || c == '"' || c == '•' || c == '—' || c == ':' || c == '!' || c == '\n';
    }

    private static final class Hit {
        private final int start;
        private final int end;
        private final String text;

        Hit(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }

        public String text() {
            return text;
        }
    }

    private static void collect(List<Hit> hits, String text, Pattern p) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(), m.group()));
        }
    }
}
