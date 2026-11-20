package forge.ptbr.translator;
import forge.ptbr.model.CardTextEn;
import forge.ptbr.translator.TranslationMemory.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tradutor de efeitos de cartas (inglês → português do Brasil), somente para exibição.
 *
 * <p>Ordem de tentativa para cada frase:</p>
 * <ol>
 *   <li>memória de tradução oficial (frase inteira, literal e depois com lacunas);</li>
 *   <li>decomposição: custo + efeito, ou cabeça de gatilho/condição + efeito;</li>
 *   <li>regras gramaticais ({@link RuleTranslator}) para padrões comuns;</li>
 *   <li>se nada servir, a frase fica em inglês, e isso é contado em {@link TranslationResult}.</li>
 * </ol>
 *
 * <p>Toda frase traduzida passa por {@link MtgText#sameInvariants}: se um custo, número ou
 * modificador de P/R não bater com o original, a tradução é descartada.</p>
 *
 * <p>Este tradutor não tem acesso ao motor de regras e não é consultado por ele: produz apenas
 * texto, e o Forge continua executando a carta a partir do script original.</p>
 */
public final class EffectTranslator {
    /** Aumente ao mudar regras ou glossário: traduções em cache com versão menor são refeitas. */
    public static final int VERSION = 1;

    private final TranslationMemory memory;
    private final Glossary glossary;
    private final RuleTranslator rules;

    public EffectTranslator(TranslationMemory memory, Glossary glossary) {
        this.memory = memory.freeze();
        this.glossary = glossary == null ? Glossary.empty() : glossary;
        this.rules = new RuleTranslator(this);
    }

    public TranslationMemory getMemory() {
        return memory;
    }

    public Glossary getGlossary() {
        return glossary;
    }

    /** Estado de uma tradução em andamento. */
    static final class Stats {
        int segments;
        int translated;
        final List<String> untranslated = new ArrayList<>();

        void ok() {
            segments++;
            translated++;
        }

        void miss(String en) {
            segments++;
            untranslated.add(en);
        }
    }

    /**
     * Traduz o texto Oracle de uma face.
     *
     * @param card        texto original
     * @param displayName nome a usar quando a carta cita a si mesma (o nome oficial em português,
     *                    se houver; senão o nome em inglês)
     */
    public TranslationResult translate(CardTextEn card, String displayName) {
        Stats stats = new Stats();
        String name = displayName == null || displayName.isEmpty() ? card.name() : displayName;
        List<String> out = new ArrayList<>();
        for (String raw : card.oracleLines()) {
            SelfReference.Normalized norm = SelfReference.normalizeEnglish(raw.trim(), card.name());
            String line = translateNormalizedLine(norm.text(), stats);
            out.add(SelfReference.render(line, norm.originals(), name, card.name()));
        }
        return new TranslationResult(String.join(CardTextEn.LINE_SEPARATOR, out),
                stats.segments, stats.translated, stats.untranslated, VERSION);
    }

    /** Traduz a linha de tipo ("Legendary Creature Elf Druid" → "Criatura Lendária — Elfo Druida"). */
    public String translateType(String enType) {
        String[] parts = CorpusMiner.splitEnglishType(enType == null ? "" : enType);
        if (parts == null) {
            return null;
        }
        String main = memory.lookup(Kind.TYPE, parts[0]);
        if (main == null) {
            main = glossary.lookup(Kind.TYPE, parts[0]);
        }
        if (main == null) {
            return null;
        }
        if (parts[1].isEmpty()) {
            return main;
        }
        List<String> subs = new ArrayList<>();
        for (String sub : parts[1].split(" ")) {
            String pt = memory.lookup(Kind.SUBTYPE, sub);
            if (pt == null) {
                pt = glossary.lookup(Kind.SUBTYPE, sub);
            }
            subs.add(pt == null ? sub : pt); // subtipo novo: nome próprio, fica como está
        }
        return main + " — " + String.join(" ", subs);
    }

    // ------------------------------------------------------------------ linhas

    String translateNormalizedLine(String line, Stats stats) {
        if (line.isEmpty()) {
            return line;
        }
        if (MtgText.isReminderOnly(line)) {
            String inner = MtgText.reminders(line).get(0);
            String pt = translateReminder(inner);
            if (pt == null) {
                stats.miss(line);
                return SelfReference.UNTRANSLATED_START + line + SelfReference.UNTRANSLATED_END;
            }
            stats.ok();
            return "(" + pt + ")";
        }
        List<String> reminders = MtgText.reminders(line);
        String base = MtgText.stripReminder(line);
        String body;
        if (CorpusMiner.isKeywordLine(base)) {
            body = translateKeywords(base, stats);
        } else {
            body = translateRulesText(base, stats);
        }
        if (reminders.size() == 1) {
            String pt = translateReminder(reminders.get(0));
            if (pt != null) {
                body = body + " (" + pt + ")";
            }
            // lembrete sem tradução é omitido: não tem efeito de regra e o original fica disponível
        }
        return body;
    }

    private String translateRulesText(String base, Stats stats) {
        StringBuilder prefix = new StringBuilder();
        Clauses.Split aw = Clauses.abilityWord(base);
        if (aw != null) {
            String pt = lookup(Kind.ABILITY_WORD, aw.head());
            if (pt == null) {
                pt = glossary.lookup(Kind.ABILITY_WORD, aw.head());
            }
            // palavra de habilidade não tem significado de regra; sem tradução oficial, mantém o nome
            prefix.append(pt == null ? aw.head() : pt).append(" — ");
            base = aw.rest();
        }
        String bullet = Clauses.stripBullet(base);
        if (bullet != null) {
            prefix.append("• ");
            base = bullet;
        }
        List<String> parts = new ArrayList<>();
        for (String sentence : MtgText.splitSentences(base)) {
            String pt = translateSentence(sentence, stats);
            if (pt == null) {
                stats.miss(sentence);
                // marcada para que a renderização devolva a auto-referência em inglês
                parts.add(SelfReference.UNTRANSLATED_START + sentence + SelfReference.UNTRANSLATED_END);
            } else {
                stats.ok();
                parts.add(pt);
            }
        }
        return prefix + String.join(" ", parts);
    }

    private String translateKeywords(String base, Stats stats) {
        String[] parts = base.split(", ");
        String separator = ", ";
        if (parts.length == 1 && base.contains("; ")) {
            parts = base.split("; ");
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String kw = parts[i].trim();
            String pt = translateKeyword(kw);
            if (pt == null) {
                stats.miss(kw);
                pt = CorpusMiner.keywordKey(kw);
            } else {
                stats.ok();
            }
            out.add(i == 0 ? MtgText.capitalize(pt) : MtgText.decapitalize(pt));
        }
        return String.join(separator, out);
    }

    /** Palavra-chave isolada ("Ward {2}", "Protection from red"), ou null. */
    public String translateKeyword(String keyword) {
        String key = CorpusMiner.keywordKey(keyword);
        String pt = lookup(Kind.KEYWORD, key);
        if (pt == null) {
            pt = glossaryWithSlots(Kind.KEYWORD, key);
        }
        return pt;
    }

    String translateReminder(String en) {
        String pt = lookup(Kind.REMINDER, en);
        if (pt == null) {
            pt = lookup(Kind.REMINDER, MtgText.capitalize(en));
        }
        if (pt == null) {
            // lembretes novos costumam ser frases comuns ("This creature can block creatures with flying.")
            Stats scratch = new Stats();
            String body = translateRulesText(en, scratch);
            if (scratch.segments > 0 && scratch.translated == scratch.segments) {
                pt = body;
            }
        }
        return pt;
    }

    // ------------------------------------------------------------------ frases

    /** Traduz uma frase já normalizada (com CARDNAME); null se não for possível com segurança. */
    String translateSentence(String sentence, Stats stats) {
        String s = sentence.trim();
        List<int[]> quoteSpans = Clauses.quotes(s);
        List<String> quoted = new ArrayList<>();
        if (!quoteSpans.isEmpty()) {
            for (int[] span : quoteSpans) {
                Stats inner = new Stats();
                String pt = translateNormalizedLine(s.substring(span[0] + 1, span[1]), inner);
                if (inner.translated != inner.segments) {
                    return null; // habilidade concedida meio traduzida confundiria mais do que ajudaria
                }
                quoted.add(pt);
            }
            s = CorpusMiner.withQuotePlaceholders(s, quoteSpans);
        }
        String pt = translateCore(s);
        if (pt == null) {
            return null;
        }
        for (int i = 0; i < quoted.size(); i++) {
            pt = pt.replace("«" + i + "»", "\"" + quoted.get(i) + "\"");
        }
        if (pt.contains("«")) {
            return null;
        }
        pt = Terminology.modernize(sentence, pt);
        if (!MtgText.sameInvariants(sentence, pt)) {
            return null;
        }
        return pt;
    }

    /** Frase sem citações: memória, decomposição e regras. */
    String translateCore(String s) {
        String pt = lookup(Kind.SENTENCE, s);
        if (pt != null) {
            return pt;
        }
        Clauses.Split cost = Clauses.cost(s);
        if (cost != null) {
            String costPt = translateCost(cost.head());
            String effectPt = costPt == null ? null : translateCore(MtgText.capitalize(cost.rest()));
            if (effectPt != null) {
                return costPt + ": " + effectPt;
            }
        }
        Clauses.Split head = Clauses.head(s, true);
        if (head != null) {
            String headPt = lookup(Kind.HEAD, head.head());
            if (headPt == null) {
                headPt = rules.translateHead(head.head());
            }
            if (headPt != null) {
                String restPt = translateCore(MtgText.capitalize(head.rest()));
                if (restPt != null) {
                    return headPt + " " + MtgText.decapitalize(restPt);
                }
            }
        }
        return rules.translateSentence(s);
    }

    String translateCost(String cost) {
        String pt = lookup(Kind.COST, cost);
        if (pt != null) {
            return pt;
        }
        List<String> items = Clauses.costItems(cost);
        if (items.size() < 2) {
            return rules.translateCostItem(cost);
        }
        List<String> out = new ArrayList<>();
        for (String item : items) {
            String it = lookup(Kind.COST, MtgText.capitalize(item));
            if (it == null) {
                it = rules.translateCostItem(MtgText.capitalize(item));
            }
            if (it == null) {
                return null;
            }
            out.add(out.isEmpty() ? it : MtgText.decapitalize(it));
        }
        return String.join(", ", out);
    }

    /** Busca literal e depois com lacunas. */
    String lookup(Kind kind, String text) {
        String pt = memory.lookup(kind, text);
        if (pt != null) {
            return pt;
        }
        Slots.Keyed keyed = Slots.key(text);
        if (keyed.values().isEmpty()) {
            return null;
        }
        String template = memory.lookup(kind, keyed.key());
        return template == null ? null : Slots.fill(template, keyed.values());
    }

    private String glossaryWithSlots(Kind kind, String text) {
        String pt = glossary.lookup(kind, text);
        if (pt != null) {
            return pt;
        }
        Slots.Keyed keyed = Slots.key(text);
        if (keyed.values().isEmpty()) {
            return null;
        }
        String template = glossary.lookup(kind, keyed.key());
        return template == null ? null : Slots.fill(template, keyed.values());
    }

    /** Estatísticas da memória, para diagnóstico. */
    public Map<String, Integer> describe() {
        Map<String, Integer> m = new java.util.LinkedHashMap<>();
        for (Kind k : Kind.values()) {
            m.put(k.name(), memory.size(k));
        }
        m.put("GLOSSARY", glossary.size());
        return m;
    }
}
