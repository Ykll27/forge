package forge.ptbr.translator;
import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.translator.TranslationMemory.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Extrai terminologia e frases oficiais de pares (Oracle inglês, texto impresso em português).
 * Tudo o que entra na {@link TranslationMemory} veio de uma carta oficial, nada é inventado aqui.
 *
 * <p>Pares desalinhados (número de linhas ou frases diferente, valores que não batem)
 * são descartados em vez de "forçados", para não ensinar traduções erradas.</p>
 */
public final class CorpusMiner {
    /** Supertipos e tipos de carta em inglês, para separar a linha de tipo do Forge. */
    static final Set<String> CARD_TYPE_WORDS = new HashSet<>(Arrays.asList(
            "Legendary", "Basic", "Snow", "World", "Ongoing", "Elite", "Host",
            "Creature", "Artifact", "Enchantment", "Land", "Instant", "Sorcery", "Planeswalker",
            "Battle", "Tribal", "Kindred", "Conspiracy", "Dungeon", "Phenomenon", "Plane", "Scheme", "Vanguard"));

    private final TranslationMemory memory = new TranslationMemory();
    private final List<String[]> multiSubtypes = new ArrayList<>();
    private final Map<String, Map<String, Integer>> singleSubtypes = new HashMap<>();
    private int cardsUsed;
    private int linesUsed;

    private int weight = 1;

    /** Registra uma carta com texto oficial. */
    public void addCard(CardTextEn en, LocalizedEntry pt) {
        addCard(en, pt, 1);
    }

    /**
     * Registra uma carta com texto oficial e um peso. Textos mais recentes devem pesar mais:
     * a redação de regras mudou ao longo dos anos ("Remova do jogo" → "Exile") e a forma
     * atual precisa vencer a votação.
     */
    public void addCard(CardTextEn en, LocalizedEntry pt, int weight) {
        this.weight = Math.max(1, weight);
        if (pt == null) {
            return;
        }
        if (pt.hasType() && !en.type().isEmpty()) {
            mineType(en.type(), pt.type());
        }
        if (!pt.hasText() || en.oracle().isEmpty()) {
            return;
        }
        mineNames(en.oracle(), pt.text());
        List<String> enLines = en.oracleLines();
        String[] ptLines = pt.text().split("\\\\n", -1);
        if (enLines.size() != ptLines.length) {
            return; // desalinhado: melhor não aprender nada do que aprender errado
        }
        boolean used = false;
        for (int i = 0; i < ptLines.length; i++) {
            String enLine = SelfReference.normalizeEnglish(enLines.get(i).trim(), en.name()).text();
            String ptLine = SelfReference.normalizePortuguese(ptLines[i].trim(), pt.name());
            used |= mineLine(enLine, ptLine);
        }
        if (used) {
            cardsUsed++;
        }
    }

    /** Fecha a mineração: resolve subtipos compostos e congela a memória. */
    public TranslationMemory finish() {
        resolveMultiSubtypes();
        return memory.freeze();
    }

    public int getCardsUsed() {
        return cardsUsed;
    }

    public int getLinesUsed() {
        return linesUsed;
    }

    // ------------------------------------------------------------------ linhas

    private boolean mineLine(String en, String pt) {
        if (en.isEmpty() || pt.isEmpty()) {
            return false;
        }
        if (MtgText.isReminderOnly(en)) {
            if (MtgText.isReminderOnly(pt)) {
                addTemplated(Kind.REMINDER, MtgText.reminders(en).get(0), MtgText.reminders(pt).get(0));
                return true;
            }
            return false;
        }
        List<String> enRem = MtgText.reminders(en);
        List<String> ptRem = MtgText.reminders(pt);
        if (enRem.size() == 1 && ptRem.size() == 1) {
            addTemplated(Kind.REMINDER, enRem.get(0), ptRem.get(0));
        }
        String enBase = MtgText.stripReminder(en);
        String ptBase = MtgText.stripReminder(pt);
        if (enBase.isEmpty() || ptBase.isEmpty()) {
            return false;
        }
        linesUsed++;
        if (isKeywordLine(enBase)) {
            return mineKeywords(enBase, ptBase);
        }
        Clauses.Split enAw = Clauses.abilityWord(enBase);
        Clauses.Split ptAw = Clauses.abilityWord(ptBase);
        if (enAw != null && ptAw != null) {
            if (!SemanticCheck.looksEnglish(enAw.head(), ptAw.head())) {
                memory.add(Kind.ABILITY_WORD, enAw.head(), ptAw.head(), weight);
            }
            enBase = enAw.rest();
            ptBase = ptAw.rest();
        }
        String enBullet = Clauses.stripBullet(enBase);
        String ptBullet = Clauses.stripBullet(ptBase);
        if (enBullet != null && ptBullet != null) {
            enBase = enBullet;
            ptBase = ptBullet;
        }
        List<String> enSent = MtgText.splitSentences(enBase);
        List<String> ptSent = MtgText.splitSentences(ptBase);
        if (enSent.size() != ptSent.size()) {
            // frases fundidas/divididas na tradução: só a linha inteira serve
            if (enSent.size() > 1 && MtgText.sameInvariants(enBase, ptBase)) {
                mineSentence(enBase, ptBase);
            }
            return false;
        }
        for (int i = 0; i < enSent.size(); i++) {
            mineSentence(enSent.get(i), ptSent.get(i));
        }
        return true;
    }

    static boolean isKeywordLine(String base) {
        if (base.endsWith(".") || base.endsWith("—") || base.endsWith("—") || base.contains(":")
                || base.contains("\"") || base.contains(" — ")) {
            return false;
        }
        for (String part : base.split(", |; ")) {
            if (part.isEmpty() || part.split(" ").length > 6) {
                return false;
            }
        }
        return true;
    }

    private boolean mineKeywords(String en, String pt) {
        String[] enParts = en.split(", |; ");
        String[] ptParts = pt.split(", |; ");
        if (enParts.length != ptParts.length) {
            return false;
        }
        for (int i = 0; i < enParts.length; i++) {
            addTemplated(Kind.KEYWORD, keywordKey(enParts[i]), MtgText.decapitalize(ptParts[i].trim()));
        }
        return true;
    }

    /** Chave de palavra-chave: primeira letra minúscula ("Flying" e "flying" são a mesma). */
    static String keywordKey(String keyword) {
        String k = keyword.trim();
        if (k.isEmpty()) {
            return k;
        }
        return k.substring(0, 1).toLowerCase(Locale.ROOT) + k.substring(1);
    }

    private static final java.util.regex.Pattern EN_TOKEN = java.util.regex.Pattern.compile("\\b(?:a|an|one) ([A-Z][a-z]+) token\\b", SemanticCheck.WORDS);
    private static final java.util.regex.Pattern PT_TOKEN = java.util.regex.Pattern.compile("ficha de ([A-ZÀ-Ú][\\p{L}]+)");
    private static final java.util.regex.Pattern EN_COUNTER = java.util.regex.Pattern.compile("\\b([a-z]+) counters?\\b", SemanticCheck.WORDS);
    private static final java.util.regex.Pattern PT_COUNTER = java.util.regex.Pattern.compile("marcador(?:es)? de ([\\p{L}]+)");
    private static final Set<String> COUNTER_STOP = new HashSet<>(Arrays.asList(
            "a", "an", "another", "that", "any", "the", "of", "those", "these", "each", "additional", "all", "more",
            "no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "x", "his", "her",
            "their", "its", "target", "such", "many", "few", "as", "with", "without", "kind", "type", "same", "different",
            // marcadores de palavra-chave usam a tradução da palavra-chave inteira
            "flying", "deathtouch", "lifelink", "trample", "menace", "reach", "indestructible", "hexproof",
            "vigilance", "strike", "haste", "first", "double"));

    /** Nomes de fichas predefinidas e de marcadores, quando a carta tem exatamente um de cada lado. */
    private void mineNames(String enText, String ptText) {
        Set<String> enTok = all(EN_TOKEN, enText, null);
        Set<String> ptTok = all(PT_TOKEN, ptText, null);
        if (enTok.size() == 1 && ptTok.size() == 1) {
            memory.add(Kind.TOKEN, enTok.iterator().next(), ptTok.iterator().next(), weight);
        }
        Set<String> enCnt = all(EN_COUNTER, enText, COUNTER_STOP);
        Set<String> ptCnt = all(PT_COUNTER, ptText, null);
        if (enCnt.size() == 1 && ptCnt.size() == 1) {
            memory.add(Kind.COUNTER, enCnt.iterator().next(), ptCnt.iterator().next(), weight);
        }
    }

    private static Set<String> all(java.util.regex.Pattern p, String text, Set<String> stop) {
        Set<String> out = new HashSet<>();
        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) {
            String g = m.group(1);
            if (stop == null || !stop.contains(g.toLowerCase(Locale.ROOT))) {
                out.add(g);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ frases

    private void mineSentence(String en, String pt) {
        // citações (habilidades concedidas a fichas, etc.) são minerados como linhas próprias
        List<int[]> enQ = Clauses.quotes(en);
        List<int[]> ptQ = Clauses.quotes(pt);
        if (!enQ.isEmpty()) {
            if (enQ.size() != ptQ.size()) {
                return;
            }
            for (int i = 0; i < enQ.size(); i++) {
                String inEn = en.substring(enQ.get(i)[0] + 1, enQ.get(i)[1]);
                String inPt = pt.substring(ptQ.get(i)[0] + 1, ptQ.get(i)[1]);
                mineLine(inEn, inPt);
            }
            en = withQuotePlaceholders(en, enQ);
            pt = withQuotePlaceholders(pt, ptQ);
        }
        if (!MtgText.sameInvariants(en, pt)) {
            return;
        }
        addTemplated(Kind.SENTENCE, en, pt);
        Clauses.Split enCost = Clauses.cost(en);
        Clauses.Split ptCost = Clauses.cost(pt);
        if (enCost != null && ptCost != null && MtgText.sameInvariants(enCost.head(), ptCost.head())) {
            addTemplated(Kind.COST, enCost.head(), ptCost.head());
            List<String> enItems = Clauses.costItems(enCost.head());
            List<String> ptItems = Clauses.costItems(ptCost.head());
            if (enItems.size() == ptItems.size() && enItems.size() > 1) {
                for (int i = 0; i < enItems.size(); i++) {
                    if (MtgText.sameInvariants(enItems.get(i), ptItems.get(i))) {
                        addTemplated(Kind.COST, MtgText.capitalize(enItems.get(i)), MtgText.capitalize(ptItems.get(i)));
                    }
                }
            }
            mineSentence(MtgText.capitalize(enCost.rest()), MtgText.capitalize(ptCost.rest()));
            return;
        }
        Clauses.Split enHead = Clauses.head(en, true);
        Clauses.Split ptHead = enHead == null ? null : Clauses.head(pt, false);
        if (enHead != null && ptHead != null
                && MtgText.sameInvariants(enHead.head(), ptHead.head())
                && MtgText.sameInvariants(enHead.rest(), ptHead.rest())) {
            addTemplated(Kind.HEAD, enHead.head(), ptHead.head());
            mineSentence(MtgText.capitalize(enHead.rest()), MtgText.capitalize(ptHead.rest()));
        }
    }

    static String withQuotePlaceholders(String s, List<int[]> spans) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < spans.size(); i++) {
            out.append(s, pos, spans.get(i)[0]).append('«').append(i).append('»');
            pos = spans.get(i)[1] + 1;
        }
        out.append(s.substring(pos));
        return out.toString();
    }

    private void addTemplated(Kind kind, String en, String pt) {
        if (en == null || pt == null || en.trim().isEmpty() || pt.trim().isEmpty()) {
            return;
        }
        en = en.trim();
        pt = pt.trim();
        if (!MtgText.sameInvariants(en, pt) || SemanticCheck.looksEnglish(en, pt)
                || !SemanticCheck.aligned(en, pt)) {
            return;
        }
        // forma literal (sempre) e forma com lacunas (quando os valores casam)
        memory.add(kind, en, pt, weight);
        Slots.TemplatePair pair = Slots.templatize(en, pt);
        if (pair != null && !pair.en().equals(en)) {
            memory.add(kind, pair.en(), pair.pt(), weight);
        }
    }

    // ------------------------------------------------------------------ tipos

    private void mineType(String enType, String ptType) {
        String[] en = splitEnglishType(enType);
        String[] pt = splitPortugueseType(ptType);
        if (en == null || pt == null) {
            return;
        }
        if (SemanticCheck.looksEnglish(en[0], pt[0])) {
            return;
        }
        memory.add(Kind.TYPE, en[0], pt[0], weight);
        if (en[1].isEmpty() || pt[1].isEmpty()) {
            return;
        }
        String[] enSub = en[1].split(" ");
        String[] ptSub = pt[1].split(" ");
        if (enSub.length == 1) {
            memory.add(Kind.SUBTYPE, enSub[0], pt[1], weight);
            singleSubtypes.computeIfAbsent(enSub[0], k -> new HashMap<>()).merge(pt[1], 1, Integer::sum);
        } else if (enSub.length == ptSub.length) {
            multiSubtypes.add(new String[]{en[1], pt[1]});
        }
    }

    /** {tipos principais, subtipos} de "Legendary Creature Elf Druid" ou "... — Elf Druid". */
    static String[] splitEnglishType(String type) {
        String t = type.trim();
        int dash = t.indexOf(" — ");
        if (dash < 0) {
            dash = t.indexOf(" - ");
        }
        if (dash >= 0) {
            return new String[]{t.substring(0, dash).trim(), t.substring(dash + 3).trim()};
        }
        StringBuilder main = new StringBuilder();
        StringBuilder sub = new StringBuilder();
        for (String w : t.split(" ")) {
            if (sub.length() == 0 && CARD_TYPE_WORDS.contains(w)) {
                if (main.length() > 0) {
                    main.append(' ');
                }
                main.append(w);
            } else {
                if (sub.length() > 0) {
                    sub.append(' ');
                }
                sub.append(w);
            }
        }
        if (main.length() == 0) {
            return null;
        }
        return new String[]{main.toString(), sub.toString()};
    }

    static String[] splitPortugueseType(String type) {
        String t = type.trim();
        int dash = t.indexOf(" — ");
        int len = 3;
        if (dash < 0) {
            dash = t.indexOf(" - ");
        }
        if (dash < 0) {
            return t.isEmpty() ? null : new String[]{t, ""};
        }
        return new String[]{t.substring(0, dash).trim(), t.substring(dash + len).trim()};
    }

    private void resolveMultiSubtypes() {
        // Impressões antigas às vezes invertem a ordem ("Elf Druid" → "Druida Elfo").
        // Os subtipos únicos já vistos decidem se o par composto está na ordem direta ou inversa.
        for (String[] pair : multiSubtypes) {
            String[] en = pair[0].split(" ");
            String[] pt = pair[1].split(" ");
            int direct = 0;
            int reversed = 0;
            for (int i = 0; i < en.length; i++) {
                Map<String, Integer> seen = singleSubtypes.get(en[i]);
                if (seen == null) {
                    continue;
                }
                if (seen.containsKey(pt[i])) {
                    direct++;
                }
                if (seen.containsKey(pt[en.length - 1 - i])) {
                    reversed++;
                }
            }
            boolean flip = reversed > direct;
            for (int i = 0; i < en.length; i++) {
                memory.add(Kind.SUBTYPE, en[i], flip ? pt[en.length - 1 - i] : pt[i]);
            }
        }
    }
}
