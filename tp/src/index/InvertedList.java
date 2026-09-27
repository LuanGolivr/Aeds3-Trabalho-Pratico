package index;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InvertedList {

    private final RandomAccessFile dictFile;
    private final RandomAccessFile blockFile;
    // O dicionário fica em memória para buscas rápidas: Termo -> Posição do 1º bloco
    private final Map<String, Long> dictionary; 

    public InvertedList(String basePath) throws IOException {
        String dictPath = basePath + ".dict";
        String blockPath = basePath + ".blk";
        
        Path dp = Path.of(dictPath);
        if (dp.getParent() != null) {
            Files.createDirectories(dp.getParent());
        }
        
        this.dictFile = new RandomAccessFile(dictPath, "rw");
        this.blockFile = new RandomAccessFile(blockPath, "rw");
        this.dictionary = new HashMap<>();
        
        loadDictionary();
    }

    // Carrega o ficheiro de dicionário para a memória ao abrir
    private void loadDictionary() throws IOException {
        dictFile.seek(0);
        dictionary.clear();
        while (dictFile.getFilePointer() < dictFile.length()) {
            String term = dictFile.readUTF();
            long offset = dictFile.readLong();
            dictionary.put(term, offset);
        }
    }

    // Grava o dicionário atualizado no disco (chame isso após inserts ou deletes)
    private void saveDictionary() throws IOException {
        dictFile.setLength(0); // Limpa o ficheiro
        dictFile.seek(0);
        for (Map.Entry<String, Long> entry : dictionary.entrySet()) {
            dictFile.writeUTF(entry.getKey());
            dictFile.writeLong(entry.getValue());
        }
    }

    // Normaliza o termo (tudo em minúsculo e sem espaços sobrando) para evitar duplicações
    private String normalize(String term) {
        return term.toLowerCase().trim();
    }

    public void insert(String term, int id) throws IOException {
        term = normalize(term);
        Long firstBlockOffset = dictionary.get(term);
        
        // Se o termo não existe, cria o primeiro bloco
        if (firstBlockOffset == null) {
            PostingBlock block = new PostingBlock();
            block.insert(id);
            long newOffset = blockFile.length(); // Fim do ficheiro
            block.writeTo(blockFile, newOffset);
            
            dictionary.put(term, newOffset);
            saveDictionary();
            return;
        }
        
        // Se o termo já existe, varre a lista encadeada de blocos no disco
        long currentOffset = firstBlockOffset;
        long lastOffset = -1;
        PostingBlock block = null;
        
        while (currentOffset != -1) {
            block = PostingBlock.readFrom(blockFile, currentOffset);
            
            // Impede a inserção de IDs duplicados no mesmo termo
            for (int i = 0; i < block.count; i++) {
                if (block.ids[i] == id) return; 
            }
            
            lastOffset = currentOffset;
            currentOffset = block.nextBlockOffset;
        }
        
        // Tenta inserir no último bloco encontrado
        if (block != null && block.insert(id)) {
            block.writeTo(blockFile, lastOffset);
        } else {
            // Se o último bloco estiver cheio, cria um novo bloco e encadeia
            PostingBlock newBlock = new PostingBlock();
            newBlock.insert(id);
            long newOffset = blockFile.length();
            newBlock.writeTo(blockFile, newOffset);
            
            if (block != null) {
                block.nextBlockOffset = newOffset;
                block.writeTo(blockFile, lastOffset);
            }
        }
    }

    public void remove(String term, int id) throws IOException {
        term = normalize(term);
        Long currentOffset = dictionary.get(term);
        if (currentOffset == null) return;
        
        while (currentOffset != -1) {
            PostingBlock block = PostingBlock.readFrom(blockFile, currentOffset);
            if (block.remove(id)) {
                block.writeTo(blockFile, currentOffset);
                return; // ID removido com sucesso
            }
            currentOffset = block.nextBlockOffset;
        }
    }

    // Retorna todos os IDs associados a um termo
    public List<Integer> search(String term) throws IOException {
        term = normalize(term);
        List<Integer> result = new ArrayList<>();
        Long currentOffset = dictionary.get(term);
        
        while (currentOffset != null && currentOffset != -1) {
            PostingBlock block = PostingBlock.readFrom(blockFile, currentOffset);
            for (int i = 0; i < block.count; i++) {
                result.add(block.ids[i]);
            }
            currentOffset = block.nextBlockOffset;
        }
        return result;
    }

    // Busca composta usando duas listas invertidas
    public static List<Integer> intersect(List<Integer> list1, List<Integer> list2) {
        List<Integer> result = new ArrayList<>();
        // O HashSet torna a verificação extremamente rápida (Complexidade O(1) por elemento)
        Set<Integer> set1 = new HashSet<>(list1);
        
        for (Integer id : list2) {
            if (set1.contains(id)) {
                result.add(id); // Guarda apenas os IDs que estão nas duas listas
            }
        }
        return result;
    }
}