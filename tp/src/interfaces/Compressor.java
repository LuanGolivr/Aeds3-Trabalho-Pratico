package interfaces;

import java.io.IOException;

// algoritmo de compressão sem perdas que trabalha sobre os bytes crus de um arquivo
// (implementado por compression.Huffman e compression.Lzw)
public interface Compressor {

    // nome usado no arquivo gerado ("songs" + nome + "CompressaoX") e nas mensagens ao usuário
    String name();

    // devolve os bytes comprimidos; o resultado é autossuficiente (carrega tudo que a
    // descompressão precisa, ex.: tamanho original e tabela de frequências)
    byte[] compress(byte[] data) throws IOException;

    // inverso exato de compress; lança IOException se os dados estiverem truncados/corrompidos
    byte[] decompress(byte[] data) throws IOException;
}
