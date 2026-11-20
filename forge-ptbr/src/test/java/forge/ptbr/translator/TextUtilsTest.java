package forge.ptbr.translator;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

/** Proteção de símbolos, números e auto-referências. */
public class TextUtilsTest {

    @Test
    public void invariantsCoverSymbolsPowerToughnessNumbersAndX() {
        List<String> inv = MtgText.invariants("{2}{G}, {T}: Create X 1/1 tokens. They get +2/−1 for 3 turns.");
        Assert.assertEquals(inv, Arrays.asList("{2}", "{G}", "{T}", "1/1", "+2/-1", "3", "X"));
        Assert.assertTrue(MtgText.sameInvariants("Deal 3 damage. {R}", "{R} Cause 3 pontos de dano."));
        Assert.assertFalse(MtgText.sameInvariants("Draw 2 cards.", "Compre 3 cards."));
        Assert.assertFalse(MtgText.sameInvariants("Create X tokens.", "Crie uma ficha."));
    }

    @Test
    public void sentencesDoNotSplitInsideQuotesOrReminders() {
        List<String> s = MtgText.splitSentences(
                "Create a token with \"Sacrifice this. Draw a card.\" Then scry 1. (Look at it. Then decide.)");
        Assert.assertEquals(s.size(), 3);
        Assert.assertEquals(s.get(0), "Create a token with \"Sacrifice this. Draw a card.\"");
        Assert.assertEquals(s.get(1), "Then scry 1.");
        Assert.assertEquals(MtgText.stripReminder("Flying (This creature can't be blocked.)"), "Flying");
        Assert.assertEquals(MtgText.reminders("Ward {2} (Whenever ... {2}.)"), Arrays.asList("Whenever ... {2}."));
    }

    @Test
    public void slotsGeneraliseOnlyWhenValuesMatch() {
        Slots.TemplatePair p = Slots.templatize("Draw two cards.", "Compre dois cards.");
        Assert.assertEquals(p.en(), "Draw {W0} cards.");
        Assert.assertEquals(p.pt(), "Compre {W0:m} cards.");
        Slots.Keyed k = Slots.key("Draw three cards.");
        Assert.assertEquals(Slots.fill(p.pt(), k.values()), "Compre três cards.");
        Slots.TemplatePair f = Slots.templatize("Create two 1/1 tokens.", "Crie duas fichas 1/1.");
        Assert.assertEquals(Slots.fill(f.pt(), Slots.key("Create four 2/2 tokens.").values()), "Crie quatro fichas 2/2.");
        Slots.TemplatePair cost = Slots.templatize("{2}{G}: Regenerate CARDNAME.", "{2}{G}: Regenere CARDNAME.");
        Assert.assertEquals(Slots.fill(cost.pt(), Slots.key("{W}{W}: Regenerate CARDNAME.").values()), "{W}{W}: Regenere CARDNAME.");
        Assert.assertNull(Slots.templatize("Deal 3 damage.", "Cause 4 pontos de dano."), "valor divergente não vira lacuna");
    }

    @Test
    public void selfReferencesRoundTrip() {
        SelfReference.Normalized n = SelfReference.normalizeEnglish(
                "When this creature enters the battlefield, Ty Lee deals 1 damage.", "Ty Lee, Artful Acrobat");
        Assert.assertEquals(n.text(), "When CARDNAME enters, CARDNAME deals 1 damage.");
        String pt = SelfReference.render("Quando CARDNAME entrar, CARDNAME causa 1 ponto de dano.", n.originals(),
                "Ty Lee, Acrobata", "Ty Lee, Artful Acrobat");
        Assert.assertEquals(pt, "Quando esta criatura entrar, Ty Lee causa 1 ponto de dano.");
        Assert.assertEquals(SelfReference.normalizePortuguese("Sacrifique esta Saga. Exile SANGUE para o deus.", "Sangue Para o Deus"),
                "Sacrifique CARDNAME. Exile CARDNAME.");
    }

    @Test
    public void semanticCheckRejectsMisalignedOrOutdatedPairs() {
        Assert.assertTrue(SemanticCheck.aligned("Destroy target nonland permanent.", "Destrua a permanente alvo não de terreno."));
        Assert.assertFalse(SemanticCheck.aligned("Exile target creature.", "Remova do jogo a criatura alvo."), "redação anterior a 2009");
        Assert.assertFalse(SemanticCheck.aligned("Create a 1/1 token.", "Coloque no campo de batalha uma ficha 1/1."), "\"crie\" é o termo atual");
        Assert.assertFalse(SemanticCheck.aligned("You gain 2 life.", "Entra no campo de batalha, você ganha 2 pontos de vida."));
        Assert.assertTrue(SemanticCheck.looksEnglish("Flying", "Flying"));
        Assert.assertFalse(SemanticCheck.looksEnglish("Exile CARDNAME.", "Exile CARDNAME."), "idêntico em português");
    }
}
