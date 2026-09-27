package index;

import interfaces.Index;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExtendibleHashIndex implements Index<Integer> {

    private final RandomAccessFile dirFile;
    private final RandomAccessFile bucketFile;
    private final HashDirectory directory;
    private final int bucketCapacity;

    private ExtendibleHashIndex(RandomAccessFile dirFile, RandomAccessFile bucketFile, HashDirectory directory, int bucketCapacity) {
        this.dirFile = dirFile;
        this.bucketFile = bucketFile;
        this.directory = directory;
        this.bucketCapacity = bucketCapacity;
    }

    public static ExtendibleHashIndex create(String basePath, int bucketCapacity) throws IOException {
        Path dirPath = Path.of(basePath + ".dir").toAbsolutePath();
        Path bucketPath = Path.of(basePath + ".bkt").toAbsolutePath();
        
        if (dirPath.getParent() != null) Files.createDirectories(dirPath.getParent());
        
        Files.deleteIfExists(dirPath);
        Files.deleteIfExists(bucketPath);

        RandomAccessFile dFile = new RandomAccessFile(dirPath.toFile(), "rw");
        RandomAccessFile bFile = new RandomAccessFile(bucketPath.toFile(), "rw");

        // Inicializa com profundidade global 1 (2 espaços no diretório)
        HashDirectory dir = new HashDirectory(1);
        
        // Cria os dois buckets iniciais
        HashBucket b0 = new HashBucket(bucketCapacity, 1);
        HashBucket b1 = new HashBucket(bucketCapacity, 1);
        
        long offset0 = 0;
        long offset1 = HashBucket.sizeInBytes(bucketCapacity);
        
        b0.writeTo(bFile, offset0);
        b1.writeTo(bFile, offset1);
        
        dir.bucketOffsets[0] = offset0;
        dir.bucketOffsets[1] = offset1;
        dir.writeTo(dFile);

        return new ExtendibleHashIndex(dFile, bFile, dir, bucketCapacity);
    }

    public static ExtendibleHashIndex open(String basePath, int bucketCapacity) throws IOException {
        RandomAccessFile dFile = new RandomAccessFile(basePath + ".dir", "rw");
        RandomAccessFile bFile = new RandomAccessFile(basePath + ".bkt", "rw");
        HashDirectory dir = HashDirectory.readFrom(dFile);
        return new ExtendibleHashIndex(dFile, bFile, dir, bucketCapacity);
    }

    @Override
    public String label() {
        return "Hashing Estendido (Capacidade: " + bucketCapacity + " registros/bucket)";
    }

    // Função Hash exigida: h(k) = k mod 2^p
    private int hash(int key, int depth) {
        return key % (1 << depth); 
    }

    @Override
    public Long search(Integer key) throws IOException {
        int dirIndex = hash(key, directory.globalDepth);
        long bucketOffset = directory.bucketOffsets[dirIndex];
        HashBucket bucket = HashBucket.readFrom(bucketFile, bucketOffset, bucketCapacity);
        return bucket.search(key);
    }

    @Override
    public void insert(Integer key, long position) throws IOException {
        int dirIndex = hash(key, directory.globalDepth);
        long bucketOffset = directory.bucketOffsets[dirIndex];
        HashBucket bucket = HashBucket.readFrom(bucketFile, bucketOffset, bucketCapacity);

        if (bucket.search(key) != null) {
            throw new IllegalArgumentException("Já existe um registro indexado com id " + key);
        }

        if (bucket.count < bucketCapacity) {
            bucket.insert(key, position);
            bucket.writeTo(bucketFile, bucketOffset);
        } else {
            splitAndInsert(bucket, bucketOffset, dirIndex, key, position);
        }
    }

    private void splitAndInsert(HashBucket bucket, long bucketOffset, int dirIndex, int newKey, long newPosition) throws IOException {
        // Se a profundidade local é igual à global, duplica o diretório primeiro
        if (bucket.localDepth == directory.globalDepth) {
            directory.doubleDirectory();
            directory.writeTo(dirFile);
        }

        int newLocalDepth = bucket.localDepth + 1;
        
        // Criação de novos buckets para a divisão
        HashBucket bucket0 = new HashBucket(bucketCapacity, newLocalDepth);
        HashBucket bucket1 = new HashBucket(bucketCapacity, newLocalDepth);

        // Redistribui os registros do bucket antigo com a nova profundidade
        for (int i = 0; i < bucket.count; i++) {
            int oldKey = bucket.keys[i];
            long oldPos = bucket.pointers[i];
            int newHash = hash(oldKey, newLocalDepth);
            
            if (newHash == hash(oldKey, bucket.localDepth)) {
                bucket0.insert(oldKey, oldPos);
            } else {
                bucket1.insert(oldKey, oldPos);
            }
        }

        // Tenta alocar a nova chave em um dos novos buckets
        int newKeyHash = hash(newKey, newLocalDepth);
        if (newKeyHash == hash(newKey, bucket.localDepth)) {
            if (bucket0.count < bucketCapacity) bucket0.insert(newKey, newPosition);
        } else {
            if (bucket1.count < bucketCapacity) bucket1.insert(newKey, newPosition);
        }

        // Grava no disco. O bucket0 reaproveita o espaço do bucket antigo. O bucket1 vai para o fim.
        long bucket1Offset = bucketFile.length();
        bucket0.writeTo(bucketFile, bucketOffset);
        bucket1.writeTo(bucketFile, bucket1Offset);

        // Atualiza os ponteiros no diretório
        int mask = (1 << newLocalDepth) - 1;
        int baseIndex0 = hash(bucket.keys[0], bucket.localDepth); // Hash original do bucket
        int baseIndex1 = baseIndex0 + (1 << bucket.localDepth);   // Novo índice gerado na divisão

        for (int i = 0; i < directory.bucketOffsets.length; i++) {
            if ((i & mask) == baseIndex0) {
                directory.bucketOffsets[i] = bucketOffset;
            } else if ((i & mask) == baseIndex1) {
                directory.bucketOffsets[i] = bucket1Offset;
            }
        }
        
        directory.writeTo(dirFile);
    }

    @Override
    public boolean remove(Integer key) throws IOException {
        int dirIndex = hash(key, directory.globalDepth);
        long bucketOffset = directory.bucketOffsets[dirIndex];
        HashBucket bucket = HashBucket.readFrom(bucketFile, bucketOffset, bucketCapacity);
        
        if (bucket.remove(key)) {
            bucket.writeTo(bucketFile, bucketOffset);
            return true;
        }
        return false;
    }
}