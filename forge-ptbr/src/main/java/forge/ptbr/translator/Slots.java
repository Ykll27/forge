package forge.ptbr.translator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generalização de frases por lacunas ("slots"). Uma frase oficial como
 * "Compre dois cards." ensina também "Compre três cards.", e
 * "{2}{G}: Regenere CARDNAME." ensina qualquer custo.
 *
 * <p>Lacunas:</p>
 * <ul>
 *   <li>{@code {M#}} sequência de símbolos (custo), copiada literalmente;</li>
 *   <li>{@code {P#}} poder/resistência (+1/+1, 2/2, X/X), copiado literalmente;</li>
 *   <li>{@code {N#}} número em algarismos, copiado literalmente;</li>
 *   <li>{@code {W#}} número por extenso; em português leva o gênero: {@code {W0:f}} → "duas".</li>
 * </ul>
 * Uma lacuna só é criada quando o mesmo valor aparece dos dois lados; caso contrário a frase
 * é guardada literalmente. Assim a tradução nunca troca um custo ou uma quantidade.
 */
public final class Slots {
    static final Pattern SLOT = Pattern.compile("\\{([MPNW])(\\d+)(?::([mf]))?}");

    // "one" fica fora: em português "um/uma" também é artigo, e a lacuna cairia no lugar errado.
    private static final String[] EN_NUMBERS = {"zero", "two", "three", "four", "five", "six",
            "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "twenty"};
    private static final int[] EN_VALUES = {0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 20};
    private static final Pattern EN_NUMBER_WORD;
    private static final Map<String, Integer> EN_WORD_VALUE = new HashMap<>();

    /** Português: valor → {masculino, feminino}. */
    private static final Map<Integer, String[]> PT_NUMBERS = new HashMap<>();
    private static final Map<String, int[]> PT_WORD_VALUE = new HashMap<>(); // palavra → {valor, gênero(0 m,1 f,2 neutro)}
    private static final Pattern PT_NUMBER_WORD;

    static {
        StringBuilder alt = new StringBuilder();
        for (int i = 0; i < EN_NUMBERS.length; i++) {
            EN_WORD_VALUE.put(EN_NUMBERS[i], EN_VALUES[i]);
            if (alt.length() > 0) {
                alt.append('|');
            }
            alt.append(EN_NUMBERS[i]);
        }
        EN_NUMBER_WORD = Pattern.compile("\\b(" + alt + ")\\b");
        pt(0, "zero", "zero");
        pt(1, "um", "uma");
        pt(2, "dois", "duas");
        pt(3, "três", "três");
        pt(4, "quatro", "quatro");
        pt(5, "cinco", "cinco");
        pt(6, "seis", "seis");
        pt(7, "sete", "sete");
        pt(8, "oito", "oito");
        pt(9, "nove", "nove");
        pt(10, "dez", "dez");
        pt(11, "onze", "onze");
        pt(12, "doze", "doze");
        pt(13, "treze", "treze");
        pt(14, "quatorze", "quatorze");
        pt(15, "quinze", "quinze");
        pt(20, "vinte", "vinte");
        StringBuilder ptAlt = new StringBuilder();
        for (String w : PT_WORD_VALUE.keySet()) {
            if (ptAlt.length() > 0) {
                ptAlt.append('|');
            }
            ptAlt.append(Pattern.quote(w));
        }
        PT_NUMBER_WORD = Pattern.compile("(?<![\\p{L}])(" + ptAlt + ")(?![\\p{L}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static void pt(int value, String m, String f) {
        PT_NUMBERS.put(value, new String[]{m, f});
        if (m.equals(f)) {
            PT_WORD_VALUE.put(m, new int[]{value, 2});
        } else {
            PT_WORD_VALUE.put(m, new int[]{value, 0});
            PT_WORD_VALUE.put(f, new int[]{value, 1});
        }
    }

    private Slots() {
    }

    /** Chave de busca: frase em inglês com lacunas, mais os valores retirados dela. */
    public static final class Keyed {
        private final String key;
        private final List<String> values;

        public Keyed(String key, List<String> values) {
            this.key = key;
            this.values = values;
        }

        public String key() {
            return key;
        }

        public List<String> values() {
            return values;
        }
        }

    /** Par minerado: modelo inglês + modelo português com as mesmas lacunas. */
    public static final class TemplatePair {
        private final String en;
        private final String pt;

        public TemplatePair(String en, String pt) {
            this.en = en;
            this.pt = pt;
        }

        public String en() {
            return en;
        }

        public String pt() {
            return pt;
        }
        }

    /**
     * Extrai as lacunas de uma frase em inglês. A ordem de extração (M, P, N, W) e a
     * numeração são as mesmas usadas na mineração, então a chave casa com o modelo minerado.
     */
    public static Keyed key(String en) {
        List<String> values = new ArrayList<>();
        String s = en;
        s = replaceAll(s, MtgText.SYMBOL_RUN, "M", values);
        s = replaceAll(s, MtgText.POWER_TOUGHNESS, "P", values);
        s = replaceAll(s, MtgText.NUMBER, "N", values);
        s = replaceWords(s, values);
        return new Keyed(s, values);
    }

    private static String replaceAll(String s, Pattern p, String kind, List<String> values) {
        Matcher m = p.matcher(s);
        StringBuilder out = new StringBuilder();
        int idx = countKind(values, kind);
        while (m.find()) {
            values.add(kind + ":" + m.group());
            m.appendReplacement(out, Matcher.quoteReplacement("{" + kind + idx++ + "}"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String replaceWords(String s, List<String> values) {
        Matcher m = EN_NUMBER_WORD.matcher(s);
        StringBuilder out = new StringBuilder();
        int idx = 0;
        while (m.find()) {
            values.add("W:" + EN_WORD_VALUE.get(m.group(1)));
            m.appendReplacement(out, "{W" + idx++ + "}");
        }
        m.appendTail(out);
        return out.toString();
    }

    private static int countKind(List<String> values, String kind) {
        int n = 0;
        for (String v : values) {
            if (v.startsWith(kind + ":")) {
                n++;
            }
        }
        return n;
    }

    /**
     * Cria o par de modelos a partir de uma frase inglesa e sua tradução oficial.
     * Devolve {@code null} se algum valor do inglês não puder ser localizado sem ambiguidade
     * no português; nesse caso o par só serve literalmente.
     */
    public static TemplatePair templatize(String en, String pt) {
        Keyed k = key(en);
        if (k.values.isEmpty()) {
            return new TemplatePair(en, pt);
        }
        String out = pt;
        int[] counters = new int[4];
        for (String v : k.values) {
            String kind = v.substring(0, 1);
            String value = v.substring(2);
            int slotIndex = counters["MPNW".indexOf(kind)]++;
            String slot;
            int pos;
            if ("W".equals(kind)) {
                int number = Integer.parseInt(value);
                Matcher m = PT_NUMBER_WORD.matcher(out);
                pos = -1;
                String gender = null;
                int len = 0;
                while (m.find()) {
                    int[] info = PT_WORD_VALUE.get(m.group(1).toLowerCase(new Locale("pt", "BR")));
                    if (info != null && info[0] == number && !insideSlot(out, m.start())) {
                        pos = m.start();
                        len = m.group().length();
                        gender = info[1] == 1 ? "f" : "m";
                        break;
                    }
                }
                if (pos < 0) {
                    return null;
                }
                // a maiúscula inicial é recalculada no preenchimento
                slot = "{W" + slotIndex + ":" + gender + "}";
                out = out.substring(0, pos) + slot + out.substring(pos + len);
            } else {
                String literal = value;
                pos = findLiteral(out, literal, kind);
                if (pos < 0 && "P".equals(kind)) {
                    pos = findLiteral(out, literal.replace('-', '−'), kind);
                    if (pos < 0) {
                        pos = findLiteral(out, literal.replace('−', '-'), kind);
                    }
                }
                if (pos < 0) {
                    return null;
                }
                slot = "{" + kind + slotIndex + "}";
                int len = literalLengthAt(out, pos, kind);
                out = out.substring(0, pos) + slot + out.substring(pos + len);
            }
        }
        // valores restantes no português que o inglês não tinha indicam desalinhamento
        if (!MtgText.invariants(SLOT.matcher(out).replaceAll(" ")).isEmpty()) {
            return null;
        }
        return new TemplatePair(k.key, out);
    }

    /** A posição cai dentro de uma lacuna já criada (estritamente antes dela há um '{' aberto)? */
    private static boolean insideSlot(String s, int pos) {
        if (pos <= 0) {
            return false;
        }
        int open = s.lastIndexOf('{', pos - 1);
        int close = s.lastIndexOf('}', pos - 1);
        return open > close;
    }

    private static int findLiteral(String s, String literal, String kind) {
        Pattern p = switch (kind) {
            case "M" -> Pattern.compile("(?<![}])" + Pattern.quote(literal) + "(?![{])");
            case "P" -> Pattern.compile("(?<![\\w/])" + Pattern.quote(literal) + "(?![\\w/])");
            default -> Pattern.compile("(?<![\\w{/+\\-−])" + Pattern.quote(literal) + "(?![\\w}/])");
        };
        Matcher m = p.matcher(s);
        while (m.find()) {
            if (!insideSlot(s, m.start())) {
                return m.start();
            }
        }
        return -1;
    }

    private static int literalLengthAt(String s, int pos, String kind) {
        Pattern p = switch (kind) {
            case "M" -> MtgText.SYMBOL_RUN;
            case "P" -> MtgText.POWER_TOUGHNESS;
            default -> MtgText.NUMBER;
        };
        Matcher m = p.matcher(s);
        if (m.find(pos) && m.start() == pos) {
            return m.end() - pos;
        }
        return 0;
    }

    /** Preenche um modelo português com os valores extraídos da frase inglesa. */
    public static String fill(String ptTemplate, List<String> values) {
        List<String> m = new ArrayList<>();
        List<String> p = new ArrayList<>();
        List<String> n = new ArrayList<>();
        List<String> w = new ArrayList<>();
        for (String v : values) {
            String val = v.substring(2);
            switch (v.charAt(0)) {
                case 'M' -> m.add(val);
                case 'P' -> p.add(val);
                case 'N' -> n.add(val);
                default -> w.add(val);
            }
        }
        Matcher matcher = SLOT.matcher(ptTemplate);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String kind = matcher.group(1);
            int idx = Integer.parseInt(matcher.group(2));
            String gender = matcher.group(3);
            List<String> list = switch (kind) {
                case "M" -> m;
                case "P" -> p;
                case "N" -> n;
                default -> w;
            };
            if (idx >= list.size()) {
                return null; // modelo incompatível com a frase
            }
            String value = list.get(idx);
            if ("W".equals(kind)) {
                String[] words = PT_NUMBERS.get(Integer.parseInt(value));
                if (words == null) {
                    return null;
                }
                value = "f".equals(gender) ? words[1] : words[0];
                if (matcher.start() == 0 || isAfterSentenceEnd(ptTemplate, matcher.start())) {
                    value = MtgText.capitalize(value);
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean isAfterSentenceEnd(String s, int at) {
        int i = at - 1;
        while (i >= 0 && s.charAt(i) == ' ') {
            i--;
        }
        return i >= 0 && (s.charAt(i) == '.' || s.charAt(i) == '•');
    }

    /** Número por extenso em português (para as regras gramaticais). */
    public static String portugueseNumber(int value, boolean feminine) {
        String[] words = PT_NUMBERS.get(value);
        if (words == null) {
            return Integer.toString(value);
        }
        return feminine ? words[1] : words[0];
    }

    /** Valor de um número por extenso em inglês, ou -1. */
    public static int englishNumber(String word) {
        Integer v = EN_WORD_VALUE.get(word.toLowerCase(Locale.ROOT));
        return v == null ? -1 : v;
    }
}
