package forge.ptbr.translator;

/**
 * Atualiza termos que mudaram nas regras depois que a carta-fonte foi impressa.
 * Só se aplica a traduções automáticas (o texto oficial nunca é alterado) e só quando
 * o Oracle inglês confirma a redação nova.
 */
final class Terminology {
    private Terminology() {
    }

    static String modernize(String english, String pt) {
        String out = pt;
        if (english.contains("mana value")) {
            out = out.replace("custo de mana convertido", "valor de mana")
                    .replace("custos de mana convertidos", "valores de mana");
        }
        if (!english.contains("mana pool")) {
            // "Add {G}." desde 2018; impressões antigas diziam "à sua reserva de mana"
            out = out.replace(" à sua reserva de mana", "").replace(" a sua reserva de mana", "");
        }
        if (english.contains("any target")) {
            out = out.replace("qualquer criatura ou jogador alvo", "qualquer alvo")
                    .replace("criatura ou jogador alvo", "qualquer alvo");
        }
        return Agreement.fix(out);
    }
}
