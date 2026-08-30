package storage;

import interfaces.RecordFile;
import interfaces.Recordable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Function;

// ponytail: linear scan per operation (O(n)); upgrade to an id->offset index if lookups get slow
public class BinaryRecordFile<T extends Recordable> implements RecordFile<T> {

    // lápide: marca um registro como logicamente deletado; qualquer outro byte significa válido
    private static final byte TOMBSTONE_DELETED = '*';

    private final RandomAccessFile file;
    private final Function<byte[], T> deserializer;
    private Header header;

    public BinaryRecordFile(String filePath, Function<byte[], T> deserializer) throws IOException {
        this.file = new RandomAccessFile(filePath, "rw");
        this.deserializer = deserializer;
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
        if (read(record.id()) != null) {
            throw new IllegalArgumentException("Já existe um registro com id " + record.id());
        }

        byte[] data = record.toBytes();
        this.file.seek(this.file.length());
        this.file.writeByte(' ');
        this.file.writeInt(data.length);
        this.file.write(data);

        this.header.recordCreated();
        this.file.seek(0);
        this.header.writeTo(this.file);
    }

    @Override
    public T read(int id) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean update(T record) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean delete(int id) throws IOException {
        throw new UnsupportedOperationException();
    }
}
