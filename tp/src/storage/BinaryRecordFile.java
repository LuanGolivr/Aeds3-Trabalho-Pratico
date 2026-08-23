package storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Function;

import interfaces.Recordable;
import interfaces.RecordFile;

// ponytail: linear scan per operation (O(n)); upgrade to an id->offset index if lookups get slow
public class BinaryRecordFile<T extends Recordable> implements RecordFile<T> {

    private final RandomAccessFile file;
    private final Function<byte[], T> deserializer;
    private Header header;

    public BinaryRecordFile(String filePath, Function<byte[], T> deserializer) throws IOException {
        this.file = new RandomAccessFile(filePath, "rw");
        this.deserializer = deserializer;
    }

    @Override
    public void createHeader() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int nextId() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void create(T record) throws IOException {
        throw new UnsupportedOperationException();
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
