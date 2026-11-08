import compression.Huffman;
import compression.Lzw;
import index.BPlusTreeIndex;
import index.ExtendibleHashIndex;
import index.InvertedList;
import input.SongInputReader;
import interfaces.Compressor;
import interfaces.Index;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import model.Song;
import pattern.BoyerMooreHorspoolMatcher;
import pattern.KmpMatcher;
import pattern.PatternMatcher;
import service.RecordService;
import sort.ExternalSort;
import sort.Tape;
import storage.BinaryRecordFile;

public class App {
    private static final String SONGS_FILE_PATH = "files/songs.bin";
    private static final String INDEX_FILE_PATH = "files/songs.idx";
    private static final String HASH_BASE_PATH = "files/songs"; // gera files/songs.dir e files/songs.bkt
    private static final Path HASH_DIR_PATH = Path.of(HASH_BASE_PATH + ".dir");
    private static final Path HASH_BUCKET_PATH = Path.of(HASH_BASE_PATH + ".bkt");
    private static final String DATASET_PATH = "dataset/Spotify Most Streamed Songs.csv";
    private static final String SORT_WORK_DIR = "files/sort_tmp";
    // prefixo dos arquivos comprimidos: files/songs + NomeAlgoritmo + "Compressao" + X
    private static final String COMPRESSED_BASE_PATH = "files/songs";
    private static final Compressor[] COMPRESSORS = { new Huffman(), new Lzw() };

    private static Scanner scanner;
    private static SongInputReader inputReader;
    private static RecordService<Song> service;
    private static InvertedList artistList;
    private static InvertedList yearList;

    public static void main(String[] args) throws IOException {
        scanner = new Scanner(System.in);
        inputReader = new SongInputReader(scanner);

        artistList = new InvertedList("files/artist");
        yearList = new InvertedList("files/year");

        // se já existe um índice construído em execuções anteriores (B+ ou hash), carrega e usa direto
        Index<Integer> index = null;
        if (Files.exists(Path.of(INDEX_FILE_PATH))) {
            index = BPlusTreeIndex.open(INDEX_FILE_PATH);
        } else if (Files.exists(HASH_DIR_PATH)) {
            index = ExtendibleHashIndex.open(HASH_BASE_PATH);
        }
        // cria o arquivo vazio se ele ainda não existir
        service = new RecordService<>(new BinaryRecordFile<>(SONGS_FILE_PATH, Song::fromBytes, index));
        displayMenu();
    }

    private static final String[] MENU_ITEMS = {
        "1 - Carregar base de dados",
        "2 - Adicionar novo registo",
        "3 - Buscar registo (Chave Primária)",
        "4 - Atualizar registo",
        "5 - Deletar registo",
        "6 - Ordenar registos",
        "7 - Criar/reconstruir índice",
        "8 - Buscar por atributos (Lista Invertida)",
        "9 - Comprimir base de dados (Huffman e LZW)",
        "10 - Descomprimir base de dados (versão X)",
        "11 - Buscar padrão em títulos/artistas (KMP e BMH)",
        "0 - Sair do programa",
    };

    public static void displayMenu() throws IOException {
        int option;

        do {
            printMenu("Spotify Songs Manager", MENU_ITEMS);

            option = inputReader.readMenuOption();
            switch (option) {
                case 1:
                    loadDatabase();
                    break;
                case 2:
                    addRecord();
                    break;
                case 3:
                    searchRecord();
                    break;
                case 4:
                    updateRecord();
                    break;
                case 5:
                    deleteRecord();
                    break;
                case 6:
                    sortRecords();
                    break;
                case 7:
                    buildIndex();
                    break;
                case 8:
                    searchByAttributes();
                    break;
                case 9:
                    compressDatabase();
                    break;
                case 10:
                    decompressDatabase();
                    break;
                case 11:
                    searchByPattern();
                    break;
                case 0:
                    System.out.println("Finalizando programa....");
                    break;
                default:
                    System.out.println("Opção inválida.");
            }
        } while (option != 0);
    }

    private static void printMenu(String title, String[] items) {
        int width = title.length();
        for (String item : items) {
            width = Math.max(width, item.length());
        }
        width += 2; // margem de 1 espaço de cada lado

        System.out.println("\n╔" + "═".repeat(width) + "╗");
        System.out.println("║" + center(title, width) + "║");
        System.out.println("╠" + "═".repeat(width) + "╣");
        for (String item : items) {
            System.out.println("║ " + padRight(item, width - 1) + "║");
        }
        System.out.println("╚" + "═".repeat(width) + "╝");
    }

    private static String center(String text, int width) {
        int left = (width - text.length()) / 2;
        int right = width - text.length() - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    private static void loadDatabase() throws IOException {
        Path songsPath = Path.of(SONGS_FILE_PATH);
        if (Files.exists(songsPath)) {
            System.out.println("O arquivo já existe. Deseja sobrescrevê-lo? (s/n)");
            if (!scanner.next().equalsIgnoreCase("s")) {
                return;
            }
            service.clear(); // limpa também o índice ativo
            artistList.clear();
            yearList.clear();
        }

        List<String> lines = Files.readAllLines(Path.of(DATASET_PATH));
        int skipped = 0;
        for (String line : lines.subList(1, lines.size())) { // pula o cabeçalho
            String[] fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

            try {
                int id = service.nextId();
                Song song = new Song(
                        id,
                        unquote(fields[0]),
                        unquote(fields[1]).split("\\s*,\\s*"),
                        Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5]),
                        Integer.parseInt(fields[6]),
                        Long.parseLong(fields[8]),
                        Integer.parseInt(fields[14]),
                        unquote(fields[16]));
                service.create(song);
                addToInvertedLists(song);
            } catch (NumberFormatException e) {
                skipped++; // linha com dado corrompido no dataset original (ex: campo "streams" inválido)
            }
        }
        System.out.println("Base de dados carregada com sucesso." + (skipped > 0 ? " (" + skipped + " linha(s) corrompida(s) ignorada(s))" : ""));
    }

    // indexa a música nas listas invertidas de artista e de ano de lançamento
    private static void addToInvertedLists(Song song) throws IOException {
        for (String artist : song.getArtistsName()) {
            artistList.insert(artist, song.id());
        }
        yearList.insert(String.valueOf(song.getReleasedDate().getYear()), song.id());
    }

    private static String unquote(String field) {
        field = field.trim();
        if (field.startsWith("\"") && field.endsWith("\"")) {
            field = field.substring(1, field.length() - 1);
        }
        return field.replace("\"\"", "\"");
    }

    private static void addRecord() throws IOException {
        int id = service.nextId();
        Song song = inputReader.readSong(id);
        service.create(song);
        addToInvertedLists(song);

        System.out.println("(criação via " + service.activeIndexLabel() + ")");
        System.out.println("Registo adicionado com sucesso:");
        System.out.println(song);
    }

    private static void searchRecord() throws IOException {
        int id = inputReader.readId();
        Song song = service.search(id);

        System.out.println("(busca via " + service.activeIndexLabel() + ")");
        if (song != null) {
            System.out.println("Registro encontrado:");
            System.out.println(song.toString());
        }
        else {
            System.out.println("Erro: Registro com o id [" + id + "] não encontrado.");
        }
    }

    private static void updateRecord() throws IOException {
        int id = inputReader.readId();
        Song existingSong = service.search(id);
        System.out.println("(atualização via " + service.activeIndexLabel() + ")");

        if (existingSong == null) {
            System.out.println("Erro: Registo com o id [" + id + "] não encontrado para atualização.");
            return;
        }
        
        System.out.println("Registo atual encontrado. Insira os novos dados abaixo:");
        Song updatedSong = inputReader.readSong(id); 
        
        if (service.update(updatedSong)) {
            for (String artist : existingSong.getArtistsName()) {
                artistList.remove(artist, id);
            }
            yearList.remove(String.valueOf(existingSong.getReleasedDate().getYear()), id);
            
            for (String artist : updatedSong.getArtistsName()) {
                artistList.insert(artist, id);
            }
            yearList.insert(String.valueOf(updatedSong.getReleasedDate().getYear()), id);

            System.out.println("Registo atualizado com sucesso e listas invertidas sincronizadas.");
        } else {
            System.out.println("Falha ao atualizar o registo.");
        }
    }

    private static void deleteRecord() throws IOException {
        int id = inputReader.readId();
        Song existingSong = service.search(id); 
        System.out.println("(remoção via " + service.activeIndexLabel() + ")");

        if (existingSong != null && service.delete(id)) {
            for (String artist : existingSong.getArtistsName()) {
                artistList.remove(artist, id);
            }
            yearList.remove(String.valueOf(existingSong.getReleasedDate().getYear()), id);

            System.out.println("Registo apagado com sucesso e listas invertidas atualizadas.");
        } else {
            System.out.println("Erro: Registo com o id [" + id + "] não encontrado para deleção.");
        }
    }

    private static void buildIndex() throws IOException {
        System.out.println("Escolha o tipo de índice (1 para Árvore B+, 2 para Hashing Estendido):");
        int type = scanner.nextInt();

        if (Files.exists(Path.of(INDEX_FILE_PATH)) || Files.exists(HASH_DIR_PATH)) {
            System.out.println("Já existe um índice. Reconstruí-lo vai sobrescrever o ficheiro atual. Continuar? (s/n)");
            if (!scanner.next().equalsIgnoreCase("s")) {
                return;
            }
        }

        Index<Integer> index;
        if (type == 1) {
            int order = inputReader.readIndexOrder(); // Ex: 8
            index = BPlusTreeIndex.create(INDEX_FILE_PATH, order);
            // só um índice fica em disco, senão o main() reabriria o errado na próxima execução
            Files.deleteIfExists(HASH_DIR_PATH);
            Files.deleteIfExists(HASH_BUCKET_PATH);
        } else if (type == 2) {
            System.out.println("Digite a capacidade do bucket (Ex: 20):");
            int capacity = scanner.nextInt();
            index = ExtendibleHashIndex.create(HASH_BASE_PATH, capacity);
            Files.deleteIfExists(Path.of(INDEX_FILE_PATH));
        } else {
            System.out.println("Tipo inválido.");
            return;
        }

        service.attachIndex(index);
        System.out.println("Índice construído com sucesso: " + service.activeIndexLabel());
    }

    private static void sortRecords() throws IOException {
        Iterator<Song> songs = service.iterator();
        if (!songs.hasNext()) {
            System.out.println("Não há registros para ordenar.");
            return;
        }

        int ways = inputReader.readSortWays();
        int heapCapacity = inputReader.readSortHeapCapacity();

        Files.createDirectories(Path.of(SORT_WORK_DIR));
        ExternalSort<Song> sorter = new ExternalSort<>(
                heapCapacity,
                ways,
                Comparator.comparing(Song::trackName),
                Song::fromBytes,
                SORT_WORK_DIR);

        Tape<Song> sorted = sorter.sort(songs);
        try {
            int position = 1;
            Song song;
            while ((song = sorted.read()) != null) {
                System.out.println(position++ + " - " + song);
            }

            sorted.rewind();
            service.replaceAll(sorted.iterator());
            System.out.println(
                    "Arquivo de dados substituído pela versão ordenada e compactada (sem registros deletados/antigos).");
            System.out.println("Registros ordenados pela TrackName:");
        } finally {
            sorted.close();
            sorted.delete();
        }
    }

    private static void searchByAttributes() throws IOException {
        System.out.println("\n--- Busca por Atributos ---");
        System.out.println("1 - Buscar por Artista");
        System.out.println("2 - Buscar por Ano de Lançamento");
        System.out.println("3 - Busca Composta (Artista E Ano)");
        System.out.print("Escolha uma opção: ");
        
        int choice = scanner.nextInt();
        scanner.nextLine();

        List<Integer> ids = null;

        if (choice == 1) {
            System.out.print("Digite o nome exato do artista: ");
            String artist = scanner.nextLine();
            ids = artistList.search(artist);
            
        } else if (choice == 2) {
            System.out.print("Digite o ano de lançamento (Ex: 2023): ");
            String year = scanner.nextLine();
            ids = yearList.search(year);
            
        } else if (choice == 3) {
            System.out.print("Digite o nome exato do artista: ");
            String artist = scanner.nextLine();
            List<Integer> artistIds = artistList.search(artist);
            
            System.out.print("Digite o ano de lançamento (Ex: 2023): ");
            String year = scanner.nextLine();
            List<Integer> yearIds = yearList.search(year);
            
            ids = InvertedList.intersect(artistIds, yearIds); 
            
        } else {
            System.out.println("Opção inválida.");
            return;
        }

        // Validação dos resultados
        if (ids == null || ids.isEmpty()) {
            System.out.println("\nNenhum registo encontrado com esses parâmetros.");
            return;
        }

        // Exibição dos resultados encontrados
        System.out.println("\nForam encontrados " + ids.size() + " registo(s). A carregar dados...");
        System.out.println("--------------------------------------------------");
        
        int count = 1;
        for (Integer id : ids) {
            Song song = service.search(id);
            if (song != null) {
                System.out.println(count + " - " + song);
                count++;
            }
        }
        System.out.println("--------------------------------------------------");
    }

    // ---------------------------------------------------------------- casamento de padrões (TP3)

    // Representa um campo textual vindo de um registro ativo do songs.bin.
    // O texto já vem normalizado para que os tempos meçam apenas as buscas.
    private record SearchField(int songId, String songTitle, String fieldName, String normalizedText) { }

    // Armazena todas as posições encontradas em determinado campo da música.
    private record SearchHit(SearchField field, List<Integer> positions) { }

    /**
     * Percorre os registros válidos do arquivo binário e pesquisa um padrão em
     * títulos, artistas ou ambos. KMP e BMH recebem exatamente os mesmos dados,
     * permitindo comparar tempo e verificar se retornam os mesmos resultados.
     */
    private static void searchByPattern() throws IOException {
        System.out.println("\n--- Casamento de Padrões (TP3) ---");
        System.out.println("1 - Títulos das músicas");
        System.out.println("2 - Nomes dos artistas");
        System.out.println("3 - Títulos e artistas");
        System.out.print("Escolha o campo a pesquisar: ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // consome a quebra de linha deixada por nextInt()

        if (choice < 1 || choice > 3) {
            System.out.println("Campo inválido.");
            return;
        }
        System.out.print("Digite o padrão a procurar: ");
        String pattern = scanner.nextLine();
        if (pattern.isBlank()) {
            System.out.println("O padrão não pode ser vazio.");
            return;
        }

        // Carrega a base apenas uma vez e ignora automaticamente os registros
        // com lápide, pois readAll() devolve somente registros ativos.
        List<Song> songs = service.readAll();
        if (songs.isEmpty()) {
            System.out.println("A base não possui músicas. Use a opção 1 para carregá-la.");
            return;
        }

        // A comparação não diferencia maiúsculas de minúsculas. Acentos são
        // mantidos: "musica" e "música" continuam sendo textos diferentes.
        String normalizedPattern = pattern.toLowerCase(Locale.ROOT);
        List<SearchField> fields = new ArrayList<>();
        for (Song song : songs) {
            if (choice == 1 || choice == 3) {
                fields.add(new SearchField(song.id(), song.trackName(), "título",
                        song.trackName().toLowerCase(Locale.ROOT)));
            }
            if (choice == 2 || choice == 3) {
                for (String artist : song.getArtistsName()) {
                    fields.add(new SearchField(song.id(), song.trackName(), "artista: " + artist,
                            artist.toLowerCase(Locale.ROOT)));
                }
            }
        }

        PatternMatcher[] matchers = {
            new KmpMatcher(normalizedPattern),
            new BoyerMooreHorspoolMatcher(normalizedPattern)
        };
        List<SearchHit> firstResult = null;
        long[] times = new long[matchers.length];
        boolean equal = true;

        System.out.printf("Pesquisando %d campo(s) de %d música(s) ativa(s).%n", fields.size(), songs.size());
        for (int i = 0; i < matchers.length; i++) {
            PatternMatcher matcher = matchers[i];
            long start = System.nanoTime();
            List<SearchHit> hits = findPatternHits(fields, matcher);
            times[i] = System.nanoTime() - start;

            int occurrences = 0;
            Set<Integer> songIds = new HashSet<>();
            for (SearchHit hit : hits) {
                occurrences += hit.positions().size();
                songIds.add(hit.field().songId());
            }
            System.out.printf("  %-20s %d ocorrência(s) em %d campo(s) e %d música(s) | %.3f ms%n",
                    matcher.name(), occurrences, hits.size(), songIds.size(), times[i] / 1e6);

            if (firstResult == null) {
                firstResult = hits;
            } else if (!firstResult.equals(hits)) {
                equal = false;
            }
        }

        // Se a implementação dos dois algoritmos divergir, não apresenta
        // resultados como se estivessem validados.
        if (!equal) {
            System.out.println("ATENÇÃO: os algoritmos produziram resultados diferentes.");
            return;
        }

        System.out.println("Resultados conferidos: KMP e BMH encontraram as mesmas ocorrências.");
        if (firstResult.isEmpty()) {
            System.out.println("Nenhuma ocorrência encontrada para: " + pattern);
        } else {
            final int limit = 20; // evita imprimir centenas de músicas de uma vez
            System.out.println("Exibindo até " + limit + " campo(s) com ocorrências:");
            for (int i = 0; i < Math.min(limit, firstResult.size()); i++) {
                SearchHit hit = firstResult.get(i);
                System.out.printf("  ID %d | %s | %s | posições: %s%n",
                        hit.field().songId(), hit.field().songTitle(),
                        hit.field().fieldName(), hit.positions());
            }
            if (firstResult.size() > limit) {
                System.out.println("  ... mais " + (firstResult.size() - limit) + " campo(s) encontrados.");
            }
        }
        int fastest = indexOfMin(times);
        System.out.printf("Mais rápido nesta execução: %s (%.3f ms).%n",
                matchers[fastest].name(), times[fastest] / 1e6);
        System.out.println("Tempos medem apenas a busca, sem leitura do arquivo e normalização."
                + " As posições começam em zero no texto normalizado.");
    }

    // Busca em todos os campos usando apenas o algoritmo selecionado.
    // Guarda tanto o campo original quanto as posições, incluindo sobreposições.
    private static List<SearchHit> findPatternHits(List<SearchField> fields, PatternMatcher matcher) {
        List<SearchHit> hits = new ArrayList<>();
        for (SearchField field : fields) {
            List<Integer> positions = matcher.findAll(field.normalizedText());
            if (!positions.isEmpty()) {
                hits.add(new SearchHit(field, positions));
            }
        }
        return hits;
    }

    // ---------------------------------------------------------------- compressão (TP3)

    // nome do arquivo de uma versão comprimida, ex.: files/songsHuffmanCompressao1
    private static Path compressedPath(Compressor compressor, int version) {
        return Path.of(COMPRESSED_BASE_PATH + compressor.name() + "Compressao" + version);
    }

    // próxima versão livre: a primeira X para a qual nenhum algoritmo ainda gerou arquivo
    private static int nextCompressionVersion() {
        int version = 1;
        while (true) {
            boolean taken = false;
            for (Compressor compressor : COMPRESSORS) {
                taken |= Files.exists(compressedPath(compressor, version));
            }
            if (!taken) {
                return version;
            }
            version++;
        }
    }

    // posição do menor valor no vetor (usado para eleger o algoritmo vencedor de cada critério)
    private static int indexOfMin(long[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[best]) {
                best = i;
            }
        }
        return best;
    }

    // comprime o arquivo de dados inteiro (header, lápides, tamanhos e campos) com cada algoritmo,
    // grava uma nova versão X e compara taxa de compressão e tempo entre eles
    // ponytail: arquivo inteiro em memória (byte[]); limite ~2 GB, suficiente para a base do TP
    private static void compressDatabase() throws IOException {
        byte[] original = Files.readAllBytes(Path.of(SONGS_FILE_PATH));
        int version = nextCompressionVersion();
        System.out.println("Comprimindo " + SONGS_FILE_PATH + " (" + original.length + " bytes) - versão " + version);

        long[] sizes = new long[COMPRESSORS.length];
        long[] times = new long[COMPRESSORS.length];
        for (int i = 0; i < COMPRESSORS.length; i++) {
            Compressor compressor = COMPRESSORS[i];

            // mede só o algoritmo (sem a escrita em disco), para a comparação ser justa
            long start = System.nanoTime();
            byte[] compressed = compressor.compress(original);
            times[i] = System.nanoTime() - start;
            sizes[i] = compressed.length;

            Path output = compressedPath(compressor, version);
            Files.write(output, compressed);

            // ganho positivo = arquivo diminuiu; negativo = a "compressão" aumentou o arquivo
            double gain = original.length == 0 ? 0 : 100.0 * (original.length - compressed.length) / original.length;
            System.out.printf("  %-8s -> %s: %d bytes | %s de %.2f%% | tempo %.2f ms%n",
                    compressor.name(), output, compressed.length,
                    gain >= 0 ? "ganho" : "perda", Math.abs(gain), times[i] / 1e6);
        }

        int smallest = indexOfMin(sizes);
        int fastest = indexOfMin(times);
        System.out.println("Melhor compressão: " + COMPRESSORS[smallest].name() + " (" + sizes[smallest] + " bytes)");
        System.out.println("Compressão mais rápida: " + COMPRESSORS[fastest].name()
                + String.format(" (%.2f ms)", times[fastest] / 1e6));
    }

    // descomprime a versão X com cada algoritmo, compara os tempos e substitui o arquivo de dados
    // pelo resultado; índice primário e listas invertidas são reconstruídos a partir dele
    private static void decompressDatabase() throws IOException {
        System.out.println("Digite a versão X a descomprimir:");
        int version = scanner.nextInt();

        byte[] restored = null;
        long[] times = new long[COMPRESSORS.length];
        Arrays.fill(times, Long.MAX_VALUE); // algoritmo sem arquivo/que falhou nunca é o mais rápido
        for (int i = 0; i < COMPRESSORS.length; i++) {
            Compressor compressor = COMPRESSORS[i];
            Path input = compressedPath(compressor, version);
            if (!Files.exists(input)) {
                System.out.println("  " + compressor.name() + ": arquivo " + input + " não encontrado.");
                continue;
            }

            byte[] compressed = Files.readAllBytes(input);
            byte[] result;
            long start = System.nanoTime();
            try {
                result = compressor.decompress(compressed);
            } catch (IOException e) {
                System.out.println("  " + compressor.name() + ": falha ao descomprimir " + input + " (" + e.getMessage() + ")");
                continue;
            }
            times[i] = System.nanoTime() - start;
            System.out.printf("  %-8s <- %s: %d bytes | tempo %.2f ms%n", compressor.name(), input, result.length, times[i] / 1e6);

            // os dois algoritmos comprimiram o mesmo arquivo: se os resultados divergem, algum
            // arquivo foi alterado/corrompido e não dá para saber qual é o certo
            if (restored != null && !Arrays.equals(restored, result)) {
                System.out.println("Erro: Huffman e LZW produziram resultados diferentes. Arquivo de dados mantido.");
                return;
            }
            restored = result;
        }

        if (restored == null) {
            System.out.println("Nenhum arquivo válido da versão " + version + " foi encontrado.");
            return;
        }
        int fastest = indexOfMin(times);
        System.out.println("Descompressão mais rápida: " + COMPRESSORS[fastest].name()
                + String.format(" (%.2f ms)", times[fastest] / 1e6));

        // substitui o songs.bin e refaz o índice primário (dentro do restore)...
        service.restore(restored);
        // ...e as listas invertidas, que podem ter termos/ids que não existem na versão restaurada
        artistList.clear();
        yearList.clear();
        Iterator<Song> songs = service.iterator();
        while (songs.hasNext()) {
            addToInvertedLists(songs.next());
        }
        System.out.println("Arquivo de dados " + SONGS_FILE_PATH + " substituído pela versão " + version
                + " descomprimida (índice e listas invertidas reconstruídos).");
    }
}
