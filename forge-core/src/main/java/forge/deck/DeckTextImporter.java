package forge.deck;

import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a deck received as text (a Forge .dck file, a .dec/MTGO/MTG Arena export or a plain
 * "4 Card Name" list, in English or with translated card names) into a {@link Deck}, without
 * opening the deck editor. Card recognition is {@link DeckRecognizer}'s; this class only adds
 * what a file shared from another app needs: the deck name from the file or its metadata
 * (accents allowed), Portuguese section headers and a report of what was not recognised.
 */
public final class DeckTextImporter {

    /** Largest text accepted (a deck list is a few KB; this guards against huge files). */
    public static final int MAX_CHARS = 512 * 1024;

    private static final Pattern NAME_LINE = Pattern.compile(
            "^\\s*(?://\\s*)?(?:name|nome|deck name|nome do deck|title|t[ií]tulo)\\s*[:=]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern METADATA_HEADER = Pattern.compile("^\\s*\\[\\s*metadata\\s*]\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTHER_METADATA = Pattern.compile(
            "^\\s*(?:description|descri[çc][ãa]o|author|autor|deck type|format|formato|tags?)\\s*[:=].*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Section headers in Portuguese ("Principal", "[Reserva]", "Sideboard:"...). */
    private static final Pattern PT_SECTION = Pattern.compile(
            "^\\s*[\\[/#]*\\s*(principal|deck principal|reserva|banco|comandante|comandantes|companheiro)\\s*[\\]:]*\\s*(?:\\(\\d+\\))?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private DeckTextImporter() {
    }

    /** What was imported and what was left out. */
    public static final class Result {
        private final Deck deck;
        private final boolean nameFromText;
        private final Map<DeckSection, Integer> cardsPerSection;
        private final List<String> unrecognised;

        Result(Deck deck, boolean nameFromText, Map<DeckSection, Integer> cardsPerSection, List<String> unrecognised) {
            this.deck = deck;
            this.nameFromText = nameFromText;
            this.cardsPerSection = Collections.unmodifiableMap(cardsPerSection);
            this.unrecognised = Collections.unmodifiableList(unrecognised);
        }

        public Deck getDeck() {
            return deck;
        }

        public String getName() {
            return deck.getName();
        }

        /** True when the name came from the text itself (metadata), not from the file name. */
        public boolean isNameFromText() {
            return nameFromText;
        }

        public Map<DeckSection, Integer> getCardsPerSection() {
            return cardsPerSection;
        }

        public int getCardCount() {
            int n = 0;
            for (int c : cardsPerSection.values()) {
                n += c;
            }
            return n;
        }

        /** Lines that looked like cards but could not be matched (with their quantity). */
        public List<String> getUnrecognised() {
            return unrecognised;
        }

        public boolean isEmpty() {
            return getCardCount() == 0;
        }
    }

    /**
     * @param text         the received text (whole file)
     * @param fallbackName name to use when the text has none (usually the file name)
     */
    public static Result parse(String text, String fallbackName) {
        String body = text == null ? "" : text;
        if (body.length() > MAX_CHARS) {
            body = body.substring(0, MAX_CHARS);
        }
        if (!body.isEmpty() && body.charAt(0) == '﻿') {
            body = body.substring(1);
        }
        String[] rawLines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        String name = null;
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            Matcher m = NAME_LINE.matcher(line);
            if (m.matches()) {
                if (name == null) {
                    name = m.group(1).trim();
                }
                continue;
            }
            if (METADATA_HEADER.matcher(line).matches() || OTHER_METADATA.matcher(line).matches()) {
                continue;
            }
            lines.add(translateSectionHeader(line));
        }

        DeckRecognizer recognizer = new DeckRecognizer();
        List<DeckRecognizer.Token> tokens = recognizer.parseCardList(lines.toArray(new String[0]));

        boolean nameFromText = name != null && !name.isEmpty();
        Deck deck = new Deck(nameFromText ? sanitize(name) : cleanName(fallbackName));
        Map<DeckSection, Integer> perSection = new EnumMap<>(DeckSection.class);
        List<String> unrecognised = new ArrayList<>();
        for (DeckRecognizer.Token token : tokens) {
            DeckRecognizer.TokenType type = token.getType();
            if (token.isCardToken() && token.getCard() != null) {
                DeckSection section = token.getTokenSection() == null ? DeckSection.Main : token.getTokenSection();
                PaperCard card = token.getCard();
                deck.getOrCreate(section).add(card, token.getQuantity());
                perSection.merge(section, token.getQuantity(), Integer::sum);
            } else if (type == DeckRecognizer.TokenType.UNKNOWN_CARD || type == DeckRecognizer.TokenType.UNSUPPORTED_CARD) {
                unrecognised.add(token.getText() + (token.getQuantity() > 0 ? " (" + token.getQuantity() + ")" : ""));
            } else if (type == DeckRecognizer.TokenType.UNKNOWN_TEXT && looksLikeCardLine(token.getText())) {
                unrecognised.add(token.getText().trim());
            }
        }
        return new Result(deck, nameFromText, perSection, unrecognised);
    }

    /** "Principal" → "Main", "[Reserva]" → "Sideboard"... (the recogniser knows the English names). */
    static String translateSectionHeader(String line) {
        Matcher m = PT_SECTION.matcher(line);
        if (!m.matches()) {
            return line;
        }
        String w = m.group(1).toLowerCase(new Locale("pt", "BR"));
        if (w.startsWith("comandante")) {
            return "Commander";
        }
        if (w.equals("reserva") || w.equals("banco") || w.equals("companheiro")) {
            return "Sideboard";
        }
        return "Main";
    }

    /** "4 Something" or "4x Something": a card line the recogniser could not place. */
    private static boolean looksLikeCardLine(String text) {
        return text != null && text.trim().matches("^\\d{1,2}x?\\s+\\S.*$");
    }

    /** File name without folders and extension, or a default. */
    public static String cleanName(String fileName) {
        if (fileName == null) {
            return "Deck importado";
        }
        String n = fileName.trim();
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        return sanitize(n.replace('_', ' '));
    }

    /** Collapses spaces and replaces characters that cannot be part of a deck file name. */
    public static String sanitize(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").trim();
        return n.isEmpty() ? "Deck importado" : n;
    }

    /** {@code base}, or "base (2)", "base (3)"... — the first name not taken. */
    public static String uniqueName(String base, Predicate<String> taken) {
        String name = sanitize(base);
        if (!taken.test(name)) {
            return name;
        }
        for (int i = 2; ; i++) {
            String candidate = name + " (" + i + ")";
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
    }
}
