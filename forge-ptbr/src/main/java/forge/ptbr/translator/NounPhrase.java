package forge.ptbr.translator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sintagma nominal de regras ("another target nonland permanent you control") analisado e
 * renderizado em português com gênero, número, artigo e posição de "alvo" como no texto oficial
 * ("outra permanente não de terreno alvo que você controla").
 *
 * <p>A análise é estrita: qualquer palavra não reconhecida faz {@link #parse} devolver null, e a
 * frase inteira fica sem tradução automática em vez de sair com um pedaço errado.</p>
 */
final class NounPhrase {
    enum Det {
        TARGET, ANOTHER_TARGET, UP_TO_TARGET, NUMBER_TARGET, EACH, EACH_OTHER, ALL, INDEFINITE,
        ANOTHER, OTHER_PLURAL, ENCHANTED, EQUIPPED, SELF, ANY_TARGET, NUMBER,
        /** Plural sem determinante ("Creatures you control") → artigo definido ("As criaturas..."). */
        BARE_PLURAL
    }

    /** Substantivo português: singular, plural, feminino? */
    static final class Noun {
        private final String singular;
        private final String plural;
        private final boolean feminine;

        Noun(String singular, String plural, boolean feminine) {
            this.singular = singular;
            this.plural = plural;
            this.feminine = feminine;
        }

        public String singular() {
            return singular;
        }

        public String plural() {
            return plural;
        }

        public boolean feminine() {
            return feminine;
        }
        }

    private static final Map<String, Noun> NOUNS = new HashMap<>();
    private static final Map<String, String[]> COLORS = new HashMap<>(); // m, f, mpl, fpl
    private static final Pattern NUMBER_WORD = Pattern.compile("(two|three|four|five|six|seven|eight|nine|ten|X|\\d+)");

    static {
        noun("creature", "criatura", "criaturas", true);
        noun("artifact", "artefato", "artefatos", false);
        noun("enchantment", "encantamento", "encantamentos", false);
        noun("land", "terreno", "terrenos", false);
        noun("permanent", "permanente", "permanentes", true);
        noun("planeswalker", "planeswalker", "planeswalkers", false);
        noun("battle", "batalha", "batalhas", true);
        noun("spell", "mágica", "mágicas", true);
        noun("card", "card", "cards", false);
        noun("player", "jogador", "jogadores", false);
        noun("opponent", "oponente", "oponentes", false);
        noun("token", "ficha", "fichas", true);
        noun("Equipment", "Equipamento", "Equipamentos", false);
        noun("Aura", "Aura", "Auras", true);
        noun("Vehicle", "Veículo", "Veículos", false);
        noun("instant", "mágica instantânea", "mágicas instantâneas", true);
        noun("sorcery", "feitiço", "feitiços", false);
        noun("creature or planeswalker", "criatura ou planeswalker", "criaturas ou planeswalkers", true);
        noun("player or planeswalker", "jogador ou planeswalker", "jogadores ou planeswalkers", false);
        noun("opponent or planeswalker", "oponente ou planeswalker", "oponentes ou planeswalkers", false);
        noun("creature or player", "criatura ou jogador", "criaturas ou jogadores", true);
        noun("artifact or enchantment", "artefato ou encantamento", "artefatos ou encantamentos", false);
        noun("artifact or creature", "artefato ou criatura", "artefatos ou criaturas", false);
        noun("creature or Vehicle", "criatura ou Veículo", "criaturas ou Veículos", true);
        noun("instant or sorcery spell", "mágica instantânea ou de feitiço", "mágicas instantâneas ou de feitiço", true);
        noun("creature spell", "mágica de criatura", "mágicas de criatura", true);
        noun("artifact spell", "mágica de artefato", "mágicas de artefato", true);
        noun("enchantment spell", "mágica de encantamento", "mágicas de encantamento", true);
        noun("creature card", "card de criatura", "cards de criatura", false);
        noun("land card", "card de terreno", "cards de terreno", false);
        noun("artifact card", "card de artefato", "cards de artefato", false);
        noun("enchantment card", "card de encantamento", "cards de encantamento", false);
        noun("permanent card", "card de permanente", "cards de permanente", false);
        noun("instant card", "card de mágica instantânea", "cards de mágica instantânea", false);
        noun("sorcery card", "card de feitiço", "cards de feitiço", false);
        noun("instant or sorcery card", "card de mágica instantânea ou de feitiço", "cards de mágica instantânea ou de feitiço", false);
        noun("artifact or creature card", "card de artefato ou de criatura", "cards de artefato ou de criatura", false);
        noun("planeswalker card", "card de planeswalker", "cards de planeswalker", false);
        noun("legendary creature", "criatura lendária", "criaturas lendárias", true);
        noun("basic land card", "card de terreno básico", "cards de terreno básico", false);
        noun("basic land", "terreno básico", "terrenos básicos", false);
        noun("artifact creature", "criatura artefato", "criaturas artefato", true);
        color("white", "branco", "branca", "brancos", "brancas");
        color("blue", "azul", "azul", "azuis", "azuis");
        color("black", "preto", "preta", "pretos", "pretas");
        color("red", "vermelho", "vermelha", "vermelhos", "vermelhas");
        color("green", "verde", "verde", "verdes", "verdes");
        color("colorless", "incolor", "incolor", "incolores", "incolores");
        color("multicolored", "multicolorido", "multicolorida", "multicoloridos", "multicoloridas");
        color("monocolored", "monocolorido", "monocolorida", "monocoloridos", "monocoloridas");
    }

    private static void noun(String en, String sg, String pl, boolean fem) {
        NOUNS.put(en, new Noun(sg, pl, fem));
        String enPlural = pluralEn(en);
        NOUNS.put(enPlural, new Noun(sg, pl, fem));
    }

    static String pluralEn(String en) {
        // pluraliza o núcleo: "creature card" → "creature cards", "sorcery" → "sorceries"
        if (en.endsWith("sorcery")) {
            return en.substring(0, en.length() - 1) + "ies";
        }
        if (en.endsWith("y") && !en.endsWith("ay")) {
            return en.substring(0, en.length() - 1) + "ies";
        }
        if (en.endsWith("ch") || en.endsWith("s")) {
            return en + "es";
        }
        if (en.endsWith(" or planeswalker") || en.endsWith(" or enchantment") || en.endsWith(" or creature")
                || en.endsWith(" or Vehicle") || en.endsWith(" or player")) {
            int or = en.indexOf(" or ");
            return pluralEn(en.substring(0, or)) + " or " + pluralEn(en.substring(or + 4));
        }
        return en + "s";
    }

    private static void color(String en, String m, String f, String mpl, String fpl) {
        COLORS.put(en, new String[]{m, f, mpl, fpl});
    }

    // ------------------------------------------------------------------ estado

    Det det;
    int count = 1;          // NUMBER/UP_TO/NUMBER_TARGET
    String countWord;       // "X" ou número original
    Noun noun;
    boolean plural;
    final List<String[]> adjectives = new ArrayList<>(); // formas m, f, mpl, fpl
    final List<String> qualifiers = new ArrayList<>();   // depois de "alvo"
    String selfText;        // para SELF

    boolean isPlural() {
        return plural;
    }

    boolean isFeminine() {
        return noun != null && noun.feminine;
    }

    // ------------------------------------------------------------------ análise

    private static final Pattern QUALIFIERS = Pattern.compile(
            "^(.*?)( you control| you don't control| an opponent controls| your opponents control| you own"
                    + "| that player controls| target player controls| defending player controls)?"
                    + "( with (?:power|toughness|mana value) (?:\\d+|X) or (?:greater|less)| with [a-z][a-z ]*?)?"
                    + "( from your graveyard| from a graveyard| in your graveyard| from your hand| in your hand"
                    + "| from among them| on the battlefield| from exile)?$");

    /**
     * @param keywords tradutor de palavra-chave (para "with flying"); devolve null se não souber
     * @param subtypes subtipo inglês → português, ou null
     */
    static NounPhrase parse(String en, Function<String, String> keywords, Function<String, String> subtypes) {
        String s = en.trim();
        NounPhrase np = new NounPhrase();
        if (s.equals(SelfReference.PLACEHOLDER)) {
            np.det = Det.SELF;
            np.selfText = s;
            return np;
        }
        if (s.equals("any target")) {
            np.det = Det.ANY_TARGET;
            return np;
        }
        // determinante
        Matcher m;
        if (s.startsWith("another target ")) {
            np.det = Det.ANOTHER_TARGET;
            s = s.substring(15);
        } else if (s.startsWith("target ")) {
            np.det = Det.TARGET;
            s = s.substring(7);
        } else if ((m = Pattern.compile("^up to (one|" + NUMBER_WORD.pattern() + ") (other )?target (.*)$").matcher(s)).matches()) {
            np.det = Det.UP_TO_TARGET;
            np.setCount(m.group(1));
            if (m.group(3) != null) {
                return null; // "up to two other target" é raro e muda a ordem
            }
            s = m.group(4);
        } else if ((m = Pattern.compile("^" + NUMBER_WORD.pattern() + " target (.*)$").matcher(s)).matches()) {
            np.det = Det.NUMBER_TARGET;
            np.setCount(m.group(1));
            s = m.group(2);
        } else if (s.startsWith("each other ")) {
            np.det = Det.EACH_OTHER;
            s = s.substring(11);
        } else if (s.startsWith("each ")) {
            np.det = Det.EACH;
            s = s.substring(5);
        } else if (s.startsWith("all other ")) {
            np.det = Det.OTHER_PLURAL;
            s = s.substring(10);
        } else if (s.startsWith("all ")) {
            np.det = Det.ALL;
            s = s.substring(4);
        } else if (s.startsWith("another ")) {
            np.det = Det.ANOTHER;
            s = s.substring(8);
        } else if (s.startsWith("other ")) {
            np.det = Det.OTHER_PLURAL;
            s = s.substring(6);
        } else if (s.startsWith("enchanted ")) {
            np.det = Det.ENCHANTED;
            s = s.substring(10);
        } else if (s.startsWith("equipped ")) {
            np.det = Det.EQUIPPED;
            s = s.substring(9);
        } else if (s.startsWith("a ") || s.startsWith("an ")) {
            np.det = Det.INDEFINITE;
            s = s.substring(s.indexOf(' ') + 1);
        } else if ((m = Pattern.compile("^" + NUMBER_WORD.pattern() + " (.*)$").matcher(s)).matches()) {
            np.det = Det.NUMBER;
            np.setCount(m.group(1));
            s = m.group(2);
        } else {
            np.det = Det.BARE_PLURAL;
        }
        // qualificadores finais
        m = QUALIFIERS.matcher(s);
        if (!m.matches()) {
            return null;
        }
        String core = m.group(1);
        if (m.group(2) != null) {
            np.qualifiers.add(control(m.group(2).trim(), np));
        }
        if (m.group(3) != null) {
            String with = withClause(m.group(3).trim(), keywords);
            if (with == null) {
                return null;
            }
            np.qualifiers.add(with);
        }
        if (m.group(4) != null) {
            np.qualifiers.add(zone(m.group(4).trim()));
        }
        // adjetivos + núcleo
        String[] words = core.split(" ");
        int i = 0;
        while (i < words.length) {
            String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
            Noun n = NOUNS.get(rest);
            if (n != null) {
                np.noun = n;
                np.plural = isPluralEnglish(rest, np);
                return np.valid() ? np : null;
            }
            String sub = subtypeNoun(rest, subtypes, np);
            if (sub != null) {
                return np.valid() ? np : null;
            }
            String[] adj = adjective(words[i]);
            if (adj == null) {
                return null;
            }
            np.adjectives.add(adj);
            i++;
        }
        return null;
    }

    private boolean valid() {
        if (det == Det.TARGET || det == Det.ANOTHER_TARGET || det == Det.EACH || det == Det.EACH_OTHER
                || det == Det.INDEFINITE || det == Det.ANOTHER || det == Det.ENCHANTED || det == Det.EQUIPPED) {
            return !plural;
        }
        if (det == Det.ALL || det == Det.OTHER_PLURAL || det == Det.BARE_PLURAL) {
            return plural;
        }
        if (det == Det.NUMBER || det == Det.NUMBER_TARGET || det == Det.UP_TO_TARGET) {
            return count == 1 ? !plural : plural;
        }
        return true;
    }

    private void setCount(String word) {
        countWord = word;
        if ("one".equals(word)) {
            count = 1;
        } else if (word.matches("\\d+")) {
            count = Integer.parseInt(word);
        } else if ("X".equals(word)) {
            count = 99;
        } else {
            count = Slots.englishNumber(word);
        }
    }

    private static boolean isPluralEnglish(String en, NounPhrase np) {
        Noun n = NOUNS.get(en);
        // a chave plural foi registrada a partir do singular; compara pela forma
        for (Map.Entry<String, Noun> e : NOUNS.entrySet()) {
            if (e.getValue() == n && !e.getKey().equals(en) && pluralEn(e.getKey()).equals(en)) {
                return true;
            }
        }
        return false;
    }

    private static String subtypeNoun(String rest, Function<String, String> subtypes, NounPhrase np) {
        if (subtypes == null) {
            return null;
        }
        // "Goblin", "Goblins", "Goblin creature", "Elf card"
        String[] forms = {rest, rest.endsWith("s") ? rest.substring(0, rest.length() - 1) : null,
                rest.endsWith("es") ? rest.substring(0, rest.length() - 2) : null};
        for (int k = 0; k < forms.length; k++) {
            String f = forms[k];
            if (f == null || f.isEmpty() || !Character.isUpperCase(f.charAt(0)) || f.contains(" ")) {
                continue;
            }
            String pt = subtypes.apply(f);
            if (pt == null) {
                continue;
            }
            boolean fem = pt.endsWith("a") && !pt.endsWith("ista");
            String plural = pt.endsWith("r") || pt.endsWith("z") ? pt + "es"
                    : pt.endsWith("ão") ? pt.substring(0, pt.length() - 2) + "ões"
                    : pt.endsWith("l") ? pt.substring(0, pt.length() - 1) + "is" : pt + "s";
            np.noun = new Noun(pt, plural, fem);
            np.plural = k > 0;
            return pt;
        }
        return null;
    }

    private static String[] adjective(String w) {
        String[] c = COLORS.get(w);
        if (c != null) {
            return c;
        }
        if (w.startsWith("non") && COLORS.containsKey(w.substring(3))) {
            String[] base = COLORS.get(w.substring(3));
            return new String[]{"não " + base[0], "não " + base[1], "não " + base[2], "não " + base[3]};
        }
        switch (w) {
            case "tapped":
                return new String[]{"virado", "virada", "virados", "viradas"};
            case "untapped":
                return new String[]{"desvirado", "desvirada", "desvirados", "desviradas"};
            case "attacking":
                return new String[]{"atacante", "atacante", "atacantes", "atacantes"};
            case "blocking":
                return new String[]{"bloqueador", "bloqueadora", "bloqueadores", "bloqueadoras"};
            case "legendary":
                return new String[]{"lendário", "lendária", "lendários", "lendárias"};
            case "nonland":
                return fixed("não de terreno");
            case "noncreature":
                return fixed("não de criatura");
            case "nonartifact":
                return fixed("não de artefato");
            case "nonenchantment":
                return fixed("não de encantamento");
            case "nontoken":
                return fixed("não ficha");
            case "nonlegendary":
                return new String[]{"não lendário", "não lendária", "não lendários", "não lendárias"};
            case "nonbasic":
                return new String[]{"não básico", "não básica", "não básicos", "não básicas"};
            case "basic":
                return new String[]{"básico", "básica", "básicos", "básicas"};
            case "snow":
                return fixed("da neve");
            default:
                return null;
        }
    }

    private static String[] fixed(String s) {
        return new String[]{s, s, s, s};
    }

    private static String control(String q, NounPhrase np) {
        return switch (q) {
            case "you control" -> "que você controla";
            case "you don't control" -> "que você não controla";
            case "an opponent controls" -> "que um oponente controla";
            case "your opponents control" -> "que seus oponentes controlam";
            case "you own" -> "que você possui";
            case "that player controls" -> "que aquele jogador controla";
            case "target player controls" -> "que o jogador alvo controla";
            case "defending player controls" -> "que o jogador defensor controla";
            default -> q;
        };
    }

    private static String withClause(String w, Function<String, String> keywords) {
        Matcher m = Pattern.compile("^with (power|toughness|mana value) (\\d+|X) or (greater|less)$").matcher(w);
        if (m.matches()) {
            String what = switch (m.group(1)) {
                case "power" -> "poder";
                case "toughness" -> "resistência";
                default -> "valor de mana";
            };
            return "com " + what + " igual ou " + ("greater".equals(m.group(3)) ? "superior" : "inferior") + " a " + m.group(2);
        }
        String kw = w.substring(5);
        String pt = keywords == null ? null : keywords.apply(kw);
        return pt == null ? null : "com " + MtgText.decapitalize(pt);
    }

    private static String zone(String z) {
        return switch (z) {
            case "from your graveyard" -> "de seu cemitério";
            case "from a graveyard" -> "de um cemitério";
            case "in your graveyard" -> "em seu cemitério";
            case "from your hand" -> "de sua mão";
            case "in your hand" -> "em sua mão";
            case "from among them" -> "entre eles";
            case "on the battlefield" -> "no campo de batalha";
            case "from exile" -> "do exílio";
            default -> z;
        };
    }

    // ------------------------------------------------------------------ renderização

    private String adjectivesPt() {
        StringBuilder sb = new StringBuilder();
        int form = (plural ? 2 : 0) + (isFeminine() ? 1 : 0);
        for (String[] a : adjectives) {
            sb.append(' ').append(a[form]);
        }
        return sb.toString();
    }

    private String nounPt() {
        return plural ? noun.plural : noun.singular;
    }

    private String qualifiersPt() {
        StringBuilder sb = new StringBuilder();
        for (String q : qualifiers) {
            sb.append(' ').append(q);
        }
        return sb.toString();
    }

    private String countPt() {
        if ("X".equals(countWord)) {
            return "X";
        }
        if (countWord != null && countWord.matches("\\d+")) {
            return countWord;
        }
        return Slots.portugueseNumber(count, isFeminine());
    }

    /** Artigo definido adequado (a, o, as, os). */
    String article() {
        return (isFeminine() ? "a" : "o") + (plural ? "s" : "");
    }

    /** Forma sem preposição (sujeito ou objeto direto). */
    String render() {
        return switch (det) {
            case SELF -> selfText;
            case ANY_TARGET -> "qualquer alvo";
            case TARGET -> article() + " " + nounPt() + adjectivesPt() + " alvo" + qualifiersPt();
            case ANOTHER_TARGET -> (isFeminine() ? "outra " : "outro ") + nounPt() + adjectivesPt() + " alvo" + qualifiersPt();
            case UP_TO_TARGET -> "até " + (count == 1 ? (isFeminine() ? "uma" : "um") : countPt()) + " "
                    + nounPt() + adjectivesPt() + " alvo" + qualifiersPt();
            case NUMBER_TARGET -> countPt() + " " + nounPt() + adjectivesPt() + " alvo" + qualifiersPt();
            case EACH -> "cada " + nounPt() + adjectivesPt() + qualifiersPt();
            case EACH_OTHER -> "cada " + (isFeminine() ? "outra " : "outro ") + nounPt() + adjectivesPt() + qualifiersPt();
            case ALL -> (isFeminine() ? "todas as " : "todos os ") + nounPt() + adjectivesPt() + qualifiersPt();
            case OTHER_PLURAL -> (isFeminine() ? "as outras " : "os outros ") + nounPt() + adjectivesPt() + qualifiersPt();
            case INDEFINITE -> (isFeminine() ? "uma " : "um ") + nounPt() + adjectivesPt() + qualifiersPt();
            case ANOTHER -> (isFeminine() ? "outra " : "outro ") + nounPt() + adjectivesPt() + qualifiersPt();
            case ENCHANTED -> article() + " " + nounPt() + adjectivesPt() + (isFeminine() ? " encantada" : " encantado") + qualifiersPt();
            case EQUIPPED -> article() + " " + nounPt() + adjectivesPt() + (isFeminine() ? " equipada" : " equipado") + qualifiersPt();
            case NUMBER -> countPt() + " " + nounPt() + adjectivesPt() + qualifiersPt();
            case BARE_PLURAL -> article() + " " + nounPt() + adjectivesPt() + qualifiersPt();
        };
    }

    /** Com preposição e contração: a+a=à, de+o=do, em+a=na, por+o=pelo. */
    String withPreposition(String prep) {
        String r = render();
        boolean definite = r.startsWith("a ") || r.startsWith("o ") || r.startsWith("as ") || r.startsWith("os ");
        if (!definite) {
            return prep + " " + r;
        }
        String art = r.substring(0, r.indexOf(' '));
        String rest = r.substring(r.indexOf(' ') + 1);
        String contracted = switch (prep) {
            case "a" -> switch (art) {
                case "a" -> "à";
                case "as" -> "às";
                case "o" -> "ao";
                default -> "aos";
            };
            case "de" -> "d" + art;
            case "em" -> "n" + art;
            case "por" -> "pel" + art;
            default -> prep + " " + art;
        };
        return contracted + " " + rest;
    }

    /** Sujeito no início da frase (maiúscula). */
    String subject() {
        return MtgText.capitalize(render());
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
