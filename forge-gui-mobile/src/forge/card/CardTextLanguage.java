package forge.card;

import forge.Forge;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.gui.card.CardDetailUtil;
import forge.gui.ptbr.PtBrIntegration;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.util.CardTranslation;

/**
 * Alternância entre o texto traduzido e o original (inglês) no zoom/detalhe das cartas, e o rótulo
 * que diz de onde veio o texto exibido (oficial, automático, corrigido pelo jogador).
 * Só muda o que é desenhado: as regras sempre vêm dos scripts originais.
 */
public final class CardTextLanguage {

    private static boolean showOriginal;

    private CardTextLanguage() {
    }

    /** Há tradução ativa (algum idioma além do inglês)? */
    public static boolean isAvailable() {
        return CardTranslation.isTranslationActive();
    }

    public static boolean isShowingOriginal() {
        return showOriginal && isAvailable();
    }

    public static void toggle() {
        showOriginal = !showOriginal;
    }

    /** Volta ao texto traduzido (ex.: ao fechar o zoom). */
    public static void reset() {
        showOriginal = false;
    }

    /** O jogador quer as cartas montadas em português (arte original + nome, tipo e texto traduzidos)? */
    public static boolean renderPreferred() {
        return isAvailable() && FModel.getPreferences().getPrefBoolean(FPref.UI_PTBR_RENDER_CARDS);
    }

    /** Montar as cartas agora? Não quando o jogador pediu para ver o original: aí vale a imagem da carta. */
    public static boolean renderCards() {
        return !showOriginal && renderPreferred();
    }

    /**
     * Mesmo critério do modo "Art" do Forge (carta desenhada pelo app, com a arte recortada da imagem)
     * mais a exibição em português. Usado em todo lugar que escolhe entre a imagem e a carta montada.
     */
    public static boolean useRenderedCard(CardView card) {
        return "Art".equals(Forge.enableUIMask) || (card != null && card.useCardArt()) || renderCards();
    }

    public static boolean showSourceBadge() {
        // a origem (oficial, automática, corrigida) só existe para as camadas em português
        return isAvailable() && PtBrIntegration.isPortuguese(FModel.getPreferences().getPref(FPref.UI_LANGUAGE))
                && FModel.getPreferences().getPrefBoolean(FPref.UI_PTBR_SHOW_TEXT_SOURCE);
    }

    /** Nome a desenhar: o original quando o jogador pediu, senão o traduzido. */
    public static String name(CardView card, boolean canShow, boolean forAltState) {
        if (isShowingOriginal() && canShow) {
            return forAltState ? card.getAlternateState().getName() : card.getName();
        }
        return CardDetailUtil.formatCardName(card, canShow, forAltState);
    }

    /** Linha de tipo a desenhar. */
    public static String type(CardStateView state, boolean canShow) {
        if (isShowingOriginal() && canShow && state.getType() != null) {
            return state.getType().toString();
        }
        return CardDetailUtil.formatCardType(state, canShow);
    }

    /** "Texto oficial", "Tradução automática"... para o selo do zoom. */
    public static String sourceLabel(CardStateView state) {
        if (state == null) {
            return "";
        }
        if (isShowingOriginal()) {
            return Forge.getLocalizer().getMessageorUseDefault("lblTextSourceOriginal", "Original text (English)");
        }
        CardTranslation.Source source = CardTranslation.getTextSource(state.getTranslationKey());
        if (source == null) {
            return Forge.getLocalizer().getMessageorUseDefault("lblTextSourceNone", "No translation (English)");
        }
        switch (source) {
            case AUTOMATIC:
                return Forge.getLocalizer().getMessageorUseDefault("lblTextSourceAutomatic", "Automatic translation");
            case USER:
                return Forge.getLocalizer().getMessageorUseDefault("lblTextSourceUser", "Your correction");
            default:
                return Forge.getLocalizer().getMessageorUseDefault("lblTextSourceOfficial", "Official text");
        }
    }

    /** Dica de toque mostrada ao lado do selo. */
    public static String toggleHint() {
        return isShowingOriginal()
                ? Forge.getLocalizer().getMessageorUseDefault("lblTapShowTranslation", "Tap: show translation")
                : Forge.getLocalizer().getMessageorUseDefault("lblTapShowOriginal", "Tap: show original");
    }
}
