package compression;

import java.io.ByteArrayOutputStream;

// escreve uma sequência de bits (do mais significativo para o menos) agrupando-os em bytes.
// Huffman e LZW geram códigos que não têm tamanho múltiplo de 8 bits, por isso precisam disso.
final class BitWriter {

    private final ByteArrayOutputStream out;
    private int buffer;   // bits acumulados que ainda não formam um byte completo
    private int bitCount; // quantos bits válidos há no buffer (0 a 7)

    BitWriter(ByteArrayOutputStream out) {
        this.out = out;
    }

    // escreve os 'length' bits menos significativos de 'value', começando pelo mais alto
    void write(long value, int length) {
        for (int i = length - 1; i >= 0; i--) {
            buffer = (buffer << 1) | (int) ((value >>> i) & 1);
            bitCount++;
            if (bitCount == 8) { // byte completo: despeja na saída
                out.write(buffer);
                buffer = 0;
                bitCount = 0;
            }
        }
    }

    // completa o último byte com zeros à direita; quem lê sabe quando parar (tamanho original
    // gravado no cabeçalho), então esses bits de preenchimento nunca são interpretados
    void flush() {
        if (bitCount > 0) {
            out.write(buffer << (8 - bitCount));
            buffer = 0;
            bitCount = 0;
        }
    }
}
