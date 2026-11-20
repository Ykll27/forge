package forge.deck;

import forge.Forge;
import forge.game.GameType;
import forge.gui.FThreads;
import forge.gui.ptbr.DeckInbox;
import forge.model.FModel;
import forge.toolbox.FOptionPane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mostra os decks enviados ao jogo (compartilhados por outro app, escolhidos no seletor de
 * arquivos ou colados) e salva os confirmados em "Meus decks", um de cada vez, sem nunca
 * sobrescrever um deck existente.
 */
public final class ReceivedDecks {

    private static boolean showing;
    private static Runnable onSaved;

    private ReceivedDecks() {
    }

    /** Começa a escutar a caixa de entrada (chamado quando a tela inicial está pronta). */
    public static void listen() {
        DeckInbox.setListener(() -> FThreads.invokeInEdtLater(ReceivedDecks::showPending));
    }

    /** Ação a executar depois de salvar (ex.: atualizar a lista de decks visível). */
    public static void setOnSaved(Runnable action) {
        onSaved = action;
    }

    /** Lê o que estiver na fila e apresenta. Chamar na thread da interface. */
    public static void showPending() {
        if (showing || !DeckInbox.hasPending()) {
            return;
        }
        showing = true;
        final List<DeckInbox.Received> items = DeckInbox.drain();
        FThreads.invokeInBackgroundThread(() -> {
            List<DeckTextImporter.Result> parsed;
            try {
                parsed = DeckInbox.parse(items);
            } catch (RuntimeException e) {
                parsed = Collections.emptyList();
                final String error = e.getMessage();
                FThreads.invokeInEdtLater(() -> FOptionPane.showErrorDialog(text("lblDeckImportFailed",
                        "Could not read the deck: ") + error));
            }
            final List<DeckTextImporter.Result> results = parsed;
            FThreads.invokeInEdtLater(() -> present(results, 0, new ArrayList<>()));
        });
    }

    private static void present(List<DeckTextImporter.Result> results, int index, List<String> savedNames) {
        if (index >= results.size()) {
            showing = false;
            if (!savedNames.isEmpty() && onSaved != null) {
                onSaved.run();
            }
            showPending(); // pode ter chegado mais coisa enquanto o jogador decidia
            return;
        }
        DeckTextImporter.Result r = results.get(index);
        String title = text("lblDeckReceived", "Deck received")
                + (results.size() > 1 ? " (" + (index + 1) + "/" + results.size() + ")" : "");
        if (r.isEmpty()) {
            FOptionPane.showMessageDialog(text("lblDeckNoCardsFound", "No cards were recognised in \"%s\".")
                            .replace("%s", r.getName()), title, FOptionPane.WARNING_ICON,
                    result -> present(results, index + 1, savedNames));
            return;
        }

        String saveAs = DeckInbox.nameForSaving(r.getDeck());
        StringBuilder message = new StringBuilder();
        message.append(r.getName()).append('\n');
        message.append(text("lblDeckCardCount", "%s cards").replace("%s", Integer.toString(r.getCardCount())));
        for (Map.Entry<DeckSection, Integer> e : r.getCardsPerSection().entrySet()) {
            message.append("\n  ").append(sectionName(e.getKey())).append(": ").append(e.getValue());
        }
        if (!saveAs.equals(r.getName())) {
            message.append("\n\n").append(text("lblDeckWillBeSavedAs",
                    "A deck with this name already exists; it will be saved as \"%s\".").replace("%s", saveAs));
        }
        List<String> missing = r.getUnrecognised();
        if (!missing.isEmpty()) {
            message.append("\n\n").append(text("lblDeckNotRecognised", "Not recognised (left out):"));
            int shown = Math.min(missing.size(), 8);
            for (int i = 0; i < shown; i++) {
                message.append("\n  • ").append(missing.get(i));
            }
            if (missing.size() > shown) {
                message.append("\n  … +").append(missing.size() - shown);
            }
        }
        List<String> options = new ArrayList<>();
        options.add(text("lblDeckSave", "Save"));
        options.add(text("lblDeckSaveAndOpen", "Save and open"));
        options.add(text("lblDeckDiscard", "Discard"));
        FOptionPane.showOptionDialog(message.toString(), title,
                missing.isEmpty() ? FOptionPane.INFORMATION_ICON : FOptionPane.WARNING_ICON, options, 0, choice -> {
                    if (choice != null && (choice == 0 || choice == 1)) {
                        Deck saved = DeckInbox.saveConstructed(r.getDeck());
                        savedNames.add(saved.getName());
                        if (choice == 1) {
                            Forge.openScreen(new FDeckEditor(FDeckEditor.EditorConfigConstructed,
                                    new DeckProxy(saved, "Constructed", GameType.Constructed, FModel.getDecks().getConstructed())));
                        }
                    }
                    present(results, index + 1, savedNames);
                });
    }

    private static String sectionName(DeckSection section) {
        switch (section) {
            case Main:
                return text("lblMainDeck", "Main");
            case Sideboard:
                return text("lblSideboard", "Sideboard");
            case Commander:
                return text("lblCommander", "Commander");
            default:
                return section.name();
        }
    }

    private static String text(String key, String fallback) {
        return Forge.getLocalizer().getMessageorUseDefault(key, fallback);
    }
}
