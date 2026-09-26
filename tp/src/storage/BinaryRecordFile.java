package storage;

import interfaces.Index;
import interfaces.RecordFile;
import interfaces.Recordable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

// sem índice (index == null), read/create/delete fazem varredura linear (O(n)); com um índice
// anexado (ver attachIndex), passam a resolver id -> posição direto por ele
public class BinaryRecordFile<T extends Recordable> implements RecordFile<T> {

    // lápide: marca um registro como logicamente deletado; qualquer outro byte significa válido
    private static final byte TOMBSTONE_DELETED = '*';

    private final RandomAccessFile file;
    private final Function<byte[], T> deserializer;
    private Header header;
    private Index<Integer> index;

    public BinaryRecordFile(String filePath, Function<byte[], T> deserializer) throws IOException {
        this(filePath, deserializer, null);
    }

    public BinaryRecordFile(String filePath, Function<byte[], T> deserializer, Index<Integer> index)
            throws IOException {
        Path parent = Path.of(filePath).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.file = new RandomAccessFile(filePath, "rw");
        this.deserializer = deserializer;
        this.index = index;
        createHeader();
    }

    @Override
    public void createHeader() throws IOException {
        this.file.seek(0);

        if (this.file.length() > 0) {
            this.header = Header.readFrom(this.file);
            return;
        }

        this.header = new Header();
        this.header.writeTo(this.file);
    }

    @Override
    public int nextId() throws IOException {
        int id = this.header.nextId();
        this.file.seek(0);
        this.header.writeTo(this.file);
        return id;
    }

    @Override
    public void create(T record) throws IOException {
        boolean exists = index != null ? index.search(record.id()) != null : read(record.id()) != null;
        if (exists) {
            throw new IllegalArgumentException("Já existe um registro com id " + record.id());
        }

        byte[] data = record.toBytes();
        long offset = this.file.length();
        this.file.seek(offset);
        this.file.writeByte(' ');
        this.file.writeInt(data.length);
        this.file.write(data);

        if (index != null) {
            index.insert(record.id(), offset);
        }

        this.header.recordCreated();
        this.file.seek(0);
        this.header.writeTo(this.file);
    }

    @Override
    public T read(int id) throws IOException {
        if (index != null) {
            Long position = index.search(id);
            return position != null ? readAt(position) : null;
        }

        // Coloca o ponteiro depois do cabeçalho
        this.file.seek(Header.SIZE_IN_BYTES);

        while (this.file.getFilePointer() < this.file.length()) {
            byte tombstone = this.file.readByte();
            int recordSize = this.file.readInt();

            if (tombstone == ' ') {
                byte[] data = new byte[recordSize];
                this.file.read(data); // Lê os bytes do registro
                T record = deserializer.apply(data); // Transforma em objeto

                if (record.id() == id) {
                    return record; // Retorna se o ID for o procurado
                }
            }
            else { // Pula se for '*'
                this.file.skipBytes(recordSize);
            }
        }

        return null;
    }

    private T readAt(long offset) throws IOException {
        this.file.seek(offset);
        byte tombstone = this.file.readByte();
        int length = this.file.readInt();
        byte[] data = new byte[length];
        this.file.readFully(data);
        return tombstone == TOMBSTONE_DELETED ? null : this.deserializer.apply(data);
    }

    @Override
    public List<T> readAll() throws IOException {
        List<T> records = new ArrayList<>();
        this.file.seek(Header.SIZE_IN_BYTES);

        while (this.file.getFilePointer() < this.file.length()) {
            byte tombstone = this.file.readByte();
            int length = this.file.readInt();
            byte[] data = new byte[length];
            this.file.readFully(data);

            if (tombstone != TOMBSTONE_DELETED) {
                records.add(this.deserializer.apply(data));
            }
        }
        return records;
    }

    @Override
    public Iterator<T> iterator() throws IOException {
        this.file.seek(Header.SIZE_IN_BYTES);
        return new Iterator<T>() {
            private T next = advance();

            private T advance() {
                try {
                    while (file.getFilePointer() < file.length()) {
                        byte tombstone = file.readByte();
                        int length = file.readInt();
                        byte[] data = new byte[length];
                        file.readFully(data);
                        if (tombstone != TOMBSTONE_DELETED) {
                            return deserializer.apply(data);
                        }
                    }
                    return null;
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

    @Override
    public void replaceAll(Iterator<T> records) throws IOException {
        this.file.setLength(0);
        this.file.seek(Header.SIZE_IN_BYTES);

        int count = 0;
        while (records.hasNext()) {
            byte[] data = records.next().toBytes();
            this.file.writeByte(' ');
            this.file.writeInt(data.length);
            this.file.write(data);
            count++;
        }

        this.header.resetRecordCount(count);
        this.file.seek(0);
        this.header.writeTo(this.file);
    }

    @Override
    public void clear() throws IOException {
        this.file.setLength(0);
        this.header = new Header();
        this.file.seek(0);
        this.header.writeTo(this.file);
    }

    @Override
    public boolean update(T record) throws IOException {
        // Deleta o registro atual
        if (delete(record.id())) {
            // Se deletado, recria o objeto atualizado no final do arquivo
            create(record);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(int id) throws IOException {
        if (index != null) {
            Long position = index.search(id);
            if (position == null) {
                return false;
            }
            this.file.seek(position);
            this.file.writeByte(TOMBSTONE_DELETED);
            index.remove(id);

            this.header.recordDeleted();
            this.file.seek(0);
            this.header.writeTo(this.file);
            return true;
        }

        this.file.seek(Header.SIZE_IN_BYTES);

        while (this.file.getFilePointer() < this.file.length()) {
            long currentOffset = this.file.getFilePointer(); // Salva a posição antes de ler o registro
            byte tombstone = this.file.readByte();
            int recordSize = this.file.readInt();

            if (tombstone == ' ') {
                byte[] data = new byte[recordSize];
                this.file.read(data);
                T record = deserializer.apply(data);

                if (record.id() == id) {
                    // Volta o ponteiro para o byte exato do marcador deste registro
                    this.file.seek(currentOffset);
                    this.file.writeByte(TOMBSTONE_DELETED); // Sobrescreve com '*'

                    // Atualiza a contagem no cabeçalho e salva no disco
                    this.header.recordDeleted();
                    this.file.seek(0);
                    this.header.writeTo(this.file);

                    return true;
                }
            }
            else {
                this.file.skipBytes(recordSize);
            }
        }
        return false;
    }

    @Override
    public void attachIndex(Index<Integer> newIndex) throws IOException {
        this.file.seek(Header.SIZE_IN_BYTES);
        while (this.file.getFilePointer() < this.file.length()) {
            long offset = this.file.getFilePointer();
            byte tombstone = this.file.readByte();
            int length = this.file.readInt();

            if (tombstone == TOMBSTONE_DELETED) {
                this.file.skipBytes(length);
                continue;
            }

            byte[] data = new byte[length];
            this.file.readFully(data);
            T record = this.deserializer.apply(data);
            newIndex.insert(record.id(), offset);
        }
        this.index = newIndex;
    }

    @Override
    public String activeIndexLabel() {
        return index != null ? index.label() : "nenhum (varredura linear)";
    }
}
