package forge.ptbr.service;

import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.scryfall.ScryfallClient;
import forge.ptbr.scryfall.UrlConnectionTransport;
import forge.ptbr.store.TranslationFile;
import forge.ptbr.translator.TranslationResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PtBrLocalizationServiceTest {

    private static final List<CardTextEn> CARDS = Arrays.asList(
            new CardTextEn("Shock", "Instant", "Shock deals 2 damage to any target."),
            new CardTextEn("Divination", "Sorcery", "Draw two cards."),
            new CardTextEn("Lava Spike", "Sorcery", "Lava Spike deals 3 damage to target player or planeswalker."),
            new CardTextEn("Tidings", "Sorcery", "Draw four cards."),
            new CardTextEn("Weird New Card", "Creature Elf", "This creature enters prepared."));

    private static PtBrLocalizationService newService(Path root) throws IOException {
        Path bundled = Files.createDirectories(root.resolve("res/languages"));
        Files.write(bundled.resolve(PtBrLocalizationService.FILE_BUNDLED), Arrays.asList(
                "Shock|Choque|Mágica Instantânea|Choque causa 2 pontos de dano a qualquer alvo.",
                "Divination|Adivinhação|Feitiço|Compre dois cards.",
                "Lava Spike|Lança de Lava||"), StandardCharsets.UTF_8);
        return new PtBrLocalizationService(root.resolve("user/ptbr"), bundled);
    }

    @Test
    public void generatesOnlyWhereOfficialTextIsMissingAndReusesOnSecondRun() throws Exception {
        Path root = Files.createTempDirectory("svc");
        PtBrLocalizationService svc = newService(root);
        AutomaticGenerator.Output first = svc.generateAutomatic(CARDS, null, 0.5, null, null);
        Map<String, LocalizedEntry> auto = TranslationFile.read(svc.getUserDir().resolve(PtBrLocalizationService.FILE_AUTOMATIC));
        Assert.assertFalse(auto.containsKey("Shock") && auto.get("Shock").hasText(), "texto oficial nunca é substituído");
        Assert.assertEquals(auto.get("Tidings").text(), "Compre quatro cards.");
        Assert.assertTrue(auto.get("Lava Spike").text().startsWith("Lança de Lava causa 3"),
                "usa o nome oficial português na auto-referência: " + auto.get("Lava Spike").text());
        Assert.assertFalse(auto.containsKey("Weird New Card") && auto.get("Weird New Card").hasText(),
                "abaixo da cobertura mínima, o texto fica em inglês");
        Assert.assertTrue(first.translated >= 2);

        AutomaticGenerator.Output second = svc.generateAutomatic(CARDS, null, 0.5, null, null);
        Assert.assertEquals(second.reused, second.considered, "nada mudou: tudo reaproveitado do cache");
        Assert.assertTrue(Files.isRegularFile(svc.getUserDir().resolve(PtBrLocalizationService.FILE_MEMORY)));

        // nova instância (app reaberto, sem rede): a memória vem do arquivo, não é minerada de novo
        PtBrLocalizationService reopened = new PtBrLocalizationService(svc.getUserDir(), root.resolve("res/languages"));
        String tag = reopened.state().getProperty("memoria");
        TranslationResult r = reopened.translateOne(CARDS.get(3), null, CARDS, null);
        Assert.assertEquals(r.text(), "Compre quatro cards.");
        Assert.assertEquals(reopened.state().getProperty("memoria"), tag);
    }

    @Test
    public void userCorrectionsAreStoredAndRemoved() throws Exception {
        PtBrLocalizationService svc = newService(Files.createTempDirectory("svc"));
        svc.saveUserCorrection(new LocalizedEntry("Tidings", "", "", "Compre 4 cards (corrigido)."));
        Assert.assertEquals(svc.userCorrections().get("Tidings").text(), "Compre 4 cards (corrigido).");
        svc.removeUserCorrection("Tidings");
        Assert.assertTrue(svc.userCorrections().isEmpty());
    }

    @Test
    public void failedSyncKeepsPreviouslyDownloadedFile() throws Exception {
        PtBrLocalizationService svc = newService(Files.createTempDirectory("svc"));
        Path official = svc.getUserDir().resolve(PtBrLocalizationService.FILE_OFFICIAL);
        Files.createDirectories(official.getParent());
        Files.write(official, Arrays.asList("Tidings|Novas|Feitiço|Compre quatro cards.|date=2020-01-01"), StandardCharsets.UTF_8);
        ScryfallClient offline = new ScryfallClient(new UrlConnectionTransport(300, 300), "http://127.0.0.1:1",
                "ForgePTBR-Teste/1.0", ScryfallClient.Clock.SYSTEM, 0, 0);
        try {
            svc.syncOfficial(offline, false, null, null, null);
            Assert.fail("sem rede");
        } catch (IOException expected) {
            // ok
        }
        Assert.assertEquals(TranslationFile.read(official).get("Tidings").name(), "Novas", "arquivo anterior intacto");
        Assert.assertEquals(svc.officialEntries().get("Tidings").name(), "Novas", "oficial baixado sobrepõe o embutido");
        Assert.assertEquals(svc.officialEntries().get("Shock").name(), "Choque");
    }
}
