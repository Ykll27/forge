package forge.ptbr.tools;
import forge.ptbr.model.CardTextEn;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Leitura direta dos recursos do Forge (res/cardsfolder, res/editions) sem carregar o motor.
 * Usada pela ferramenta de geração e pelos testes de corpus. No aplicativo, as cartas vêm do
 * {@code StaticData} já carregado.
 */
public final class ForgeCorpus {
    private static final Pattern EDITION_CARD = Pattern.compile("^\\S+\\s+\\S+\\s+(.+?)(?:\\s+@.*)?$");

    private ForgeCorpus() {
    }

    /** Todas as faces dos scripts de carta, por nome inglês. */
    public static Map<String, CardTextEn> readCardScripts(Path cardsFolder) throws IOException {
        Map<String, CardTextEn> faces = new LinkedHashMap<>();
        List<Path> files;
        try (Stream<Path> s = Files.walk(cardsFolder)) {
            files = s.filter(p -> p.toString().endsWith(".txt")).sorted().collect(java.util.stream.Collectors.toList());
        }
        for (Path file : files) {
            for (CardTextEn face : parseScript(file)) {
                faces.putIfAbsent(face.name(), face);
            }
        }
        return faces;
    }

    /**
     * Para cada face, o nome da primeira face do mesmo script (o nome listado nas edições).
     * Permite datar as faces traseiras de cartas de duas faces.
     */
    public static Map<String, String> readFaceOwners(Path cardsFolder) throws IOException {
        Map<String, String> owners = new HashMap<>();
        List<Path> files;
        try (Stream<Path> s = Files.walk(cardsFolder)) {
            // ordenado: a saída da ferramenta precisa ser reproduzível (o CI compara o arquivo gerado)
            files = s.filter(p -> p.toString().endsWith(".txt")).sorted().collect(java.util.stream.Collectors.toList());
        }
        for (Path file : files) {
            List<CardTextEn> faces = parseScript(file);
            if (faces.isEmpty()) {
                continue;
            }
            String main = faces.get(0).name();
            StringBuilder split = new StringBuilder();
            for (CardTextEn f : faces) {
                if (split.length() > 0) {
                    split.append(" // ");
                }
                split.append(f.name());
            }
            for (CardTextEn f : faces) {
                owners.putIfAbsent(f.name(), main);
                owners.putIfAbsent(split.toString(), main);
            }
        }
        return owners;
    }

    /** Faces de um script (separadas por ALTERNATE/SPECIALIZE). */
    public static List<CardTextEn> parseScript(Path file) throws IOException {
        List<CardTextEn> faces = new ArrayList<>();
        String name = null;
        String type = null;
        String oracle = null;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("ALTERNATE") || line.startsWith("SPECIALIZE")) {
                    if (name != null) {
                        faces.add(new CardTextEn(name, type, oracle));
                    }
                    name = type = oracle = null;
                } else if (line.startsWith("Name:")) {
                    name = line.substring(5).trim();
                } else if (line.startsWith("Types:")) {
                    type = line.substring(6).trim();
                } else if (line.startsWith("Oracle:")) {
                    oracle = line.substring(7).trim();
                }
            }
        }
        if (name != null) {
            faces.add(new CardTextEn(name, type, oracle));
        }
        return faces;
    }

    /** Data da primeira impressão de cada carta, a partir de res/editions. */
    public static Map<String, String> readFirstPrintDates(Path editionsDir) throws IOException {
        Map<String, String> first = new HashMap<>();
        List<Path> files;
        try (Stream<Path> s = Files.list(editionsDir)) {
            files = s.filter(p -> p.toString().endsWith(".txt")).collect(java.util.stream.Collectors.toList());
        }
        for (Path file : files) {
            String date = null;
            boolean inCards = false;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.startsWith("Date=")) {
                    date = line.substring(5);
                } else if (line.startsWith("[")) {
                    inCards = line.toLowerCase().contains("card");
                } else if (inCards && date != null && !line.isEmpty() && !line.startsWith("#")) {
                    Matcher m = EDITION_CARD.matcher(line);
                    if (m.matches()) {
                        String card = m.group(1).trim();
                        String d = date;
                        first.merge(card, d, (a, b) -> a.compareTo(b) <= 0 ? a : b);
                    }
                }
            }
        }
        return first;
    }
}
