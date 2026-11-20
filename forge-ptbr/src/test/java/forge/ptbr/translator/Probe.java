package forge.ptbr.translator;

import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.store.TranslationFile;
import forge.ptbr.tools.ForgeCorpus;

import java.nio.file.Path;
import java.util.Map;

/** Depuração: mostra como frases específicas são tratadas. Uso: Probe "frase" ... */
public final class Probe {
    public static void main(String[] args) throws Exception {
        Path res = QualityReport.resDir();
        Map<String, CardTextEn> cards = ForgeCorpus.readCardScripts(res.resolve("cardsfolder"));
        Map<String, LocalizedEntry> pt = TranslationFile.read(res.resolve("languages/cardnames-pt-BR.txt"));
        CorpusMiner miner = new CorpusMiner();
        for (CardTextEn c : cards.values()) {
            LocalizedEntry e = pt.get(c.name());
            if (e != null && e.hasText()) miner.addCard(c, e);
        }
        EffectTranslator tr = new EffectTranslator(miner.finish(), Glossary.bundled());
        for (String a : args) {
            if (a.startsWith("@")) { // @Nome da carta
                CardTextEn c = cards.get(a.substring(1));
                LocalizedEntry e = pt.get(c.name());
                TranslationResult r = tr.translate(c, e == null ? null : e.name());
                System.out.println("EN: " + c.oracle() + "\nPT: " + r.text() + "\n   cobertura=" + r.coverage() + " faltando=" + r.untranslated());
                continue;
            }
            TranslationResult r = tr.translate(new CardTextEn("Xyzzy", "", a), null);
            Slots.Keyed k = Slots.key(SelfReference.normalizeEnglish(a, "Xyzzy").text());
            System.out.println("EN: " + a + "\n   chave=" + k.key() + "\n   PT: " + r.text() + "  (" + r.translated() + "/" + r.segments() + ")");
            for (TranslationMemory.Kind kind : TranslationMemory.Kind.values()) {
                String v = tr.getMemory().lookup(kind, k.key());
                if (v != null) System.out.println("   " + kind + " -> " + v);
            }
            String kw = tr.getMemory().lookup(TranslationMemory.Kind.KEYWORD, CorpusMiner.keywordKey(k.key()));
            if (kw != null) System.out.println("   KEYWORD(lc) -> " + kw);
        }
    }
}
