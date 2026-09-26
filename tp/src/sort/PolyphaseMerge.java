package sort;

import interfaces.Recordable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;

// fase 2 da ordenação externa: distribui as runs da fase 1 entre `ways` fitas de entrada segundo
// o Fibonacci generalizado (distribuição "ideal" da polifásica) e intercala em passadas,
// reaproveitando a fita que esvazia a cada passada como a próxima fita de saída.
// total de fitas usadas: ways + 1 (nunca mais que isso).
public class PolyphaseMerge<T extends Recordable> {

    // um item do heap de merge de uma rodada: o registro lido + de qual fita ele veio
    private static final class HeapItem<T> {
        final T record;
        final int tapeIndex;

        HeapItem(T record, int tapeIndex) {
            this.record = record;
            this.tapeIndex = tapeIndex;
        }
    }

    private final int ways;
    private final Comparator<T> comparator;
    private final Function<byte[], T> deserializer;
    private final String workDir;

    public PolyphaseMerge(int ways, Comparator<T> comparator, Function<byte[], T> deserializer, String workDir) {
        if (ways < 2) {
            throw new IllegalArgumentException("polifásica precisa de pelo menos 2 fitas de entrada (ways >= 2)");
        }
        this.ways = ways;
        this.comparator = comparator;
        this.deserializer = deserializer;
        this.workDir = workDir;
    }

    // gera o vetor de Fibonacci de ordem `ways`: quantas runs cada fita de entrada "deveria" ter
    // pra que todas esvaziem em momentos diferentes e nenhuma fita extra seja necessária.
    // nivel(0) = [1,1,...,1,0]; nivel(k)[0] = soma(nivel(k-1)); nivel(k)[i] = nivel(k-1)[i-1]
    private long[] idealDistribution(int totalRuns) {
        long[] level = new long[ways];
        java.util.Arrays.fill(level, 1);
        level[ways - 1] = 0;

        while (sum(level) < totalRuns) {
            long total = sum(level);
            long[] next = new long[ways];
            next[0] = total;
            for (int i = 1; i < ways; i++) {
                next[i] = level[i - 1];
            }
            level = next;
        }
        return level;
    }

    private static long sum(long[] values) {
        long total = 0;
        for (long v : values) {
            total += v;
        }
        return total;
    }

    // ordena `staging` (que contém runLengths.size() runs, uma após a outra) e devolve uma fita
    // rebobinada com todos os registros em ordem. quem chamar é responsável por ler até o fim
    // (Tape#read retorna null) e depois fechar/apagar a fita.
    public Tape<T> merge(Tape<T> staging, List<Integer> runLengths) throws IOException {
        int totalRuns = runLengths.size();
        if (totalRuns == 0) {
            staging.rewind();
            return staging;
        }

        long[] target = idealDistribution(totalRuns);

        List<Tape<T>> inputTapes = new ArrayList<>();
        List<Deque<Integer>> inputRuns = new ArrayList<>();

        staging.rewind();
        Iterator<Integer> lengths = runLengths.iterator();

        for (int i = 0; i < ways; i++) {
            long quota = target[i];
            if (quota == 0) {
                continue; // essa fita não é necessária pra essa distribuição (entrada pequena)
            }

            Tape<T> tape = new Tape<>(workDir + "/fita_" + i + ".tmp", deserializer);
            Deque<Integer> runs = new ArrayDeque<>();

            long real = 0;
            while (real < quota && lengths.hasNext()) {
                int length = lengths.next();
                for (int r = 0; r < length; r++) {
                    tape.write(staging.read());
                }
                runs.addLast(length);
                real++;
            }
            for (long dummy = real; dummy < quota; dummy++) {
                runs.addLast(0); // run "dummy": conta como run mas não tem dado (comprimento 0)
            }

            tape.rewind();
            inputTapes.add(tape);
            inputRuns.add(runs);
        }
        staging.delete();

        Tape<T> outputTape = new Tape<>(workDir + "/fita_" + ways + ".tmp", deserializer);

        while (countNonEmpty(inputRuns) > 1) {
            List<Integer> newOutputRuns = new ArrayList<>();

            while (allHaveRuns(inputRuns)) {
                newOutputRuns.add(mergeOneRound(inputTapes, inputRuns, outputTape));
            }

            // mais de uma fita pode esvaziar no mesmo round (empate); todas saem da entrada,
            // e só uma delas vira a próxima fita de saída — as demais ficam ociosas (sobra de fita).
            List<Tape<T>> emptied = removeEmptyTapes(inputTapes, inputRuns);
            for (Tape<T> tape : emptied) {
                tape.truncate();
            }

            outputTape.rewind();
            inputTapes.add(outputTape);
            inputRuns.add(new ArrayDeque<>(newOutputRuns));

            Iterator<Tape<T>> spareTapes = emptied.iterator();
            outputTape = spareTapes.next();
            while (spareTapes.hasNext()) {
                Tape<T> surplus = spareTapes.next();
                surplus.close();
                surplus.delete();
            }
        }

        outputTape.close();
        outputTape.delete(); // sobrou vazia e sem uso — não faz parte do resultado

        int sortedIndex = indexOfNonEmpty(inputRuns);
        Tape<T> sorted = inputTapes.get(sortedIndex);
        sorted.rewind();
        return sorted;
    }

    // intercala 1 run de cada fita ativa nesta rodada (pulando as que estão numa run dummy) e
    // grava o resultado, como uma única run maior, na fita de saída. devolve o tamanho dessa run.
    private int mergeOneRound(List<Tape<T>> tapes, List<Deque<Integer>> runs, Tape<T> output) throws IOException {
        int n = tapes.size();
        int[] remaining = new int[n];
        PriorityQueue<HeapItem<T>> heap = new PriorityQueue<>((a, b) -> comparator.compare(a.record, b.record));

        for (int i = 0; i < n; i++) {
            remaining[i] = runs.get(i).poll();
            if (remaining[i] > 0) {
                heap.add(new HeapItem<>(tapes.get(i).read(), i));
            }
        }

        int mergedLength = 0;
        while (!heap.isEmpty()) {
            HeapItem<T> min = heap.poll();
            output.write(min.record);
            mergedLength++;

            remaining[min.tapeIndex]--;
            if (remaining[min.tapeIndex] > 0) {
                heap.add(new HeapItem<>(tapes.get(min.tapeIndex).read(), min.tapeIndex));
            }
        }
        return mergedLength;
    }

    private static int countNonEmpty(List<Deque<Integer>> runs) {
        int count = 0;
        for (Deque<Integer> r : runs) {
            if (!r.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static boolean allHaveRuns(List<Deque<Integer>> runs) {
        for (Deque<Integer> r : runs) {
            if (r.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // remove (e devolve) todas as fitas cuja fila de runs esvaziou nesta passada
    private List<Tape<T>> removeEmptyTapes(List<Tape<T>> tapes, List<Deque<Integer>> runs) {
        List<Tape<T>> removed = new ArrayList<>();
        for (int i = tapes.size() - 1; i >= 0; i--) {
            if (runs.get(i).isEmpty()) {
                removed.add(0, tapes.remove(i));
                runs.remove(i);
            }
        }
        return removed;
    }

    private static int indexOfNonEmpty(List<Deque<Integer>> runs) {
        for (int i = 0; i < runs.size(); i++) {
            if (!runs.get(i).isEmpty()) {
                return i;
            }
        }
        throw new IllegalStateException("nenhuma fita com dados encontrada");
    }
}
