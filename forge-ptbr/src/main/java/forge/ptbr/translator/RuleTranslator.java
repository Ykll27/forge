package forge.ptbr.translator;
import forge.ptbr.translator.TranslationMemory.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras gramaticais para frases que a memória oficial não cobre. Cada regra reconhece um
 * molde inteiro da frase inglesa e só produz texto quando todas as partes (sintagmas,
 * palavras-chave, tipos de marcador) foram reconhecidas. O vocabulário vem da memória
 * minerada (subtipos, palavras-chave, nomes de marcador e de ficha), não de listas inventadas.
 */
final class RuleTranslator {
    private final EffectTranslator owner;

    RuleTranslator(EffectTranslator owner) {
        this.owner = owner;
    }

    /** Verbo: imperativo ("Destrua") e infinitivo ("destruir"). */
    private static final class Verb {
        private final String imperative;
        private final String infinitive;

        Verb(String imperative, String infinitive) {
            this.imperative = imperative;
            this.infinitive = infinitive;
        }

        public String imperative() {
            return imperative;
        }

        public String infinitive() {
            return infinitive;
        }
        }

    private static final java.util.Map<String, Verb> VERBS = new java.util.HashMap<>();

    static {
        VERBS.put("Destroy", new Verb("Destrua", "destruir"));
        VERBS.put("Exile", new Verb("Exile", "exilar"));
        VERBS.put("Tap", new Verb("Vire", "virar"));
        VERBS.put("Untap", new Verb("Desvire", "desvirar"));
        VERBS.put("Sacrifice", new Verb("Sacrifique", "sacrificar"));
        VERBS.put("Return", new Verb("Devolva", "devolver"));
        VERBS.put("Create", new Verb("Crie", "criar"));
        VERBS.put("Put", new Verb("Coloque", "colocar"));
        VERBS.put("Remove", new Verb("Remova", "remover"));
        VERBS.put("Draw", new Verb("Compre", "comprar"));
        VERBS.put("Discard", new Verb("Descarte", "descartar"));
        VERBS.put("Attach", new Verb("Anexe", "anexar"));
        VERBS.put("Reveal", new Verb("Revele", "revelar"));
        VERBS.put("Transform", new Verb("Transforme", "transformar"));
        VERBS.put("Investigate", new Verb("Investigue", "investigar"));
        VERBS.put("Proliferate", new Verb("Prolifere", "proliferar"));
        VERBS.put("Shuffle", new Verb("Embaralhe", "embaralhar"));
    }

    private static final Pattern DESTROY_LIKE = Pattern.compile("^(Destroy|Exile|Tap|Untap|Sacrifice|Transform|Reveal) (.+?)\\.$");
    private static final Pattern RETURN_HAND = Pattern.compile("^Return (.+?) to (?:its|their) owners?'?s? hands?\\.$");
    private static final Pattern RETURN_GY_HAND = Pattern.compile("^Return (.+?) from your graveyard to your hand\\.$");
    private static final Pattern RETURN_GY_BF = Pattern.compile("^Return (.+?) from your graveyard to the battlefield( tapped)?\\.$");
    private static final Pattern RETURN_BF = Pattern.compile("^Return (.+?) to the battlefield( tapped)?( under (?:its|their) owner'?s'? control| under your control)?\\.$");
    private static final Pattern GETS = Pattern.compile("^(.+?) gets? ([+\\-−][\\dX]+/[+\\-−][\\dX]+) until end of turn\\.$");
    private static final Pattern GAINS = Pattern.compile("^(.+?) gains? (.+?) until end of turn\\.$");
    private static final Pattern GETS_GAINS = Pattern.compile("^(.+?) gets? ([+\\-−][\\dX]+/[+\\-−][\\dX]+) and gains? (.+?) until end of turn\\.$");
    private static final Pattern HAS = Pattern.compile("^(.+?) (has|have) (.+?)\\.$");
    private static final Pattern STATIC_GETS = Pattern.compile("^(.+?) gets? ([+\\-−][\\dX]+/[+\\-−][\\dX]+)\\.$");
    private static final Pattern DEALS = Pattern.compile("^(.+?) deals? (\\d+|X) damage to (.+?)\\.$");
    private static final Pattern CANT = Pattern.compile("^(.+?) (can't block|can't attack|can't attack or block|can't be blocked) this turn\\.$");
    private static final Pattern CREATE_CREATURE = Pattern.compile(
            "^Create (a|an|one|two|three|four|five|six|seven|eight|nine|ten|X|\\d+) (tapped )?(\\d+|X)/(\\d+|X) ((?:(?:white|blue|black|red|green|colorless)(?:, | and | )?)+) ((?:[A-Z][a-z]+ )+)(artifact )?creature tokens?( with (.+?))?\\.$");
    private static final Pattern CREATE_NAMED = Pattern.compile("^Create (a|an|one|two|three|four|five|X|\\d+) (tapped )?([A-Z][a-z]+) tokens?\\.$");
    private static final Pattern PUT_COUNTER = Pattern.compile("^Put (a|an|one|two|three|four|five|six|X|\\d+) (.+?) counters? on (.+?)\\.$");
    private static final Pattern REMOVE_COUNTER = Pattern.compile("^Remove (a|an|one|two|three|X|\\d+) (.+?) counters? from (.+?)\\.$");
    private static final Pattern YOU_MAY = Pattern.compile("^You may (.+)$");
    private static final Pattern ATTACH = Pattern.compile("^Attach (CARDNAME) to (.+?)\\.$");
    private static final Pattern ENTERS_TAPPED = Pattern.compile("^(CARDNAME) enters tapped\\.$");

    private static final Pattern HEAD_ENTERS = Pattern.compile("^(When|Whenever) (.+?) enters,$");
    private static final Pattern HEAD_ATTACKS = Pattern.compile("^(When|Whenever) (.+?) attacks,$");
    private static final Pattern HEAD_DIES = Pattern.compile("^(When|Whenever) (.+?) dies,$");
    private static final Pattern HEAD_COMBAT_DAMAGE = Pattern.compile("^(When|Whenever) (.+?) deals combat damage to a player,$");
    private static final Pattern HEAD_CAST = Pattern.compile("^Whenever you cast (.+?),$");

    // ------------------------------------------------------------------ API

    String translateSentence(String s) {
        String out = imperative(s, false);
        if (out != null) {
            return out;
        }
        Matcher m = YOU_MAY.matcher(s);
        if (m.matches()) {
            String vp = imperative(MtgText.capitalize(m.group(1)), true);
            if (vp != null) {
                return "Você pode " + vp;
            }
        }
        return statements(s);
    }

    String translateHead(String head) {
        Matcher m;
        if ((m = HEAD_ENTERS.matcher(head)).matches()) {
            NounPhrase np = np(m.group(2));
            return np == null ? null : when(m.group(1)) + np.render() + " " + (np.isPlural() ? "entrarem" : "entrar") + " no campo de batalha,";
        }
        if ((m = HEAD_ATTACKS.matcher(head)).matches()) {
            NounPhrase np = np(m.group(2));
            return np == null ? null : when(m.group(1)) + np.render() + " " + (np.isPlural() ? "atacarem" : "atacar") + ",";
        }
        if ((m = HEAD_DIES.matcher(head)).matches()) {
            NounPhrase np = np(m.group(2));
            return np == null ? null : when(m.group(1)) + np.render() + " " + (np.isPlural() ? "morrerem" : "morrer") + ",";
        }
        if ((m = HEAD_COMBAT_DAMAGE.matcher(head)).matches()) {
            NounPhrase np = np(m.group(2));
            return np == null ? null : when(m.group(1)) + np.render() + " " + (np.isPlural() ? "causarem" : "causar") + " dano de combate a um jogador,";
        }
        if ((m = HEAD_CAST.matcher(head)).matches()) {
            NounPhrase np = np(m.group(1));
            return np == null ? null : "Toda vez que você conjurar " + np.render() + ",";
        }
        return null;
    }

    String translateCostItem(String item) {
        Matcher m = Pattern.compile("^Sacrifice (.+)$").matcher(item);
        if (m.matches()) {
            NounPhrase np = np(m.group(1));
            return np == null ? null : "Sacrifique " + np.render();
        }
        m = Pattern.compile("^Pay (\\d+|X) life$").matcher(item);
        if (m.matches()) {
            return "Pague " + m.group(1) + ("1".equals(m.group(1)) ? " ponto" : " pontos") + " de vida";
        }
        m = Pattern.compile("^Discard (a|an|two|three) (.+)$").matcher(item);
        if (m.matches()) {
            NounPhrase np = np(m.group(1) + " " + m.group(2));
            return np == null ? null : "Descarte " + np.render();
        }
        m = Pattern.compile("^Exile (.+?) from your graveyard$").matcher(item);
        if (m.matches()) {
            NounPhrase np = np(m.group(1));
            return np == null ? null : "Exile " + np.render() + " de seu cemitério";
        }
        m = Pattern.compile("^Tap an untapped (.+?) you control$").matcher(item);
        if (m.matches()) {
            NounPhrase np = np("an untapped " + m.group(1) + " you control");
            return np == null ? null : "Vire " + np.render();
        }
        return null;
    }

    // ------------------------------------------------------------------ frases

    /** Frases imperativas; com {@code infinitive}, devolve o verbo no infinitivo (para "Você pode..."). */
    private String imperative(String s, boolean infinitive) {
        Matcher m;
        if ((m = RETURN_GY_HAND.matcher(s)).matches()) {
            NounPhrase np = np(m.group(1));
            return np == null ? null : verb("Return", infinitive) + " " + np.render() + " de seu cemitério para sua mão.";
        }
        if ((m = RETURN_GY_BF.matcher(s)).matches()) {
            NounPhrase np = np(m.group(1));
            return np == null ? null : verb("Return", infinitive) + " " + np.render() + " de seu cemitério para o campo de batalha"
                    + (m.group(2) != null ? " " + participle("virad", np) : "") + ".";
        }
        if ((m = RETURN_HAND.matcher(s)).matches()) {
            NounPhrase np = np(m.group(1));
            if (np == null) {
                return null;
            }
            return verb("Return", infinitive) + " " + np.render()
                    + (np.isPlural() ? " para as mãos de seus donos." : " para a mão de seu dono.");
        }
        if ((m = RETURN_BF.matcher(s)).matches()) {
            NounPhrase np = np(m.group(1));
            if (np == null) {
                return null;
            }
            String control = m.group(3) == null ? "" : m.group(3).contains("your control")
                    ? " sob seu controle" : " sob o controle de seu dono";
            return verb("Return", infinitive) + " " + np.render() + " para o campo de batalha"
                    + (m.group(2) != null ? " " + participle("virad", np) : "") + control + ".";
        }
        if ((m = ATTACH.matcher(s)).matches()) {
            NounPhrase np = np(m.group(2));
            return np == null ? null : verb("Attach", infinitive) + " CARDNAME " + np.withPreposition("a") + ".";
        }
        if ((m = DESTROY_LIKE.matcher(s)).matches()) {
            NounPhrase np = np(m.group(2));
            return np == null ? null : verb(m.group(1), infinitive) + " " + np.render() + ".";
        }
        if ((m = CREATE_CREATURE.matcher(s)).matches()) {
            return createCreature(m, infinitive);
        }
        if ((m = CREATE_NAMED.matcher(s)).matches()) {
            String name = tokenName(m.group(3));
            if (name == null) {
                return null;
            }
            int n = amount(m.group(1));
            String qty = n == 1 ? "uma ficha" : quantity(m.group(1), true) + " fichas";
            return verb("Create", infinitive) + " " + qty + (m.group(2) != null ? (n == 1 ? " virada" : " viradas") : "")
                    + " de " + name + ".";
        }
        if ((m = PUT_COUNTER.matcher(s)).matches()) {
            String counter = counterName(m.group(2), amount(m.group(1)) != 1);
            NounPhrase np = np(m.group(3));
            if (counter == null || np == null) {
                return null;
            }
            int n = amount(m.group(1));
            String qty = n == 1 ? "um" : quantity(m.group(1), false);
            return verb("Put", infinitive) + " " + qty + " " + counter + " " + np.withPreposition("em") + ".";
        }
        if ((m = REMOVE_COUNTER.matcher(s)).matches()) {
            String counter = counterName(m.group(2), amount(m.group(1)) != 1);
            NounPhrase np = np(m.group(3));
            if (counter == null || np == null) {
                return null;
            }
            int n = amount(m.group(1));
            String qty = n == 1 ? "um" : quantity(m.group(1), false);
            return verb("Remove", infinitive) + " " + qty + " " + counter + " " + np.withPreposition("de") + ".";
        }
        return null;
    }

    /** Afirmações com sujeito ("A criatura alvo recebe +2/+2 até o final do turno."). */
    private String statements(String s) {
        Matcher m;
        if ((m = GETS_GAINS.matcher(s)).matches()) {
            NounPhrase np = np(MtgText.decapitalizeEnglish(m.group(1)));
            String kws = keywordList(m.group(3));
            if (np == null || kws == null) {
                return null;
            }
            return np.subject() + (np.isPlural() ? " recebem " : " recebe ") + m.group(2)
                    + (np.isPlural() ? " e ganham " : " e ganha ") + kws + " até o final do turno.";
        }
        if ((m = GETS.matcher(s)).matches()) {
            NounPhrase np = np(MtgText.decapitalizeEnglish(m.group(1)));
            return np == null ? null : np.subject() + (np.isPlural() ? " recebem " : " recebe ") + m.group(2) + " até o final do turno.";
        }
        if ((m = GAINS.matcher(s)).matches()) {
            NounPhrase np = np(MtgText.decapitalizeEnglish(m.group(1)));
            String kws = keywordList(m.group(2));
            if (np == null || kws == null) {
                return null;
            }
            return np.subject() + (np.isPlural() ? " ganham " : " ganha ") + kws + " até o final do turno.";
        }
        if ((m = STATIC_GETS.matcher(s)).matches()) {
            NounPhrase np = np(MtgText.decapitalizeEnglish(m.group(1)));
            return np == null ? null : np.subject() + (np.isPlural() ? " recebem " : " recebe ") + m.group(2) + ".";
        }
        if ((m = DEALS.matcher(s)).matches()) {
            NounPhrase subj = np(MtgText.decapitalizeEnglish(m.group(1)));
            NounPhrase obj = np(m.group(3));
            if (subj == null || obj == null) {
                return null;
            }
            String n = m.group(2);
            return subj.subject() + (subj.isPlural() ? " causam " : " causa ") + n
                    + ("1".equals(n) ? " ponto" : " pontos") + " de dano " + obj.withPreposition("a") + ".";
        }
        if ((m = CANT.matcher(s)).matches()) {
            NounPhrase np = np(MtgText.decapitalizeEnglish(m.group(1)));
            if (np == null || np.det == NounPhrase.Det.SELF) {
                return null; // sem gênero conhecido para "bloqueado/bloqueada"
            }
            boolean pl = np.isPlural();
            String pred = switch (m.group(2)) {
                case "can't block" -> "bloquear";
                case "can't attack" -> "atacar";
                case "can't attack or block" -> "atacar nem bloquear";
                default -> "ser " + participle("bloquead", np);
            };
            return np.subject() + (pl ? " não podem " : " não pode ") + pred + " neste turno.";
        }
        if ((m = HAS.matcher(s)).matches()) {
            NounPhrase np = np(MtgText.decapitalizeEnglish(m.group(1)));
            String kws = keywordList(m.group(3));
            if (np == null || kws == null) {
                return null;
            }
            return np.subject() + (np.isPlural() ? " têm " : " tem ") + kws + ".";
        }
        if ((m = ENTERS_TAPPED.matcher(s)).matches()) {
            return "CARDNAME entra no campo de batalha virado.";
        }
        return null;
    }

    private String createCreature(Matcher m, boolean infinitive) {
        int n = amount(m.group(1));
        String pt = m.group(3) + "/" + m.group(4);
        List<String> colors = new ArrayList<>();
        for (String c : m.group(5).trim().split(", and |, | and ")) {
            String col = colorF(c.trim());
            if (col == null) {
                return null;
            }
            colors.add(col);
        }
        List<String> subs = new ArrayList<>();
        for (String sub : m.group(6).trim().split(" ")) {
            String ptSub = subtype(sub);
            if (ptSub == null) {
                return null;
            }
            subs.add(ptSub);
        }
        String kws = "";
        if (m.group(9) != null) {
            String k = keywordList(m.group(9));
            if (k == null) {
                return null;
            }
            kws = " com " + k;
        }
        String colorText = colors.size() == 1 ? colors.get(0)
                : String.join(", ", colors.subList(0, colors.size() - 1)) + " e " + colors.get(colors.size() - 1);
        String qty = n == 1 ? "uma ficha" : quantity(m.group(1), true) + " fichas";
        String tapped = m.group(2) == null ? "" : (n == 1 ? " virada" : " viradas");
        return verb("Create", infinitive) + " " + qty + tapped + " de criatura " + (m.group(7) != null ? "artefato " : "")
                + String.join(" ", subs) + " " + colorText + " " + pt + kws + ".";
    }

    // ------------------------------------------------------------------ apoio

    private NounPhrase np(String en) {
        NounPhrase np = NounPhrase.parse(en, this::keywordSingle, this::subtype);
        if (np == null && !en.isEmpty() && Character.isLowerCase(en.charAt(0))) {
            // sujeito com subtipo ("Goblins you control"): a maiúscula faz parte do nome
            np = NounPhrase.parse(MtgText.capitalize(en), this::keywordSingle, this::subtype);
        }
        return np;
    }

    private String keywordSingle(String kw) {
        return owner.translateKeyword(kw);
    }

    /** "flying and haste", "first strike, vigilance, and lifelink" → "voar e ímpeto"... */
    private String keywordList(String list) {
        String[] parts = list.split(", and |, | and ");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p.contains("\"") || p.contains("{")) {
                return null;
            }
            String kw = owner.translateKeyword(p.trim());
            if (kw == null) {
                return null;
            }
            out.add(MtgText.decapitalize(kw));
        }
        if (out.size() == 1) {
            return out.get(0);
        }
        return String.join(", ", out.subList(0, out.size() - 1)) + " e " + out.get(out.size() - 1);
    }

    private String subtype(String en) {
        String pt = owner.getMemory().lookup(Kind.SUBTYPE, en);
        if (pt == null) {
            pt = owner.getGlossary().lookup(Kind.SUBTYPE, en);
        }
        return pt;
    }

    private String tokenName(String en) {
        String pt = owner.getMemory().lookup(Kind.TOKEN, en);
        if (pt == null) {
            pt = owner.getGlossary().lookup(Kind.TOKEN, en);
        }
        return pt;
    }

    /** "+1/+1" → "marcador +1/+1"; "oil" → "marcador de óleo". */
    private String counterName(String en, boolean plural) {
        String head = plural ? "marcadores" : "marcador";
        if (en.matches("[+\\-−][\\dX]+/[+\\-−][\\dX]+")) {
            return head + " " + en;
        }
        if (!en.matches("[a-z]+")) {
            return null;
        }
        String kw = owner.translateKeyword(en);
        if (kw != null) {
            return head + " de " + MtgText.decapitalize(kw);
        }
        String pt = owner.getMemory().lookup(Kind.COUNTER, en);
        if (pt == null) {
            pt = owner.getGlossary().lookup(Kind.COUNTER, en);
        }
        return pt == null ? null : head + " de " + pt;
    }

    private static String verb(String en, boolean infinitive) {
        Verb v = VERBS.get(en);
        return infinitive ? v.infinitive : v.imperative;
    }

    private static String when(String en) {
        return "When".equals(en) ? "Quando " : "Toda vez que ";
    }

    private static String participle(String stem, NounPhrase np) {
        return stem + (np.isFeminine() ? "a" : "o") + (np.isPlural() ? "s" : "");
    }

    private static int amount(String w) {
        if ("a".equals(w) || "an".equals(w) || "one".equals(w)) {
            return 1;
        }
        if ("X".equals(w)) {
            return 99;
        }
        if (w.matches("\\d+")) {
            return Integer.parseInt(w);
        }
        return Slots.englishNumber(w);
    }

    private static String quantity(String w, boolean feminine) {
        if ("X".equals(w) || w.matches("\\d+")) {
            return w;
        }
        return Slots.portugueseNumber(amount(w), feminine);
    }

    private static String colorF(String en) {
        return switch (en) {
            case "white" -> "branca";
            case "blue" -> "azul";
            case "black" -> "preta";
            case "red" -> "vermelha";
            case "green" -> "verde";
            case "colorless" -> "incolor";
            default -> null;
        };
    }
}
