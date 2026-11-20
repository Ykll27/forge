package forge.ptbr.translator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Concordância de número depois de preencher lacunas: um modelo aprendido com
 * "2 pontos de dano" aplicado a 1 precisa virar "1 ponto de dano", e vice-versa.
 */
final class Agreement {
    private static final String[][] NOUNS = {
            {"ponto", "pontos"}, {"marcador", "marcadores"}, {"card", "cards"}, {"ficha", "fichas"},
            {"criatura", "criaturas"}, {"terreno", "terrenos"}, {"mágica", "mágicas"}, {"vez", "vezes"},
            {"turno", "turnos"}, {"jogador", "jogadores"}, {"oponente", "oponentes"}, {"alvo", "alvos"},
    };
    private static final Pattern NUMBER_NOUN;

    static {
        StringBuilder alt = new StringBuilder();
        for (String[] n : NOUNS) {
            alt.append(alt.length() == 0 ? "" : "|").append(n[0]).append('|').append(n[1]);
        }
        NUMBER_NOUN = Pattern.compile("(?<![\\w{/+\\-\u2212])(\\d+) (" + alt + ")\\b", SemanticCheck.WORDS);
    }

    private Agreement() {
    }

    static String fix(String text) {
        Matcher m = NUMBER_NOUN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            boolean singular = "1".equals(m.group(1)) || "0".equals(m.group(1)) && false;
            String noun = m.group(2);
            String fixed = noun;
            for (String[] n : NOUNS) {
                if (noun.equals(n[0]) || noun.equals(n[1])) {
                    fixed = singular ? n[0] : n[1];
                    break;
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + " " + fixed));
        }
        m.appendTail(out);
        return out.toString();
    }
}
