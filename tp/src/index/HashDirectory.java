package index;

import java.io.IOException;
import java.io.RandomAccessFile;

class HashDirectory {

    int globalDepth;
    long[] bucketOffsets;
    
    HashDirectory(int globalDepth) {
        this.globalDepth = globalDepth;
        int size = 1 << globalDepth;
        this.bucketOffsets = new long[size];
    }

    void doubleDirectory() {
        int oldSize = 1 << globalDepth;
        globalDepth++;
        int newSize = 1 << globalDepth;
        
        long[] newOffsets = new long[newSize];
        for (int i = 0; i < oldSize; i++) {
            newOffsets[i] = bucketOffsets[i];
            newOffsets[i + oldSize] = bucketOffsets[i]; // Os novos índices apontam para os mesmos buckets
        }
        this.bucketOffsets = newOffsets;
    }

    void writeTo(RandomAccessFile file) throws IOException {
        file.seek(0);
        file.writeInt(globalDepth);
        file.writeInt(bucketOffsets.length);
        for (long offset : bucketOffsets) {
            file.writeLong(offset);
        }
    }

    static HashDirectory readFrom(RandomAccessFile file) throws IOException {
        file.seek(0);
        if (file.length() == 0) return null;
        
        int globalDepth = file.readInt();
        int size = file.readInt();
        
        HashDirectory dir = new HashDirectory(globalDepth);
        dir.bucketOffsets = new long[size];
        for (int i = 0; i < size; i++) {
            dir.bucketOffsets[i] = file.readLong();
        }
        return dir;
    }
}