package forge.ptbr.model;
import java.util.Objects;

/**
 * Uma linha de arquivo de tradução: nome, tipo e texto em português de uma face de carta.
 * Campos vazios significam "não traduzido aqui" e não sobrescrevem outra origem.
 * O texto usa o marcador literal {@code \n} entre linhas, como os arquivos do Forge.
 */
public final class LocalizedEntry {
    private final String key;
    private final String name;
    private final String type;
    private final String text;

    public LocalizedEntry(String key, String name, String type, String text) {
        Objects.requireNonNull(key, "key");
        name = clean(name);
        type = clean(type);
        text = clean(text);
        this.key = key;
        this.name = name;
        this.type = type;
        this.text = text;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocalizedEntry o)) {
            return false;
        }
        return java.util.Objects.equals(key, o.key) && java.util.Objects.equals(name, o.name) && java.util.Objects.equals(type, o.type) && java.util.Objects.equals(text, o.text);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key, name, type, text);
    }

    @Override
    public String toString() {
        return "LocalizedEntry[" + key + ", " + name + ", " + type + ", " + text + "]";
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        // '|' é o separador de colunas e quebras reais quebrariam o formato de linha única
        return s.replace("|", "/").replace("\r\n", "\\n").replace("\n", "\\n").trim();
    }

    public boolean hasName() {
        return !name.isEmpty();
    }

    public boolean hasType() {
        return !type.isEmpty();
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    public boolean isEmpty() {
        return !hasName() && !hasType() && !hasText();
    }

    /** Combina com uma entrada de prioridade maior: campos preenchidos dela vencem. */
    public LocalizedEntry overlaidBy(LocalizedEntry higher) {
        if (higher == null) {
            return this;
        }
        return new LocalizedEntry(key,
                higher.hasName() ? higher.name : name,
                higher.hasType() ? higher.type : type,
                higher.hasText() ? higher.text : text);
    }

    /** Linha no formato {@code Nome|Nome PT|Tipo PT|Texto PT}. */
    public String toLine() {
        return key + "|" + name + "|" + type + "|" + text;
    }

    public static LocalizedEntry parseLine(String line) {
        if (line == null || line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length < 2 || parts[0].isEmpty()) {
            return null;
        }
        return new LocalizedEntry(parts[0],
                parts[1],
                parts.length > 2 ? parts[2] : "",
                parts.length > 3 ? parts[3] : "");
    }
}
