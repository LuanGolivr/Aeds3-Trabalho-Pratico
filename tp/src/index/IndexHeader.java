package index;

import java.io.IOException;
import java.io.RandomAccessFile;

class IndexHeader {

    static final int SIZE_IN_BYTES = Integer.BYTES + Long.BYTES + Long.BYTES; // order, rootOffset, nextNodeOffset

    final int order;
    long rootOffset;
    long nextNodeOffset;

    IndexHeader(int order) {
        this.order = order;
        this.rootOffset = -1; // sem raiz ainda
        this.nextNodeOffset = SIZE_IN_BYTES;
    }

    private IndexHeader(int order, long rootOffset, long nextNodeOffset) {
        this.order = order;
        this.rootOffset = rootOffset;
        this.nextNodeOffset = nextNodeOffset;
    }

    // reserva espaço pro próximo nó no fim do arquivo; nós nunca são reaproveitados (sem free-list)
    long allocateNode() {
        long offset = nextNodeOffset;
        nextNodeOffset += Node.sizeInBytes(order);
        return offset;
    }

    void writeTo(RandomAccessFile file) throws IOException {
        file.seek(0);
        file.writeInt(order);
        file.writeLong(rootOffset);
        file.writeLong(nextNodeOffset);
    }

    static IndexHeader readFrom(RandomAccessFile file) throws IOException {
        file.seek(0);
        int order = file.readInt();
        long rootOffset = file.readLong();
        long nextNodeOffset = file.readLong();
        return new IndexHeader(order, rootOffset, nextNodeOffset);
    }
}
