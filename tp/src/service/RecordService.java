package service;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import interfaces.Recordable;
import interfaces.RecordFile;

public class RecordService<T extends Recordable> {

    private final RecordFile<T> file;

    public RecordService(RecordFile<T> file) {
        this.file = file;
    }

    public int nextId() throws IOException {
        return file.nextId();
    }

    public void create(T record) throws IOException {
        file.create(record);
    }

    public T search(int id) throws IOException {
        return file.read(id);
    }

    public List<T> readAll() throws IOException {
        return file.readAll();
    }

    public Iterator<T> iterator() throws IOException {
        return file.iterator();
    }

    public void replaceAll(Iterator<T> records) throws IOException {
        file.replaceAll(records);
    }

    public void clear() throws IOException {
        file.clear();
    }

    public boolean update(T record) throws IOException {
        return file.update(record);
    }

    public boolean delete(int id) throws IOException {
        return file.delete(id);
    }
}
