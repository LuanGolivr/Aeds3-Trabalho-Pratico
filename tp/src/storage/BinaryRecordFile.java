package storage;

import interfaces.RecordFile;
import interfaces.Recordable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

// ponytail: linear scan per operation (O(n)); upgrade to an id->offset index if lookups get slow
public class BinaryRecordFile<T extends Recordable> implements RecordFile<T> {

    // lápide: marca um registro como logicamente deletado; qualquer outro byte significa válido
    private static final byte TOMBSTONE_DELETED = '*';

    private final RandomAccessFile file;
    private final Function<byte[], T> deserializer;
    private Header header;

    public BinaryRecordFile(String filePath, Function<byte[], T> deserializer) throws IOException {
        this.file = new RandomAccessFile(filePath, "rw");
        this.deserializer = deserializer;
        createHeader();
    }

    @Override
    public void createHeader() throws IOException {
        this.file.seek(0);

        if (this.file.length() > 0) {
            this.header = Header.readFrom(this.file);
            return;
        }

        this.header = new Header();
        this.header.writeTo(this.file);
    }

    @Override
    public int nextId() throws IOException {
        int id = this.header.nextId();
        this.file.seek(0);
        this.header.writeTo(this.file);
        return id;
    }

    @Override
    public void create(T record) throws IOException {
        if (read(record.id()) != null) {
            throw new IllegalArgumentException("Já existe um registro com id " + record.id());
        }

        byte[] data = record.toBytes();
        this.file.seek(this.file.length());
        this.file.writeByte(' ');
        this.file.writeInt(data.length);
        this.file.write(data);

        this.header.recordCreated();
        this.file.seek(0);
        this.header.writeTo(this.file);
    }

    @Override
    public T read(int id) throws IOException {
        // Coloca o ponteiro depois do cabeçalho
        this.file.seek(Header.SIZE_IN_BYTES); 
        
        while (this.file.getFilePointer() < this.file.length()) {
            byte tombstone = this.file.readByte();
            int recordSize = this.file.readInt();
            
            if (tombstone == ' ') { 
                byte[] data = new byte[recordSize];
                this.file.read(data); // Lê os bytes do registro
                T record = deserializer.apply(data); // Transforma em objeto
                
                if (record.id() == id) {
                    return record; // Retorna se o ID for o procurado
                }
            }
            else { // Pula se for '*'
                this.file.skipBytes(recordSize);
            }
        }
        
        return null;
    }

    @Override
    public List<T> readAll() throws IOException {
        List<T> records = new ArrayList<>();
        this.file.seek(Header.SIZE_IN_BYTES);

        while (this.file.getFilePointer() < this.file.length()) {
            byte tombstone = this.file.readByte();
            int length = this.file.readInt();
            byte[] data = new byte[length];
            this.file.readFully(data);

            if (tombstone != TOMBSTONE_DELETED) {
                records.add(this.deserializer.apply(data));
            }
        }
        return records;
    }

    @Override
    public Iterator<T> iterator() throws IOException {
        this.file.seek(Header.SIZE_IN_BYTES);
        return new Iterator<T>() {
            private T next = advance();

            private T advance() {
                try {
                    while (file.getFilePointer() < file.length()) {
                        byte tombstone = file.readByte();
                        int length = file.readInt();
                        byte[] data = new byte[length];
                        file.readFully(data);
                        if (tombstone != TOMBSTONE_DELETED) {
                            return deserializer.apply(data);
                        }
                    }
                    return null;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public T next() {
                T current = next;
                next = advance();
                return current;
            }
        };
    }

    @Override
    public void replaceAll(Iterator<T> records) throws IOException {
        this.file.setLength(0);
        this.file.seek(Header.SIZE_IN_BYTES);

        int count = 0;
        while (records.hasNext()) {
            byte[] data = records.next().toBytes();
            this.file.writeByte(' ');
            this.file.writeInt(data.length);
            this.file.write(data);
            count++;
        }

        this.header.resetRecordCount(count);
        this.file.seek(0);
        this.header.writeTo(this.file);
    }

    @Override
    public boolean update(T record) throws IOException {
        // Deleta o registro atual
        if (delete(record.id())) {
            // Se deletado, recria o objeto atualizado no final do arquivo
            create(record);
            return true;
        }
        return false;
    }

    @Override
    public boolean delete(int id) throws IOException {
        this.file.seek(Header.SIZE_IN_BYTES); 
        
        while (this.file.getFilePointer() < this.file.length()) {
            long currentOffset = this.file.getFilePointer(); // Salva a posição antes de ler o registro
            byte tombstone = this.file.readByte();
            int recordSize = this.file.readInt();
            
            if (tombstone == ' ') {
                byte[] data = new byte[recordSize];
                this.file.read(data);
                T record = deserializer.apply(data);
                
                if (record.id() == id) {
                    // Volta o ponteiro para o byte exato do marcador deste registro
                    this.file.seek(currentOffset);
                    this.file.writeByte(TOMBSTONE_DELETED); // Sobrescreve com '*'
                    
                    // Atualiza a contagem no cabeçalho e salva no disco
                    this.header.recordDeleted();
                    this.file.seek(0);
                    this.header.writeTo(this.file);
                    
                    return true;
                }
            }
            else {
                this.file.skipBytes(recordSize);
            }
        }
        return false;
    }
}
