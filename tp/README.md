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
linha como um registro em `files/songs.bin` (arquivo binário, ignorado pelo git).

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

No menu, a opção 6 ordena os registros por número de streams (`Song::streams`). O tamanho do
heap e o número de fitas de entrada (`ways`) são constantes ajustáveis em `App.java`
(`SORT_HEAP_CAPACITY`, `SORT_MERGE_WAYS`).

## Status / limitações conhecidas

O CRUD ainda está em desenvolvimento:

- `BinaryRecordFile.read(id)`, `update()` e `delete()` ainda não estão implementados (lançam
  `UnsupportedOperationException`) — por isso as opções 3, 4 e 5 do menu ainda não funcionam.
- `create()` chama `read(id)` internamente para checar duplicata, então as opções 1 (carregar
  base de dados) e 2 (adicionar registro) também ficam bloqueadas até `read()` ser implementado.
- `readAll()` (usado pela ordenação) não depende de `read(id)` e já funciona — mas, com o
  bloqueio acima, o arquivo `files/songs.bin` só pode ser populado manualmente por enquanto.
