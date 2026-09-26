package interfaces;

import java.io.IOException;

// índice chave única -> posição no arquivo de dados (ex.: id -> offset)
public interface Index<K> {

    void insert(K key, long position) throws IOException;

    // null se a chave não existir
    Long search(K key) throws IOException;

    boolean remove(K key) throws IOException;

    // nome da estrutura, pra indicar ao usuário qual índice atendeu a operação (ex.: "Árvore B+")
    String label();
}
