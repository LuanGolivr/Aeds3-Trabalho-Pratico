package storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Function;

import interfaces.Recordable;
import interfaces.RecordFile;

// ponytail: linear scan per operation (O(n)); upgrade to an id->offset index if lookups get slow
public class BinaryRecordFile<T extends Recordable> implements RecordFile<T> {

    private final RandomAccessFile file;
    private final Function<byte[], T> deserializer;
    private Header header;

    public BinaryRecordFile(String filePath, Function<byte[], T> deserializer) throws IOException {
        this.file = new RandomAccessFile(filePath, "rw");
        this.deserializer = deserializer;
        if (file.length() == 0) {
            createHeader();
        } else {
            file.seek(0);
            header = Header.readFrom(file);
        }
    }

    @Override
    public void createHeader() throws IOException {
        header = new Header();
        file.seek(0);
        header.writeTo(file);
    }

    @Override
    public int nextId() throws IOException {
        int id = header.nextId();
        writeHeader();
        return id;
    }

    @Override
    public void create(T record) throws IOException {
        byte[] data = record.toBytes();
        file.seek(file.length());
        file.writeBoolean(false); // deleted flag
        file.writeInt(data.length);
        file.write(data);
        header.recordCreated();
        writeHeader();
    }

    @Override
    public T read(int id) throws IOException {
        file.seek(Header.SIZE_IN_BYTES);
        while (file.getFilePointer() < file.length()) {
            boolean deleted = file.readBoolean();
            byte[] data = new byte[file.readInt()];
            file.readFully(data);
            if (!deleted) {
                T record = deserializer.apply(data);
                if (record.id() == id) {
                    return record;
                }
            }
        }
        return null;
    }

    @Override
    public boolean update(T record) throws IOException {
        file.seek(Header.SIZE_IN_BYTES);
        while (file.getFilePointer() < file.length()) {
            long flagPosition = file.getFilePointer();
            boolean deleted = file.readBoolean();
            int length = file.readInt();
            long dataPosition = file.getFilePointer();
            byte[] data = new byte[length];
            file.readFully(data);
            if (!deleted && deserializer.apply(data).id() == record.id()) {
                byte[] newData = record.toBytes();
                if (newData.length <= length) {
                    file.seek(dataPosition);
                    file.write(newData);
                } else {
                    // doesn't fit in place: tombstone this slot and append the new version at the end
                    file.seek(flagPosition);
                    file.writeBoolean(true);
                    file.seek(file.length());
                    file.writeBoolean(false);
                    file.writeInt(newData.length);
                    file.write(newData);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean delete(int id) throws IOException {
        file.seek(Header.SIZE_IN_BYTES);
        while (file.getFilePointer() < file.length()) {
            long flagPosition = file.getFilePointer();
            boolean deleted = file.readBoolean();
            byte[] data = new byte[file.readInt()];
            file.readFully(data);
            if (!deleted && deserializer.apply(data).id() == id) {
                file.seek(flagPosition);
                file.writeBoolean(true);
                header.recordDeleted();
                writeHeader();
                return true;
            }
        }
        return false;
    }

    private void writeHeader() throws IOException {
        file.seek(0);
        header.writeTo(file);
    }
}
