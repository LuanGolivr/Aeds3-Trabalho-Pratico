package index;

import java.io.IOException;
import java.io.RandomAccessFile;

class PostingBlock {

    static final int CAPACITY = 20; // Capacidade de IDs por bloco no disco
    
    int count;
    final int[] ids;
    long nextBlockOffset;

    PostingBlock() {
        this.count = 0;
        this.ids = new int[CAPACITY];
        this.nextBlockOffset = -1; // -1 significa que não tem próximo bloco
    }

    // Calcula o tamanho exato do bloco em bytes
    static int sizeInBytes() {
        return Integer.BYTES + (Integer.BYTES * CAPACITY) + Long.BYTES;
    }

    void writeTo(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);
        file.writeInt(count);
        for (int i = 0; i < CAPACITY; i++) {
            file.writeInt(ids[i]);
        }
        file.writeLong(nextBlockOffset);
    }

    static PostingBlock readFrom(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);
        PostingBlock block = new PostingBlock();
        block.count = file.readInt();
        
        for (int i = 0; i < CAPACITY; i++) {
            block.ids[i] = file.readInt();
        }
        block.nextBlockOffset = file.readLong();
        return block;
    }

    boolean insert(int id) {
        if (count < CAPACITY) {
            ids[count] = id;
            count++;
            return true;
        }
        return false;
    }

    boolean remove(int id) {
        for (int i = 0; i < count; i++) {
            if (ids[i] == id) {
                // Substitui o ID removido pelo último ID válido do array para não deixar buracos
                ids[i] = ids[count - 1];
                count--;
                return true;
            }
        }
        return false;
    }
}