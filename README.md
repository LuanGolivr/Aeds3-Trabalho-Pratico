# AEDS3 — Trabalho Prático

CRUD de músicas do Spotify em arquivo binário, rodando no terminal em Java, com ordenação
externa, índice primário em Árvore B+ ou Hashing Estendido, Lista Invertida por artista e ano, e
compressão da base com Huffman e LZW.

O código-fonte, o dataset e as instruções detalhadas ficam em [`tp/`](tp/README.md).

## Como compilar e rodar

Linux/macOS:

```bash
cd tp
javac -d bin $(find src -name "*.java")
java -cp bin App
```

Windows (cmd):

```bat
cd tp
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt
java -cp bin App
```

Veja [tp/README.md](tp/README.md) para o menu, a estrutura do projeto, as decisões de cada
estrutura/algoritmo e como rodar os testes.
