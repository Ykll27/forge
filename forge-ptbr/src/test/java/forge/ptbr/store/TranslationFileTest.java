package forge.ptbr.store;

import forge.ptbr.model.LocalizedEntry;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public class TranslationFileTest {

    @Test
    public void roundTripKeepsFieldsAndMetadata() throws Exception {
        Path dir = Files.createTempDirectory("tf");
        Path f = dir.resolve("sub/cardnames-pt-BR-teste.txt");
        TranslationFile.write(f, Arrays.asList(
                new LocalizedEntry("Llanowar Elves", "Elfos de Llanowar", "Criatura — Elfo Druida", "{T}: Adicione {G}."),
                new LocalizedEntry("Abandon Hope", "Abandonar a Esperança", "", ""),
                new LocalizedEntry("Vazia", "", "", "")), k -> k.startsWith("Llanowar") ? "date=2019-07-12" : null);
        Map<String, LocalizedEntry> read = TranslationFile.read(f);
        Assert.assertEquals(read.size(), 2, "entradas vazias não são gravadas");
        Assert.assertEquals(read.get("Llanowar Elves").text(), "{T}: Adicione {G}.");
        Assert.assertEquals(read.get("Abandon Hope").type(), "");
        Assert.assertEquals(TranslationFile.readMetadata(f).get("Llanowar Elves"), "date=2019-07-12");
        Assert.assertFalse(Files.exists(dir.resolve("sub/cardnames-pt-BR-teste.txt.tmp")), "temporário removido");
    }

    @Test
    public void realLineBreaksAndPipesAreEscaped() throws Exception {
        Path f = Files.createTempFile("tf", ".txt");
        TranslationFile.write(f, Arrays.asList(new LocalizedEntry("X", "Nome|com barra", "", "linha 1\nlinha 2")));
        String raw = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        Assert.assertEquals(raw, "X|Nome/com barra||linha 1\\nlinha 2\n");
    }

    @Test
    public void toleratesBomCommentsAndMissingFile() throws Exception {
        Path f = Files.createTempFile("tf", ".txt");
        Files.write(f, "﻿A|Á||\n# comentário\n|sem chave\nB|Bê|Tipo|Texto|meta\n".getBytes(StandardCharsets.UTF_8));
        Map<String, LocalizedEntry> read = TranslationFile.read(f);
        Assert.assertEquals(read.keySet(), new java.util.LinkedHashSet<>(Arrays.asList("A", "B")));
        Assert.assertTrue(TranslationFile.read(f.resolveSibling("nao-existe.txt")).isEmpty());
    }

    @Test
    public void overlayKeepsFieldsTheHigherSourceLeavesEmpty() {
        LocalizedEntry base = new LocalizedEntry("K", "Nome oficial", "Tipo oficial", "Texto oficial");
        LocalizedEntry user = new LocalizedEntry("K", "", "", "Texto corrigido");
        LocalizedEntry merged = base.overlaidBy(user);
        Assert.assertEquals(merged.name(), "Nome oficial");
        Assert.assertEquals(merged.type(), "Tipo oficial");
        Assert.assertEquals(merged.text(), "Texto corrigido");
    }
}
