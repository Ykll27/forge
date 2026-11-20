package forge.ptbr.translator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitários de texto para regras de Magic: separação de frases, texto de lembrete
 * e os "invariantes" (símbolos de mana, modificadores de P/R e números) que precisam
 * sobreviver intactos a qualquer tradução.
 */
public final class MtgText {
    /** Símbolo de mana/custo: {T}, {2}, {W/U}, {G/P}, {E}, {C}, {X}, {S}... */
    public static final Pattern SYMBOL = Pattern.compile("\\{[^{}]{1,8}}");
    /** Sequência contígua de símbolos (um custo inteiro). */
    public static final Pattern SYMBOL_RUN = Pattern.compile("(?:\\{[^{}]{1,8}})+");
    /** Modificador ou valor de poder/resistência: +1/+1, -2/-0, −1/−1, 2/2, X/X, +X/+0. */
    public static final Pattern POWER_TOUGHNESS = Pattern.compile("(?<![\\w/])[+\\-−]?(?:\\d+|X|\\*)/[+\\-−]?(?:\\d+|X|\\*)(?![\\w/])");
    /** Número isolado (fora de símbolos, P/R e palavras). */
    public static final Pattern NUMBER = Pattern.compile("(?<![\\w{/+\\-−])\\d+(?![\\w}/])");
    /** A variável X fora de símbolos ("Create X tokens"): também é um invariante. */
    public static final Pattern VARIABLE_X = Pattern.compile("(?<![\\w{/+\\-\u2212])X(?![\\w}/])");

    private MtgText() {
    }

    /** Remove todo texto de lembrete entre parênteses (fora de aspas) e normaliza espaços. */
    public static String stripReminder(String line) {
        StringBuilder out = new StringBuilder(line.length());
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' || c == '“' || c == '”') {
                quoted = !quoted;
            }
            if (!quoted && c == '(') {
                depth++;
                continue;
            }
            if (!quoted && c == ')' && depth > 0) {
                depth--;
                continue;
            }
            if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString().replaceAll("\\s{2,}", " ").replace(" .", ".").replace(" ,", ",").trim();
    }

    /** Textos de lembrete (sem os parênteses), na ordem em que aparecem. */
    public static List<String> reminders(String line) {
        List<String> found = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            }
            if (quoted) {
                continue;
            }
            if (c == '(') {
                if (depth++ == 0) {
                    start = i + 1;
                }
            } else if (c == ')' && depth > 0 && --depth == 0) {
                found.add(line.substring(start, i).trim());
            }
        }
        return found;
    }

    /** A linha inteira é texto de lembrete? */
    public static boolean isReminderOnly(String line) {
        String t = line.trim();
        return t.startsWith("(") && t.endsWith(")") && stripReminder(t).isEmpty();
    }

    /**
     * Divide em frases terminadas por '.', '!' ou '?' seguidos de espaço/fim,
     * sem quebrar dentro de aspas ou parênteses.
     */
    public static List<String> splitSentences(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean quoted = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            buf.append(c);
            boolean closingQuote = false;
            if (c == '"') {
                quoted = !quoted;
                closingQuote = !quoted;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }
            boolean atBoundary = i + 1 == text.length() || text.charAt(i + 1) == ' ';
            boolean end = (c == '.' || c == '!' || c == '?') && !quoted && depth == 0 && atBoundary;
            // citação que termina a frase: 'ganha "Compre um card." Depois...' — o ponto fica dentro das aspas
            if (closingQuote && depth == 0 && atBoundary && i > 0 && ".!?".indexOf(text.charAt(i - 1)) >= 0) {
                end = true;
            }
            if (end) {
                String s = buf.toString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
                buf.setLength(0);
            }
        }
        String rest = buf.toString().trim();
        if (!rest.isEmpty()) {
            out.add(rest);
        }
        return out;
    }

    /**
     * Invariantes de uma frase: símbolos, P/R e números, em ordem de ocorrência
     * e com o sinal de menos normalizado. Dois textos com os mesmos invariantes
     * (como multiconjunto) descrevem os mesmos custos e quantidades.
     */
    public static List<String> invariants(String text) {
        List<String> found = new ArrayList<>();
        String rest = text;
        Matcher m = SYMBOL.matcher(rest);
        while (m.find()) {
            found.add(m.group());
        }
        rest = SYMBOL.matcher(rest).replaceAll(" ");
        m = POWER_TOUGHNESS.matcher(rest);
        while (m.find()) {
            found.add(m.group().replace('−', '-'));
        }
        rest = POWER_TOUGHNESS.matcher(rest).replaceAll(" ");
        m = NUMBER.matcher(rest);
        while (m.find()) {
            found.add(m.group());
        }
        m = VARIABLE_X.matcher(rest);
        while (m.find()) {
            found.add("X");
        }
        return found;
    }

    /** Compara invariantes ignorando a ordem (traduções podem reordenar a frase). */
    public static boolean sameInvariants(String a, String b) {
        List<String> x = invariants(a);
        List<String> y = invariants(b);
        if (x.size() != y.size()) {
            return false;
        }
        Collections.sort(x);
        Collections.sort(y);
        return x.equals(y);
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty() || !Character.isLowerCase(s.charAt(0))) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * Baixa a primeira letra, exceto quando a frase começa com um marcador
     * ({@code CARDNAME}, slot) ou com palavra toda em maiúsculas/nome próprio composto.
     */
    public static String decapitalize(String s) {
        if (s == null || s.isEmpty() || !Character.isUpperCase(s.charAt(0))) {
            return s;
        }
        if (s.startsWith(SelfReference.PLACEHOLDER) || s.startsWith("{")) {
            return s;
        }
        int end = 0;
        while (end < s.length() && Character.isLetter(s.charAt(end))) {
            end++;
        }
        String word = s.substring(0, end);
        if (word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT))) {
            return s; // sigla
        }
        return s.substring(0, 1).toLowerCase(new Locale("pt", "BR")) + s.substring(1);
    }

    /** Baixa a primeira letra de um sujeito inglês ("Target creature" → "target creature"). */
    public static String decapitalizeEnglish(String s) {
        if (s == null || s.isEmpty() || s.startsWith(SelfReference.PLACEHOLDER) || !Character.isUpperCase(s.charAt(0))) {
            return s;
        }
        return s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    /** Normaliza espaços e o sinal de menos para comparação. */
    public static String normalizeForCompare(String s) {
        return s.replace('−', '-').replace('—', '-').replace('’', '\'')
                .replaceAll("\\s+", " ").trim().toLowerCase(new Locale("pt", "BR"));
    }
}
