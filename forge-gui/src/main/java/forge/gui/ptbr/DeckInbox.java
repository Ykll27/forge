package forge.gui.ptbr;

import forge.deck.Deck;
import forge.deck.DeckTextImporter;
import forge.model.FModel;
import forge.util.storage.IStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Caixa de entrada de decks enviados ao jogo: arquivos compartilhados por outros apps
 * ("Compartilhar"/"Abrir com"), escolhidos no seletor de arquivos ou colados como texto.
 *
 * <p>O envio pode chegar antes de o jogo terminar de carregar (o Android abre o app pelo arquivo),
 * por isso os decks ficam na fila até a interface registrar o {@link Listener}.</p>
 */
public final class DeckInbox {

    /** Um deck recebido, ainda como texto. */
    public static final class Received {
        public final String fileName;
        public final String text;

        public Received(String fileName, String text) {
            this.fileName = fileName;
            this.text = text;
        }
    }

    /** Avisado (em qualquer thread) quando há decks na fila. */
    public interface Listener {
        void onDecksReceived();
    }

    private static final Queue<Received> PENDING = new ConcurrentLinkedQueue<>();
    private static volatile Listener listener;

    private DeckInbox() {
    }

    /** Recebe um deck. Textos vazios são ignorados; textos enormes são cortados. */
    public static void offer(String fileName, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        String body = text.length() > DeckTextImporter.MAX_CHARS ? text.substring(0, DeckTextImporter.MAX_CHARS) : text;
        PENDING.add(new Received(fileName, body));
        Listener l = listener;
        if (l != null) {
            l.onDecksReceived();
        }
    }

    /** Registra quem mostra os decks recebidos; se já houver algo na fila, avisa na hora. */
    public static void setListener(Listener l) {
        listener = l;
        if (l != null && !PENDING.isEmpty()) {
            l.onDecksReceived();
        }
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /** Retira tudo o que está na fila. */
    public static List<Received> drain() {
        List<Received> out = new ArrayList<>();
        Received r;
        while ((r = PENDING.poll()) != null) {
            out.add(r);
        }
        return out;
    }

    /** Reconhece as cartas (chamar fora da thread da interface). */
    public static List<DeckTextImporter.Result> parse(List<Received> items) {
        List<DeckTextImporter.Result> out = new ArrayList<>();
        for (Received r : items) {
            out.add(DeckTextImporter.parse(r.text, r.fileName));
        }
        return out;
    }

    /** Nome com que o deck será salvo (nunca sobrescreve um deck existente). */
    public static String nameForSaving(Deck deck) {
        IStorage<Deck> storage = FModel.getDecks().getConstructed();
        return DeckTextImporter.uniqueName(deck.getName(), storage::contains);
    }

    /** Salva em "Meus decks" (construídos) e devolve o deck salvo. */
    public static Deck saveConstructed(Deck deck) {
        IStorage<Deck> storage = FModel.getDecks().getConstructed();
        String name = DeckTextImporter.uniqueName(deck.getName(), storage::contains);
        Deck toSave = name.equals(deck.getName()) ? deck : (Deck) deck.copyTo(name);
        storage.add(toSave);
        return toSave;
    }
}
