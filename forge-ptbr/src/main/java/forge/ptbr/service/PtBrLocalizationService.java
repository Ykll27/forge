package forge.ptbr.service;
import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.scryfall.PtBrCardSync;
import forge.ptbr.scryfall.ScryfallClient;
import forge.ptbr.store.TranslationFile;
import forge.ptbr.translator.EffectTranslator;
import forge.ptbr.translator.Glossary;
import forge.ptbr.translator.TranslationMemory;
import forge.ptbr.translator.TranslationResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Fachada da localização PT-BR. Mantém, no diretório do usuário, os arquivos lidos pelo
 * {@code CardTranslation} do Forge, em ordem crescente de prioridade:
 * <ol>
 *   <li>{@value #FILE_AUTOMATIC}: traduções automáticas (só onde não há texto oficial);</li>
 *   <li>arquivo oficial embutido no Forge ({@code res/languages/cardnames-pt-BR.txt});</li>
 *   <li>{@value #FILE_OFFICIAL}: textos oficiais baixados do Scryfall;</li>
 *   <li>{@value #FILE_USER}: correções feitas pelo jogador.</li>
 * </ol>
 * Nada aqui toca scripts de carta ou o motor: são só textos de exibição.
 */
public final class PtBrLocalizationService {
    public static final String LANGUAGE = "pt-BR";
    public static final String FILE_BUNDLED = "cardnames-pt-BR.txt";
    public static final String FILE_AUTOMATIC = "cardnames-pt-BR-automatica.txt";
    public static final String FILE_OFFICIAL = "cardnames-pt-BR-oficial.txt";
    public static final String FILE_USER = "cardnames-pt-BR-usuario.txt";
    public static final String FILE_STATE = "ptbr-estado.properties";
    public static final String FILE_MEMORY = "ptbr-memoria.tsv.gz";
    public static final String FILE_USER_GLOSSARY = "ptbr-glossario-usuario.tsv";
    public static final double DEFAULT_MIN_COVERAGE = 0.5;

    private final Path userDir;
    private final Path bundledDir;
    private EffectTranslator translator;
    private String translatorTag;

    /**
     * @param userDir    diretório gravável (ex.: {@code <dados do usuário>/ptbr})
     * @param bundledDir diretório {@code res/languages} distribuído com o Forge
     */
    public PtBrLocalizationService(Path userDir, Path bundledDir) {
        this.userDir = userDir;
        this.bundledDir = bundledDir;
    }

    public Path getUserDir() {
        return userDir;
    }

    // ------------------------------------------------------------------ leitura

    /** Textos oficiais: o arquivo embutido sobreposto pelo que foi baixado do Scryfall. */
    public synchronized Map<String, LocalizedEntry> officialEntries() throws IOException {
        Map<String, LocalizedEntry> merged = new HashMap<>(TranslationFile.read(bundledDir.resolve(FILE_BUNDLED)));
        for (LocalizedEntry e : TranslationFile.read(userDir.resolve(FILE_OFFICIAL)).values()) {
            LocalizedEntry base = merged.get(e.key());
            merged.put(e.key(), base == null ? e : base.overlaidBy(e));
        }
        return merged;
    }

    public synchronized Properties state() {
        Properties p = new Properties();
        Path f = userDir.resolve(FILE_STATE);
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException ignored) {
                // estado corrompido: recomeça do zero (a próxima sincronização será completa)
            }
        }
        return p;
    }

    private void saveState(Properties p) throws IOException {
        Files.createDirectories(userDir);
        Path tmp = userDir.resolve(FILE_STATE + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            p.store(out, "Estado da localização PT-BR do Forge");
        }
        Files.move(tmp, userDir.resolve(FILE_STATE), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // ------------------------------------------------------------------ sincronização

    /** Relatório de sincronização. */
    public static final class SyncReport {
        public final int pages;
        public final int printingsSeen;
        public final int entriesBefore;
        public final int entriesAfter;
        public final boolean full;

        SyncReport(int pages, int printingsSeen, int before, int after, boolean full) {
            this.pages = pages;
            this.printingsSeen = printingsSeen;
            this.entriesBefore = before;
            this.entriesAfter = after;
            this.full = full;
        }
    }

    /**
     * Baixa textos oficiais. Incremental por padrão (só impressões a partir da mais recente já vista);
     * {@code full} refaz tudo. O arquivo só é substituído ao final, de modo que cancelar ou perder a
     * conexão no meio não estraga o que já estava baixado.
     */
    public synchronized SyncReport syncOfficial(ScryfallClient client, boolean full, Predicate<String> wanted,
                                                PtBrCardSync.Progress progress, BooleanSupplier cancelled) throws IOException {
        Path file = userDir.resolve(FILE_OFFICIAL);
        Map<String, LocalizedEntry> existing = full ? new HashMap<>() : TranslationFile.read(file);
        Map<String, String> dates = full ? new HashMap<>()
                : PtBrCardSync.datesFromMetadata(TranslationFile.readMetadata(file));
        Properties st = state();
        String since = full || existing.isEmpty() ? null : st.getProperty("ultimaImpressao");
        PtBrCardSync.Result r = new PtBrCardSync(client).run(since, existing, dates, wanted, progress, cancelled);
        TranslationFile.write(file, r.entries.values(), r.metadata::get);
        st.setProperty("ultimaSincronizacao", Long.toString(System.currentTimeMillis()));
        if (r.newestRelease != null && !r.newestRelease.isEmpty()) {
            st.setProperty("ultimaImpressao", r.newestRelease);
        }
        st.setProperty("entradasOficiais", Integer.toString(r.entries.size()));
        saveState(st);
        invalidateTranslator();
        return new SyncReport(r.pages, r.printingsSeen, existing.size(), r.entries.size(), since == null);
    }

    // ------------------------------------------------------------------ tradutor

    /** Descarta o tradutor em memória (novos textos oficiais mudam a memória de tradução). */
    public synchronized void invalidateTranslator() {
        translator = null;
        translatorTag = null;
    }

    /** Libera a memória de tradução (economia de RAM depois de gerar as traduções). */
    public synchronized void releaseTranslator() {
        invalidateTranslator();
    }

    /**
     * Tradutor pronto para uso. A memória é minerada uma vez e guardada em disco; nas próximas
     * aberturas basta ler o arquivo, a menos que os textos oficiais tenham mudado.
     */
    public synchronized EffectTranslator translator(Collection<CardTextEn> cards, ToIntFunction<String> weightOf)
            throws IOException {
        return translator(() -> cards, weightOf);
    }

    /**
     * Como {@link #translator(Collection, ToIntFunction)}, mas só pede a lista de cartas se for preciso
     * minerar (cache ausente ou desatualizado): ler o cache não exige carregar todas as cartas.
     */
    public synchronized EffectTranslator translator(Supplier<Collection<CardTextEn>> cards, ToIntFunction<String> weightOf)
            throws IOException {
        String tag = memoryTag();
        if (translator != null && tag.equals(translatorTag)) {
            return translator;
        }
        Path cache = userDir.resolve(FILE_MEMORY);
        Properties st = state();
        TranslationMemory memory = null;
        if (tag.equals(st.getProperty("memoria")) && Files.isRegularFile(cache)) {
            try (InputStream in = Files.newInputStream(cache)) {
                memory = TranslationMemory.read(in);
            } catch (IOException e) {
                memory = null; // cache corrompido: minera de novo
            }
        }
        if (memory == null) {
            memory = MemoryBuilder.build(cards.get(), officialEntries(), weightOf);
            Files.createDirectories(userDir);
            Path tmp = userDir.resolve(FILE_MEMORY + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                memory.write(out);
            }
            Files.move(tmp, cache, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            st.setProperty("memoria", tag);
            saveState(st);
        }
        Glossary glossary = Glossary.bundled().loadFile(userDir.resolve(FILE_USER_GLOSSARY));
        translator = new EffectTranslator(memory, glossary);
        translatorTag = tag;
        return translator;
    }

    /**
     * Identifica a memória: muda quando os textos oficiais (embutidos ou baixados), o glossário do
     * usuário ou o tradutor mudam. Uma atualização do Forge traz um arquivo embutido novo.
     */
    String memoryTag() throws IOException {
        long a = sizeAndTime(bundledDir.resolve(FILE_BUNDLED));
        long b = sizeAndTime(userDir.resolve(FILE_OFFICIAL));
        long c = sizeAndTime(userDir.resolve(FILE_USER_GLOSSARY));
        return AutomaticGenerator.hash(a + ":" + b + ":" + c + ":" + EffectTranslator.VERSION);
    }

    /** Há memória em cache válida? (Ler o cache é barato; minerar exige todas as cartas.) */
    public synchronized boolean hasCachedMemory() throws IOException {
        return memoryTag().equals(state().getProperty("memoria")) && Files.isRegularFile(userDir.resolve(FILE_MEMORY));
    }

    private static long sizeAndTime(Path p) throws IOException {
        if (!Files.isRegularFile(p)) {
            return 0;
        }
        return Files.size(p) * 31 + Files.getLastModifiedTime(p).toMillis();
    }

    // ------------------------------------------------------------------ automáticas

    /** Gera/atualiza {@value #FILE_AUTOMATIC} para todas as faces sem texto oficial. */
    public synchronized AutomaticGenerator.Output generateAutomatic(Collection<CardTextEn> cards,
                                                                    ToIntFunction<String> weightOf,
                                                                    double minCoverage,
                                                                    AutomaticGenerator.Progress progress,
                                                                    BooleanSupplier cancelled) throws IOException {
        EffectTranslator tr = translator(cards, weightOf);
        Path file = userDir.resolve(FILE_AUTOMATIC);
        AutomaticGenerator.Output out = AutomaticGenerator.generate(cards, officialEntries(), tr,
                TranslationFile.read(file), TranslationFile.readMetadata(file), minCoverage,
                translatorTag, progress, cancelled);
        if (!out.cancelled) {
            TranslationFile.write(file, out.entries.values(), out.metadata::get);
            Properties st = state();
            st.setProperty("automaticas", Integer.toString(out.entries.size()));
            st.setProperty("ultimaGeracao", Long.toString(System.currentTimeMillis()));
            saveState(st);
        }
        return out;
    }

    /** Tradução de uma única face, sob demanda (cartas personalizadas, por exemplo). */
    public synchronized TranslationResult translateOne(CardTextEn card, String displayName,
                                                       Collection<CardTextEn> cards, ToIntFunction<String> weightOf)
            throws IOException {
        return translator(cards, weightOf).translate(card, displayName);
    }

    /** Tradução de uma face com o tradutor já carregado, ou null se ele ainda não foi preparado. */
    public synchronized TranslationResult translateIfLoaded(CardTextEn card, String displayName) {
        return translator == null ? null : translator.translate(card, displayName);
    }

    /** Linha de tipo traduzida com o tradutor já carregado, ou null. */
    public synchronized String translateTypeIfLoaded(String type) {
        return translator == null ? null : translator.translateType(type);
    }

    // ------------------------------------------------------------------ correções do usuário

    public synchronized Map<String, LocalizedEntry> userCorrections() throws IOException {
        return new LinkedHashMap<>(TranslationFile.read(userDir.resolve(FILE_USER)));
    }

    /** Grava (ou substitui) a correção de uma face. Campos vazios mantêm o texto de outra origem. */
    public synchronized void saveUserCorrection(LocalizedEntry correction) throws IOException {
        Map<String, LocalizedEntry> all = userCorrections();
        if (correction.isEmpty()) {
            all.remove(correction.key());
        } else {
            all.put(correction.key(), correction);
        }
        TranslationFile.write(userDir.resolve(FILE_USER), all.values());
    }

    public synchronized void removeUserCorrection(String key) throws IOException {
        Map<String, LocalizedEntry> all = userCorrections();
        if (all.remove(key) != null) {
            TranslationFile.write(userDir.resolve(FILE_USER), all.values());
        }
    }
}
