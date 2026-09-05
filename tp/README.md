# Trabalho Prático - AEDS3

CRUD de músicas do Spotify em arquivo binário, rodando no terminal, em Java, com ordenação
externa (seleção por substituição + intercalação polifásica) sobre os registros.

## Como compilar e rodar

```bash
javac -d bin $(find src -name "*.java")
java -cp bin App
```

## Dataset

`dataset/Spotify Most Streamed Songs.csv` — carregado pela opção 1 do menu, que grava cada
linha como um registro em `files/songs.bin` (arquivo binário, ignorado pelo git). Linhas com
campo numérico corrompido são puladas e contadas no resumo final.

## Estrutura do projeto

```
src/
├── App.java          # menu e ponto de entrada
├── model/             # Song — dado de domínio, serialização (toBytes/fromBytes)
├── interfaces/         # Recordable, RecordFile, RecordInput
├── storage/            # BinaryRecordFile + Header — persistência em arquivo binário
├── service/            # RecordService — camada entre o menu e o armazenamento
├── input/              # SongInputReader — leitura dos dados via terminal
└── sort/                # ordenação externa (ver seção abaixo)
```

## CRUD (opções 1-5 do menu)

`BinaryRecordFile` implementa `create`, `read`, `update` (delete + create do mesmo id) e
`delete` (lógico, via lápide `'*'`), com contador de próximo id e quantidade de registros
ativos persistidos num header de 8 bytes no início do arquivo.

Além do `readAll()` (carrega tudo em uma `List`), há `iterator()`: lê os registros válidos um
por vez direto do disco, sem materializar a base inteira em memória — é o que a ordenação
externa usa como entrada.

## Ordenação externa (opção 6 do menu)

Implementada em `src/sort/`, em duas fases:

- **`ReplacementSelection`** — fase 1: consome o arquivo de entrada uma única vez mantendo um
  heap (min-heap) de tamanho fixo, gerando runs (trechos já ordenados) geralmente bem mais
  longas que o próprio heap.
- **`PolyphaseMerge`** — fase 2: distribui as runs entre fitas de entrada segundo o Fibonacci
  generalizado (distribuição "ideal" da intercalação polifásica) e intercala em passadas,
  reaproveitando a fita que esvazia a cada passada como a próxima fita de saída — usa no máximo
  `ways + 1` fitas no total, nunca mais.
- **`Tape`** — abstração de uma fita: arquivo de trabalho sequencial usado pelas duas fases.
- **`ExternalSort`** — fachada que encadeia as duas fases; recebe um `Iterator<T>` de entrada e
  devolve uma `Tape<T>` com os registros em ordem.

No menu, a opção 6 ordena os registros por número de streams (`Song::streams`). O número de
caminhos (fitas, `ways`) e a quantidade máxima de registros por vez em memória primária (tamanho
do heap) são perguntados ao usuário a cada execução — não são mais constantes fixas no código.

Depois de exibir o resultado, a ordenação **substitui** `files/songs.bin` pela versão ordenada:
como a leitura de entrada (`iterator()`) já ignora registros deletados/desatualizados, o novo
arquivo sai compactado, sem os "espaços em branco" deixados por deleções e updates anteriores.
O contador de próximo id é preservado; as operações de CRUD seguintes já passam a atuar nesse
novo arquivo.

## Status / limitações conhecidas

- Toda busca por id (`read`, `update`, `delete`) é uma varredura linear O(n) do arquivo — não há
  índice id→offset. Upgrade sugerido se isso virar gargalo: manter um `Map<Integer, Long>` (ou
  estrutura em disco) de id para offset.
- `interfaces.RecordInput` existe no código mas não é implementada por `SongInputReader` — é uma
  interface órfã, sem impacto funcional hoje.
