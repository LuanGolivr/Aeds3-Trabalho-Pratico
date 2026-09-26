package index;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

class HashBucket {

    int localDepth;
    int count;
    final int[] keys;
    final long[] pointers;
    final int capacity;

    HashBucket(int capacity, int localDepth) {
        this.capacity = capacity;
        this.localDepth = localDepth;
        this.count = 0;
        this.keys = new int[capacity];
        this.pointers = new long[capacity];
        Arrays.fill(this.pointers, -1);
    }

    // Calcula o tamanho exato do bucket em bytes no arquivo
    static int sizeInBytes(int capacity) {
        return Integer.BYTES + Integer.BYTES + (Integer.BYTES * capacity) + (Long.BYTES * capacity);
    }

    void writeTo(RandomAccessFile file, long offset) throws IOException {
        file.seek(offset);
        file.writeInt(localDepth);
        file.writeInt(count);
        for (int i = 0; i < capacity; i++) {
            file.writeInt(keys[i]);
        }
        for (int i = 0; i < capacity; i++) {
            file.writeLong(pointers[i]);
        }
    }

    static HashBucket readFrom(RandomAccessFile file, long offset, int capacity) throws IOException {
        file.seek(offset);
        int localDepth = file.readInt();
        HashBucket bucket = new HashBucket(capacity, localDepth);
        bucket.count = file.readInt();
        
        for (int i = 0; i < capacity; i++) {
            bucket.keys[i] = file.readInt();
        }
        for (int i = 0; i < capacity; i++) {
            bucket.pointers[i] = file.readLong();
        }
        return bucket;
    }

    void insert(int key, long pointer) {
        keys[count] = key;
        pointers[count] = pointer;
        count++;
    }
    
    boolean remove(int key) {
        for (int i = 0; i < count; i++) {
            if (keys[i] == key) {
                // Move o último elemento para a posição removida para não deixar buracos
                keys[i] = keys[count - 1];
                pointers[i] = pointers[count - 1];
                count--;
                return true;
            }
        }
        return false;
    }

    Long search(int key) {
        for (int i = 0; i < count; i++) {
            if (keys[i] == key) {
                return pointers[i];
            }
        }
        return null;
    }
}