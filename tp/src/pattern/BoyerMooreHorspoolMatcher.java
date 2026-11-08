package pattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Boyer-Moore-Horspool (BMH): variante simplificada do Boyer-Moore que
 * compara da direita para a esquerda e aplica a regra do caractere ruim.
 *
 * O pré-processamento calcula deslocamentos a partir do padrão. Em muitos
 * textos, ele permite saltar vários caracteres de uma só vez; o pior caso,
 * entretanto, ainda é O(n * m). A tabela cobre todos os valores de char do
 * Java (UTF-16), permitindo caracteres acentuados sem dependências externas.
 */
public class BoyerMooreHorspoolMatcher implements PatternMatcher {

    private final String pattern;
    private final int[] shifts;

    public BoyerMooreHorspoolMatcher(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("O padrão não pode ser vazio.");
        }
        this.pattern = pattern;

        // Por padrão, se o caractere não aparece antes do último do padrão,
        // podemos deslocar a janela inteira (m posições).
        int m = pattern.length();
        this.shifts = new int[Character.MAX_VALUE + 1];
        Arrays.fill(shifts, m);

        // Para cada caractere antes do último, guarda a distância até o final.
        // Repetições usam a ocorrência mais à direita (menor deslocamento).
        for (int i = 0; i < m - 1; i++) {
            shifts[pattern.charAt(i)] = m - 1 - i;
        }
    }

    @Override
    public String name() {
        return "Boyer-Moore-Horspool";
    }

    @Override
    public List<Integer> findAll(String text) {
        if (text == null) {
            throw new IllegalArgumentException("O texto não pode ser nulo.");
        }

        List<Integer> positions = new ArrayList<>();
        int m = pattern.length();
        int offset = 0; // primeira posição da janela atual no texto

        while (offset <= text.length() - m) {
            int j = m - 1;

            // Compara do último ao primeiro caractere do padrão.
            while (j >= 0 && pattern.charAt(j) == text.charAt(offset + j)) {
                j--;
            }

            if (j < 0) {
                // Todos os caracteres coincidiram: encontramos uma ocorrência.
                positions.add(offset);
            }

            // Usa o caractere alinhado ao FINAL do padrão para determinar o
            // próximo salto, exista ou não uma ocorrência nesta janela.
            // Mesmo após um acerto, o deslocamento é seguro e não perde
            // ocorrências sobrepostas.
            offset += shifts[text.charAt(offset + m - 1)];
        }

        return positions;
    }
}
