package sort;

import interfaces.Recordable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.function.Function;

// arquivo de trabalho sequencial: registros gravados como (int tamanho + bytes), um após o outro.
// sem header nem lápide — é usado só internamente pelo módulo de ordenação externa.
public class Tape<T extends Recordable> {

    private final String path;
    private final RandomAccessFile file;
    private final Function<byte[], T> deserializer;

    public Tape(String path, Function<byte[], T> deserializer) throws IOException {
        this.path = path;
        this.deserializer = deserializer;
        this.file = new RandomAccessFile(path, "rw");
        this.file.setLength(0);
    }

    public void write(T record) throws IOException {
        byte[] data = record.toBytes();
        file.writeInt(data.length);
        file.write(data);
    }

    // retorna null ao chegar no fim da fita
    public T read() throws IOException {
        try {
            int length = file.readInt();
            byte[] data = new byte[length];
            file.readFully(data);
            return deserializer.apply(data);
        } catch (EOFException e) {
            return null;
        }
    }

    // lê o restante da fita a partir da posição atual, um registro por vez
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private T next = advance();

            private T advance() {
                try {
                    return read();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public T next() {
                T current = next;
                next = advance();
                return current;
            }
        };
    }

    public void rewind() throws IOException {
        file.seek(0);
    }

    public void truncate() throws IOException {
        file.setLength(0);
        file.seek(0);
    }

    public void close() throws IOException {
        file.close();
    }

    public void delete() throws IOException {
        file.close();
        new File(path).delete();
    }
}
