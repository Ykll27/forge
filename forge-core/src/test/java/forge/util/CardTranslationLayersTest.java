package forge.util;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Camadas de tradução: automática < oficial embutida < oficial baixada < usuário, campo a campo. */
public class CardTranslationLayersTest {

    private Path langDir;
    private Path userDir;

    @BeforeMethod
    public void setUp() throws Exception {
        Lang.createInstance("en-US");
        langDir = Files.createTempDirectory("lang");
        userDir = Files.createTempDirectory("user");
        Files.write(langDir.resolve("cardnames-pt-BR.txt"), Arrays.asList(
                "Lightning Bolt|Raio|Mágica Instantânea|Raio causa 3 pontos de dano a qualquer alvo.",
                "Abandon Hope|Abandonar a Esperança||",
                "Serra Angel|Anjo Serra|Criatura — Anjo|Voar\\nVigilância",
                "Razorfoot Griffin|Grifo Garra-de-Navalha|Criatura — Grifo|Voar (Esta criatura só pode ser bloqueada por criaturas com a habilidade de voar.)\\nIniciativa (Esta criatura causa dano de combate antes de criaturas sem a habilidade de iniciativa.)"), StandardCharsets.UTF_8);
        Files.write(langDir.resolve("cardnames-pt-BR-automatica.txt"), Arrays.asList(
                "Abandon Hope||Feitiço|Texto automático.|v=1;h=x",
                "Lightning Bolt||Tipo automático errado|Texto automático errado.|v=1;h=y",
                "Summit Sentinel||Criatura — Elemental Soldado|Quando esta criatura morrer, compre um card.|v=1"), StandardCharsets.UTF_8);
        Files.write(userDir.resolve("cardnames-pt-BR-oficial.txt"), Arrays.asList(
                "Serra Angel|||Voar, vigilância|date=2020-01-01"), StandardCharsets.UTF_8);
        Files.write(userDir.resolve("cardnames-pt-BR-usuario.txt"), Arrays.asList(
                "Lightning Bolt|Raio!||"), StandardCharsets.UTF_8);
        CardTranslation.preloadTranslation("pt-BR", langDir + File.separator, userDir + File.separator);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        CardTranslation.setAutomaticTranslationProvider(null);
        CardTranslation.preloadTranslation("en-US", langDir + File.separator);
    }

    @Test
    public void eachFieldTakesTheHighestPrioritySourceThatHasIt() {
        // nome: usuário; tipo e texto: oficial embutido (o automático nunca vence o oficial)
        Assert.assertEquals(CardTranslation.getTranslatedName("Lightning Bolt"), "Raio!");
        Assert.assertEquals(CardTranslation.getNameSource("Lightning Bolt"), CardTranslation.Source.USER);
        Assert.assertEquals(CardTranslation.getTranslatedType("Lightning Bolt", "Instant"), "Mágica Instantânea");
        Assert.assertTrue(CardTranslation.getTranslatedOracle("Lightning Bolt").startsWith("Raio causa 3"));
        Assert.assertEquals(CardTranslation.getTextSource("Lightning Bolt"), CardTranslation.Source.OFFICIAL_BUNDLED);
        // campos vazios no oficial não apagam o automático
        Assert.assertEquals(CardTranslation.getTranslatedName("Abandon Hope"), "Abandonar a Esperança");
        Assert.assertEquals(CardTranslation.getTranslatedType("Abandon Hope", "Sorcery"), "Feitiço");
        Assert.assertEquals(CardTranslation.getTextSource("Abandon Hope"), CardTranslation.Source.AUTOMATIC);
        // texto baixado substitui o embutido
        Assert.assertEquals(CardTranslation.getTranslatedOracle("Serra Angel"), "Voar, vigilância");
        Assert.assertEquals(CardTranslation.getTextSource("Serra Angel"), CardTranslation.Source.OFFICIAL_DOWNLOADED);
        // carta sem nome oficial: nome inglês, texto automático com quebra de linha convertida
        Assert.assertEquals(CardTranslation.getTranslatedName("Summit Sentinel"), "Summit Sentinel");
        Assert.assertEquals(CardTranslation.getTextSource("Summit Sentinel"), CardTranslation.Source.AUTOMATIC);
        Assert.assertNull(CardTranslation.getTextSource("Carta Inexistente"));
    }

    @Test
    public void providerIsAskedOnceAndOnlyWhenReady() {
        AtomicInteger calls = new AtomicInteger();
        final boolean[] ready = {false};
        CardTranslation.setAutomaticTranslationProvider(new CardTranslation.AutomaticTranslationProvider() {
            @Override
            public boolean isReady() {
                return ready[0];
            }

            @Override
            public String[] translate(String key) {
                calls.incrementAndGet();
                return "Custom Card".equals(key) ? new String[]{"Criatura", "Linha 1.\\nLinha 2."} : null;
            }
        });
        Assert.assertEquals(CardTranslation.getTranslatedOracle("Custom Card"), "");
        Assert.assertEquals(calls.get(), 0, "não pergunta enquanto carrega");
        ready[0] = true;
        Assert.assertEquals(CardTranslation.getTranslatedOracle("Custom Card"), "Linha 1.\r\n\r\nLinha 2.");
        Assert.assertEquals(CardTranslation.getTranslatedType("Custom Card", "Creature"), "Criatura");
        Assert.assertEquals(CardTranslation.getTextSource("Custom Card"), CardTranslation.Source.AUTOMATIC);
        CardTranslation.getTranslatedOracle("Unknown");
        CardTranslation.getTranslatedOracle("Unknown");
        Assert.assertEquals(calls.get(), 2, "uma vez por carta, inclusive para as que não têm tradução");
    }

    @Test
    public void reloadPicksUpNewCorrections() throws Exception {
        Files.write(userDir.resolve("cardnames-pt-BR-usuario.txt"), Arrays.asList(
                "Serra Angel|||Voar (corrigido)"), StandardCharsets.UTF_8);
        CardTranslation.reload();
        Assert.assertEquals(CardTranslation.getTranslatedOracle("Serra Angel"), "Voar (corrigido)");
        Assert.assertEquals(CardTranslation.getTextSource("Serra Angel"), CardTranslation.Source.USER);
        Assert.assertEquals(CardTranslation.getTranslatedName("Lightning Bolt"), "Raio", "correção antiga removida");
    }

    @Test
    public void combinedKeywordLineTranslatesEveryKeyword() {
        ITranslatable griffin = () -> "Razorfoot Griffin";
        CardTranslation.buildOracleMapping("Razorfoot Griffin", "Flying\\nFirst strike", null); // \\n literal, como nos scripts
        Assert.assertEquals(CardTranslation.translateMultipleDescriptionText("Flying, first strike", griffin), "Voar, iniciativa");
        Assert.assertEquals(CardTranslation.translateMultipleDescriptionText("When it dies, draw a card, then discard a card.", griffin),
                "When it dies, draw a card, then discard a card.", "frase com vírgulas não é lista de palavras-chave");
    }

    @Test
    public void inGameAbilityTextUsesTheLineAlignedTranslation() {
        ITranslatable sentinel = () -> "Summit Sentinel";
        CardTranslation.buildOracleMapping("Summit Sentinel", "When this creature dies, draw a card.", null);
        Assert.assertEquals(CardTranslation.translateSingleDescriptionText("When this creature dies, draw a card.", sentinel),
                "Quando esta criatura morrer, compre um card.");
    }

    @Test
    public void englishShowsNoTranslation() {
        CardTranslation.preloadTranslation("en-US", langDir + File.separator, userDir + File.separator);
        Assert.assertEquals(CardTranslation.getTranslatedName("Lightning Bolt"), "Lightning Bolt");
        Assert.assertNull(CardTranslation.getTextSource("Lightning Bolt"));
        Assert.assertFalse(CardTranslation.isTranslationActive());
    }
}
