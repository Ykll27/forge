package forge.ptbr.translator;

import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.store.TranslationFile;
import forge.ptbr.tools.ForgeCorpus;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Avaliação fora da amostra do tradutor: a memória é minerada só com cartas impressas antes
 * de {@code cutoff}; a tradução é comparada ao texto oficial das cartas posteriores.
 * Usado pelo teste de qualidade e executável à mão para inspecionar erros.
 */
public final class QualityReport {

    public int cardsEvaluated;
    public int sentences;
    public int translatedSentences;
    public int linesCompared;
    public int linesExact;
    public double similaritySum;
    public final List<String[]> mismatches = new ArrayList<>();
    public final Map<String, Integer> misses = new HashMap<>();

    public static Path resDir() {
        return Paths.get(System.getProperty("forge.res.dir", "../forge-gui/res"));
    }

    public static QualityReport run(String cutoff) throws Exception {
        Path res = resDir();
        Map<String, CardTextEn> cards = ForgeCorpus.readCardScripts(res.resolve("cardsfolder"));
        Map<String, String> first = ForgeCorpus.readFirstPrintDates(res.resolve("editions"));
        Map<String, LocalizedEntry> pt = TranslationFile.read(res.resolve("languages/cardnames-pt-BR.txt"));
        Map<String, String> owners = ForgeCorpus.readFaceOwners(res.resolve("cardsfolder"));

        CorpusMiner miner = new CorpusMiner();
        List<CardTextEn> test = new ArrayList<>();
        for (CardTextEn c : cards.values()) {
            LocalizedEntry e = pt.get(c.name());
            if (e == null || !e.hasText()) {
                continue;
            }
            String d = first.getOrDefault(c.name(), first.getOrDefault(owners.getOrDefault(c.name(), ""), "9999"));
            if (d.compareTo(cutoff) < 0) {
                miner.addCard(c, e, recencyWeight(d));
            } else {
                test.add(c);
            }
        }
        EffectTranslator tr = new EffectTranslator(miner.finish(), Glossary.bundled());
        QualityReport r = new QualityReport();
        for (CardTextEn c : test) {
            LocalizedEntry e = pt.get(c.name());
            TranslationResult res2 = tr.translate(c, e.name());
            r.cardsEvaluated++;
            r.sentences += res2.segments();
            r.translatedSentences += res2.translated();
            for (String u : res2.untranslated()) {
                r.misses.merge(u.replaceAll("\\d+", "N"), 1, Integer::sum);
            }
            String[] ours = res2.text().split("\\\\n", -1);
            String[] official = e.text().split("\\\\n", -1);
            if (ours.length != official.length) {
                continue;
            }
            Stats inner = null;
            for (int i = 0; i < ours.length; i++) {
                // só compara linhas que o tradutor considerou completas
                CardTextEn single = new CardTextEn(c.name(), c.type(), c.oracleLines().get(i));
                TranslationResult lineRes = tr.translate(single, e.name());
                if (!lineRes.isComplete()) {
                    continue;
                }
                String a = MtgText.normalizeForCompare(MtgText.stripReminder(ours[i]));
                String b = MtgText.normalizeForCompare(MtgText.stripReminder(official[i]));
                r.linesCompared++;
                if (a.equals(b)) {
                    r.linesExact++;
                    r.similaritySum += 1.0;
                } else {
                    double sim = similarity(a, b);
                    r.similaritySum += sim;
                    r.mismatches.add(new String[]{c.name(), c.oracleLines().get(i), ours[i], official[i], String.format("%.2f", sim)});
                }
            }
        }
        return r;
    }

    private static final class Stats { }

    /** Peso pela data da primeira impressão: a redação moderna precisa vencer a antiga. */
    public static int recencyWeight(String date) {
        if (date == null || date.length() < 4) {
            return 1;
        }
        int year = Integer.parseInt(date.substring(0, 4));
        return year >= 2021 ? 8 : year >= 2018 ? 4 : year >= 2010 ? 2 : 1;
    }

    /** 1 - distância de Levenshtein normalizada. */
    static double similarity(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0 && m == 0) {
            return 1;
        }
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return 1.0 - (double) prev[m] / Math.max(n, m);
    }

    public double coverage() {
        return sentences == 0 ? 0 : (double) translatedSentences / sentences;
    }

    public double exactRate() {
        return linesCompared == 0 ? 0 : (double) linesExact / linesCompared;
    }

    public double meanSimilarity() {
        return linesCompared == 0 ? 0 : similaritySum / linesCompared;
    }

    public static void main(String[] args) throws Exception {
        String cutoff = args.length > 0 ? args[0] : "2023-01-01";
        long t0 = System.currentTimeMillis();
        QualityReport r = run(cutoff);
        System.out.printf("corte=%s cartas=%d frases=%d traduzidas=%d (%.1f%%)%n", cutoff, r.cardsEvaluated,
                r.sentences, r.translatedSentences, 100 * r.coverage());
        System.out.printf("linhas completas comparadas=%d idênticas ao oficial=%d (%.1f%%) similaridade média=%.3f  [%d ms]%n",
                r.linesCompared, r.linesExact, 100 * r.exactRate(), r.meanSimilarity(), System.currentTimeMillis() - t0);
        int show = args.length > 1 ? Integer.parseInt(args[1]) : 25;
        System.out.println("--- frases mais frequentes sem tradução");
        r.misses.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(show)
                .forEach(e -> System.out.println(e.getValue() + "  " + e.getKey()));
        System.out.println("--- divergências (menor similaridade primeiro)");
        Map<String, String[]> uniq = new LinkedHashMap<>();
        r.mismatches.stream().sorted((a, b) -> a[4].compareTo(b[4])).forEach(m -> uniq.putIfAbsent(m[1], m));
        uniq.values().stream().limit(show).forEach(m ->
                System.out.println(m[4] + " | " + m[1] + "\n     nós: " + m[2] + "\n oficial: " + m[3]));
    }
}
