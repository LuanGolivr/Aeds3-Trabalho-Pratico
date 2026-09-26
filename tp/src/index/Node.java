package index;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

// nó da árvore B+, tamanho fixo em disco (calculado a partir da ordem).
// internos: pointers[0..count] são offsets dos filhos.
// folhas: pointers[0..count-1] são posições no arquivo de dados; pointers[order-1] (última posição,
// nunca usada por uma folha cheia como valor) é o offset da próxima folha, formando a lista encadeada.
class Node {

    boolean leaf;
    int count;
    final int[] keys;      // capacidade order - 1
    final long[] pointers; // capacidade order

    Node(int order, boolean leaf) {
        this.leaf = leaf;
        this.count = 0;
        this.keys = new int[order - 1];
        this.pointers = new long[order];
        Arrays.fill(this.pointers, -1);
    }

    static int sizeInBytes(int order) {
        return 1 + Integer.BYTES + Integer.BYTES * (order - 1) + Long.BYTES * order;
    }

    long nextLeaf() {
        return pointers[pointers.length - 1];
    }

    void setNextLeaf(long offset) {
        pointers[pointers.length - 1] = offset;
    }

    void writeTo(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);
        file.writeByte(leaf ? 1 : 0);
        file.writeInt(count);
        for (int key : keys) {
            file.writeInt(key);
        }
        for (long pointer : pointers) {
            file.writeLong(pointer);
        }
    }

    static Node readFrom(RandomAccessFile file, long offset, int order) throws IOException {
        file.seek(offset);
        Node node = new Node(order, file.readByte() == 1);
        node.count = file.readInt();
        for (int i = 0; i < node.keys.length; i++) {
            node.keys[i] = file.readInt();
        }
        for (int i = 0; i < node.pointers.length; i++) {
            node.pointers[i] = file.readLong();
        }
        return node;
    }
}
