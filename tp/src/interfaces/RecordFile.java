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

    // substitui todo o conteúdo do arquivo pelos registros dados (ex.: resultado já ordenado e
    // compactado da ordenação externa), preservando a contagem de próximo id
    void replaceAll(Iterator<T> records) throws IOException;

    // apaga todo o conteúdo do arquivo e reseta o header (nextId volta a começar do zero) —
    // usado ao recarregar a base de dados do zero
    void clear() throws IOException;

    boolean update(T record) throws IOException;

    boolean delete(int id) throws IOException;

    // reconstrói o índice do zero varrendo o arquivo de dados e passa a usá-lo nas operações
    // seguintes (create/read/update/delete), substituindo o índice ativo anterior, se houver
    void attachIndex(Index<Integer> index) throws IOException;

    // "Árvore B+ (ordem N)" com índice ativo, ou "nenhum (varredura linear)" sem índice
    String activeIndexLabel();
}
