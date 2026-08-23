import java.io.IOException;
import java.util.Scanner;
import model.Song;
import service.RecordService;
import storage.BinaryRecordFile;

public class App {
    private static final String SONGS_FILE_PATH = "files/songs.bin";

    private static Scanner scanner;
    private static RecordService<Song> service;

    public static void main(String[] args) throws IOException {
        scanner = new Scanner(System.in);
        // cria o arquivo vazio se ele ainda não existir
        service = new RecordService<>(new BinaryRecordFile<>(SONGS_FILE_PATH, Song::fromBytes));
        displayMenu();
    }

    public static void displayMenu() throws IOException {
        int option;

        do {
            System.out.println("1 - Carregar base de dados");
            System.out.println("2 - Buscar registro");
            System.out.println("3 - Atualizar registro");
            System.out.println("4 - Deletar registro");
            System.out.println("0 - Sair do programa");

            System.out.println("Digite a opção:");
            option = scanner.nextInt();

            switch (option) {
                case 1:
                    loadDatabase();
                    break;
                case 2:
                    searchRecord();
                    break;
                case 3:
                    updateRecord();
                    break;
                case 4:
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
    }

    private static void searchRecord() throws IOException {
    }

    private static void updateRecord() throws IOException {
    }

    private static void deleteRecord() throws IOException {
    }
}
