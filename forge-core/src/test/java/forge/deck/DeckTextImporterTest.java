package forge.deck;

import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.util.CardTranslation;
import forge.util.Lang;
import forge.util.Localizer;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Importação de decks recebidos como texto: .dck, listas simples, MTG Arena e nomes em português. */
public class DeckTextImporterTest {

    private static String res;

    @BeforeClass
    public static void setUp() {
        res = new File(System.getProperty("forge.res.dir", "../forge-gui/res")).getAbsolutePath() + File.separator;
        Lang.createInstance("en-US");
        // o FModel faz isto no app; aqui só para o caminho com número de coleção (formato Arena)
        String pics = System.getProperty("java.io.tmpdir") + File.separator + "forge-pics" + File.separator;
        ImageKeys.initializeDirs(pics, new java.util.HashMap<>(), pics, pics, pics, pics, pics, pics, pics);
        if (StaticData.instance() == null) {
            new StaticData(new CardStorageReader(res + "cardsfolder", null, true),
                    new CardStorageReader(res + "tokenscripts", null, true), null, null,
                    res + "editions", res + "editions", res + "blockdata", res + "setlookup",
                    "Latest Art All Editions", true, true, false, false);
        }
        Localizer.getInstance().initialize("en-US", res + "languages" + File.separator);
    }

    @AfterMethod(alwaysRun = true)
    public void english() {
        CardTranslation.preloadTranslation("en-US", res + "languages" + File.separator);
    }

    private static String fixture(String name) throws Exception {
        try (InputStream in = DeckTextImporterTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int count(Deck deck, DeckSection section, String card) {
        CardPool pool = deck.get(section);
        return pool == null ? 0 : pool.countByName(card);
    }

    @Test
    public void forgeDckFileSharedFromAnotherApp() throws Exception {
        DeckTextImporter.Result r = DeckTextImporter.parse(fixture("Sapos_Puro_60.dck"), "qualquer-coisa.dck");
        Assert.assertEquals(r.getName(), "Sapos Puro 60", "nome vem do [metadata]");
        Assert.assertTrue(r.isNameFromText());
        Assert.assertEquals(r.getUnrecognised(), Arrays.asList(), "todas as cartas reconhecidas");
        Assert.assertEquals(r.getCardCount(), 60);
        Assert.assertEquals(r.getCardsPerSection().get(DeckSection.Main), Integer.valueOf(60));
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Clifftop Lookout"), 4);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Clement, the Worrywort"), 1);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Long River's Pull"), 1);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Forest"), 7);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Island"), 7);
    }

    @Test
    public void plainListTakesTheNameFromTheFile() throws Exception {
        String list = fixture("Sapos_Puro_60.dck").replaceAll("(?s)\\[metadata].*?\\[Main]\\s*", "");
        DeckTextImporter.Result r = DeckTextImporter.parse(list, "/storage/emulated/0/Download/Sapos_Puro_60.dck");
        Assert.assertEquals(r.getName(), "Sapos Puro 60");
        Assert.assertFalse(r.isNameFromText());
        Assert.assertEquals(r.getCardCount(), 60);
    }

    @Test
    public void mtgArenaExportWithSideboard() {
        String arena = "Deck\n4 Clifftop Lookout (BLB) 168\n20 Island\n\nSideboard\n2 Long River's Pull (BLB) 58\n";
        DeckTextImporter.Result r = DeckTextImporter.parse(arena, "arena.txt");
        Assert.assertEquals(r.getUnrecognised(), Arrays.asList());
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Clifftop Lookout"), 4);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Island"), 20);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Sideboard, "Long River's Pull"), 2);
    }

    @Test
    public void portugueseNamesHeadersAndAccentedDeckName() {
        CardTranslation.preloadTranslation("pt-BR", res + "languages" + File.separator);
        String text = "Nome: Elfos Mágicos\n"
                + "Principal\n"
                + "4 Raio\n"
                + "4x contramágica\n"
                + "10 ilha\n"
                + "10 MONTANHA\n"
                + "4 Elfos de Llanowar\n"
                + "\n"
                + "Reserva:\n"
                + "2 Anjo Serra\n"
                + "1 Carta Que Não Existe\n";
        DeckTextImporter.Result r = DeckTextImporter.parse(text, "x.txt");
        Assert.assertEquals(r.getName(), "Elfos Mágicos");
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Lightning Bolt"), 4);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Counterspell"), 4, "sem diferenciar maiúsculas/acentos");
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Island"), 10);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Mountain"), 10);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Main, "Llanowar Elves"), 4);
        Assert.assertEquals(count(r.getDeck(), DeckSection.Sideboard, "Serra Angel"), 2);
        Assert.assertEquals(r.getUnrecognised().size(), 1);
        Assert.assertTrue(r.getUnrecognised().get(0).contains("Carta Que Não Existe"), r.getUnrecognised().toString());
    }

    @Test
    public void translatedNamesAreOnlyResolvedWhenATranslationIsLoaded() {
        Assert.assertNull(CardTranslation.getEnglishName("Raio"));
        CardTranslation.preloadTranslation("pt-BR", res + "languages" + File.separator);
        Assert.assertEquals(CardTranslation.getEnglishName("raio"), "Lightning Bolt");
        Assert.assertEquals(CardTranslation.getEnglishName("CONTRAMAGICA"), "Counterspell");
        Assert.assertNull(CardTranslation.getEnglishName("Nada Disso"));
    }

    @Test
    public void textThatIsNotADeckYieldsNothing() {
        DeckTextImporter.Result r = DeckTextImporter.parse("Olá! Vamos jogar amanhã às 19h?", "mensagem.txt");
        Assert.assertTrue(r.isEmpty());
    }

    @Test
    public void namesAreCleanedAndNeverOverwriteAnExistingDeck() {
        Assert.assertEquals(DeckTextImporter.cleanName("C:\\decks\\Mono_Verde.v2.dck"), "Mono Verde.v2");
        Assert.assertEquals(DeckTextImporter.cleanName(null), "Deck importado");
        Set<String> existing = new HashSet<>(Arrays.asList("Sapos Puro 60", "Sapos Puro 60 (2)"));
        Assert.assertEquals(DeckTextImporter.uniqueName("Sapos Puro 60", existing::contains), "Sapos Puro 60 (3)");
        Assert.assertEquals(DeckTextImporter.uniqueName("Deck/Novo: 1", existing::contains), "Deck-Novo- 1");
    }
}
