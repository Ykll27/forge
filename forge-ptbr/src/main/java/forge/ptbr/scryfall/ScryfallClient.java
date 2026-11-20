package forge.ptbr.scryfall;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Cliente do Scryfall que segue as regras públicas da API:
 * <ul>
 *   <li>cabeçalhos {@code User-Agent} (nome/versão do app) e {@code Accept} obrigatórios;</li>
 *   <li>/cards/search limitado a 2 requisições por segundo (500 ms entre chamadas);</li>
 *   <li>429 e erros 5xx: espera ({@code Retry-After} quando informado) e tenta de novo, com recuo
 *       exponencial e número máximo de tentativas.</li>
 * </ul>
 */
public final class ScryfallClient {
    public static final String DEFAULT_BASE_URL = "https://api.scryfall.com";
    public static final long SEARCH_INTERVAL_MS = 500;

    /** Relógio/espera injetáveis, para testar o limite de taxa sem esperar de verdade. */
    public interface Clock {
        long nowMillis();
        void sleep(long millis) throws InterruptedException;

        Clock SYSTEM = new Clock() {
            @Override
            public long nowMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public void sleep(long millis) throws InterruptedException {
                Thread.sleep(millis);
            }
        };
    }

    /** Erro de API com o código HTTP. */
    public static final class ScryfallException extends IOException {
        private final int status;

        public ScryfallException(int status, String message) {
            super("Scryfall HTTP " + status + ": " + message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    /** Sincronização cancelada pelo usuário. */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("cancelado");
        }
    }

    private final HttpTransport transport;
    private final String baseUrl;
    private final String userAgent;
    private final Clock clock;
    private final long minIntervalMs;
    private final int maxRetries;
    private long lastRequestAt = Long.MIN_VALUE / 2;
    private int requestCount;

    public ScryfallClient(HttpTransport transport, String userAgent) {
        this(transport, DEFAULT_BASE_URL, userAgent, Clock.SYSTEM, SEARCH_INTERVAL_MS, 4);
    }

    public ScryfallClient(HttpTransport transport, String baseUrl, String userAgent, Clock clock,
                          long minIntervalMs, int maxRetries) {
        if (userAgent == null || userAgent.trim().isEmpty()) {
            throw new IllegalArgumentException("o Scryfall exige User-Agent com nome e versão do app");
        }
        this.transport = transport;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.userAgent = userAgent;
        this.clock = clock;
        this.minIntervalMs = minIntervalMs;
        this.maxRetries = maxRetries;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getRequestCount() {
        return requestCount;
    }

    /**
     * GET com limite de taxa e novas tentativas. Devolve a resposta 2xx ou 404
     * (o Scryfall responde 404 quando uma busca não encontra nada).
     */
    public HttpTransport.Response get(String urlOrPath, BooleanSupplier cancelled) throws IOException {
        String url = urlOrPath.startsWith("http") ? urlOrPath : baseUrl + urlOrPath;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", userAgent);
        headers.put("Accept", "application/json;q=0.9,*/*;q=0.8");
        long backoff = 1000;
        for (int attempt = 0; ; attempt++) {
            checkCancelled(cancelled);
            throttle();
            HttpTransport.Response r;
            try {
                requestCount++;
                r = transport.get(url, headers);
            } catch (IOException e) {
                if (attempt >= maxRetries) {
                    throw e;
                }
                pause(backoff, cancelled);
                backoff *= 2;
                continue;
            }
            int s = r.status();
            if ((s >= 200 && s < 300) || s == 404) {
                return r;
            }
            if ((s == 429 || s >= 500) && attempt < maxRetries) {
                long wait = backoff;
                String retryAfter = r.header("Retry-After");
                if (retryAfter != null) {
                    try {
                        wait = Math.max(wait, Long.parseLong(retryAfter.trim()) * 1000L);
                    } catch (NumberFormatException ignored) {
                        // formato de data: usa o recuo exponencial
                    }
                }
                pause(wait, cancelled);
                backoff *= 2;
                continue;
            }
            throw new ScryfallException(s, abbreviate(r.body()));
        }
    }

    private void throttle() throws IOException {
        long wait = lastRequestAt + minIntervalMs - clock.nowMillis();
        if (wait > 0) {
            try {
                clock.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancelledException();
            }
        }
        lastRequestAt = clock.nowMillis();
    }

    private void pause(long millis, BooleanSupplier cancelled) throws IOException {
        long end = clock.nowMillis() + millis;
        while (clock.nowMillis() < end) {
            checkCancelled(cancelled);
            try {
                clock.sleep(Math.min(250, end - clock.nowMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancelledException();
            }
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws CancelledException {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new CancelledException();
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
