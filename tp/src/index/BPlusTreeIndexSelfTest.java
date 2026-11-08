package index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// checagem manual da árvore B+: sem framework de teste no projeto, então isso fica como
// smoke test executável (java -cp bin index.BPlusTreeIndexSelfTest) pra validar split/busca/
// remoção sempre que a implementação mudar.
public class BPlusTreeIndexSelfTest {

    public static void main(String[] args) throws IOException {
        for (int order : new int[] {3, 4, 5, 10}) {
            checkInsertAndSearch(order, 200);
            checkRemove(order, 200);
            checkHeavyRemoval(order, 300);
        }
        checkDuplicateRejected();
        System.out.println("BPlusTreeIndexSelfTest: OK");
    }

    private static void checkInsertAndSearch(int order, int n) throws IOException {
        String path = tempPath("insert-" + order);
        try {
            BPlusTreeIndex index = BPlusTreeIndex.create(path, order);
            List<Integer> keys = shuffledRange(n);
            for (int key : keys) {
                index.insert(key, key * 10L);
            }
            for (int key : keys) {
                Long position = index.search(key);
                assertTrue(position != null && position == key * 10L,
                        "order " + order + ": esperava " + (key * 10L) + " para a chave " + key + ", achou " + position);
            }
            assertTrue(index.search(n + 1000) == null, "chave inexistente não deveria ser encontrada");
        } finally {
            Files.deleteIfExists(Path.of(path));
        }
    }

    private static void checkRemove(int order, int n) throws IOException {
        String path = tempPath("remove-" + order);
        try {
            BPlusTreeIndex index = BPlusTreeIndex.create(path, order);
            for (int key = 1; key <= n; key++) {
                index.insert(key, key * 10L);
            }
            for (int key = 1; key <= n; key += 2) {
                assertTrue(index.remove(key), "remoção deveria ter achado a chave " + key);
            }
            for (int key = 1; key <= n; key++) {
                Long position = index.search(key);
                if (key % 2 == 1) {
                    assertTrue(position == null, "chave removida " + key + " ainda aparece na busca");
                } else {
                    assertTrue(position != null && position == key * 10L, "chave " + key + " deveria continuar indexada");
                }
            }
            assertTrue(!index.remove(999999), "remoção de chave inexistente deveria devolver false");
        } finally {
            Files.deleteIfExists(Path.of(path));
        }
    }

    // remove quase tudo em ordem aleatória, forçando redistribuição e fusão de nós repetidamente
    // (principalmente com order pequeno, onde a capacidade mínima é atingida o tempo todo)
    private static void checkHeavyRemoval(int order, int n) throws IOException {
        String path = tempPath("heavy-" + order);
        try {
            BPlusTreeIndex index = BPlusTreeIndex.create(path, order);
            List<Integer> keys = shuffledRange(n);
            for (int key : keys) {
                index.insert(key, key * 10L);
            }

            List<Integer> removalOrder = shuffledRange(n);
            int toRemove = (int) (n * 0.9);
            List<Integer> removed = removalOrder.subList(0, toRemove);
            List<Integer> remaining = removalOrder.subList(toRemove, n);

            for (int key : removed) {
                assertTrue(index.remove(key), "order " + order + ": remoção deveria achar a chave " + key);
                assertTrue(index.search(key) == null, "chave " + key + " ainda encontrada logo após removê-la");
            }
            for (int key : removed) {
                assertTrue(index.search(key) == null, "chave removida " + key + " reapareceu após outras remoções");
            }
            for (int key : remaining) {
                Long position = index.search(key);
                assertTrue(position != null && position == key * 10L,
                        "order " + order + ": chave " + key + " deveria continuar indexada após as remoções em massa");
            }
        } finally {
            Files.deleteIfExists(Path.of(path));
        }
    }

    private static void checkDuplicateRejected() throws IOException {
        String path = tempPath("duplicate");
        try {
            BPlusTreeIndex index = BPlusTreeIndex.create(path, 4);
            index.insert(1, 100L);
            try {
                index.insert(1, 200L);
                throw new AssertionError("inserir chave duplicada deveria lançar IllegalArgumentException");
            } catch (IllegalArgumentException expected) {
                // esperado
            }
        } finally {
            Files.deleteIfExists(Path.of(path));
        }
    }

    private static List<Integer> shuffledRange(int n) {
        List<Integer> keys = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            keys.add(i);
        }
        Collections.shuffle(keys);
        return keys;
    }

    private static String tempPath(String name) {
        return "files/selftest_" + name + ".idx";
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
