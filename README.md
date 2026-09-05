# AEDS3 — Trabalho Prático

CRUD de músicas do Spotify em arquivo binário, rodando no terminal em Java, com ordenação
externa (seleção por substituição + intercalação polifásica) sobre os registros.

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

Veja [tp/README.md](tp/README.md) para a estrutura do projeto e detalhes da ordenação externa.
