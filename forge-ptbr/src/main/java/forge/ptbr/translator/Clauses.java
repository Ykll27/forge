package forge.ptbr.translator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decomposição de frases de regras em partes que se traduzem de forma independente:
 * custo/efeito ("{T}: Adicione {G}.") e cabeça/efeito ("Quando ..., compre um card.").
 * Usada igualmente na mineração e na tradução, para que as chaves coincidam.
 */
final class Clauses {
    private static final Pattern HEAD_WORDS = Pattern.compile(
            "^(When|Whenever|At|If|As|During|Until|For as long as|Each time|Otherwise|Then|Once|Unless|Except|For each|Starting|Instead)\\b.*", SemanticCheck.WORDS);

    private Clauses() {
    }

    static final class Split {
        private final String head;
        private final String rest;

        Split(String head, String rest) {
            this.head = head;
            this.rest = rest;
        }

        public String head() {
            return head;
        }

        public String rest() {
            return rest;
        }
    }

    /** "When X, Y." → ("When X,", "Y.") se a frase começa com palavra de condição. */
    static Split head(String sentence, boolean english) {
        if (english && !HEAD_WORDS.matcher(sentence).matches()) {
            return null;
        }
        int idx = indexOutsideQuotes(sentence, ", ");
        if (idx <= 0 || idx + 2 >= sentence.length()) {
            return null;
        }
        String head = sentence.substring(0, idx + 1);
        String rest = sentence.substring(idx + 2);
        if (head.indexOf(':') >= 0) {
            return null;
        }
        return new Split(head, rest);
    }

    /** "{2}{G}, {T}: Efeito." → ("{2}{G}, {T}", "Efeito."). */
    static Split cost(String sentence) {
        int idx = indexOutsideQuotes(sentence, ": ");
        if (idx <= 0) {
            return null;
        }
        String cost = sentence.substring(0, idx);
        if (cost.contains(".") || cost.length() > 120) {
            return null;
        }
        return new Split(cost, sentence.substring(idx + 2));
    }

    /** Itens de um custo separados por vírgula, sem quebrar dentro de símbolos ou aspas. */
    static List<String> costItems(String cost) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < cost.length(); i++) {
            char c = cost.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == ',' && depth == 0 && !quoted && i + 1 < cost.length() && cost.charAt(i + 1) == ' ') {
                items.add(cost.substring(start, i).trim());
                start = i + 2;
            }
        }
        items.add(cost.substring(start).trim());
        // "{2}, {T}, Sacrifice X" e "{2}, {T}, and sacrifice" — o "and" final só no inglês antigo
        return items;
    }

    static int indexOutsideQuotes(String s, String needle) {
        boolean quoted = false;
        int depth = 0;
        for (int i = 0; i <= s.length() - needle.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '“' || c == '”') {
                quoted = !quoted;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (!quoted && depth == 0 && s.startsWith(needle, i)) {
                return i;
            }
        }
        return -1;
    }

    /** Remove o marcador de modo ("• ") e devolve o restante, ou null. */
    static String stripBullet(String line) {
        if (line.startsWith("• ")) {
            return line.substring(2);
        }
        if (line.startsWith("•")) {
            return line.substring(1).trim();
        }
        return null;
    }

    /** "Landfall — Whenever..." → ("Landfall", "Whenever..."). */
    static Split abilityWord(String line) {
        int idx = line.indexOf(" — ");
        if (idx <= 0 || idx > 60) {
            return null;
        }
        String head = line.substring(0, idx);
        if (head.contains(".") || head.contains(":") || head.contains("{") || head.contains("\"")) {
            return null;
        }
        return new Split(head, line.substring(idx + 3));
    }

    /** Lista de citações ("...") na ordem; usa aspas retas ou tipográficas. */
    static List<int[]> quotes(String s) {
        List<int[]> spans = new ArrayList<>();
        int open = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean isQuote = c == '"' || c == '“' || c == '”';
            if (!isQuote) {
                continue;
            }
            if (open < 0) {
                open = i;
            } else {
                spans.add(new int[]{open, i});
                open = -1;
            }
        }
        return spans;
    }
}
