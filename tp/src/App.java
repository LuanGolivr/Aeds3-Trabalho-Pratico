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

    private static final String[] MENU_ITEMS = {
        "1 - Carregar base de dados",
        "2 - Adicionar novo registro",
        "3 - Buscar registro",
        "4 - Atualizar registro",
        "5 - Deletar registro",
        "6 - Ordenar registros",
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
        int id = inputReader.readId();
        Song song = service.search(id);
        
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
        
        if (existingSong == null) {
            System.out.println("Erro: Registro com o id [" + id + "] não encontrado para atualização.");
            return;
        }
        
        System.out.println("Registro atual encontrado. Insira os novos dados abaixo:");
        // Chama o método readSong() passando o mesmo ID, para gerar o objeto com os novos atributos
        Song updatedSong = inputReader.readSong(id); 
        
        if (service.update(updatedSong)) {
            System.out.println("Registro atualizado com sucesso.");
        }
        else {
            System.out.println("Falha ao atualizar o registro.");
        }
    }

    private static void deleteRecord() throws IOException {
        int id = inputReader.readId();
        
        if (service.delete(id)) {
            System.out.println("Registro deletado com sucesso.");
        }
        else {
            System.out.println("Erro: Registro com o id [" + id + "] não encontrado para deleção.");
        }
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
