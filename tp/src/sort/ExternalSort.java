package sort;

import interfaces.Recordable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

// fachada da ordenação externa: fase 1 (seleção por substituição) + fase 2 (intercalação polifásica).
public class ExternalSort<T extends Recordable> {

    private final int heapCapacity;
    private final int ways;
    private final Comparator<T> comparator;
    private final Function<byte[], T> deserializer;
    private final String workDir;

    public ExternalSort(
            int heapCapacity,
            int ways,
            Comparator<T> comparator,
            Function<byte[], T> deserializer,
            String workDir) {
        this.heapCapacity = heapCapacity;
        this.ways = ways;
        this.comparator = comparator;
        this.deserializer = deserializer;
        this.workDir = workDir;
    }

    // varre `input` uma única vez e devolve uma fita rebobinada com os registros em ordem.
    // quem chamar é responsável por ler até o fim (Tape#read retorna null) e depois fechar/apagar a fita.
    public Tape<T> sort(Iterator<T> input) throws IOException {
        Tape<T> staging = new Tape<>(workDir + "/staging.tmp", deserializer);
        ReplacementSelection<T> replacementSelection = new ReplacementSelection<>(heapCapacity, comparator);
        List<Integer> runLengths = replacementSelection.generateRuns(input, staging);

        PolyphaseMerge<T> polyphaseMerge = new PolyphaseMerge<>(ways, comparator, deserializer, workDir);
        return polyphaseMerge.merge(staging, runLengths);
    }

    // self-check: gera uma entrada pequena embaralhada, ordena e confere contra Arrays.sort.
    public static void main(String[] args) throws IOException {
        record IntRecord(int value) implements Recordable {
            @Override
            public int id() {
                return value;
            }

            @Override
            public byte[] toBytes() {
                return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
            }
        }

        Function<byte[], IntRecord> fromBytes = bytes -> new IntRecord(ByteBuffer.wrap(bytes).getInt());

        int[] input = {5, 2, 8, 1, 9, 3, 7, 4, 6, 0, 20, 15, 12, 18, 11, 14, 17, 13, 19, 16};
        List<IntRecord> records = new ArrayList<>();
        for (int v : input) {
            records.add(new IntRecord(v));
        }

        File workDir = Files.createTempDirectory("external-sort-check").toFile();
        try {
            ExternalSort<IntRecord> sorter = new ExternalSort<>(
                    3, 2, Comparator.comparingInt(IntRecord::value), fromBytes, workDir.getPath());

            Tape<IntRecord> sorted = sorter.sort(records.iterator());

            int[] expected = input.clone();
            Arrays.sort(expected);

            List<Integer> actual = new ArrayList<>();
            IntRecord record;
            while ((record = sorted.read()) != null) {
                actual.add(record.value());
            }
            sorted.close();
            sorted.delete();

            if (actual.size() != expected.length) {
                throw new AssertionError(
                        "quantidade errada: esperado " + expected.length + ", obtido " + actual.size());
            }
            for (int i = 0; i < expected.length; i++) {
                if (actual.get(i) != expected[i]) {
                    throw new AssertionError("ordenação incorreta na posição " + i
                            + ": esperado " + expected[i] + ", obtido " + actual.get(i));
                }
            }
            System.out.println("ExternalSort: OK (" + expected.length + " registros ordenados)");
        } finally {
            File[] leftovers = workDir.listFiles();
            if (leftovers != null) {
                for (File f : leftovers) {
                    f.delete();
                }
            }
            workDir.delete();
        }
    }
}
