package compression;

import interfaces.Compressor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// LZW (Lempel-Ziv-Welch) sobre bytes.
//
// Ideia: o dicionário começa com sequências de 1 byte e vai aprendendo sequências maiores
// conforme o arquivo é lido; cada sequência repetida passa a ser gravada como um único código.
// O dicionário NÃO é gravado no arquivo: a descompressão o reconstrói sozinha, na mesma ordem.
//
// Dicionário inicial: os 256 valores possíveis de um byte (códigos 0..255). Como comprimimos o
// arquivo binário inteiro (cabeçalho, inteiros, lápides, tamanhos de string), qualquer byte pode
// aparecer, então um dicionário inicial só com letras não daria conta.
//
// Tamanho dos códigos: variável, de 9 até 16 bits. Começa com 9 (cabe 0..511) e cresce 1 bit
// sempre que o dicionário passa a ter códigos que não cabem mais. Com 16 bits o dicionário
// chega a 65536 entradas; quando enche, ele é congelado (para de aprender e continua usando o
// que já tem) — simples e suficiente para o tamanho da base.
//
// Formato do arquivo gerado:
//   int   tamanho original (em bytes)
//   bits  sequência de códigos (último byte completado com zeros)
public class Lzw implements Compressor {

    private static final int INITIAL_CODES = 256; // códigos 0..255 = bytes isolados
    private static final int MIN_BITS = 9;
    private static final int MAX_BITS = 16;
    private static final int MAX_CODES = 1 << MAX_BITS; // 65536 entradas no máximo

    @Override
    public String name() {
        return "LZW";
    }

    // quantos bits são necessários para gravar qualquer código entre 0 e maxCode, limitado à
    // faixa 9..16. Compressão e descompressão usam esta mesma função para ficarem em sincronia.
    private static int codeWidth(int maxCode) {
        int bits = 32 - Integer.numberOfLeadingZeros(maxCode);
        return Math.max(MIN_BITS, Math.min(MAX_BITS, bits));
    }

    @Override
    public byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DataOutputStream(out).writeInt(data.length);
        if (data.length == 0) {
            return out.toByteArray();
        }

        // dicionário das sequências aprendidas: cada sequência é "código do prefixo + 1 byte",
        // então a chave é (prefixo << 8 | byte) e o valor é o código da sequência.
        // Os códigos 0..255 (bytes isolados) são implícitos e não precisam estar no mapa.
        Map<Integer, Integer> dictionary = new HashMap<>();
        int nextCode = INITIAL_CODES;
        BitWriter bits = new BitWriter(out);

        int current = data[0] & 0xFF; // código da maior sequência já reconhecida (começa com 1 byte)
        for (int i = 1; i < data.length; i++) {
            int nextByte = data[i] & 0xFF;
            Integer extended = dictionary.get((current << 8) | nextByte);
            if (extended != null) {
                // sequência atual + próximo byte já é conhecida: continua estendendo
                current = extended;
                continue;
            }
            // sequência nova: grava o código do que já era conhecido...
            bits.write(current, codeWidth(nextCode - 1));
            // ...ensina ao dicionário a sequência nova (se ainda houver espaço)...
            if (nextCode < MAX_CODES) {
                dictionary.put((current << 8) | nextByte, nextCode++);
            }
            // ...e recomeça a partir do byte que não coube
            current = nextByte;
        }
        bits.write(current, codeWidth(nextCode - 1)); // última sequência pendente
        bits.flush();
        return out.toByteArray();
    }

    @Override
    public byte[] decompress(byte[] data) throws IOException {
        int originalLength = new DataInputStream(new ByteArrayInputStream(data)).readInt();
        if (originalLength < 0) {
            throw new IOException("Cabeçalho LZW inválido.");
        }
        byte[] output = new byte[originalLength];
        if (originalLength == 0) {
            return output;
        }

        // dicionário reconstruído: cada código guarda o código do prefixo e o último byte.
        // Também guardamos o tamanho e o primeiro byte de cada sequência para escrevê-la sem
        // precisar montar Strings/arrays intermediários.
        int[] prefix = new int[MAX_CODES];
        byte[] lastByte = new byte[MAX_CODES];
        byte[] firstByte = new byte[MAX_CODES];
        int[] length = new int[MAX_CODES];
        for (int code = 0; code < INITIAL_CODES; code++) {
            prefix[code] = -1; // sequência de 1 byte não tem prefixo
            lastByte[code] = (byte) code;
            firstByte[code] = (byte) code;
            length[code] = 1;
        }
        int nextCode = INITIAL_CODES;

        BitReader bits = new BitReader(data, Integer.BYTES);
        int written = 0;

        // o primeiro código é sempre um byte isolado
        int previous = bits.read(codeWidth(nextCode - 1));
        if (previous >= INITIAL_CODES) {
            throw new IOException("Arquivo LZW corrompido.");
        }
        written = writeSequence(previous, output, written, prefix, lastByte, length);

        while (written < originalLength) {
            // a descompressão está sempre uma entrada atrás da compressão (ela só aprende a
            // sequência nova quando lê o código seguinte), por isso o maior código possível
            // aqui é nextCode, e não nextCode - 1
            int code = bits.read(codeWidth(nextCode));
            if (code > nextCode || (code == nextCode && nextCode >= MAX_CODES)) {
                throw new IOException("Arquivo LZW corrompido.");
            }

            // aprende a sequência "anterior + primeiro byte da atual". Se o código lido é
            // justamente o que está sendo criado agora (caso especial do LZW, ex.: "aaa"), o
            // primeiro byte da atual é o primeiro byte da anterior.
            if (nextCode < MAX_CODES) {
                byte first = code < nextCode ? firstByte[code] : firstByte[previous];
                prefix[nextCode] = previous;
                lastByte[nextCode] = first;
                firstByte[nextCode] = firstByte[previous];
                length[nextCode] = length[previous] + 1;
                nextCode++;
            }

            if (written + length[code] > originalLength) {
                throw new IOException("Arquivo LZW corrompido.");
            }
            written = writeSequence(code, output, written, prefix, lastByte, length);
            previous = code;
        }
        return output;
    }

    // escreve a sequência do código em output[position..]: percorre a cadeia de prefixos de trás
    // para frente (último byte primeiro) e devolve a nova posição de escrita
    private static int writeSequence(int code, byte[] output, int position, int[] prefix, byte[] lastByte, int[] length) {
        int end = position + length[code];
        for (int i = end - 1, c = code; c != -1; i--, c = prefix[c]) {
            output[i] = lastByte[c];
        }
        return end;
    }
}
