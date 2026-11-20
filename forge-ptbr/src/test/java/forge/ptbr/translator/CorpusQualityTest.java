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
import java.util.Map;

/**
 * Qualidade medida contra os textos oficiais reais (precisa de forge-gui/res).
 * Os limites são pisos: uma mudança no tradutor que piore a fidelidade falha aqui.
 */
public class CorpusQualityTest {

    private static Path res() {
        Path res = QualityReport.resDir();
        if (!Files.isDirectory(res.resolve("cardsfolder"))) {
            throw new SkipException("forge-gui/res não encontrado");
        }
        return res;
    }

    @Test
    public void outOfSampleTranslationsMatchOfficialText() throws Exception {
        res();
        QualityReport r = QualityReport.run("2023-01-01");
        System.out.printf("cobertura=%.3f idênticas=%.3f similaridade=%.3f%n", r.coverage(), r.exactRate(), r.meanSimilarity());
        Assert.assertTrue(r.cardsEvaluated > 2000);
        Assert.assertTrue(r.meanSimilarity() >= 0.97, "similaridade média com o texto oficial: " + r.meanSimilarity());
        Assert.assertTrue(r.exactRate() >= 0.75, "linhas idênticas ao oficial: " + r.exactRate());
        Assert.assertTrue(r.coverage() >= 0.55, "cobertura de frases: " + r.coverage());
    }

    /**
     * Toda tradução automática distribuída mantém, linha a linha, os mesmos custos, números,
     * P/R e variáveis do Oracle original, e o mesmo número de linhas.
     */
    @Test
    public void bundledAutomaticTranslationsPreserveEveryInvariant() throws Exception {
        Path res = res();
        Path autoFile = res.resolve("languages").resolve(PtBrLocalizationService.FILE_AUTOMATIC);
        if (!Files.isRegularFile(autoFile)) {
            throw new SkipException("arquivo automático ainda não gerado");
        }
        Map<String, CardTextEn> cards = ForgeCorpus.readCardScripts(res.resolve("cardsfolder"));
        Map<String, LocalizedEntry> official = TranslationFile.read(res.resolve("languages").resolve(PtBrLocalizationService.FILE_BUNDLED));
        int checked = 0;
        for (LocalizedEntry e : TranslationFile.read(autoFile).values()) {
            CardTextEn en = cards.get(e.key());
            Assert.assertNotNull(en, "carta desconhecida no arquivo automático: " + e.key());
            LocalizedEntry off = official.get(e.key());
            Assert.assertFalse(off != null && off.hasText() && e.hasText(), "automático por cima de texto oficial: " + e.key());
            if (!e.hasText()) {
                continue;
            }
            String[] ours = e.text().split("\\\\n", -1);
            Assert.assertEquals(ours.length, en.oracleLines().size(), "número de linhas: " + e.key());
            for (int i = 0; i < ours.length; i++) {
                String src = MtgText.stripReminder(en.oracleLines().get(i));
                String dst = MtgText.stripReminder(ours[i]);
                Assert.assertTrue(MtgText.sameInvariants(src, dst),
                        e.key() + " linha " + i + "\n  en: " + src + "\n  pt: " + dst);
            }
            checked++;
        }
        Assert.assertTrue(checked > 5000, "traduções verificadas: " + checked);
    }
}
