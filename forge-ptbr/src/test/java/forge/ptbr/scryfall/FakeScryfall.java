package forge.ptbr.scryfall;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Servidor HTTP local que imita /cards/search do Scryfall, com paginação e falhas programáveis. */
final class FakeScryfall implements AutoCloseable {

    final HttpServer server;
    final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    final List<String> userAgents = Collections.synchronizedList(new ArrayList<>());
    final List<String> accepts = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger throttleNext = new AtomicInteger();
    volatile String sinceSeen;

    static String card(String en, String pt, String type, String text, String date) {
        return "{\"object\":\"card\",\"lang\":\"pt\",\"name\":" + q(en) + ",\"printed_name\":" + q(pt)
                + ",\"printed_type_line\":" + q(type) + ",\"printed_text\":" + q(text)
                + ",\"set\":\"tst\",\"collector_number\":\"1\",\"released_at\":\"" + date + "\",\"id\":\"" + en.hashCode() + "\"}";
    }

    static String q(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    FakeScryfall() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cards/search", this::search);
        server.start();
    }

    String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void search(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getRawQuery();
        requests.add(query);
        userAgents.add(ex.getRequestHeaders().getFirst("User-Agent"));
        accepts.add(ex.getRequestHeaders().getFirst("Accept"));
        if (throttleNext.getAndDecrement() > 0) {
            ex.getResponseHeaders().add("Retry-After", "1");
            send(ex, 429, "{\"object\":\"error\",\"code\":\"rate_limited\",\"status\":429}");
            return;
        }
        String q = URLDecoder.decode(query.replaceAll(".*q=([^&]*).*", "$1"), "UTF-8");
        int page = query.contains("page=2") ? 2 : 1;
        if (q.contains("date>=")) {
            sinceSeen = q.substring(q.indexOf("date>=") + 6);
            if (sinceSeen.compareTo("2024-06-14") > 0) {
                send(ex, 404, "{\"object\":\"error\",\"code\":\"not_found\",\"status\":404,\"details\":\"Your query didn't match any cards.\"}");
                return;
            }
            send(ex, 200, "{\"object\":\"list\",\"total_cards\":1,\"has_more\":false,\"data\":["
                    + card("Llanowar Elves", "Elfos de Llanowar", "Criatura — Elfo Druida", "{T}: Adicione {G}.", "2024-06-14") + "]}");
            return;
        }
        if (page == 1) {
            send(ex, 200, "{\"object\":\"list\",\"total_cards\":4,\"has_more\":true,\"next_page\":\"" + base()
                    + "/cards/search?" + query + "&page=2\",\"data\":["
                    + card("Llanowar Elves", "Elfos de Llanowar", "Criatura — Elfo Druida", "{T}: Adicione {G} à sua reserva de mana.", "2019-07-12") + ","
                    + "{\"object\":\"card\",\"lang\":\"pt\",\"name\":\"Fire // Ice\",\"released_at\":\"2018-01-19\",\"set\":\"tst\",\"collector_number\":\"2\",\"card_faces\":["
                    + "{\"name\":\"Fire\",\"printed_name\":\"Fogo\",\"printed_type_line\":\"Mágica Instantânea\",\"printed_text\":\"Fogo causa 2 pontos de dano dividido como você escolher entre um ou dois alvos.\"},"
                    + "{\"name\":\"Ice\",\"printed_name\":\"Gelo\",\"printed_type_line\":\"Mágica Instantânea\",\"printed_text\":\"Vire a permanente alvo.\\nCompre um card.\"}]}"
                    + "]}");
        } else {
            send(ex, 200, "{\"object\":\"list\",\"total_cards\":4,\"has_more\":false,\"data\":["
                    // impressão mais antiga do mesmo card: não pode substituir a de 2019
                    + card("Llanowar Elves", "Elfos de Llanowar", "Criatura — Elfo", "{T}: Adicione {G} à sua reserva de mana. (antigo)", "1997-01-01") + ","
                    + card("Counterspell", "Contramágica", "Mágica Instantânea", "Anule a mágica alvo.", "2021-01-01") + ","
                    + "{\"object\":\"card\",\"lang\":\"en\",\"name\":\"Intruso\",\"printed_name\":\"Intruder\",\"released_at\":\"2021-01-01\"}"
                    + "]}");
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
