package forge.ptbr.translator;

import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Tradutor com uma memória pequena e controlada (sem depender dos recursos do Forge). */
public class EffectTranslatorTest {

    private EffectTranslator tr;

    @BeforeClass
    public void setUp() {
        CorpusMiner miner = new CorpusMiner();
        miner.addCard(new CardTextEn("Serra Angel", "Creature Angel", "Flying, vigilance"),
                new LocalizedEntry("Serra Angel", "Anjo Serra", "Criatura — Anjo", "Voar, vigilância"));
        miner.addCard(new CardTextEn("Shock", "Instant", "Shock deals 2 damage to any target."),
                new LocalizedEntry("Shock", "Choque", "Mágica Instantânea", "Choque causa 2 pontos de dano a qualquer alvo."));
        miner.addCard(new CardTextEn("Divination", "Sorcery", "Draw two cards."),
                new LocalizedEntry("Divination", "Adivinhação", "Feitiço", "Compre dois cards."));
        miner.addCard(new CardTextEn("Wall of Omens", "Creature Wall", "Defender\\nWhen Wall of Omens enters the battlefield, draw a card."),
                new LocalizedEntry("Wall of Omens", "Muralha dos Presságios", "Criatura — Muralha",
                        "Defensor\\nQuando Muralha dos Presságios entrar no campo de batalha, compre um card."));
        miner.addCard(new CardTextEn("Goblin Piker", "Creature Goblin Warrior", "Goblin Piker can't block."),
                new LocalizedEntry("Goblin Piker", "Goblin Lanceiro", "Criatura — Goblin Guerreiro", "Goblin Lanceiro não pode bloquear."));
        miner.addCard(new CardTextEn("Elvish Mystic", "Creature Elf Druid", "{T}: Add {G}."),
                new LocalizedEntry("Elvish Mystic", "Mística Élfica", "Criatura — Elfo Druida", "{T}: Adicione {G}."));
        tr = new EffectTranslator(miner.finish(), Glossary.empty());
    }

    @Test
    public void reusesOfficialSentencesWithOtherNumbersAndNames() {
        TranslationResult r = tr.translate(new CardTextEn("Lava Spike", "Sorcery", "Lava Spike deals 3 damage to any target."), null);
        Assert.assertEquals(r.text(), "Lava Spike causa 3 pontos de dano a qualquer alvo.");
        Assert.assertTrue(r.isComplete());
        Assert.assertEquals(tr.translate(new CardTextEn("Tidings", "Sorcery", "Draw four cards."), null).text(), "Compre quatro cards.");
    }

    @Test
    public void modernTemplatingMapsToOfficialWording() {
        TranslationResult r = tr.translate(new CardTextEn("Summit Sentinel", "Creature Elemental",
                "Flying\\nWhen this creature enters, draw a card."), null);
        Assert.assertEquals(r.text(), "Voar\\nQuando esta criatura entrar no campo de batalha, compre um card.");
    }

    @Test
    public void costsAndManaSymbolsArePreserved() {
        TranslationResult r = tr.translate(new CardTextEn("Birds", "Creature Bird", "{T}: Add {W}{U}."), null);
        Assert.assertEquals(r.text(), "{T}: Adicione {W}{U}.");
    }

    @Test
    public void unknownSentencesStayInEnglishAndAreCounted() {
        TranslationResult r = tr.translate(new CardTextEn("New Thing", "Creature",
                "Flying\\nThis creature enters prepared."), "Coisa Nova");
        Assert.assertEquals(r.text(), "Voar\\nThis creature enters prepared.", "a auto-referência fica em inglês dentro da frase inglesa");
        Assert.assertEquals(r.segments(), 2);
        Assert.assertEquals(r.translated(), 1);
        Assert.assertEquals(r.untranslated().size(), 1);
        Assert.assertEquals(r.coverage(), 0.5, 1e-9);
    }

    @Test
    public void grammarHandlesCommonEffects() {
        Assert.assertEquals(tr.translate(new CardTextEn("A", "Instant", "Destroy target tapped creature."), null).text(),
                "Destrua a criatura virada alvo.");
        Assert.assertEquals(tr.translate(new CardTextEn("B", "Instant", "Target creature gets +3/+3 until end of turn."), null).text(),
                "A criatura alvo recebe +3/+3 até o final do turno.");
        Assert.assertEquals(tr.translate(new CardTextEn("C", "Instant", "Return target nonland permanent to its owner's hand."), null).text(),
                "Devolva a permanente não de terreno alvo para a mão de seu dono.");
        Assert.assertEquals(tr.translate(new CardTextEn("D", "Sorcery", "Put two +1/+1 counters on target creature you control."), null).text(),
                "Coloque dois marcadores +1/+1 na criatura alvo que você controla.");
    }

    @Test
    public void typeLineUsesMinedTerms() {
        Assert.assertEquals(tr.translateType("Creature Goblin Warrior"), "Criatura — Goblin Guerreiro");
        Assert.assertEquals(tr.translateType("Creature Elf Druid"), "Criatura — Elfo Druida");
    }

    @Test
    public void memorySurvivesSerialisation() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        tr.getMemory().write(out);
        TranslationMemory back = TranslationMemory.read(new java.io.ByteArrayInputStream(out.toByteArray()));
        Assert.assertEquals(back.size(), tr.getMemory().size());
        EffectTranslator again = new EffectTranslator(back, Glossary.empty());
        Assert.assertEquals(again.translate(new CardTextEn("Tidings", "Sorcery", "Draw four cards."), null).text(), "Compre quatro cards.");
    }
}
