package forge.ptbr.service;
import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.translator.EffectTranslator;
import forge.ptbr.translator.TranslationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Gera traduções automáticas para as faces que não têm texto oficial, reaproveitando
 * as que já foram geradas para o mesmo texto inglês pela mesma versão do tradutor.
 */
public final class AutomaticGenerator {
    /** Resultado da geração. */
    public static final class Output {
        public final Map<String, LocalizedEntry> entries = new HashMap<>();
        public final Map<String, String> metadata = new HashMap<>();
        public int considered;
        public int reused;
        public int translated;
        public int complete;
        public int belowThreshold;
        public int segments;
        public int segmentsTranslated;
        public boolean cancelled;

        public double sentenceCoverage() {
            return segments == 0 ? 0 : (double) segmentsTranslated / segments;
        }
    }

    /** Progresso: faces processadas / total. */
    public interface Progress {
        void onProgress(int done, int total);
    }

    private AutomaticGenerator() {
    }

    /**
     * @param minCoverage fração mínima de frases traduzidas para publicar o texto; abaixo disso a carta
     *                    continua em inglês (o tipo traduzido ainda é publicado)
     * @param memoryTag   identifica a memória usada; mudou, as traduções são refeitas
     */
    public static Output generate(Collection<CardTextEn> cards, Map<String, LocalizedEntry> official,
                                  EffectTranslator translator, Map<String, LocalizedEntry> previous,
                                  Map<String, String> previousMeta, double minCoverage, String memoryTag,
                                  Progress progress, BooleanSupplier cancelled) {
        Output out = new Output();
        int total = cards.size();
        int done = 0;
        for (CardTextEn card : cards) {
            done++;
            if (cancelled != null && cancelled.getAsBoolean()) {
                out.cancelled = true;
                break;
            }
            if (progress != null && (done % 500 == 0 || done == total)) {
                progress.onProgress(done, total);
            }
            LocalizedEntry off = official.get(card.name());
            boolean needsText = !card.oracle().isEmpty() && (off == null || !off.hasText());
            boolean needsType = !card.type().isEmpty() && (off == null || !off.hasType());
            if (!needsText && !needsType) {
                continue;
            }
            out.considered++;
            String displayName = off != null && off.hasName() ? off.name() : card.name();
            String hash = hash(card.name() + "|" + card.type() + "|" + card.oracle() + "|" + displayName);
            String meta = "v=" + EffectTranslator.VERSION + ";h=" + hash + ";m=" + memoryTag;
            String old = previousMeta == null ? null : previousMeta.get(card.name());
            if (old != null && old.startsWith(meta) && previous != null && previous.containsKey(card.name())) {
                out.entries.put(card.name(), previous.get(card.name()));
                out.metadata.put(card.name(), old);
                out.reused++;
                continue;
            }
            String type = needsType ? translator.translateType(card.type()) : "";
            String text = "";
            double coverage = 1;
            if (needsText) {
                TranslationResult r = translator.translate(card, displayName);
                out.segments += r.segments();
                out.segmentsTranslated += r.translated();
                coverage = r.coverage();
                if (r.isComplete()) {
                    out.complete++;
                }
                if (coverage >= minCoverage) {
                    text = r.text();
                    out.translated++;
                } else {
                    out.belowThreshold++;
                }
            }
            // mesmo sem nada publicável a linha é gravada (vazia, com metadados): é o cache negativo
            // que evita traduzir de novo, a cada abertura, uma carta que o tradutor ainda não cobre
            LocalizedEntry entry = new LocalizedEntry(card.name(), "", type == null ? "" : type, text);
            out.entries.put(card.name(), entry);
            out.metadata.put(card.name(), meta + ";c=" + String.format(Locale.ROOT, "%.2f", coverage));
        }
        return out;
    }

    static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
