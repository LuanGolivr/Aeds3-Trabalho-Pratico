import input.SongInputReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import model.Song;
import service.RecordService;
import sort.ExternalSort;
import sort.Tape;
import storage.BinaryRecordFile;

public class App {
    private static final String SONGS_FILE_PATH = "files/songs.bin";
    private static final String DATASET_PATH = "dataset/Spotify Most Streamed Songs.csv";
    private static final String SORT_WORK_DIR = "files/sort_tmp";
    private static final int SORT_HEAP_CAPACITY = 50;
    private static final int SORT_MERGE_WAYS = 3;

    private static Scanner scanner;
    private static SongInputReader inputReader;
    private static RecordService<Song> service;

    public static void main(String[] args) throws IOException {
        scanner = new Scanner(System.in);
        inputReader = new SongInputReader(scanner);
        // cria o arquivo vazio se ele ainda não existir
        service = new RecordService<>(new BinaryRecordFile<>(SONGS_FILE_PATH, Song::fromBytes));
        displayMenu();
    }

    public static void displayMenu() throws IOException {
        int option;

        do {
            System.out.println("1 - Carregar base de dados");
            System.out.println("2 - Adicionar novo registro");
            System.out.println("3 - Buscar registro");
            System.out.println("4 - Atualizar registro");
            System.out.println("5 - Deletar registro");
            System.out.println("6 - Ordenar registros");
            System.out.println("0 - Sair do programa");

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
                case 0:
                    System.out.println("Finalizando programa....");
                    break;
                default:
                    System.out.println("Opção inválida.");
            }
        } while (option != 0);
    }

    private static void loadDatabase() throws IOException {
        Path songsPath = Path.of(SONGS_FILE_PATH);
        if (Files.exists(songsPath)) {
            System.out.println("O arquivo já existe. Deseja sobrescrevê-lo? (s/n)");
            if (!scanner.next().equalsIgnoreCase("s")) {
                return;
            }
        }

        List<String> lines = Files.readAllLines(Path.of(DATASET_PATH));
        for (String line : lines.subList(1, lines.size())) { // pula o cabeçalho
            String[] fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

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
        }
        System.out.println("Base de dados carregada com sucesso.");
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
        System.out.println("Registro adicionado com sucesso.");
    }

    private static void searchRecord() throws IOException {
        //int id = inputReader.readId();
    }

    private static void updateRecord() throws IOException {
        //int id = inputReader.readId();
    }

    private static void deleteRecord() throws IOException {
        //int id = inputReader.readId();
    }

    private static void sortRecords() throws IOException {
        List<Song> songs = service.readAll();
        if (songs.isEmpty()) {
            System.out.println("Não há registros para ordenar.");
            return;
        }

        Files.createDirectories(Path.of(SORT_WORK_DIR));
        ExternalSort<Song> sorter = new ExternalSort<>(
                SORT_HEAP_CAPACITY,
                SORT_MERGE_WAYS,
                Comparator.comparingLong(Song::streams),
                Song::fromBytes,
                SORT_WORK_DIR);

        Tape<Song> sorted = sorter.sort(songs.iterator());
        try {
            System.out.println("Registros ordenados por número de streams:");
            int position = 1;
            Song song;
            while ((song = sorted.read()) != null) {
                System.out.println(position++ + " - " + song);
            }
        } finally {
            sorted.close();
            sorted.delete();
        }
    }
}
