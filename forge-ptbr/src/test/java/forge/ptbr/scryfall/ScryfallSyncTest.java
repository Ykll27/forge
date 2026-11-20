package forge.ptbr.scryfall;

import forge.ptbr.model.LocalizedEntry;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScryfallSyncTest {

    /** Relógio falso: registra as esperas pedidas sem esperar de verdade. */
    static final class FakeClock implements ScryfallClient.Clock {
        long now = 1_000_000;
        final List<Long> sleeps = new ArrayList<>();

        @Override
        public long nowMillis() {
            return now;
        }

        @Override
        public void sleep(long millis) {
            sleeps.add(millis);
            now += millis;
        }
    }

    private static ScryfallClient client(FakeScryfall fake, FakeClock clock) {
        return new ScryfallClient(new UrlConnectionTransport(), fake.base(), "ForgePTBR-Teste/1.0", clock,
                ScryfallClient.SEARCH_INTERVAL_MS, 3);
    }

    @Test
    public void fullSyncFollowsPagesKeepsNewestPrintingAndSplitsFaces() throws Exception {
        try (FakeScryfall fake = new FakeScryfall()) {
            FakeClock clock = new FakeClock();
            List<int[]> progress = new ArrayList<>();
            PtBrCardSync.Result r = new PtBrCardSync(client(fake, clock)).run(null, null, null, null,
                    (done, total, seen) -> progress.add(new int[]{done, total, seen}), () -> false);
            Assert.assertEquals(r.pages, 2);
            Assert.assertEquals(fake.requests.size(), 2);
            Assert.assertTrue(fake.requests.get(0).contains("lang%3Apt"), fake.requests.get(0));
            Assert.assertTrue(fake.requests.get(0).contains("order=released"));
            LocalizedEntry elves = r.entries.get("Llanowar Elves");
            Assert.assertEquals(elves.text(), "{T}: Adicione {G} à sua reserva de mana.", "a impressão de 2019 vence a de 1997");
            Assert.assertEquals(r.metadata.get("Llanowar Elves"), "date=2019-07-12");
            Assert.assertEquals(r.entries.get("Fire").name(), "Fogo");
            Assert.assertEquals(r.entries.get("Ice").text(), "Vire a permanente alvo.\\nCompre um card.");
            Assert.assertFalse(r.entries.containsKey("Intruso"), "cartas em outro idioma são ignoradas");
            Assert.assertEquals(r.newestRelease, "2021-01-01");
            Assert.assertEquals(progress.size(), 2);
            // cabeçalhos exigidos pelo Scryfall em todas as requisições
            for (String ua : fake.userAgents) {
                Assert.assertEquals(ua, "ForgePTBR-Teste/1.0");
            }
            for (String accept : fake.accepts) {
                Assert.assertTrue(accept.contains("application/json"));
            }
            // limite de taxa: a segunda requisição esperou os 500 ms da busca
            Assert.assertTrue(clock.sleeps.contains(ScryfallClient.SEARCH_INTERVAL_MS), clock.sleeps.toString());
        }
    }

    @Test
    public void incrementalSyncAsksOnlyForNewerPrintingsAndHandlesEmptyResult() throws Exception {
        try (FakeScryfall fake = new FakeScryfall()) {
            PtBrCardSync sync = new PtBrCardSync(client(fake, new FakeClock()));
            java.util.Map<String, LocalizedEntry> existing = new java.util.HashMap<>();
            existing.put("Counterspell", new LocalizedEntry("Counterspell", "Contramágica", "", "Anule a mágica alvo."));
            PtBrCardSync.Result r = sync.run("2024-06-14", existing, null, null, null, null);
            Assert.assertEquals(fake.sinceSeen, "2024-06-14");
            Assert.assertTrue(r.entries.containsKey("Counterspell"), "mantém o que já havia");
            Assert.assertEquals(r.entries.get("Llanowar Elves").text(), "{T}: Adicione {G}.");
            PtBrCardSync.Result empty = sync.run("2030-01-01", r.entries, null, null, null, null);
            Assert.assertEquals(empty.entries.size(), r.entries.size(), "404 do Scryfall = nada novo");
        }
    }

    @Test
    public void rateLimitedResponsesAreRetriedHonouringRetryAfter() throws Exception {
        try (FakeScryfall fake = new FakeScryfall()) {
            FakeClock clock = new FakeClock();
            fake.throttleNext.set(2);
            PtBrCardSync.Result r = new PtBrCardSync(client(fake, clock)).run(null, null, null, null, null, null);
            Assert.assertEquals(r.entries.size(), 4);
            Assert.assertEquals(fake.requests.size(), 4, "2 recusas + 2 páginas");
            long waited = clock.sleeps.stream().mapToLong(Long::longValue).sum();
            Assert.assertTrue(waited >= 2000, "respeitou Retry-After de 1 s duas vezes: " + waited);
        }
    }

    @Test
    public void givesUpAfterMaxRetriesAndCanBeCancelled() throws Exception {
        try (FakeScryfall fake = new FakeScryfall()) {
            fake.throttleNext.set(100);
            try {
                new PtBrCardSync(client(fake, new FakeClock())).run(null, null, null, null, null, null);
                Assert.fail("deveria desistir");
            } catch (ScryfallClient.ScryfallException e) {
                Assert.assertEquals(e.getStatus(), 429);
            }
            fake.throttleNext.set(0);
            try {
                new PtBrCardSync(client(fake, new FakeClock())).run(null, null, null, null, null, () -> true);
                Assert.fail("deveria cancelar");
            } catch (ScryfallClient.CancelledException expected) {
                // ok
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void userAgentIsMandatory() {
        new ScryfallClient(new UrlConnectionTransport(), " ");
    }

    @Test
    public void unreachableServerFailsWithIOException() {
        ScryfallClient c = new ScryfallClient(new UrlConnectionTransport(500, 500), "http://127.0.0.1:1",
                "ForgePTBR-Teste/1.0", new FakeClock(), 0, 1);
        try {
            c.get("/cards/search?q=x", null);
            Assert.fail("sem rede deveria falhar");
        } catch (IOException expected) {
            // o chamador mantém os arquivos já baixados (ver PtBrLocalizationServiceTest)
        }
    }
}
