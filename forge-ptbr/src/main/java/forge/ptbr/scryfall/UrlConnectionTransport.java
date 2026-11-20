package forge.ptbr.scryfall;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Transporte com {@link HttpURLConnection}: disponível no Android e no desktop, sem dependências.
 * (java.net.http.HttpClient não existe no Android.)
 */
public final class UrlConnectionTransport implements HttpTransport {
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public UrlConnectionTransport() {
        this(15_000, 30_000);
    }

    public UrlConnectionTransport(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public Response get(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestProperty("Accept-Encoding", "gzip");
            for (Map.Entry<String, String> h : headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
            int status = conn.getResponseCode();
            Map<String, String> responseHeaders = new HashMap<>();
            for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                if (e.getKey() != null && !e.getValue().isEmpty()) {
                    responseHeaders.put(e.getKey(), e.getValue().get(0));
                }
            }
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = "";
            if (in != null) {
                if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                    in = new GZIPInputStream(in);
                }
                try (InputStream stream = in) {
                    body = readAll(stream);
                }
            }
            return new Response(status, responseHeaders, body);
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
