package forge.ptbr.store;
import forge.ptbr.model.LocalizedEntry;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Leitura e escrita de arquivos no formato {@code cardnames-<idioma>.txt} do Forge:
 * {@code Nome|Nome traduzido|Tipo traduzido|Texto traduzido[|metadados]}.
 *
 * <p>A quinta coluna (metadados) é ignorada pelo {@code CardTranslation} do Forge, então os
 * arquivos gerados aqui continuam legíveis pelo código original.</p>
 *
 * <p>A escrita é atômica (arquivo temporário + rename): uma queda de energia ou o Android
 * matando o processo no meio da gravação nunca deixa um arquivo pela metade.</p>
 */
public final class TranslationFile {
    private TranslationFile() {
    }

    /** Lê o arquivo; arquivo ausente resulta em mapa vazio. Linhas inválidas são ignoradas. */
    public static Map<String, LocalizedEntry> read(Path file) throws IOException {
        Map<String, LocalizedEntry> result = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return result;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                LocalizedEntry entry = LocalizedEntry.parseLine(stripBom(line));
                if (entry != null) {
                    result.put(entry.key(), entry);
                }
            }
        }
        return result;
    }

    /** Lê só a coluna de metadados (quinta coluna), por chave. */
    public static Map<String, String> readMetadata(Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return result;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = stripBom(line).split("\\|", -1);
                if (parts.length >= 5 && !parts[0].isEmpty()) {
                    result.put(parts[0], parts[4]);
                }
            }
        }
        return result;
    }

    /** Grava as entradas ordenadas por chave, sem metadados. */
    public static void write(Path file, Collection<LocalizedEntry> entries) throws IOException {
        write(file, entries, null);
    }

    /**
     * Grava as entradas ordenadas por chave. {@code metadata} pode devolver {@code null}
     * para omitir a quinta coluna de uma entrada.
     */
    public static void write(Path file, Collection<LocalizedEntry> entries,
                             Function<String, String> metadata) throws IOException {
        List<LocalizedEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(LocalizedEntry::key));
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (LocalizedEntry entry : sorted) {
                String meta = metadata == null ? null : metadata.apply(entry.key());
                // entrada vazia só é gravada quando carrega metadados (cache negativo)
                if (entry.isEmpty() && (meta == null || meta.isEmpty())) {
                    continue;
                }
                writer.write(entry.toLine());
                if (meta != null && !meta.isEmpty()) {
                    writer.write('|');
                    writer.write(meta.replace("|", "/"));
                }
                writer.write('\n');
            }
        }
        moveAtomically(tmp, file);
    }

    static void moveAtomically(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String stripBom(String line) {
        return !line.isEmpty() && line.charAt(0) == '﻿' ? line.substring(1) : line;
    }
}
