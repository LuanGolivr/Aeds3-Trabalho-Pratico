package interfaces;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public interface RecordFile<T extends Recordable> {

    void createHeader() throws IOException;

    int nextId() throws IOException;

    void create(T record) throws IOException;

    T read(int id) throws IOException;

    List<T> readAll() throws IOException;

    // lê os registros válidos um de cada vez, direto do disco, sem carregar tudo em memória
    Iterator<T> iterator() throws IOException;

    boolean update(T record) throws IOException;

    boolean delete(int id) throws IOException;
}
