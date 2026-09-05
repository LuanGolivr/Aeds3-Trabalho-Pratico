package storage;

import java.io.IOException;
import java.io.RandomAccessFile;

public class Header {

    public static final int SIZE_IN_BYTES = Integer.BYTES * 2; // lastId + recordCount

    private int lastId;
    private int recordCount;

    public Header() {
        this.lastId = 0;
        this.recordCount = 0;
    }

    private Header(int lastId, int recordCount) {
        this.lastId = lastId;
        this.recordCount = recordCount;
    }

    public int nextId() {
        lastId++;
        return lastId;
    }

    public void recordCreated() {
        recordCount++;
    }

    public void recordDeleted() {
        recordCount--;
    }

    // usado depois de reescrever o arquivo do zero (ex.: compactação pela ordenação externa)
    public void resetRecordCount(int count) {
        this.recordCount = count;
    }

    public void writeTo(RandomAccessFile file) throws IOException {
        file.writeInt(lastId);
        file.writeInt(recordCount);
    }

    public static Header readFrom(RandomAccessFile file) throws IOException {
        int lastId = file.readInt();
        int recordCount = file.readInt();
        return new Header(lastId, recordCount);
    }
}
