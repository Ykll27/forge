package forge.ptbr.tools;
import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.service.AutomaticGenerator;
import forge.ptbr.service.MemoryBuilder;
import forge.ptbr.service.PtBrLocalizationService;
import forge.ptbr.store.TranslationFile;
import forge.ptbr.translator.EffectTranslator;
import forge.ptbr.translator.Glossary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * Gera {@code res/languages/cardnames-pt-BR-automatica.txt} a partir dos recursos do Forge.
 * Roda no computador/CI, para que o celular receba as traduções prontas e não gaste CPU e
 * bateria gerando-as.
 *
 * <pre>java -cp forge-ptbr.jar:gson.jar forge.ptbr.tools.GenerateAutomaticTranslations forge-gui/res [cobertura-minima] [--force]</pre>
 * Use {@code --force} depois de mudar o tradutor sem mudar {@code EffectTranslator.VERSION}.
 */
public final class GenerateAutomaticTranslations {
    private GenerateAutomaticTranslations() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("uso: GenerateAutomaticTranslations <diretório res do Forge> [cobertura-mínima] [--force]");
            System.exit(2);
        }
        Path res = Paths.get(args[0]);
        boolean force = java.util.Arrays.asList(args).contains("--force");
        String[] rest = java.util.Arrays.stream(args).filter(a -> !a.startsWith("--")).toArray(String[]::new);
        double minCoverage = rest.length > 1 ? Double.parseDouble(rest[1]) : PtBrLocalizationService.DEFAULT_MIN_COVERAGE;
        long t0 = System.currentTimeMillis();
        Map<String, CardTextEn> cards = ForgeCorpus.readCardScripts(res.resolve("cardsfolder"));
        Map<String, String> first = ForgeCorpus.readFirstPrintDates(res.resolve("editions"));
        Map<String, String> owners = ForgeCorpus.readFaceOwners(res.resolve("cardsfolder"));
        Path langDir = res.resolve("languages");
        Map<String, LocalizedEntry> official = TranslationFile.read(langDir.resolve(PtBrLocalizationService.FILE_BUNDLED));

        EffectTranslator translator = new EffectTranslator(
                MemoryBuilder.build(cards.values(), official, name -> MemoryBuilder.recencyWeight(
                        first.getOrDefault(name, first.get(owners.getOrDefault(name, ""))))),
                Glossary.bundled());
        Path out = langDir.resolve(PtBrLocalizationService.FILE_AUTOMATIC);
        String tag = "res" + EffectTranslator.VERSION + "-" + Files.size(langDir.resolve(PtBrLocalizationService.FILE_BUNDLED));
        AutomaticGenerator.Output o = AutomaticGenerator.generate(cards.values(), official, translator,
                force ? null : TranslationFile.read(out), force ? null : TranslationFile.readMetadata(out),
                minCoverage, tag, null, null);
        TranslationFile.write(out, o.entries.values(), o.metadata::get);
        System.out.printf(Locale.ROOT,
                "%s: %d faces sem texto oficial, %d com texto publicado (%d completas, %d abaixo de %.0f%%), "
                        + "frases traduzidas %.1f%%, reaproveitadas %d, %d ms%n",
                out, o.considered, o.translated, o.complete, o.belowThreshold, 100 * minCoverage,
                100 * o.sentenceCoverage(), o.reused, System.currentTimeMillis() - t0);
        System.out.println("memória: " + translator.describe());
    }
}
