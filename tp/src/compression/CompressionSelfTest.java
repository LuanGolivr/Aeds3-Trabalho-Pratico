package compression;

import interfaces.Compressor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

// teste executável de Huffman e LZW (sem framework de teste no projeto):
//   java -cp bin compression.CompressionSelfTest [arquivo opcional, ex.: files/songs.bin]
// Para cada entrada verifica que descomprimir(comprimir(x)) == x, e que arquivo truncado vira
// IOException (e não lixo ou laço infinito).
public class CompressionSelfTest {

    public static void main(String[] args) throws IOException {
        Random random = new Random(42);
        Compressor[] compressors = { new Huffman(), new Lzw() };

        for (Compressor c : compressors) {
            roundTrip(c, "vazio", new byte[0]);
            roundTrip(c, "1 byte", new byte[] { 7 });
            roundTrip(c, "byte repetido", filled(10_000, (byte) 'a'));
            roundTrip(c, "byte negativo repetido", filled(3, (byte) -1));
            roundTrip(c, "abab... (caso especial do LZW)", "ab".repeat(5000).getBytes(StandardCharsets.UTF_8));
            roundTrip(c, "aaab aaab ...", "aaab aaab aaab aaaab ".repeat(300).getBytes(StandardCharsets.UTF_8));
            roundTrip(c, "todos os 256 bytes", allBytes());
            roundTrip(c, "2 símbolos", new byte[] { 1, 2, 1, 1, 2 });
            roundTrip(c, "aleatório 100 KB", randomBytes(random, 100_000, 256));
            // texto com muitas palavras diferentes: enche o dicionário do LZW (65536 códigos)
            // e força o caminho "dicionário congelado"; também passa pela troca de 9 a 16 bits
            roundTrip(c, "texto variado 3 MB", randomBytes(random, 3_000_000, 40));
            for (int n = 0; n < 300; n++) { // vários tamanhos pequenos, pega erros de borda
                roundTrip(c, null, randomBytes(random, n, 1 + n % 5));
            }
            if (args.length > 0) {
                byte[] real = Files.readAllBytes(Path.of(args[0]));
                byte[] compressed = roundTrip(c, args[0], real);
                System.out.printf("%s em %s: %d -> %d bytes (%.1f%%)%n", c.name(), args[0], real.length,
                        compressed.length, 100.0 * (real.length - compressed.length) / real.length);
            }
            truncatedFails(c, "texto ".repeat(2000).getBytes(StandardCharsets.UTF_8));
        }

        // texto repetitivo tem que encolher pelo menos 30% com os dois algoritmos
        byte[] text = "Taylor Swift, Bad Bunny, The Weeknd; ".repeat(1000).getBytes(StandardCharsets.UTF_8);
        for (Compressor c : compressors) {
            check(c.compress(text).length < text.length * 0.7, c.name() + " não comprimiu texto repetitivo");
        }
        System.out.println("CompressionSelfTest: OK");
    }

    private static byte[] roundTrip(Compressor c, String label, byte[] data) throws IOException {
        byte[] compressed = c.compress(data);
        byte[] restored = c.decompress(compressed);
        check(Arrays.equals(data, restored),
                c.name() + ": ida e volta falhou em " + (label != null ? label : data.length + " bytes"));
        return compressed;
    }

    // corta o arquivo comprimido em vários pontos: todos têm que lançar IOException
    private static void truncatedFails(Compressor c, byte[] data) throws IOException {
        byte[] compressed = c.compress(data);
        for (int cut = 0; cut < compressed.length - 1; cut += 1 + compressed.length / 50) {
            try {
                c.decompress(Arrays.copyOf(compressed, cut));
                throw new AssertionError(c.name() + ": arquivo truncado em " + cut + " bytes não deu erro");
            } catch (IOException expected) {
                // ok: truncamento detectado
            }
        }
    }

    private static byte[] filled(int n, byte value) {
        byte[] data = new byte[n];
        Arrays.fill(data, value);
        return data;
    }

    private static byte[] allBytes() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) i;
        return data;
    }

    // bytes sorteados entre 'a' e 'a' + alphabet - 1 (alphabet = 256 cobre todos os valores)
    private static byte[] randomBytes(Random random, int n, int alphabet) {
        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) data[i] = (byte) (alphabet == 256 ? random.nextInt(256) : 'a' + random.nextInt(alphabet));
        return data;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
