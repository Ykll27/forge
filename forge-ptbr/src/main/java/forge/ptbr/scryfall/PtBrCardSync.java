package forge.ptbr.scryfall;
import forge.ptbr.model.LocalizedEntry;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Baixa do Scryfall os textos impressos em português (busca {@code lang:pt}) e guarda,
 * para cada face, a impressão mais recente, cuja redação é a mais próxima das regras atuais.
 *
 * <p>Modo incremental: com uma data de corte, só pede impressões lançadas a partir dela
 * ({@code date>=AAAA-MM-DD}), o que torna as verificações periódicas baratas. A impressão em
 * português acabou em 2024 (Modern Horizons 3), então depois da primeira sincronização as
 * seguintes normalmente não trazem nada novo, só correções do Scryfall.</p>
 */
public final class PtBrCardSync {
    /** Progresso: páginas lidas, total estimado de páginas, impressões vistas. */
    public interface Progress {
        void onPage(int pagesDone, int pagesEstimated, int printingsSeen);
    }

    /** Resultado da sincronização. */
    public static final class Result {
        public final Map<String, LocalizedEntry> entries;
        public final Map<String, String> metadata;
        public final int pages;
        public final int printingsSeen;
        public final String newestRelease;

        Result(Map<String, LocalizedEntry> entries, Map<String, String> metadata, int pages, int printingsSeen,
               String newestRelease) {
            this.entries = entries;
            this.metadata = metadata;
            this.pages = pages;
            this.printingsSeen = printingsSeen;
            this.newestRelease = newestRelease;
        }
    }

    static final int PAGE_SIZE = 175; // tamanho de página da busca do Scryfall

    private final ScryfallClient client;

    public PtBrCardSync(ScryfallClient client) {
        this.client = client;
    }

    /** URL da primeira página de busca. {@code since} nulo = sincronização completa. */
    public static String searchPath(String since) {
        String q = "lang:pt" + (since == null || since.isEmpty() ? "" : " date>=" + since);
        String encoded;
        try {
            encoded = URLEncoder.encode(q, "UTF-8"); // a sobrecarga com Charset não existe no Android 8
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return "/cards/search?q=" + encoded
                + "&unique=prints&order=released&dir=desc&include_multilingual=true";
    }

    /**
     * @param since         data mínima de lançamento (AAAA-MM-DD) ou null para tudo
     * @param existing      entradas já baixadas (são mantidas e atualizadas)
     * @param existingDates data da impressão de cada entrada existente (para manter a mais nova)
     * @param wanted        filtro de nomes ingleses (por exemplo, só cartas que o Forge conhece); null = todos
     */
    public Result run(String since, Map<String, LocalizedEntry> existing, Map<String, String> existingDates,
                      Predicate<String> wanted, Progress progress, BooleanSupplier cancelled) throws IOException {
        Map<String, LocalizedEntry> entries = existing == null ? new HashMap<>() : new HashMap<>(existing);
        Map<String, String> dates = existingDates == null ? new HashMap<>() : new HashMap<>(existingDates);
        String url = searchPath(since);
        int pages = 0;
        int seen = 0;
        int estimatedPages = 1;
        String newest = since == null ? "" : since;
        while (url != null) {
            HttpTransport.Response r = client.get(url, cancelled);
            ScryfallCardParser.Page page = r.status() == 404 ? ScryfallCardParser.Page.empty()
                    : ScryfallCardParser.parsePage(r.body());
            pages++;
            if (page.totalCards > 0) {
                estimatedPages = Math.max(1, (page.totalCards + PAGE_SIZE - 1) / PAGE_SIZE);
            }
            for (ScryfallCardParser.Printing p : page.printings) {
                seen++;
                if (wanted != null && !wanted.test(p.englishName)) {
                    continue;
                }
                if (p.releasedAt.compareTo(newest) > 0) {
                    newest = p.releasedAt;
                }
                String known = dates.get(p.englishName);
                if (known != null && known.compareTo(p.releasedAt) > 0) {
                    continue; // já temos uma impressão mais nova
                }
                LocalizedEntry fresh = new LocalizedEntry(p.englishName, p.name, p.type, p.text);
                LocalizedEntry previous = entries.get(p.englishName);
                // campos vazios da impressão nova não apagam o que a anterior tinha
                entries.put(p.englishName, previous == null ? fresh : previous.overlaidBy(fresh));
                dates.put(p.englishName, p.releasedAt);
            }
            if (progress != null) {
                progress.onPage(pages, Math.max(pages, estimatedPages), seen);
            }
            url = page.hasMore ? page.nextPage : null;
        }
        Map<String, String> meta = new HashMap<>();
        for (Map.Entry<String, String> d : dates.entrySet()) {
            meta.put(d.getKey(), "date=" + d.getValue());
        }
        return new Result(entries, meta, pages, seen, newest);
    }

    /** Lê a data guardada na coluna de metadados ("date=AAAA-MM-DD"). */
    public static Map<String, String> datesFromMetadata(Map<String, String> metadata) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            for (String part : e.getValue().split(";")) {
                if (part.startsWith("date=")) {
                    out.put(e.getKey(), part.substring(5));
                }
            }
        }
        return out;
    }
}
