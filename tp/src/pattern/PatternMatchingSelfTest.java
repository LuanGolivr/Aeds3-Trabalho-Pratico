package pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Testes independentes dos dois algoritmos. Executar com:
 * java -cp bin pattern.PatternMatchingSelfTest
 *
 * O String.indexOf é usado SOMENTE aqui, como referência de correção;
 * as implementações de busca não utilizam esse método nem força bruta.
 */
public class PatternMatchingSelfTest {

    public static void main(String[] args) {
        check("banana", "ana");             // ocorrências sobrepostas
        check("aaaaa", "aaa");             // repetição e sobreposição
        check("abababab", "abab");         // prefixos e sufixos repetidos
        check("abc", "xyz");              // padrão inexistente
        check("abc", "abc");              // texto igual ao padrão
        check("abc", "abcd");             // padrão maior que o texto
        check("", "a");                   // texto vazio
        check("a", "a");                  // padrão de um caractere
        check("aáaá Árvore", "á");        // texto com caracteres acentuados
        check("🎵música🎵música", "🎵música"); // pares UTF-16

        // Muitos casos gerados com semente fixa, incluindo padrões que não
        // aparecem nos textos; compara cada ocorrência com uma busca de referência.
        Random random = new Random(2026);
        for (int test = 0; test < 1500; test++) {
            String text = randomString(random, random.nextInt(100));
            String pattern = randomString(random, 1 + random.nextInt(18));
            check(text, pattern);
        }

        // Ambas as implementações devem rejeitar padrão vazio.
        expectInvalid(() -> new KmpMatcher(""));
        expectInvalid(() -> new BoyerMooreHorspoolMatcher(""));
        System.out.println("PatternMatchingSelfTest: OK");
    }

    private static String randomString(Random random, int size) {
        String alphabet = "abcxyzáç";
        StringBuilder result = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }

    private static void check(String text, String pattern) {
        List<Integer> expected = reference(text, pattern);
        List<Integer> kmp = new KmpMatcher(pattern).findAll(text);
        List<Integer> bmh = new BoyerMooreHorspoolMatcher(pattern).findAll(text);

        if (!expected.equals(kmp) || !expected.equals(bmh)) {
            throw new AssertionError("Padrão: " + pattern + ", texto: " + text
                    + ", esperado: " + expected + ", KMP: " + kmp + ", BMH: " + bmh);
        }
    }

    private static List<Integer> reference(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();
        int index = text.indexOf(pattern);
        while (index != -1) {
            positions.add(index);
            // Avança só um caractere para incluir ocorrências sobrepostas.
            index = text.indexOf(pattern, index + 1);
        }
        return positions;
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Um padrão vazio deveria ser rejeitado.");
        } catch (IllegalArgumentException expected) {
            // A rejeição é o resultado esperado deste teste.
        }
    }
}
