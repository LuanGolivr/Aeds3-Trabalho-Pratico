package compression;

import java.io.EOFException;

// lê bits de um vetor de bytes na mesma ordem em que o BitWriter escreveu (mais significativo
// primeiro). Lança EOFException se tentar ler além do fim — é assim que um arquivo comprimido
// truncado/corrompido vira erro em vez de laço infinito ou lixo.
final class BitReader {

    private final byte[] data;
    private long bitPosition; // próximo bit a ser lido, contado desde o início de 'data'

    // 'byteOffset' pula o cabeçalho que vem antes dos bits (tamanho original, tabela etc.)
    BitReader(byte[] data, int byteOffset) {
        this.data = data;
        this.bitPosition = byteOffset * 8L;
    }

    int readBit() throws EOFException {
        if (bitPosition >= data.length * 8L) {
            throw new EOFException("Arquivo comprimido terminou antes do esperado (truncado ou corrompido).");
        }
        int currentByte = data[(int) (bitPosition >>> 3)];  // byte onde está o bit
        int shift = 7 - (int) (bitPosition & 7);             // posição do bit dentro do byte
        bitPosition++;
        return (currentByte >>> shift) & 1;
    }

    // lê 'length' bits e monta um inteiro com eles (o primeiro lido é o mais significativo)
    int read(int length) throws EOFException {
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 1) | readBit();
        }
        return value;
    }
}
