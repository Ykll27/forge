package forge.ptbr.translator;

import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.store.TranslationFile;
import forge.ptbr.tools.ForgeCorpus;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Cobertura no alvo real: cartas do Forge sem texto oficial em português (memória com todo o corpus). */
public final class TargetCoverage {
    public static void main(String[] args) throws Exception {
        Path res = QualityReport.resDir();
        Map<String, CardTextEn> cards = ForgeCorpus.readCardScripts(res.resolve("cardsfolder"));
        Map<String, String> first = ForgeCorpus.readFirstPrintDates(res.resolve("editions"));
        Map<String, String> owners = ForgeCorpus.readFaceOwners(res.resolve("cardsfolder"));
        Map<String, LocalizedEntry> pt = TranslationFile.read(res.resolve("languages/cardnames-pt-BR.txt"));
        CorpusMiner miner = new CorpusMiner();
        for (CardTextEn c : cards.values()) {
            LocalizedEntry e = pt.get(c.name());
            if (e != null && e.hasText()) {
                String d = first.getOrDefault(c.name(), first.getOrDefault(owners.getOrDefault(c.name(), ""), "2000"));
                miner.addCard(c, e, QualityReport.recencyWeight(d));
            }
        }
        long t0 = System.currentTimeMillis();
        EffectTranslator tr = new EffectTranslator(miner.finish(), Glossary.bundled());
        int cardsN = 0, full = 0, half = 0, seg = 0, ok = 0;
        Map<String, Integer> misses = new HashMap<>();
        for (CardTextEn c : cards.values()) {
            LocalizedEntry e = pt.get(c.name());
            if ((e != null && e.hasText()) || c.oracle().isEmpty()) {
                continue;
            }
            TranslationResult r = tr.translate(c, e == null || !e.hasName() ? null : e.name());
            cardsN++;
            seg += r.segments();
            ok += r.translated();
            if (r.isComplete()) full++;
            if (r.coverage() >= 0.5) half++;
            for (String u : r.untranslated()) misses.merge(u.replaceAll("\\d+", "N"), 1, Integer::sum);
        }
        System.out.printf("memória=%s%n", tr.describe());
        System.out.printf("cartas sem texto oficial=%d  frases=%d traduzidas=%d (%.1f%%)  cartas completas=%d (%.1f%%)  >=50%%: %d (%.1f%%) [%d ms]%n",
                cardsN, seg, ok, 100.0 * ok / seg, full, 100.0 * full / cardsN, half, 100.0 * half / cardsN, System.currentTimeMillis() - t0);
        int show = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        misses.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(show)
                .forEach(x -> System.out.println(x.getValue() + "  " + x.getKey()));
    }
}
