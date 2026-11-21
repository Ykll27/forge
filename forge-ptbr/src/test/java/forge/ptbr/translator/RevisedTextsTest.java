package forge.ptbr.translator;

import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.service.PtBrLocalizationService;
import forge.ptbr.store.TranslationFile;
import forge.ptbr.tools.ForgeCorpus;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** As traduções revisadas valem só para cartas sem texto oficial e mantêm tudo o que as regras leem. */
public class RevisedTextsTest {

    @Test
    public void revisedTextsMatchTheirOracleText() throws Exception {
        Path res = QualityReport.resDir();
        if (!Files.isDirectory(res.resolve("cardsfolder"))) {
            throw new SkipException("forge-gui/res não encontrado");
        }
        RevisedTexts revised = RevisedTexts.bundled();
        Assert.assertTrue(revised.size() > 100, "traduções revisadas carregadas: " + revised.size());
        Map<String, CardTextEn> cards = ForgeCorpus.readCardScripts(res.resolve("cardsfolder"));
        Map<String, LocalizedEntry> official = TranslationFile.read(res.resolve("languages").resolve(PtBrLocalizationService.FILE_BUNDLED));
        List<String> problems = new ArrayList<>();
        for (String name : revised.names()) {
            CardTextEn en = cards.get(name);
            if (en == null) {
                problems.add(name + ": carta desconhecida");
                continue;
            }
            LocalizedEntry off = official.get(name);
            if (off != null && off.hasText()) {
                problems.add(name + ": já tem texto oficial");
            }
            String[] pt = revised.get(name).split("\\\\n", -1);
            if (pt.length != en.oracleLines().size()) {
                problems.add(name + ": " + pt.length + " linhas, Oracle tem " + en.oracleLines().size());
                continue;
            }
            for (int i = 0; i < pt.length; i++) {
                String src = MtgText.stripReminder(en.oracleLines().get(i));
                String dst = MtgText.stripReminder(pt[i]);
                if (!MtgText.sameInvariants(src, dst)) {
                    problems.add(name + " linha " + i + "\n  en: " + src + "\n  pt: " + dst);
                }
            }
        }
        Assert.assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    public void translatorPrefersRevisedText() {
        RevisedTexts revised = RevisedTexts.bundled();
        String name = revised.names().iterator().next();
        EffectTranslator t = new EffectTranslator(new TranslationMemory(), Glossary.empty());
        TranslationResult r = t.translate(new CardTextEn(name, "Creature", "Flying"), null);
        Assert.assertTrue(r.isComplete());
        Assert.assertEquals(r.text(), revised.get(name));
    }
}
