package forge.ptbr.scryfall;
import java.io.IOException;
import java.util.Map;

/** Transporte HTTP mínimo, substituível nos testes. */
public interface HttpTransport {
    public final class Response {
        private final int status;
        private final Map<String, String> headers;
        private final String body;

        public Response(int status, Map<String, String> headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public Map<String, String> headers() {
            return headers;
        }

        public String body() {
            return body;
        }

        public String header(String name) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
            return null;
        }
    }

    Response get(String url, Map<String, String> headers) throws IOException;
}
