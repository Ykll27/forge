package forge.ptbr.translator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Verificação de alinhamento por conceitos: se o inglês fala de "target", o português tem de
 * falar de "alvo", e vice-versa. Rejeita pares desalinhados na mineração e, de quebra,
 * descarta redações antigas que usam outro conceito ("Remova do jogo" para "Exile",
 * "Coloque em jogo" para "Create", "for colocado num cemitério" para "dies"), de modo que
 * a memória aprende a terminologia atual.
 */
final class SemanticCheck {
    private static final Locale PT = new Locale("pt", "BR");

    /**
     * Fronteira de palavra (\\b) com letras acentuadas: sem esta opção, o Java 19+ trata "á" como
     * não-letra e "poderá" casaria com "poder"; com ela, Java 17 e 21 dão o mesmo resultado.
     */
    static final int WORDS = Pattern.UNICODE_CHARACTER_CLASS;

    /** Pares (regex inglês, regex português). Verificados nos dois sentidos. */
    private static final Pattern[][] MARKERS = {
            pair("\\btargets?\\b", "\\balvos?\\b"),
            pair("\\bopponents?\\b|\\bopponent's\\b", "\\boponentes?\\b"),
            pair("(?<![a-z])(?:non-?)?creatures?\\b", "\\bcriaturas?\\b"),
            pair("(?<![a-z])(?:non-?)?artifacts?\\b", "\\bartefatos?\\b"),
            pair("(?<![a-z])(?:non-?)?enchantments?\\b", "\\bencantamentos?\\b"),
            pair("(?<![a-z])(?:non-?)?lands?\\b", "\\bterrenos?\\b"),
            pair("\\bgraveyards?\\b", "\\bcemit[ée]rios?\\b"),
            pair("\\blibrary\\b|\\blibraries\\b", "\\bgrim[óo]rios?\\b"),
            pair("\\blife", "\\bvidas?\\b"),
            pair("\\bdamage\\b", "\\bdanos?\\b"),
            pair("(?<![a-z])(?:non-?)?tokens?\\b", "\\bfichas?\\b"),
            pair("(?<![a-z])(?:non-?)?legendary\\b", "\\blend[áa]ri[oa]s?\\b"),
            pair("\\bplayers?\\b", "\\bjogador(es)?\\b"),
            pair("\\bsacrific", "\\bsacrifi"),
            pair("\\bdestroy", "\\bdestr[uóo]"),
            pair("\\bdraws?\\b|\\bdrew\\b|\\bdrawn\\b", "\\bcompr"),
            pair("\\bdiscard", "\\bdescart"),
            pair("\\bexile", "\\bex[ií]l"),
            pair("\\bcreate", "\\bcri(e|a|ar|ou|am|em|ad[oa]s?)\\b"),
            pair("\\bmills?\\b|\\bmilled\\b", "\\btritur"),
            pair("\\bdies\\b|\\bdie\\b|\\bdied\\b", "\\bmorr"),
            pair("\\bhexproof\\b", "\\bresist[êe]ncia a magia\\b|\\bresist[êe]ncia [àa] magia\\b"),
            pair("\\bscry\\b", "\\bvid[êe]ncia\\b|\\bvidencie\\b"),
            pair("(?<![a-z])(?:non-?)?spells?\\b|\\binstants?\\b", "\\bm[áa]gicas?\\b"),
            pair("\\bcast", "\\bconjur"),
            pair("\\bshuffle", "\\bembaralh"),
            pair("\\bsearch", "\\bprocur"),
            pair("\\buntap", "\\bdesvir"),
            pair("\\w+n't\\b|\\bcannot\\b|\\bnot\\b|\\bno\\b|\\bnever\\b|\\bwithout\\b|\\bnon-?[a-zA-Z]", "\\bn[ãa]o\\b|\\bnenhum|\\bnunca\\b|\\bsem\\b"),
            pair("\\bpower\\b", "\\bpoder\\b"),
            pair("\\btoughness\\b", "\\bresist[êe]ncia\\b(?! [àa] magia)"),
            pair("\\bcounter", "\\bmarcador(es)?\\b|\\banul"),
            pair("\\bpoison\\b", "\\bveneno\\b"),
            pair("\\bcopy\\b|\\bcopies\\b", "\\bc[óo]pias?\\b|\\bcopi"),
            pair("\\bflying\\b", "\\bvoar\\b|\\bvoa\\b"),
            pair("\\bequip", "\\bequip"),
            pair("\\bcombat\\b", "\\bcombate\\b"),
            pair("\\bupkeep\\b", "\\bmanuten[çc][ãa]o\\b"),
            pair("\\bend step\\b", "\\betapa final\\b"),
            pair("\\bmana value\\b", "\\bvalor de mana\\b|\\bcusto de mana convertido\\b"),
            pair("\\benter", "\\bentr[ae]|\\bentrou\\b|\\bentrando\\b|\\bentram\\b|\\bentrarem\\b"),
            pair("\\bbattlefield\\b|\\benter", "\\bcampo de batalha\\b|\\bentr[ae]|\\bentrou\\b|\\bentrando\\b|\\bentram\\b|\\bentrarem\\b"),
            pair("\\bchoose\\b|\\bchooses\\b|\\bchose\\b|\\bchosen\\b", "\\bescolh"),
            pair("\\bgain", "\\bganh"),
            pair("\\blose\\b|\\bloses\\b|\\blost\\b", "\\bperd|\\bperc[ae]"),
    };

    private static Pattern[] pair(String en, String pt) {
        return new Pattern[]{
                Pattern.compile(en, Pattern.CASE_INSENSITIVE | WORDS),
                Pattern.compile(pt, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | WORDS)};
    }

    /** Palavras que só existem em inglês (não aparecem em texto de regras em português). */
    private static final Pattern ENGLISH_WORDS = Pattern.compile(
            "\\b(the|target|you|your|creatures?|each|with|this|of|and|draw|until|end|turn|gets|gains|has|"
            + "flying|flash|trample|haste|vigilance|reach|defender|menace|deathtouch|lifelink|ward|equip|crew|"
            + "cycling|enchant|enchanted|destroy|create|return|choose|mill|scry|kicker|first strike)\\b", WORDS);

    private SemanticCheck() {
    }

    /** Terminologia anterior a 2009 ("em jogo", "remova do jogo"). */
    private static final Pattern OLD_ZONE_WORDING = Pattern.compile("\\b(em|do|no|ao|para o) jogo\\b", Pattern.CASE_INSENSITIVE | WORDS);
    private static final Pattern GAME = Pattern.compile("\\bgames?\\b", Pattern.CASE_INSENSITIVE | WORDS);

    /** O par fala dos mesmos conceitos? */
    static boolean aligned(String en, String pt) {
        // a carta cita a si mesma dos dois lados ou de nenhum
        boolean selfEn = en.contains(SelfReference.PLACEHOLDER);
        boolean selfPt = pt.contains(SelfReference.PLACEHOLDER);
        if (selfEn != selfPt) {
            return false;
        }
        if (OLD_ZONE_WORDING.matcher(pt).find() && !GAME.matcher(en).find()) {
            return false;
        }
        String e = en.replace(SelfReference.PLACEHOLDER, " ");
        String p = pt.replace(SelfReference.PLACEHOLDER, " ").toLowerCase(PT);
        for (Pattern[] m : MARKERS) {
            boolean inEn = m[0].matcher(e).find();
            boolean inPt = m[1].matcher(p).find();
            if (inEn != inPt) {
                return false;
            }
        }
        return true;
    }

    /** O "português" é na verdade inglês (erro de dados na fonte)? */
    static boolean looksEnglish(String en, String pt) {
        java.util.regex.Matcher m = ENGLISH_WORDS.matcher(pt.toLowerCase(Locale.ROOT));
        int hits = 0;
        while (m.find()) {
            hits++;
        }
        // texto idêntico é legítimo quando as palavras existem nos dois idiomas ("Exile CARDNAME.")
        if (MtgText.normalizeForCompare(en).equals(MtgText.normalizeForCompare(pt))) {
            return hits >= 1;
        }
        return hits >= 3;
    }
}
