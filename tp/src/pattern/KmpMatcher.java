package pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Knuth-Morris-Pratt (KMP): busca um padrão sem retroceder no texto.
 *
 * Pré-processamento: O(m) de tempo e espaço, onde m é o tamanho do padrão.
 * Busca: O(n), onde n é o tamanho do texto, mesmo no pior caso.
 */
public class KmpMatcher implements PatternMatcher {

    private final String pattern;
    private final int[] lps;

    public KmpMatcher(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("O padrão não pode ser vazio.");
        }
        this.pattern = pattern;
        this.lps = buildLps(pattern);
    }

    /**
     * LPS (Longest Proper Prefix which is also Suffix): para cada posição i,
     * guarda o comprimento do maior prefixo próprio de pattern[0..i] que também
     * é sufixo desse trecho. Um prefixo próprio nunca é a string inteira.
     *
     * Exemplo: para "abab", lps = [0, 0, 1, 2]. Assim, quando há uma
     * divergência depois de "abab", podemos reaproveitar o prefixo "ab".
     */
    private static int[] buildLps(String pattern) {
        int[] lps = new int[pattern.length()];
        int length = 0; // tamanho do prefixo/sufixo encontrado até o momento
        int i = 1;      // a primeira posição tem LPS igual a zero

        while (i < pattern.length()) {
            if (pattern.charAt(i) == pattern.charAt(length)) {
                // A igualdade estende o prefixo que já conhecíamos.
                lps[i] = ++length;
                i++;
            } else if (length > 0) {
                // Em vez de recomeçar do zero, tenta o próximo prefixo possível.
                length = lps[length - 1];
            } else {
                // Nenhum prefixo possível termina na posição atual.
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }

    @Override
    public String name() {
        return "KMP";
    }

    @Override
    public List<Integer> findAll(String text) {
        if (text == null) {
            throw new IllegalArgumentException("O texto não pode ser nulo.");
        }

        List<Integer> positions = new ArrayList<>();
        int i = 0; // posição atual no texto
        int j = 0; // quantidade de caracteres do padrão já reconhecidos

        while (i < text.length()) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;

                if (j == pattern.length()) {
                    // Padrão completo: registra o início da ocorrência.
                    positions.add(i - j);
                    // Continua a partir do maior prefixo que também é sufixo.
                    // Isso preserva ocorrências sobrepostas ("ana" em "banana").
                    j = lps[j - 1];
                }
            } else if (j > 0) {
                // Divergência: o texto não retrocede; somente o padrão é ajustado.
                j = lps[j - 1];
            } else {
                // Não havia prefixo aproveitável; avança um caractere no texto.
                i++;
            }
        }

        return positions;
    }
}
