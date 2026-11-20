package forge.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardTranslation {

    private static Map <String, String> translatednames;
    private static Map <String, String> translatedtypes;
    private static Map <String, String> translatedoracles;
    private static Map <String, List <Pair <String, String> > > oracleMappings;
    private static Map <String, Map <String, String> > translatedCaches;
    private static Map <String, String> sharedCache;
    private static Map <String, String> translatedEffectNames;
    private static Map <String, String> translatedTokenNames;
    private static final List <String> knownEffectNames = Arrays.asList("The Ring", "The Monarch", "The Initiative", "City's Blessing", "Keyword Effects");
    private static final Pattern TRANSLATION_INVARIANT = Pattern.compile("\\{[^}]{1,6}\\}|[+-]\\d+/[+-]\\d+");
    private static String languageSelected = "en-US";

    /**
     * Where a displayed translation came from. Later constants take priority over earlier ones,
     * field by field: an automatic translation only shows where no official text exists, and a
     * player's own correction wins over everything.
     */
    public enum Source {
        /** Machine translation (display only, never used by the rules engine). */
        AUTOMATIC,
        /** Official printed text shipped with Forge (res/languages/cardnames-*.txt). */
        OFFICIAL_BUNDLED,
        /** Official printed text downloaded later (e.g. from Scryfall) into the user directory. */
        OFFICIAL_DOWNLOADED,
        /** Correction made by the player. */
        USER
    }

    /**
     * Supplies a translation on demand for a card that has none on file (a custom card, or a card
     * added after the translation files were generated). Called at most once per card while ready.
     */
    public interface AutomaticTranslationProvider {
        /** False while the provider is still loading; misses are not remembered meanwhile. */
        boolean isReady();

        /** @return {translated type, translated text with literal "\\n" between lines}, either may be empty; or null */
        String[] translate(String translationKey);
    }

    private static Map <String, Source> nameSources;
    private static Map <String, Source> typeSources;
    private static Map <String, Source> textSources;
    private static String languagesDirectoryLoaded;
    private static String userDirectoryLoaded;
    private static boolean automaticLoaded = true;
    private static volatile AutomaticTranslationProvider automaticProvider;
    private static Set <String> providerMisses = ConcurrentHashMap.newKeySet();
    /** Translated name (normalized) -> English name; built on first use, cleared on reload. */
    private static volatile Map <String, String> englishByTranslatedName;

    private static void readTranslationFile(String language, String languagesDirectory) {
        readTranslationFile(languagesDirectory + "cardnames-" + language + ".txt", Source.OFFICIAL_BUNDLED, true);
    }

    /**
     * Reads one translation file into the current maps. Only non-empty fields are taken, so a
     * file with a higher priority source can fill in or replace single fields of a card without
     * blanking the others.
     */
    private static void readTranslationFile(String path, Source source, boolean required) {
        if (!required && !Files.isRegularFile(Paths.get(path))) {
            return; // optional overlay that has not been created yet
        }
        try (LineReader translationFile = new LineReader(Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
            for (String line : translationFile.readLines()) {
                String[] matches = line.split("\\|");
                if (matches.length >= 2) {
                    if (matches[0].indexOf('$') > 0) {
                        //Functional variant, e.g. "Garbage Elemental $C"
                        String[] variantSplit = matches[0].split("\\s*\\$", 2);
                        if(variantSplit.length > 1) {
                            //Add the base name to the translated names.
                            if (!matches[1].isEmpty()) {
                                translatednames.put(variantSplit[0], matches[1]);
                            }
                            matches[0] = variantSplit[0] + " $" + variantSplit[1]; //Standardize storage.
                        }
                    }
                    if (!matches[1].isEmpty()) {
                        translatednames.put(matches[0], matches[1]);
                        nameSources.put(matches[0], source);
                    }
                }
                if (matches.length >= 3 && !matches[2].isEmpty()) {
                    translatedtypes.put(matches[0], matches[2]);
                    typeSources.put(matches[0], source);
                }
                if (matches.length >= 4 && !matches[3].isEmpty()) {
                    translatedoracles.put(matches[0], normalizeOracle(matches[3]));
                    textSources.put(matches[0], source);
                }
            }
        } catch (IOException e) {
            if (required && !"en-US".equalsIgnoreCase(languageSelected))
                System.err.println("Error reading translation file: " + path);
        }
    }

    private static String normalizeOracle(String toracle) {
        // Workaround to remove additional //Level_2// and //Level_3// lines from non-English Class cards
        toracle = toracle.replace("//Level_2//\\n", "").replace("//Level_3//\\n", "");
        // Workaround for roll dice cards
        return toracle.replace("\\n", "\r\n\r\n").replace("VERT", "|");
    }

    /** Asks the automatic provider, once, for a card that has no translated type or text on file. */
    private static void consultProvider(String key) {
        AutomaticTranslationProvider provider = automaticProvider;
        if (provider == null || key == null || providerMisses.contains(key) || !provider.isReady()) {
            return;
        }
        String[] result;
        try {
            result = provider.translate(key);
        } catch (RuntimeException e) {
            result = null; // a display helper must never break the game
        }
        providerMisses.add(key);
        if (result == null) {
            return;
        }
        if (result.length > 0 && result[0] != null && !result[0].isEmpty() && !translatedtypes.containsKey(key)) {
            translatedtypes.put(key, result[0]);
            typeSources.put(key, Source.AUTOMATIC);
        }
        if (result.length > 1 && result[1] != null && !result[1].isEmpty() && !translatedoracles.containsKey(key)) {
            translatedoracles.put(key, normalizeOracle(result[1]));
            textSources.put(key, Source.AUTOMATIC);
        }
    }

    public static String getTranslatedName(String name) {
        if (needsTranslation()) {
            if (name.contains(" // ")) {
                int splitIndex = name.indexOf(" // ");
                String leftname = name.substring(0, splitIndex);
                String rightname = name.substring(splitIndex + 4);
                return translatednames.getOrDefault(leftname, leftname) + " // " + translatednames.getOrDefault(rightname, rightname);
            }
            try {
                if (name.endsWith(" Token")) {
                    return translateTokenName(name);
                } else if (name.startsWith("Emblem — ") || name.contains("'s Effect") || name.contains("'s Boon")) {
                    return translateEffectNames(name);
                } else if (knownEffectNames.contains(name)) {
                    return translateKnownEffectNames(name);
                } else {
                    String tname = translatednames.get(name);
                    return (tname == null || tname.isEmpty()) ? name : tname;
                }
            } catch (Exception e) {
                return name;
            }
        }
        return name;
    }

    public static String getTranslatedName(ITranslatable card) {
        return getTranslatedName(card.getUntranslatedName());
    }

    private static String translateTokenName(String name) {
        if (translatedTokenNames == null)
            translatedTokenNames = new HashMap<>();
        String ttype = translatedTokenNames.get(name);
        if (ttype == null) {
            String sub = name.replace(" Token", "");
            ttype = Localizer.getInstance().getMessageorUseDefault("lbl" + sub, "");
            if (ttype == null || ttype.isEmpty()) {
                ttype = name;
            } else {
                ttype = ttype  + " " + Localizer.getInstance().getMessage("lblToken");
            }
            translatedTokenNames.put(name, ttype);
            return ttype;
        } else {
            return ttype;
        }
    }

    private static String translateKnownEffectNames(String name) {
        if (translatedEffectNames == null)
            translatedEffectNames = new HashMap<>();
        String fname = translatedEffectNames.get(name);
        if (fname == null) {
            switch (name) {
                case "The Ring":
                    fname = Localizer.getInstance().getMessage("lblTheRing");
                    translatedEffectNames.put(name, fname);
                    return fname;
                case "The Monarch":
                    fname = Localizer.getInstance().getMessage("lblTheMonarch");
                    translatedEffectNames.put(name, fname);
                    return fname;
                case "The Initiative":
                    fname = Localizer.getInstance().getMessage("lblTheInitiative");
                    translatedEffectNames.put(name, fname);
                    return fname;
                case "City's Blessing":
                    fname = Localizer.getInstance().getMessage("lblCityBlessing");
                    translatedEffectNames.put(name, fname);
                    return fname;
                case "Keyword Effects":
                    fname = Localizer.getInstance().getMessage("lblKeywordEffects");
                    translatedEffectNames.put(name, fname);
                    return fname;
                default:
                    return name;
            }
        } else {
            return fname;
        }
    }

    private static String translateEffectNames(String name) {
        if (translatedEffectNames == null)
            translatedEffectNames = new HashMap<>();
        String fname = translatedEffectNames.get(name);
        if (fname == null) {
            String finalname = name.replaceAll("\\([^()]*\\)", "");
            if (finalname.contains(" 's Effect")) {
                finalname = finalname.replace( " 's Effect", "");
                fname = translatednames.get(finalname);
                if (fname == null || fname.isEmpty())
                    fname = finalname;
                else {
                    fname = fname + " " + Localizer.getInstance().getMessage("lblEffect");
                }
                translatedEffectNames.put(name, fname);
                return fname;
            } else if (finalname.contains("'s Effect")) {
                finalname = finalname.replace( "'s Effect", "");
                fname = translatednames.get(finalname);
                if (fname == null || fname.isEmpty())
                    fname = finalname;
                else {
                    fname = fname + " " + Localizer.getInstance().getMessage("lblEffect");
                }
                translatedEffectNames.put(name, fname);
                return fname;
            } else if (finalname.contains(" 's Boon")) {
                finalname = finalname.replace( " 's Boon", "");
                fname = translatednames.get(finalname);
                if (fname == null || fname.isEmpty())
                    fname = finalname;
                else {
                    fname = fname + " " + Localizer.getInstance().getMessage("lblBoon");
                }
                translatedEffectNames.put(name, fname);
                return fname;
            } else if (finalname.contains("'s Boon")) {
                finalname = finalname.replace( "'s Boon", "");
                fname = translatednames.get(finalname);
                if (fname == null || fname.isEmpty())
                    fname = finalname;
                else {
                    fname = fname + " " + Localizer.getInstance().getMessage("lblBoon");
                }
                translatedEffectNames.put(name, fname);
                return fname;
            } else if (finalname.startsWith("Emblem — ")) {
                String []s = finalname.split(" — ");
                try {
                    fname = translatednames.get(s[1].endsWith(" ") ? s[1].substring(0, s[1].lastIndexOf(" ")) : s[1]);
                    if (fname == null || fname.isEmpty())
                        fname = finalname;
                    else {
                        fname = fname + " " + Localizer.getInstance().getMessage("lblEmblem");
                    }
                    translatedEffectNames.put(name, fname);
                    return fname;
                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }
            return name;
        } else {
            return fname;
        }
    }

    public static String getTranslatedType(String name, String originaltype) {
        if (needsTranslation()) {
            String ttype = translatedtypes.get(name);
            if (ttype == null) {
                consultProvider(name);
                ttype = translatedtypes.get(name);
            }
            return ttype == null ? originaltype : ttype;
        }

        return originaltype;
    }

    public static String getTranslatedType(ITranslatable item) {
        if (!needsTranslation())
            return item.getUntranslatedType();
        String key = item.getTranslationKey();
        if (!translatedtypes.containsKey(key)) {
            consultProvider(key);
        }
        return translatedtypes.getOrDefault(key, item.getUntranslatedType());
    }

    public static String getTranslatedOracle(String name) {
        if (needsTranslation()) {
            String toracle = translatedoracles.get(name);
            if (toracle == null) {
                consultProvider(name);
                toracle = translatedoracles.get(name);
            }
            return toracle == null ? "" : toracle;
        }

        return "";
    }

    public static String getTranslatedOracle(ITranslatable card) {
        if(!needsTranslation())
            return ""; //card.getUntranslatedOracle();
        //Fallbacks and english versions of oracle texts are handled elsewhere.
        return getTranslatedOracle(card.getTranslationKey());
    }

    /** Where the displayed rules text of a card comes from, or null when it is shown in English. */
    public static Source getTextSource(String translationKey) {
        if (!needsTranslation() || translationKey == null) {
            return null;
        }
        if (!translatedoracles.containsKey(translationKey)) {
            consultProvider(translationKey);
        }
        return textSources.get(translationKey);
    }

    /** Where the displayed name of a card comes from, or null when it is shown in English. */
    public static Source getNameSource(String translationKey) {
        if (!needsTranslation() || translationKey == null) {
            return null;
        }
        return nameSources.get(translationKey);
    }

    /**
     * Reverse lookup: the English card name for a translated one, ignoring case, accents and
     * curly quotes ("raio", "Raio" -> "Lightning Bolt"). Returns null when no translation is
     * loaded or the name is unknown. Used by deck import so lists can be written in the
     * player's language.
     */
    public static String getEnglishName(String translatedName) {
        if (!needsTranslation() || translatedName == null || translatedName.trim().isEmpty()) {
            return null;
        }
        Map<String, String> index = englishByTranslatedName;
        if (index == null) {
            index = new HashMap<>();
            for (Map.Entry<String, String> e : translatednames.entrySet()) {
                if (e.getKey().contains(" $") || e.getValue().isEmpty()) {
                    continue; // functional variants share the base name
                }
                // first one wins: an ambiguous translated name keeps its first English card
                index.putIfAbsent(normalizeName(e.getValue()), e.getKey());
            }
            englishByTranslatedName = index;
        }
        return index.get(normalizeName(translatedName));
    }

    private static String normalizeName(String name) {
        String n = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u2019', '\'').replace('\u2018', '\'');
        return n.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    /** True while a language other than English is active. */
    public static boolean isTranslationActive() {
        return needsTranslation();
    }

    /**
     * Registers the provider asked for cards without a translation on file. Passing null removes it.
     * Clears the remembered misses so cards skipped before get another chance.
     */
    public static void setAutomaticTranslationProvider(AutomaticTranslationProvider provider) {
        automaticProvider = provider;
        providerMisses = ConcurrentHashMap.newKeySet();
    }

    public static HashMap<String, String> getTranslationTexts(ITranslatable card) {
        return getTranslationTexts(card, null);
    }

    public static HashMap<String, String> getTranslationTexts(ITranslatable cardMain, ITranslatable cardOther) {
        if(!needsTranslation()) return null;
        HashMap<String, String> translations = new HashMap<>();
        translations.put("name", getTranslatedName(cardMain));
        translations.put("oracle", getTranslatedOracle(cardMain));
        if(cardOther == null) {
            translations.put("altname", "");
            translations.put("altoracle", "");
        }
        else {
            translations.put("altname", getTranslatedName(cardOther));
            translations.put("altoracle", getTranslatedOracle(cardOther));
        }
        return translations;
    }

    public static String getLanguageSelected() {
        return languageSelected;
    }

    private static boolean needsTranslation() {
        return !languageSelected.equals("en-US");
    }

    public static void preloadTranslation(String language, String languagesDirectory) {
        preloadTranslation(language, languagesDirectory, null);
    }

    /**
     * Loads translations for a language. Besides the bundled {@code cardnames-<lang>.txt}, reads,
     * when present and in increasing priority:
     * <ol>
     *   <li>{@code cardnames-<lang>-automatica.txt} in the languages directory (shipped machine translations);</li>
     *   <li>{@code cardnames-<lang>-automatica.txt} in the user directory (regenerated on the device);</li>
     *   <li>the bundled official file;</li>
     *   <li>{@code cardnames-<lang>-oficial.txt} in the user directory (official text downloaded later);</li>
     *   <li>{@code cardnames-<lang>-usuario.txt} in the user directory (the player's corrections).</li>
     * </ol>
     * These are display strings only; card scripts and the rules engine are untouched.
     *
     * @param userDirectory writable directory with the downloaded/generated files, or null
     */
    public static void preloadTranslation(String language, String languagesDirectory, String userDirectory) {
        preloadTranslation(language, languagesDirectory, userDirectory, true);
    }

    /** As above; {@code includeAutomatic=false} skips the machine translation layers entirely. */
    public static void preloadTranslation(String language, String languagesDirectory, String userDirectory,
                                          boolean includeAutomatic) {
        languagesDirectoryLoaded = languagesDirectory;
        userDirectoryLoaded = userDirectory;
        automaticLoaded = includeAutomatic;

        if (!"en-US".equals(language)) {
            // read from the game thread and the EDT both: concurrent maps throughout. They are
            // filled here and only then published, so a reload never shows a half-read state.
            Map<String, String> names = new ConcurrentHashMap<>();
            Map<String, String> types = new ConcurrentHashMap<>();
            Map<String, String> oracles = new ConcurrentHashMap<>();
            Map<String, Source> nameSrc = new ConcurrentHashMap<>();
            Map<String, Source> typeSrc = new ConcurrentHashMap<>();
            Map<String, Source> textSrc = new ConcurrentHashMap<>();
            Map<String, String> prevNames = translatednames;
            Map<String, String> prevTypes = translatedtypes;
            Map<String, String> prevOracles = translatedoracles;
            Map<String, Source> prevNameSrc = nameSources;
            Map<String, Source> prevTypeSrc = typeSources;
            Map<String, Source> prevTextSrc = textSources;
            translatednames = names;
            translatedtypes = types;
            translatedoracles = oracles;
            nameSources = nameSrc;
            typeSources = typeSrc;
            textSources = textSrc;
            String languageBefore = languageSelected;
            languageSelected = language;
            try {
                if (includeAutomatic) {
                    readTranslationFile(languagesDirectory + "cardnames-" + language + "-automatica.txt", Source.AUTOMATIC, false);
                    if (userDirectory != null) {
                        readTranslationFile(userDirectory + "cardnames-" + language + "-automatica.txt", Source.AUTOMATIC, false);
                    }
                }
                readTranslationFile(languagesDirectory + "cardnames-" + language + ".txt", Source.OFFICIAL_BUNDLED, true);
                if (userDirectory != null) {
                    readTranslationFile(userDirectory + "cardnames-" + language + "-oficial.txt", Source.OFFICIAL_DOWNLOADED, false);
                    readTranslationFile(userDirectory + "cardnames-" + language + "-usuario.txt", Source.USER, false);
                }
            } catch (RuntimeException e) {
                // keep whatever was loaded before rather than leave the game without names
                translatednames = prevNames;
                translatedtypes = prevTypes;
                translatedoracles = prevOracles;
                nameSources = prevNameSrc;
                typeSources = prevTypeSrc;
                textSources = prevTextSrc;
                languageSelected = languageBefore;
                throw e;
            }
            oracleMappings = new ConcurrentHashMap<>();
            englishByTranslatedName = null;
            translatedCaches = new ConcurrentHashMap<>();
            sharedCache = new ConcurrentHashMap<>();
            providerMisses = ConcurrentHashMap.newKeySet();
        } else {
            languageSelected = language;
        }
    }

    /** Reloads the files of the current language, e.g. after a download or a correction. */
    public static void reload() {
        if (languagesDirectoryLoaded != null) {
            preloadTranslation(languageSelected, languagesDirectoryLoaded, userDirectoryLoaded, automaticLoaded);
        }
    }

    private static String replaceCardName(String language, String name, String toracle) {
        String nickName = language.equals("en-US") ? Lang.getEnglishInstance().getNickName(name) : Lang.getInstance().getNickName(name);
        String result = TextUtil.fastReplace(toracle, name, "CARDNAME");
        if (!nickName.equals(name)) {
            result = TextUtil.fastReplace(result, nickName, "NICKNAME");
        }
        return result;
    }

    public static void buildOracleMapping(String faceName, String oracleText, String variantName) {
        String translationKey = faceName;
        if(variantName != null)
            translationKey = faceName + " $" + variantName;
        if (!needsTranslation() || oracleMappings.containsKey(translationKey)) return;
        String translatedText = getTranslatedOracle(translationKey);
        if (translatedText.isEmpty()) {
            // english card only, fall back
            return;
        }
        String translatedName = getTranslatedName(translationKey);
        List <Pair <String, String> > mapping = new ArrayList<>();
        String [] splitOracleText = oracleText.split("\\\\n");
        String [] splitTranslatedText = translatedText.split("\r\n\r\n");

        int offset = leadingLineOffset(splitOracleText, splitTranslatedText);

        for (int i = 0; i < splitOracleText.length; i++) {
            String toracle = replaceCardName("en-US", faceName, splitOracleText[i]);
            // Remove reminder text in English oracle text unless entire line is reminder text
            if (!toracle.startsWith("(")) {
                toracle = toracle.replaceAll("\\(.*\\)", "");
            }
            // A translated oracle can have fewer lines than the English one. Give the uncovered
            // lines an entry of their own anyway, with no translation - otherwise they match some
            // other line's entry and two abilities display the same text.
            int t = i - offset;
            String ttranslated = t >= 0 && t < splitTranslatedText.length
                    ? replaceCardName(languageSelected, translatedName, splitTranslatedText[t])
                    : null;
            mapping.add(Pair.of(toracle, ttranslated));
        }
        oracleMappings.put(translationKey, mapping);
    }

    /**
     * Translated oracles routinely reach us a line short, having folded a leading keyword line
     * - "Enchant creature", "Flying", "Devoid (...)" - into the line below it, which leaves
     * every remaining line paired with its neighbour's translation. Mana symbols and stat
     * changes survive translation unaltered, so where a line carries one they say which pairing
     * is right; where none does, the leading line is the one usually folded away.
     *
     * @return how far the translated lines have slipped against the English ones, 0 or 1
     */
    private static int leadingLineOffset(String [] oracle, String [] translated) {
        if (oracle.length - translated.length != 1
                || !(oracle[0].startsWith("Enchant ") || isKeywordLine(oracle[0]))) {
            return 0;
        }
        int asIs = 0, shifted = 0;
        for (int t = 0; t < translated.length; t++) {
            List <String> theirs = translationInvariants(translated[t]);
            // a translated line carrying no marks of its own says nothing either way: plenty
            // of translations write a cost out in words where the English uses a symbol
            if (theirs.isEmpty()) {
                continue;
            }
            asIs += agreement(oracle[t], theirs);
            shifted += agreement(oracle[t + 1], theirs);
        }
        return asIs > shifted ? 0 : 1;
    }

    /** 1 where the English line's marks are the translated line's, -1 where they differ. */
    private static int agreement(String oracleLine, List <String> theirs) {
        List <String> marks = translationInvariants(oracleLine);
        return marks.isEmpty() ? 0 : marks.equals(theirs) ? 1 : -1;
    }

    /**
     * Whether a line is a keyword line - "Flying", "Devoid (...)", "Enchant creature",
     * "Renown 1 (...)" - rather than a sentence. This is the line translations fold into the
     * one below it. They fold sentences too, but keep those at the front, so for a sentence
     * the lines still pair from the top and there is nothing to realign.
     */
    private static boolean isKeywordLine(String line) {
        String bare = line.replaceAll("\\([^()]*\\)", "").trim();
        return bare.isEmpty() || (!bare.endsWith(".") && bare.split("\\s+").length <= 5);
    }

    /** The parts of an oracle line that read the same in every language. */
    private static List <String> translationInvariants(String line) {
        List <String> found = new ArrayList<>();
        Matcher m = TRANSLATION_INVARIANT.matcher(line);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    public static String translateMultipleDescriptionText(String descText, ITranslatable card) {
        if (!needsTranslation()) return descText;
        String [] splitDescText = descText.split("\n");
        String result = descText;
        for (String text : splitDescText) {
            text = text.trim();
            if (text.isEmpty()) continue;
            String translated = translateSingleDescriptionText(text, card);
            if (!text.equals(translated)) {
                result = TextUtil.fastReplace(result, text, translated);
            } else {
                // keywords maybe combined into one line, split them and try translate again
                String [] splitKeywords = text.split(", ");
                if (splitKeywords.length <= 1) continue;
                for (String keyword : splitKeywords) {
                    if (keyword.contains(" ")) continue;
                    translated = translateSingleDescriptionText(keyword, card);
                    if (!keyword.equals(translated)) {
                        result = TextUtil.fastReplace(result, keyword, translated);
                    }
                }
            }
        }
        return result;
    }

    public static String translateSingleDescriptionText(String descText, ITranslatable card) {
        if (descText == null)
            return "";
        if (!needsTranslation()) return descText;
        // the answer comes out of one card's oracle mapping, so it can only be reused for that
        // card - two cards sharing an ability need not word its translation the same way
        String key = card.getTranslationKey();
        Map <String, String> cardCache = translatedCaches.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        if (cardCache.containsKey(descText)) return cardCache.get(descText);

        List <Pair <String, String> > mapping = oracleMappings.get(key);
        if (mapping == null) {
            // no mapping of its own - a clone that took a different name, say - so fall back to
            // whatever another card already worked out for this exact text
            return sharedCache.getOrDefault(descText, descText);
        }
        String result = descText;
        if (!mapping.isEmpty()) {
            result = translateSingleIngameText(descText, mapping);
        }
        cardCache.put(descText, result);
        if (!result.equals(descText)) {
            sharedCache.putIfAbsent(descText, result);
        }
        return result;
    }

    private static String translateSingleIngameText(String descText, List <Pair <String, String> > mapping) {
        String tcompare = descText.startsWith("(") ? descText : descText.replaceAll("\\(.*\\)", "");

        // Use Levenshtein Distance to find matching oracle text and replace it with translated text
        int candidateIndex = mapping.size();
        int minDistance = tcompare.length();
        for (int i = 0; i < mapping.size(); i++) {
            String toracle = mapping.get(i).getLeft();
            int threshold = Math.min(toracle.length(), tcompare.length()) / 3;
            int distance = StringUtils.getLevenshteinDistance(toracle, tcompare, threshold);
            if (distance != -1 && distance < minDistance) {
                minDistance = distance;
                candidateIndex = i;
            }
        }

        if (candidateIndex < mapping.size()) {
            String translated = mapping.get(candidateIndex).getRight();
            // an oracle line the translation does not cover carries no text of its own
            return translated == null ? descText : translated;
        }

        return descText;
    }

}
