package index;

import interfaces.Index;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

// Árvore B+ em arquivo, chave int -> posição (long) no arquivo de dados.
// Escolhida em vez de B ou B*: nós internos guardam só chaves (sem valor), o que dá mais fan-out
// por página que uma B tradicional (árvore mais rasa, menos I/O por busca); e evita a
// complexidade extra de redistribuição adiantada da B* sem necessidade real neste projeto.
// Remoção mantém a ocupação mínima de 50% por nó (exceto a raiz): pega emprestado de um irmão
// com sobra ou funde com um irmão, igual o algoritmo de livro-texto.
public class BPlusTreeIndex implements Index<Integer> {

    private final RandomAccessFile file;
    private final IndexHeader header;
    private final int order;

    private BPlusTreeIndex(RandomAccessFile file, IndexHeader header) {
        this.file = file;
        this.header = header;
        this.order = header.order;
    }

    // cria um índice novo do zero, sobrescrevendo o arquivo se ele já existir
    public static BPlusTreeIndex create(String filePath, int order) throws IOException {
        if (order < 3) {
            throw new IllegalArgumentException("A ordem da árvore B+ deve ser pelo menos 3.");
        }
        Path path = Path.of(filePath).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.deleteIfExists(path);

        RandomAccessFile file = new RandomAccessFile(filePath, "rw");
        IndexHeader header = new IndexHeader(order);
        header.writeTo(file);
        return new BPlusTreeIndex(file, header);
    }

    // abre um índice já existente no disco, preservando a ordem gravada no header
    public static BPlusTreeIndex open(String filePath) throws IOException {
        RandomAccessFile file = new RandomAccessFile(filePath, "rw");
        IndexHeader header = IndexHeader.readFrom(file);
        return new BPlusTreeIndex(file, header);
    }

    @Override
    public String label() {
        return "Árvore B+ (ordem " + order + ")";
    }

    @Override
    public Long search(Integer key) throws IOException {
        if (header.rootOffset == -1) {
            return null;
        }
        Node leaf = Node.readFrom(file, findLeafOffset(key), order);
        int i = indexOfKey(leaf, key);
        return i >= 0 ? leaf.pointers[i] : null;
    }

    private long findLeafOffset(int key) throws IOException {
        long offset = header.rootOffset;
        Node node = Node.readFrom(file, offset, order);
        while (!node.leaf) {
            offset = node.pointers[childIndexFor(node, key)];
            node = Node.readFrom(file, offset, order);
        }
        return offset;
    }

    // acha o filho cuja subárvore deve conter a chave: primeiro índice cuja separadora é > key
    private static int childIndexFor(Node node, int key) {
        int i = 0;
        while (i < node.count && key >= node.keys[i]) {
            i++;
        }
        return i;
    }

    private static int indexOfKey(Node leaf, int key) {
        for (int i = 0; i < leaf.count; i++) {
            if (leaf.keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- inserção

    private record Split(int key, long rightOffset) {}

    @Override
    public void insert(Integer key, long position) throws IOException {
        if (header.rootOffset == -1) {
            Node root = new Node(order, true);
            root.setNextLeaf(-1);
            header.rootOffset = allocateAndWrite(root);
        }

        Split split = insert(header.rootOffset, key, position);
        if (split != null) {
            Node newRoot = new Node(order, false);
            newRoot.count = 1;
            newRoot.keys[0] = split.key();
            newRoot.pointers[0] = header.rootOffset;
            newRoot.pointers[1] = split.rightOffset();
            header.rootOffset = allocateAndWrite(newRoot);
        }
        header.writeTo(file);
    }

    // insere na subárvore em nodeOffset; devolve a chave/nó promovidos se o nó estourou e teve
    // que ser dividido, ou null se coube sem dividir
    private Split insert(long nodeOffset, int key, long position) throws IOException {
        Node node = Node.readFrom(file, nodeOffset, order);

        if (node.leaf) {
            int i = 0;
            while (i < node.count && node.keys[i] < key) {
                i++;
            }
            if (i < node.count && node.keys[i] == key) {
                throw new IllegalArgumentException("Já existe um registro indexado com id " + key);
            }
            // na folha, a chave e o valor (posição) entram juntos no mesmo índice i
            return insertAndMaybeSplit(node, nodeOffset, i, i, node.count, key, position);
        }

        int i = childIndexFor(node, key);
        Split childSplit = insert(node.pointers[i], key, position);
        if (childSplit == null) {
            return null;
        }
        // no nó interno, a chave promovida entra em i, mas o filho novo entra em i+1 (é o filho
        // da direita da divisão) — por isso os índices de chave e de ponteiro divergem aqui
        return insertAndMaybeSplit(node, nodeOffset, i, i + 1, node.count + 1, childSplit.key(),
                childSplit.rightOffset());
    }

    // insere (newKey, newPointer) num nó cujas chaves/ponteiros ainda cabem no formato fixo em
    // disco (arrays temporários criados por withInserted); se não couber, divide o nó
    private Split insertAndMaybeSplit(Node node, long nodeOffset, int keyIndex, int pointerIndex,
            int pointerCount, int newKey, long newPointer
        ) throws IOException {

        int[] keys = withInserted(node.keys, node.count, keyIndex, newKey);
        long[] pointers = withInserted(node.pointers, pointerCount, pointerIndex, newPointer);
        int newKeyCount = node.count + 1;

        if (newKeyCount <= node.keys.length) {
            System.arraycopy(keys, 0, node.keys, 0, newKeyCount);
            System.arraycopy(pointers, 0, node.pointers, 0, pointerCount + 1);
            node.count = newKeyCount;
            node.writeTo(file, nodeOffset);
            return null;
        }
        return node.leaf
                ? splitLeaf(node, nodeOffset, keys, pointers, newKeyCount)
                : splitInternal(node, nodeOffset, keys, pointers, newKeyCount);
    }

    private Split splitLeaf(Node node, long nodeOffset, int[] keys, long[] values, int totalCount)
            throws IOException {
        int leftCount = (totalCount + 1) / 2;
        int rightCount = totalCount - leftCount;

        Node right = new Node(order, true);
        right.count = rightCount;
        System.arraycopy(keys, leftCount, right.keys, 0, rightCount);
        System.arraycopy(values, leftCount, right.pointers, 0, rightCount);
        right.setNextLeaf(node.nextLeaf());
        long rightOffset = allocateAndWrite(right);

        node.count = leftCount;
        System.arraycopy(keys, 0, node.keys, 0, leftCount);
        System.arraycopy(values, 0, node.pointers, 0, leftCount);
        node.setNextLeaf(rightOffset);
        node.writeTo(file, nodeOffset);

        return new Split(right.keys[0], rightOffset);
    }

    private Split splitInternal(Node node, long nodeOffset, int[] keys, long[] children, int totalCount)
            throws IOException {
        int mid = totalCount / 2;
        int promotedKey = keys[mid];
        int leftCount = mid;
        int rightCount = totalCount - mid - 1;

        Node right = new Node(order, false);
        right.count = rightCount;
        System.arraycopy(keys, mid + 1, right.keys, 0, rightCount);
        System.arraycopy(children, mid + 1, right.pointers, 0, rightCount + 1);
        long rightOffset = allocateAndWrite(right);

        node.count = leftCount;
        System.arraycopy(keys, 0, node.keys, 0, leftCount);
        System.arraycopy(children, 0, node.pointers, 0, leftCount + 1);
        node.writeTo(file, nodeOffset);

        return new Split(promotedKey, rightOffset);
    }

    private long allocateAndWrite(Node node) throws IOException {
        long offset = header.allocateNode();
        node.writeTo(file, offset);
        return offset;
    }

    private static int[] withInserted(int[] src, int count, int index, int value) {
        int[] result = new int[count + 1];
        System.arraycopy(src, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(src, index, result, index + 1, count - index);
        return result;
    }

    private static long[] withInserted(long[] src, int count, int index, long value) {
        long[] result = new long[count + 1];
        System.arraycopy(src, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(src, index, result, index + 1, count - index);
        return result;
    }

    // ---------------------------------------------------------------- remoção

    private record RemoveResult(boolean found, boolean underflow) {
        static RemoveResult notFound() {
            return new RemoveResult(false, false);
        }

        static RemoveResult of(boolean underflow) {
            return new RemoveResult(true, underflow);
        }
    }

    @Override
    public boolean remove(Integer key) throws IOException {
        if (header.rootOffset == -1) {
            return false;
        }

        RemoveResult result = remove(header.rootOffset, key);
        if (!result.found()) {
            return false;
        }

        // se a raiz é um nó interno que ficou com um único filho (0 chaves), a árvore encolhe
        Node root = Node.readFrom(file, header.rootOffset, order);
        if (!root.leaf && root.count == 0) {
            header.rootOffset = root.pointers[0];
        }
        header.writeTo(file);
        return true;
    }

    // remove a chave na subárvore em nodeOffset; devolve se achou a chave e, em caso positivo, se
    // o próprio nó ficou abaixo da ocupação mínima (pra o chamador redistribuir/fundir)
    private RemoveResult remove(long nodeOffset, int key) throws IOException {
        Node node = Node.readFrom(file, nodeOffset, order);

        if (node.leaf) {
            int i = indexOfKey(node, key);
            if (i < 0) {
                return RemoveResult.notFound();
            }
            shiftLeft(node.keys, i, node.count);
            shiftLeft(node.pointers, i, node.count);
            node.count--;
            node.writeTo(file, nodeOffset);
            boolean underflow = nodeOffset != header.rootOffset && node.count < minLeafKeys();
            return RemoveResult.of(underflow);
        }

        int i = childIndexFor(node, key);
        RemoveResult childResult = remove(node.pointers[i], key);
        if (!childResult.found()) {
            return childResult;
        }

        if (childResult.underflow()) {
            fixUnderflow(node, nodeOffset, i);
            node = Node.readFrom(file, nodeOffset, order); // fixUnderflow já persistiu as mudanças
        }

        boolean underflow = nodeOffset != header.rootOffset && node.count < minInternalKeys();
        return RemoveResult.of(underflow);
    }

    // capacidade mínima (>= 50% de ocupação): metade arredondada pra cima da capacidade máxima
    private int minLeafKeys() {
        int maxLeafKeys = order - 1;
        return (maxLeafKeys + 1) / 2;
    }

    private int minInternalKeys() {
        int minChildren = (order + 1) / 2; // ceil(order / 2)
        return minChildren - 1;
    }

    // corrige o filho parent.pointers[childIndex] que ficou abaixo do mínimo: pega emprestado de
    // um irmão com sobra, ou funde com um irmão se nenhum tiver
    private void fixUnderflow(Node parent, long parentOffset, int childIndex) throws IOException {
        long childOffset = parent.pointers[childIndex];
        Node child = Node.readFrom(file, childOffset, order);
        int min = child.leaf ? minLeafKeys() : minInternalKeys();

        if (childIndex > 0) {
            long leftOffset = parent.pointers[childIndex - 1];
            Node left = Node.readFrom(file, leftOffset, order);
            if (left.count > min) {
                borrowFromLeft(parent, childIndex, left, child);
                left.writeTo(file, leftOffset);
                child.writeTo(file, childOffset);
                parent.writeTo(file, parentOffset);
                return;
            }
        }

        if (childIndex < parent.count) {
            long rightOffset = parent.pointers[childIndex + 1];
            Node right = Node.readFrom(file, rightOffset, order);
            if (right.count > min) {
                borrowFromRight(parent, childIndex, child, right);
                child.writeTo(file, childOffset);
                right.writeTo(file, rightOffset);
                parent.writeTo(file, parentOffset);
                return;
            }
        }

        // nenhum irmão tem sobra: funde com um deles. O nó absorvido vira espaço morto no arquivo
        // (mesma política de não reaproveitar nós já usada pra alocação — ver IndexHeader).
        if (childIndex > 0) {
            long leftOffset = parent.pointers[childIndex - 1];
            Node left = Node.readFrom(file, leftOffset, order);
            merge(parent, childIndex - 1, left, child);
            left.writeTo(file, leftOffset);
            removeChildFromParent(parent, childIndex);
        } else {
            long rightOffset = parent.pointers[childIndex + 1];
            Node right = Node.readFrom(file, rightOffset, order);
            merge(parent, childIndex, child, right);
            child.writeTo(file, childOffset);
            removeChildFromParent(parent, childIndex + 1);
        }
        parent.writeTo(file, parentOffset);
    }

    // move o maior elemento do irmão esquerdo pro início do filho. Em folha, chave/valor migram
    // direto e a separadora do pai é atualizada pra copiar a nova primeira chave do filho. Em nó
    // interno é uma rotação: a separadora do pai desce pro filho e a última chave do irmão sobe.
    private static void borrowFromLeft(Node parent, int childIndex, Node left, Node child) {
        shiftRightFromZero(child.keys, child.count);
        shiftRightFromZero(child.pointers, child.leaf ? child.count : child.count + 1);
        int lastKey = left.keys[left.count - 1];

        if (child.leaf) {
            child.keys[0] = lastKey;
            child.pointers[0] = left.pointers[left.count - 1];
            child.count++;
            left.count--;
            parent.keys[childIndex - 1] = child.keys[0];
        } else {
            child.keys[0] = parent.keys[childIndex - 1];
            child.pointers[0] = left.pointers[left.count];
            child.count++;
            parent.keys[childIndex - 1] = lastKey;
            left.count--;
        }
    }

    // simétrico: move o menor elemento do irmão direito pro final do filho
    private static void borrowFromRight(Node parent, int childIndex, Node child, Node right) {
        if (child.leaf) {
            child.keys[child.count] = right.keys[0];
            child.pointers[child.count] = right.pointers[0];
            child.count++;
            shiftLeft(right.keys, 0, right.count);
            shiftLeft(right.pointers, 0, right.count);
            right.count--;
            parent.keys[childIndex] = right.keys[0];
        } else {
            child.keys[child.count] = parent.keys[childIndex];
            child.pointers[child.count + 1] = right.pointers[0];
            child.count++;
            parent.keys[childIndex] = right.keys[0];
            shiftLeft(right.keys, 0, right.count);
            shiftLeft(right.pointers, 0, right.count + 1);
            right.count--;
        }
    }

    // funde right dentro de left (left mantém seu offset na árvore; right vira espaço morto). Em
    // folha, os pares migram direto e a lista encadeada é reconectada pulando right. Em nó
    // interno, a separadora do pai (entre left e right) desce pro meio dos dois antes de juntar.
    private static void merge(Node parent, int leftIndex, Node left, Node right) {
        if (left.leaf) {
            System.arraycopy(right.keys, 0, left.keys, left.count, right.count);
            System.arraycopy(right.pointers, 0, left.pointers, left.count, right.count);
            left.count += right.count;
            left.setNextLeaf(right.nextLeaf());
        } else {
            left.keys[left.count] = parent.keys[leftIndex];
            left.count++;
            System.arraycopy(right.keys, 0, left.keys, left.count, right.count);
            System.arraycopy(right.pointers, 0, left.pointers, left.count, right.count + 1);
            left.count += right.count;
        }
    }

    // remove de parent a chave/filho do filho no índice childPointerIndex (e o separador à
    // esquerda dele), deslocando o restante
    private static void removeChildFromParent(Node parent, int childPointerIndex) {
        shiftLeft(parent.keys, childPointerIndex - 1, parent.count);
        shiftLeft(parent.pointers, childPointerIndex, parent.count + 1);
        parent.count--;
    }

    // remove o elemento em from, deslocando os count elementos válidos uma posição pra esquerda
    private static void shiftLeft(int[] arr, int from, int count) {
        for (int j = from; j < count - 1; j++) {
            arr[j] = arr[j + 1];
        }
    }

    private static void shiftLeft(long[] arr, int from, int count) {
        for (int j = from; j < count - 1; j++) {
            arr[j] = arr[j + 1];
        }
    }

    // abre espaço no índice 0, deslocando os count elementos válidos uma posição pra direita
    private static void shiftRightFromZero(int[] arr, int count) {
        for (int j = count; j > 0; j--) {
            arr[j] = arr[j - 1];
        }
    }

    private static void shiftRightFromZero(long[] arr, int count) {
        for (int j = count; j > 0; j--) {
            arr[j] = arr[j - 1];
        }
    }
}
