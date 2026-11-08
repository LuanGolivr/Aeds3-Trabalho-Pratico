import index.BPlusTreeIndex;
import index.ExtendibleHashIndex;
import index.InvertedList;
import interfaces.Index;
import interfaces.Recordable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import storage.BinaryRecordFile;

// regressão dos problemas apontados na correção do TP:
//  - hash estendido perdia a chave nova na divisão e não era reaberto do disco
//  - ordenação externa (replaceAll) reescrevia o .bin sem atualizar o índice
//  - recarregar a base não limpava as listas invertidas
// rodar: java -cp bin RegressionSelfTest
public class RegressionSelfTest {

    record Rec(int id) implements Recordable {
        public byte[] toBytes() {
            return ByteBuffer.allocate(4).putInt(id).array();
        }

        static Rec fromBytes(byte[] b) {
            return new Rec(ByteBuffer.wrap(b).getInt());
        }
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("tp-selftest");
        checkHashSplitAndReopen(dir.resolve("hash").toString());
        checkReplaceAllKeepsIndex(dir, "bplus", p -> BPlusTreeIndex.create(p, 4));
        checkReplaceAllKeepsIndex(dir, "hash2", p -> ExtendibleHashIndex.create(p, 3));
        checkInvertedListClear(dir.resolve("artist").toString());
        System.out.println("RegressionSelfTest: OK");
    }

    private static void checkHashSplitAndReopen(String base) throws IOException {
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 5000; i++) keys.add(i);
        Collections.shuffle(keys, new Random(42));

        ExtendibleHashIndex hash = ExtendibleHashIndex.create(base, 2);
        for (int k : keys) hash.insert(k, k * 10L);
        for (int k : keys) check(Long.valueOf(k * 10L).equals(hash.search(k)), "hash perdeu a chave " + k);

        ExtendibleHashIndex reopened = ExtendibleHashIndex.open(base);
        for (int k : keys) check(Long.valueOf(k * 10L).equals(reopened.search(k)), "hash reaberto perdeu " + k);
        for (int k = 0; k < 5000; k += 2) check(reopened.remove(k), "remove " + k);
        for (int k = 0; k < 5000; k++) check((reopened.search(k) == null) == (k % 2 == 0), "pós-remoção " + k);
    }

    interface IndexFactory {
        Index<Integer> create(String path) throws IOException;
    }

    private static void checkReplaceAllKeepsIndex(Path dir, String name, IndexFactory factory) throws IOException {
        BinaryRecordFile<Rec> file = new BinaryRecordFile<>(dir.resolve(name + ".bin").toString(), Rec::fromBytes);
        file.attachIndex(factory.create(dir.resolve(name + ".idx").toString()));
        for (int i = 0; i < 300; i++) file.create(new Rec(file.nextId()));
        for (int i = 0; i < 300; i += 3) file.delete(i);

        // simula a ordenação externa: reescreve em outra ordem (offsets mudam)
        List<Rec> sorted = file.readAll();
        Collections.reverse(sorted);
        file.replaceAll(sorted.iterator());
        for (int i = 0; i < 300; i++) {
            Rec r = file.read(i);
            check(i % 3 == 0 ? r == null : r != null && r.id() == i, name + ": leitura pós-ordenação do id " + i);
        }

        // recarregar a base: clear() tem que limpar o índice também
        file.clear();
        check(file.read(5) == null, name + ": clear manteve id 5 no índice");
        file.create(new Rec(file.nextId()));
        check(file.read(1) != null, name + ": create pós-clear");
    }

    private static void checkInvertedListClear(String base) throws IOException {
        InvertedList list = new InvertedList(base);
        list.insert("Taylor Swift", 1);
        list.clear();
        check(list.search("Taylor Swift").isEmpty(), "termo antigo continuou na lista");
        check(new InvertedList(base).search("Taylor Swift").isEmpty(), "termo antigo continuou no disco");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
