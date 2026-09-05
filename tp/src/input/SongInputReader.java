package input;

import java.util.Scanner;

import model.Song;

public class SongInputReader {

    private final Scanner scanner;

    public SongInputReader(Scanner scanner) {
        this.scanner = scanner;
    }

    public int readMenuOption() {
        System.out.println("Digite a opção:");
        return scanner.nextInt();
    }

    public int readId() {
        System.out.println("Digite o id:");
        return scanner.nextInt();
    }

    public int readSortWays() {
        System.out.println("Número de caminhos (fitas) para a ordenação externa:");
        return scanner.nextInt();
    }

    public int readSortHeapCapacity() {
        System.out.println("Número máximo de registros por ordenação em memória primária:");
        return scanner.nextInt();
    }

    public Song readSong(int id) {
        System.out.println("Nome da faixa:");
        scanner.nextLine();
        String trackName = scanner.nextLine();

        System.out.println("Artistas (separados por vírgula):");
        String[] artistsName = scanner.nextLine().split("\\s*,\\s*");

        System.out.println("Ano de lançamento:");
        int releasedYear = scanner.nextInt();
        System.out.println("Mês de lançamento:");
        int releasedMonth = scanner.nextInt();
        System.out.println("Dia de lançamento:");
        int releasedDay = scanner.nextInt();

        System.out.println("Quantidade de playlists no Spotify:");
        int inSpotifyPlaylists = scanner.nextInt();

        System.out.println("Quantidade de streams:");
        long streams = scanner.nextLong();

        System.out.println("BPM:");
        int bpm = scanner.nextInt();

        System.out.println("Modo (ex: Major, Minor):");
        scanner.nextLine();
        String mode = scanner.nextLine();

        return new Song(id, trackName, artistsName, releasedYear, releasedMonth, releasedDay,
                inSpotifyPlaylists, streams, bpm, mode);
    }
}
