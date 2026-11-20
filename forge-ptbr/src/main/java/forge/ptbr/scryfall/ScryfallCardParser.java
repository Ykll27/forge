package forge.ptbr.scryfall;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converte respostas JSON do Scryfall em impressões em português por face.
 * Usa o modelo de árvore do Gson (sem reflexão), o que dispensa regras de ProGuard no Android.
 */
public final class ScryfallCardParser {
    private ScryfallCardParser() {
    }

    /** Texto impresso em português de uma face. */
    public static final class Printing {
        public final String englishName;
        public final String name;
        public final String type;
        public final String text;
        public final String set;
        public final String collectorNumber;
        public final String releasedAt;
        public final String scryfallId;

        public Printing(String englishName, String name, String type, String text, String set,
                        String collectorNumber, String releasedAt, String scryfallId) {
            this.englishName = englishName;
            this.name = name == null ? "" : name;
            this.type = type == null ? "" : type;
            this.text = text == null ? "" : text;
            this.set = set == null ? "" : set;
            this.collectorNumber = collectorNumber == null ? "" : collectorNumber;
            this.releasedAt = releasedAt == null ? "" : releasedAt;
            this.scryfallId = scryfallId == null ? "" : scryfallId;
        }

        public boolean isEmpty() {
            return name.isEmpty() && type.isEmpty() && text.isEmpty();
        }
    }

    /** Uma página de /cards/search. */
    public static final class Page {
        public final List<Printing> printings;
        public final boolean hasMore;
        public final String nextPage;
        public final int totalCards;

        Page(List<Printing> printings, boolean hasMore, String nextPage, int totalCards) {
            this.printings = printings;
            this.hasMore = hasMore;
            this.nextPage = nextPage;
            this.totalCards = totalCards;
        }

        static Page empty() {
            return new Page(Collections.emptyList(), false, null, 0);
        }
    }

    /** Lê uma página; uma resposta de erro "not_found" vira página vazia. */
    public static Page parsePage(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("resposta inesperada do Scryfall");
        }
        JsonObject obj = root.getAsJsonObject();
        String object = str(obj, "object");
        if ("error".equals(object)) {
            if ("not_found".equals(str(obj, "code"))) {
                return Page.empty();
            }
            throw new IllegalArgumentException("erro do Scryfall: " + str(obj, "details"));
        }
        List<Printing> out = new ArrayList<>();
        JsonArray data = obj.has("data") && obj.get("data").isJsonArray() ? obj.getAsJsonArray("data") : new JsonArray();
        for (JsonElement el : data) {
            if (el.isJsonObject()) {
                parseCard(el.getAsJsonObject(), out);
            }
        }
        boolean hasMore = obj.has("has_more") && obj.get("has_more").getAsBoolean();
        String next = str(obj, "next_page");
        int total = obj.has("total_cards") ? obj.get("total_cards").getAsInt() : out.size();
        return new Page(out, hasMore && next != null, next, total);
    }

    /** Uma carta → uma impressão por face (cartas divididas, de duas faces, aventuras...). */
    static void parseCard(JsonObject card, List<Printing> out) {
        if (!"pt".equals(str(card, "lang"))) {
            return; // a busca pede lang:pt, mas não confiamos cegamente
        }
        String set = str(card, "set");
        String cn = str(card, "collector_number");
        String date = str(card, "released_at");
        String id = str(card, "id");
        JsonArray faces = card.has("card_faces") && card.get("card_faces").isJsonArray()
                ? card.getAsJsonArray("card_faces") : null;
        if (faces != null && faces.size() > 1) {
            for (JsonElement f : faces) {
                JsonObject face = f.getAsJsonObject();
                String en = str(face, "name");
                if (en == null) {
                    continue;
                }
                Printing p = new Printing(en, str(face, "printed_name"), str(face, "printed_type_line"),
                        str(face, "printed_text"), set, cn, date, id);
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
            return;
        }
        String en = str(card, "name");
        if (en == null) {
            return;
        }
        Printing p = new Printing(en, str(card, "printed_name"), str(card, "printed_type_line"),
                str(card, "printed_text"), set, cn, date, id);
        if (!p.isEmpty()) {
            out.add(p);
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
