package forge.ptbr.model;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Texto original (inglês) de uma face de carta, como o Forge o conhece.
 * É a entrada do tradutor e a referência para "ver original".
 *
 * @param name   nome da face em inglês (chave de tradução do Forge)
 * @param type   linha de tipo em inglês, no formato do script ("Legendary Creature Elf Druid")
 * @param oracle texto Oracle com linhas separadas pelo marcador literal {@code \n} do Forge
 */
public final class CardTextEn {
    private final String name;
    private final String type;
    private final String oracle;

    public CardTextEn(String name, String type, String oracle) {
        Objects.requireNonNull(name, "name");
        type = type == null ? "" : type;
        oracle = oracle == null ? "" : oracle;
        this.name = name;
        this.type = type;
        this.oracle = oracle;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String oracle() {
        return oracle;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CardTextEn o)) {
            return false;
        }
        return java.util.Objects.equals(name, o.name) && java.util.Objects.equals(type, o.type) && java.util.Objects.equals(oracle, o.oracle);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, type, oracle);
    }

    @Override
    public String toString() {
        return "CardTextEn[" + name + ", " + type + ", " + oracle + "]";
    }

    /** Marcador de quebra de linha usado nos scripts e nos arquivos cardnames-*.txt. */
    public static final String LINE_SEPARATOR = "\\n";

    public List<String> oracleLines() {
        if (oracle.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(oracle.split("\\\\n", -1));
    }
}
