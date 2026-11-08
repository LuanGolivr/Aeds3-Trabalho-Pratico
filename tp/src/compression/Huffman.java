package compression;

import interfaces.Compressor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.PriorityQueue;

// Codificação de Huffman sobre bytes (símbolos de 0 a 255).
//
// Ideia: bytes que aparecem muito ganham códigos curtos e bytes raros ganham códigos longos.
// A árvore é montada juntando sempre os dois nós de menor frequência; o caminho da raiz até uma
// folha (esquerda = 0, direita = 1) é o código daquele byte.
//
// Formato do arquivo gerado:
//   int   tamanho original (em bytes)
//   short quantidade de símbolos distintos (N)
//   N x (byte símbolo, int frequência)   -> tabela de frequências
//   bits  códigos de cada byte do original, em sequência (último byte completado com zeros)
//
// Gravamos a tabela de frequências (e não a árvore) porque a descompressão reconstrói
// exatamente a mesma árvore a partir dela, já que a montagem é determinística (ver Node.order).
// Custo: 5 bytes por símbolo distinto usado, no máximo 1280 bytes.
public class Huffman implements Compressor {

    private static final int SYMBOLS = 256; // um símbolo por valor possível de byte

    @Override
    public String name() {
        return "Huffman";
    }

    // nó da árvore: folhas guardam um byte (symbol); nós internos só a soma das frequências
    private static final class Node {
        final int symbol;
        final int frequency;
        final Node left;
        final Node right;
        // desempate entre nós de mesma frequência: folhas usam o próprio símbolo (0..255) e nós
        // internos recebem 256, 257, ... na ordem de criação. Assim compressão e descompressão
        // montam sempre a mesma árvore a partir da mesma tabela de frequências.
        final int order;

        Node(int symbol, int frequency, int order) {
            this(symbol, frequency, null, null, order);
        }

        Node(int symbol, int frequency, Node left, Node right, int order) {
            this.symbol = symbol;
            this.frequency = frequency;
            this.left = left;
            this.right = right;
            this.order = order;
        }

        boolean isLeaf() {
            return left == null;
        }
    }

    @Override
    public byte[] compress(byte[] data) throws IOException {
        // 1) conta quantas vezes cada byte aparece
        int[] frequencies = new int[SYMBOLS];
        for (byte b : data) {
            frequencies[b & 0xFF]++; // & 0xFF converte o byte com sinal (-128..127) para 0..255
        }

        // 2) cabeçalho: tamanho original + tabela só com os símbolos que aparecem
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(out);
        header.writeInt(data.length);
        int distinct = 0;
        for (int frequency : frequencies) {
            if (frequency > 0) {
                distinct++;
            }
        }
        header.writeShort(distinct);
        for (int symbol = 0; symbol < SYMBOLS; symbol++) {
            if (frequencies[symbol] > 0) {
                header.writeByte(symbol);
                header.writeInt(frequencies[symbol]);
            }
        }
        header.flush();

        // arquivo vazio: só o cabeçalho, não há bits a escrever
        Node root = buildTree(frequencies);
        if (root == null) {
            return out.toByteArray();
        }

        // 3) gera a tabela símbolo -> código percorrendo a árvore uma vez
        long[] codes = new long[SYMBOLS];
        int[] lengths = new int[SYMBOLS];
        assignCodes(root, 0L, 0, codes, lengths);

        // 4) substitui cada byte do original pelo seu código
        BitWriter bits = new BitWriter(out);
        for (byte b : data) {
            bits.write(codes[b & 0xFF], lengths[b & 0xFF]);
        }
        bits.flush();
        return out.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] data) throws IOException {
        // 1) lê o cabeçalho e reconstrói a tabela de frequências
        DataInputStream header = new DataInputStream(new ByteArrayInputStream(data));
        int originalLength = header.readInt();
        int distinct = header.readUnsignedShort();
        if (originalLength < 0 || distinct > SYMBOLS) {
            throw new IOException("Cabeçalho Huffman inválido.");
        }
        int[] frequencies = new int[SYMBOLS];
        for (int i = 0; i < distinct; i++) {
            int symbol = header.readUnsignedByte();
            frequencies[symbol] = header.readInt();
        }

        byte[] output = new byte[originalLength];
        Node root = buildTree(frequencies);
        if (root == null) {
            return output; // arquivo original vazio
        }

        // 2) os bits começam logo depois do cabeçalho: 4 (int) + 2 (short) + 5 por símbolo
        int headerSize = Integer.BYTES + Short.BYTES + distinct * (1 + Integer.BYTES);
        BitReader bits = new BitReader(data, headerSize);

        // 3) para cada byte do original: desce da raiz seguindo os bits até chegar numa folha
        for (int i = 0; i < originalLength; i++) {
            Node node = root;
            if (root.isLeaf()) {
                bits.readBit(); // só 1 símbolo distinto: cada byte foi gravado como um único bit 0
            }
            while (!node.isLeaf()) {
                node = bits.readBit() == 0 ? node.left : node.right;
            }
            output[i] = (byte) node.symbol;
        }
        return output;
    }

    // monta a árvore de Huffman; devolve null se não houver nenhum símbolo (entrada vazia)
    private static Node buildTree(int[] frequencies) {
        // fila de prioridade: sempre entrega o nó de menor frequência (desempate por 'order')
        PriorityQueue<Node> queue = new PriorityQueue<>((a, b) -> a.frequency != b.frequency
                ? Integer.compare(a.frequency, b.frequency)
                : Integer.compare(a.order, b.order));

        // uma folha para cada byte que aparece no arquivo
        for (int symbol = 0; symbol < SYMBOLS; symbol++) {
            if (frequencies[symbol] > 0) {
                queue.add(new Node(symbol, frequencies[symbol], symbol));
            }
        }
        if (queue.isEmpty()) {
            return null;
        }

        // junta os dois menores num nó pai até sobrar só a raiz
        int nextOrder = SYMBOLS;
        while (queue.size() > 1) {
            Node left = queue.poll();
            Node right = queue.poll();
            queue.add(new Node(-1, left.frequency + right.frequency, left, right, nextOrder++));
        }
        return queue.poll();
    }

    // percorre a árvore acumulando o caminho: esquerda acrescenta bit 0, direita bit 1.
    // A profundidade máxima é limitada (a frequência cresce no mínimo como Fibonacci a cada
    // nível, e o total cabe num int), então o código sempre cabe num long.
    private static void assignCodes(Node node, long code, int length, long[] codes, int[] lengths) {
        if (node.isLeaf()) {
            // caso especial: arquivo com um único byte distinto (a raiz já é folha) recebe o
            // código "0" de 1 bit, senão o código teria tamanho zero e nada seria gravado
            codes[node.symbol] = code;
            lengths[node.symbol] = Math.max(length, 1);
            return;
        }
        assignCodes(node.left, code << 1, length + 1, codes, lengths);
        assignCodes(node.right, (code << 1) | 1, length + 1, codes, lengths);
    }
}
