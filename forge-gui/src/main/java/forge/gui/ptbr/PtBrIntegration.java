package forge.gui.ptbr;

import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardRules;
import forge.card.ICardFace;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.scryfall.PtBrCardSync;
import forge.ptbr.scryfall.ScryfallClient;
import forge.ptbr.scryfall.UrlConnectionTransport;
import forge.ptbr.service.AutomaticGenerator;
import forge.ptbr.service.MemoryBuilder;
import forge.ptbr.service.PtBrLocalizationService;
import forge.ptbr.translator.TranslationResult;
import forge.util.BuildInfo;
import forge.util.CardTranslation;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Liga o módulo forge-ptbr ao Forge: carrega as camadas de tradução do diretório do usuário,
 * registra o tradutor sob demanda para cartas sem tradução em arquivo e expõe à interface as
 * ações de baixar textos oficiais, regenerar traduções automáticas e salvar correções.
 *
 * <p>Tudo aqui é exibição. O motor continua lendo os scripts originais das cartas.</p>
 */
public final class PtBrIntegration {

    public static final String LANGUAGE = PtBrLocalizationService.LANGUAGE;

    private static PtBrLocalizationService service;
    private static volatile boolean providerReady;
    private static volatile List<CardTextEn> faceCache;

    private PtBrIntegration() {
    }

    public static boolean isPortuguese(String language) {
        return LANGUAGE.equals(language);
    }

    /** Diretório gravável com os arquivos baixados, gerados e corrigidos. */
    public static String userDirectory() {
        return ForgeConstants.USER_DIR + "ptbr" + ForgeConstants.PATH_SEPARATOR;
    }

    public static synchronized PtBrLocalizationService service() {
        if (service == null) {
            service = new PtBrLocalizationService(Paths.get(userDirectory()), Paths.get(ForgeConstants.LANG_DIR));
        }
        return service;
    }

    /** Substitui {@code CardTranslation.preloadTranslation(lang, LANG_DIR)}: em pt-BR inclui as camadas do usuário. */
    public static void preloadTranslation(String language) {
        boolean automatic = FModel.getPreferences().getPrefBoolean(FPref.UI_PTBR_AUTOMATIC_TRANSLATION);
        CardTranslation.preloadTranslation(language, ForgeConstants.LANG_DIR,
                isPortuguese(language) ? userDirectory() : null, automatic);
    }

    /**
     * Chamado com o banco de cartas pronto. Em pt-BR registra o provedor sob demanda, que só carrega
     * o tradutor quando aparece uma carta sem tradução em arquivo (personalizada ou recém-adicionada),
     * e só a partir do cache em disco: nada de minerar o corpus nem carregar todas as cartas na
     * inicialização, o que pesaria em RAM e bateria nos celulares mais simples.
     */
    public static void onCardDatabaseReady(String language) {
        boolean enabled = isPortuguese(language) && FModel.getPreferences().getPrefBoolean(FPref.UI_PTBR_AUTOMATIC_TRANSLATION);
        if (!enabled) {
            release();
            return;
        }
        providerReady = false;
        loading = false;
        CardTranslation.setAutomaticTranslationProvider(new OnDemandProvider());
    }

    private static volatile boolean loading;

    private static void loadInBackground() {
        synchronized (PtBrIntegration.class) {
            if (loading || providerReady) {
                return;
            }
            loading = true;
        }
        Thread t = new Thread(() -> {
            try {
                if (service().hasCachedMemory()) {
                    service().translator(PtBrIntegration::allFaces, PtBrIntegration::weightOf);
                    providerReady = true;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("PT-BR: tradutor automático indisponível: " + e);
            }
        }, "ptbr-tradutor");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Provedor para cartas que não estão em nenhum arquivo (personalizadas, recém-adicionadas). */
    private static final class OnDemandProvider implements CardTranslation.AutomaticTranslationProvider {
        @Override
        public boolean isReady() {
            if (!providerReady) {
                loadInBackground(); // a primeira carta sem tradução dispara a carga; as seguintes já encontram pronto
            }
            return providerReady;
        }

        @Override
        public String[] translate(String translationKey) {
            String name = translationKey;
            int variant = name.indexOf(" $");
            if (variant > 0) {
                name = name.substring(0, variant);
            }
            ICardFace face = StaticData.instance().getCommonCards().getFaceByName(name);
            if (face == null) {
                return null;
            }
            CardTextEn en = toText(face);
            TranslationResult r = service().translateIfLoaded(en, CardTranslation.getTranslatedName(name));
            if (r == null) {
                return null;
            }
            String text = r.coverage() >= PtBrLocalizationService.DEFAULT_MIN_COVERAGE ? r.text() : "";
            String type = service().translateTypeIfLoaded(en.type());
            return new String[]{type == null ? "" : type, text};
        }
    }

    // ------------------------------------------------------------------ ações da interface

    /** Agente de usuário exigido pelo Scryfall: nome e versão do aplicativo. */
    static String userAgent() {
        return "ForgePTBR/" + BuildInfo.getVersionString().replace(' ', '_');
    }

    /**
     * Baixa (ou atualiza) os textos oficiais em português, refaz as traduções automáticas afetadas
     * e recarrega as traduções exibidas. Deve rodar fora da thread da interface.
     */
    public static String syncOfficialTexts(boolean full, PtBrCardSync.Progress progress, BooleanSupplier cancelled)
            throws IOException {
        ScryfallClient client = new ScryfallClient(new UrlConnectionTransport(), userAgent());
        java.util.Set<String> known = new java.util.HashSet<>();
        for (CardTextEn face : allFaces()) {
            known.add(face.name()); // inclui versos e metades, que o CardDb não indexa pelo nome
        }
        PtBrLocalizationService.SyncReport report = service().syncOfficial(client, full, known::contains, progress, cancelled);
        AutomaticGenerator.Output auto = regenerate(null, cancelled);
        CardTranslation.reload();
        return String.format(Locale.ROOT, "%d páginas lidas, %d textos oficiais baixados (%d novos). "
                        + "Traduções automáticas: %d cartas.", report.pages, report.entriesAfter,
                Math.max(0, report.entriesAfter - report.entriesBefore), auto == null ? 0 : auto.translated);
    }

    /** Refaz as traduções automáticas (incremental: só o que mudou) e recarrega. */
    public static AutomaticGenerator.Output regenerate(AutomaticGenerator.Progress progress, BooleanSupplier cancelled)
            throws IOException {
        AutomaticGenerator.Output out = service().generateAutomatic(allFaces(), PtBrIntegration::weightOf,
                PtBrLocalizationService.DEFAULT_MIN_COVERAGE, progress, cancelled);
        CardTranslation.reload();
        providerReady = true;
        return out;
    }

    /** Salva a correção de uma carta (campos vazios mantêm o texto de outra origem) e recarrega. */
    public static void saveCorrection(String cardName, String name, String type, String text) throws IOException {
        service().saveUserCorrection(new LocalizedEntry(cardName, name, type, text));
        CardTranslation.reload();
    }

    public static void removeCorrection(String cardName) throws IOException {
        service().removeUserCorrection(cardName);
        CardTranslation.reload();
    }

    /** Resumo do estado (para a tela de configurações). */
    public static String statusSummary() {
        java.util.Properties st = service().state();
        String last = st.getProperty("ultimaSincronizacao");
        String when = last == null ? "nunca" : new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"))
                .format(new java.util.Date(Long.parseLong(last)));
        return "Textos oficiais baixados: " + st.getProperty("entradasOficiais", "0") + " (última atualização: " + when + ")";
    }

    /** Texto original em inglês de uma face, para a opção "ver original". */
    public static String originalText(String cardName) {
        ICardFace face = StaticData.instance().getCommonCards().getFaceByName(cardName);
        return face == null ? "" : face.getOracleText().replace("\\n", "\n");
    }

    // ------------------------------------------------------------------ cartas

    private static CardTextEn toText(ICardFace face) {
        return new CardTextEn(face.getName(), face.getType() == null ? "" : face.getType().toString(),
                face.getOracleText());
    }

    /** Todas as faces conhecidas (montado uma vez e reaproveitado). */
    static List<CardTextEn> allFaces() {
        List<CardTextEn> faces = faceCache;
        if (faces != null) {
            return faces;
        }
        Map<String, CardTextEn> byName = new LinkedHashMap<>();
        for (PaperCard pc : StaticData.instance().getCommonCards().getUniqueCards()) {
            CardRules rules = pc.getRules();
            if (rules == null) {
                continue;
            }
            for (ICardFace face : rules.getAllFaces()) {
                if (face != null && face.getName() != null) {
                    byName.putIfAbsent(face.getName(), toText(face));
                }
            }
        }
        faces = Collections.unmodifiableList(new ArrayList<>(byName.values()));
        faceCache = faces;
        return faces;
    }

    private static final Map<String, Integer> weights = Collections.synchronizedMap(new HashMap<>());

    /** Peso pela primeira edição da carta: a redação mais recente vence na memória de tradução. */
    static int weightOf(String faceName) {
        Integer w = weights.get(faceName);
        if (w != null) {
            return w;
        }
        int weight = 1;
        try {
            PaperCard pc = StaticData.instance().getCommonCards().getCardFromEditions(faceName,
                    CardDb.CardArtPreference.ORIGINAL_ART_ALL_EDITIONS, -1, null);
            if (pc != null) {
                CardEdition ed = StaticData.instance().getEditions().get(pc.getEdition());
                if (ed != null && ed.getDate() != null) {
                    weight = MemoryBuilder.recencyWeight(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(ed.getDate()));
                }
            }
        } catch (RuntimeException ignored) {
            // carta sem edição conhecida: peso neutro
        }
        weights.put(faceName, weight);
        return weight;
    }

    /** Libera memória ao sair do português (o celular agradece). */
    public static void release() {
        CardTranslation.setAutomaticTranslationProvider(null);
        providerReady = false;
        faceCache = null;
        weights.clear();
        synchronized (PtBrIntegration.class) {
            if (service != null) {
                service.releaseTranslator();
            }
        }
    }
}
