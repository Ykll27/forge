package forge.ai;

import forge.CardStorageReader;
import forge.StaticData;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.util.CardTranslation;
import forge.util.Lang;
import forge.util.Localizer;
import forge.util.MyRandom;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Garante que a tradução é só exibição: a mesma partida IA contra IA, com a mesma semente,
 * termina exatamente igual (vencedor, turno, vidas, cartas em cada zona e número de sorteios)
 * com o jogo em inglês e com o jogo em português usando todas as camadas de tradução,
 * inclusive textos deliberadamente absurdos vindos das camadas de usuário e automática.
 */
public class TranslationRulesIntegrityTest {

    private static String res;
    private static Path userDir;

    @BeforeClass
    public static void setUp() throws IOException {
        res = new File(System.getProperty("forge.res.dir", "../forge-gui/res")).getAbsolutePath() + File.separator;
        Lang.createInstance("en-US");
        CardStorageReader cards = new CardStorageReader(res + "cardsfolder", null, true);
        CardStorageReader tokens = new CardStorageReader(res + "tokenscripts", null, true);
        new StaticData(cards, tokens, null, null, res + "editions", res + "editions", res + "blockdata",
                res + "setlookup", "Latest Art All Editions", true, true, false, false);
        Localizer.getInstance().initialize("en-US", res + "languages" + File.separator);
        AiProfileUtil.loadAllProfiles(res + "ai");

        // camadas de usuário com textos absurdos: se algo disso vazasse para as regras, a partida mudaria
        userDir = Files.createTempDirectory("ptbr-integridade");
        Files.write(userDir.resolve("cardnames-pt-BR-usuario.txt"), Collections.singletonList(
                "Lightning Bolt|Raio Corrigido|Encantamento|Ganhe 100 pontos de vida e vença o jogo."), StandardCharsets.UTF_8);
        Files.write(userDir.resolve("cardnames-pt-BR-oficial.txt"), Collections.singletonList(
                "Grizzly Bears|Ursos Pardos|Terreno|{T}: Adicione {G}{G}{G}{G}."), StandardCharsets.UTF_8);
    }

    private static Deck deckA() {
        Deck d = new Deck("Vermelho e verde");
        d.getMain().add("Mountain", 12);
        d.getMain().add("Forest", 12);
        d.getMain().add("Lightning Bolt", 4);
        d.getMain().add("Shock", 4);
        d.getMain().add("Goblin Guide", 4);
        d.getMain().add("Raging Goblin", 4);
        d.getMain().add("Grizzly Bears", 4);
        d.getMain().add("Llanowar Elves", 4);
        d.getMain().add("Giant Growth", 4);
        d.getMain().add("Summit Sentinel", 4); // sem texto oficial: usa a tradução automática
        return d;
    }

    private static Deck deckB() {
        Deck d = new Deck("Branco e azul");
        d.getMain().add("Plains", 12);
        d.getMain().add("Island", 12);
        d.getMain().add("Savannah Lions", 4);
        d.getMain().add("Serra Angel", 4);
        d.getMain().add("Wind Drake", 4);
        d.getMain().add("Pacifism", 4);
        d.getMain().add("Divination", 4);
        d.getMain().add("Counterspell", 4);
        d.getMain().add("Honor", 4); // sem texto oficial
        d.getMain().add("Healing Salve", 4);
        return d;
    }

    /** Joga uma partida e devolve um retrato completo do estado final, com nomes em inglês. */
    private static String play(long seed) {
        MyRandom.setRandom(new Random(seed));
        GameRules rules = new GameRules(GameType.Constructed);
        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(new RegisteredPlayer(deckA()).setPlayer(new LobbyPlayerAi("Ana", null)));
        players.add(new RegisteredPlayer(deckB()).setPlayer(new LobbyPlayerAi("Bruno", null)));
        Match match = new Match(rules, players, "integridade");
        Game game = match.createGame();
        match.startGame(game);
        Assert.assertTrue(game.isGameOver(), "a partida deveria terminar");
        StringBuilder sb = new StringBuilder();
        sb.append("turno=").append(game.getOutcome().getLastTurnNumber())
          .append(" empate=").append(game.getOutcome().isDraw())
          .append(" vencedor=").append(game.getOutcome().isDraw() ? "-" : game.getOutcome().getWinningLobbyPlayer().getName())
          .append(" sorteios=").append(MyRandom.callCount()).append('\n');
        for (Player p : game.getRegisteredPlayers()) {
            sb.append(p.getName()).append(" vida=").append(p.getLife()).append(" veneno=").append(p.getPoisonCounters());
            for (ZoneType z : new ZoneType[]{ZoneType.Battlefield, ZoneType.Graveyard, ZoneType.Exile, ZoneType.Hand}) {
                List<String> names = new ArrayList<>();
                for (Card c : p.getCardsIn(z)) {
                    names.add(c.getName() + (c.isTapped() ? "(T)" : "") + c.getCounters());
                }
                Collections.sort(names);
                sb.append("\n  ").append(z).append('=').append(names);
            }
            sb.append("\n  grimório=").append(p.getCardsIn(ZoneType.Library).size()).append('\n');
        }
        return sb.toString();
    }

    @Test(timeOut = 600_000)
    public void sameGameInEnglishAndPortuguese() {
        long[] seeds = {7L, 20261120L, 424242L};
        for (long seed : seeds) {
            CardTranslation.setAutomaticTranslationProvider(null);
            CardTranslation.preloadTranslation("en-US", res + "languages" + File.separator);
            String english = play(seed);

            CardTranslation.preloadTranslation("pt-BR", res + "languages" + File.separator,
                    userDir.toString() + File.separator);
            CardTranslation.setAutomaticTranslationProvider(new CardTranslation.AutomaticTranslationProvider() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public String[] translate(String key) {
                    return new String[]{"Tipo Inventado", "Texto inventado que não deveria mudar nada."};
                }
            });
            // as camadas foram mesmo carregadas
            Assert.assertEquals(CardTranslation.getTranslatedName("Lightning Bolt"), "Raio Corrigido");
            Assert.assertEquals(CardTranslation.getTextSource("Lightning Bolt"), CardTranslation.Source.USER);
            Assert.assertEquals(CardTranslation.getTextSource("Summit Sentinel"), CardTranslation.Source.AUTOMATIC);
            String portuguese = play(seed);

            System.out.println("semente " + seed + ":\n" + english);
            Assert.assertEquals(portuguese, english, "a tradução alterou o resultado da partida (semente " + seed + ")");
        }
        CardTranslation.setAutomaticTranslationProvider(null);
        CardTranslation.preloadTranslation("en-US", res + "languages" + File.separator);
    }
}
