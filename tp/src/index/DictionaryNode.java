package index;

import java.io.IOException;
import java.io.RandomAccessFile;

class DictionaryNode {

    String term;
    long firstBlockOffset;

    DictionaryNode(String term, long firstBlockOffset) {
        this.term = term;
        this.firstBlockOffset = firstBlockOffset;
    }

    // Lê um nó do ficheiro a partir da posição atual do ponteiro
    static DictionaryNode readFrom(RandomAccessFile file) throws IOException {
        String term = file.readUTF();
        long offset = file.readLong();
        return new DictionaryNode(term, offset);
    }

    // Escreve o nó no ficheiro na posição atual do ponteiro
    void writeTo(RandomAccessFile file) throws IOException {
        file.writeUTF(term);
        file.writeLong(firstBlockOffset);
    }
    
    // Atualiza o ponteiro do primeiro bloco
    void setFirstBlockOffset(long firstBlockOffset) {
        this.firstBlockOffset = firstBlockOffset;
    }
}