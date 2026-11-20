package forge.ptbr.service;
import forge.ptbr.model.CardTextEn;
import forge.ptbr.model.LocalizedEntry;
import forge.ptbr.translator.CorpusMiner;
import forge.ptbr.translator.TranslationMemory;
import java.util.Collection;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Constrói a memória de tradução a partir das cartas do Forge e dos textos oficiais disponíveis. */
public final class MemoryBuilder {
    private MemoryBuilder() {
    }

    /**
     * @param cards    textos ingleses (Oracle) de todas as faces conhecidas
     * @param official textos oficiais em português por nome inglês
     * @param weightOf peso de cada carta (textos mais recentes pesam mais); null = 1
     */
    public static TranslationMemory build(Collection<CardTextEn> cards, Map<String, LocalizedEntry> official,
                                          ToIntFunction<String> weightOf) {
        CorpusMiner miner = new CorpusMiner();
        for (CardTextEn card : cards) {
            LocalizedEntry pt = official.get(card.name());
            if (pt != null && (pt.hasText() || pt.hasType())) {
                miner.addCard(card, pt, weightOf == null ? 1 : weightOf.applyAsInt(card.name()));
            }
        }
        return miner.finish();
    }

    /** Peso pela data (AAAA-MM-DD) da impressão ou da primeira edição. */
    public static int recencyWeight(String date) {
        if (date == null || date.length() < 4) {
            return 1;
        }
        int year;
        try {
            year = Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return 1;
        }
        return year >= 2021 ? 8 : year >= 2018 ? 4 : year >= 2010 ? 2 : 1;
    }
}
