import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import input.SongInputReader;
import model.Song;
import service.RecordService;
import storage.BinaryRecordFile;

public class App {
    private static final String SONGS_FILE_PATH = "files/songs.bin";
    private static final String DATASET_PATH = "dataset/Spotify Most Streamed Songs.csv";

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
}
