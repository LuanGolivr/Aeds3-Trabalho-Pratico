package sort;

import interfaces.Recordable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

// fase 1 da ordenação externa: consome o arquivo de entrada uma única vez, mantendo um heap de
// tamanho fixo, e escreve na fita de staging uma sequência de runs (trechos já ordenados),
// em geral bem mais longas que o próprio heap.
public class ReplacementSelection<T extends Recordable> {

    // etiqueta run: elementos da run atual sempre saem do heap antes dos da próxima run,
    // não importa o valor da chave — é isso que separa uma run da outra.
    private static final class HeapEntry<T> {
        final T record;
        final int run;

        HeapEntry(T record, int run) {
            this.record = record;
            this.run = run;
        }
    }

    private final int heapCapacity;
    private final Comparator<T> comparator;

    public ReplacementSelection(int heapCapacity, Comparator<T> comparator) {
        if (heapCapacity <= 0) {
            throw new IllegalArgumentException("heapCapacity precisa ser > 0");
        }
        this.heapCapacity = heapCapacity;
        this.comparator = comparator;
    }

    // devolve o tamanho de cada run, na ordem em que foram gravadas na fita de staging
    public List<Integer> generateRuns(Iterator<T> input, Tape<T> staging) throws IOException {
        Comparator<HeapEntry<T>> heapOrder = (a, b) -> a.run != b.run
                ? Integer.compare(a.run, b.run)
                : comparator.compare(a.record, b.record);
        PriorityQueue<HeapEntry<T>> heap = new PriorityQueue<>(heapCapacity, heapOrder);

        for (int i = 0; i < heapCapacity && input.hasNext(); i++) {
            heap.add(new HeapEntry<>(input.next(), 0));
        }

        List<Integer> runLengths = new ArrayList<>();
        int currentRun = 0;
        int currentRunLength = 0;

        while (!heap.isEmpty()) {
            HeapEntry<T> min = heap.poll();

            if (min.run != currentRun) {
                runLengths.add(currentRunLength);
                currentRun = min.run;
                currentRunLength = 0;
            }

            staging.write(min.record);
            currentRunLength++;

            if (input.hasNext()) {
                T next = input.next();
                int nextRun = comparator.compare(next, min.record) >= 0 ? currentRun : currentRun + 1;
                heap.add(new HeapEntry<>(next, nextRun));
            }
        }

        if (currentRunLength > 0) {
            runLengths.add(currentRunLength);
        }
        return runLengths;
    }
}
