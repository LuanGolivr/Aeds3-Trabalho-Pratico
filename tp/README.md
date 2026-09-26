# Trabalho Prático - AEDS3

CRUD de músicas do Spotify em arquivo binário, rodando no terminal, em Java, com ordenação
externa (seleção por substituição + intercalação polifásica) e índice em Árvore B+ sobre o id
dos registros.

## Como compilar e rodar

Linux/macOS:

```bash
javac -d bin $(find src -name "*.java")
java -cp bin App
```

Windows (cmd):

```bat
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt
java -cp bin App
```

## Dataset

`dataset/Spotify Most Streamed Songs.csv` — carregado pela opção 1 do menu, que grava cada
linha como um registro em `files/songs.bin` (arquivo binário, ignorado pelo git). Linhas com
campo numérico corrompido são puladas e contadas no resumo final. O índice, quando construído,
fica em `files/songs.idx` (também ignorado pelo git — é derivado do `.bin`, dá pra reconstruir).

## Estrutura do projeto

```
src/
├── App.java          # menu e ponto de entrada
├── model/             # Song — dado de domínio, serialização (toBytes/fromBytes)
├── interfaces/         # Recordable, RecordFile, RecordInput, Index
├── storage/            # BinaryRecordFile + Header — persistência em arquivo binário
├── service/            # RecordService — camada entre o menu e o armazenamento
├── input/              # SongInputReader — leitura dos dados via terminal
├── index/              # índice em Árvore B+ sobre o id (ver seção abaixo)
└── sort/                # ordenação externa (ver seção abaixo)
```

## CRUD (opções 1-5 do menu)

`BinaryRecordFile` implementa `create`, `read`, `update` (delete + create do mesmo id) e
`delete` (lógico, via lápide `'*'`), com contador de próximo id e quantidade de registros
ativos persistidos num header de 8 bytes no início do arquivo.

Além do `readAll()` (carrega tudo em uma `List`), há `iterator()`: lê os registros válidos um
por vez direto do disco, sem materializar a base inteira em memória — é o que a ordenação
externa usa como entrada.

## Índice em Árvore B+ (opção 7 do menu)

Arquivo separado (`files/songs.idx`), chave `id -> posição no songs.bin`.

**Por que Árvore B+ e não B ou B\***: numa B tradicional cada nó (raiz, internos e folhas) guarda
a posição do registro junto da chave, mesmo nos níveis que só servem de roteamento — isso
desperdiça espaço e reduz quantas chaves cabem por nó. Na B+ só as folhas guardam a posição; os
nós internos guardam somente chaves separadoras, cabem mais por nó, a árvore fica mais rasa e
busca menos páginas em disco. B\* melhora ainda mais a ocupação redistribuindo antes de dividir,
mas é uma complexidade de implementação sem ganho real pro tamanho da base aqui — a política de
remoção da B+ já garante pelo menos 50% de ocupação por nó (ver abaixo).

Implementação em `src/index/`:
- **`IndexHeader`** — cabeçalho do `.idx`: ordem (definida na criação/reconstrução do índice,
  nunca muda depois), offset do nó raiz, offset livre pro próximo nó.
- **`Node`** — nó de tamanho fixo em disco, calculado a partir da ordem, endereçável só por
  aritmética de offset (sem precisar de um índice à parte pra achar nós). As folhas ficam
  encadeadas entre si (não usado hoje, já que a busca é só por id exato, mas deixa pronta uma
  busca por faixa futura sem redesenhar nada).
- **`BPlusTreeIndex`** — busca (`O(log n)` acessos a disco em vez do `O(n)` da varredura linear),
  inserção com split (propagando até criar uma raiz nova se preciso) e remoção com
  redistribuição/fusão de nós (pega emprestado de um irmão com sobra, ou funde com um irmão se
  nenhum tiver — mantém a ocupação mínima de 50% por nó, exceto a raiz).

A ordem é perguntada só na criação/reconstrução do índice e fica salva no próprio `.idx` — não
precisa ser informada de novo nas próximas execuções, o índice é recarregado do disco
automaticamente se já existir. Reconstruir um índice existente pede confirmação antes de
sobrescrever.

O índice é opcional e transparente: `BinaryRecordFile` recebe um `Index<Integer>` que pode ser
`null`. Sem índice, `create`/`read`/`delete` caem pra varredura linear, como antes de essa
funcionalidade existir. Com índice, toda alteração no `songs.bin` atualiza o `.idx` no mesmo
momento (nunca fica desatualizado), e cada operação do menu (3 a 5) indica no terminal qual
estrutura resolveu ela.

`interfaces.Index<K>` já deixa o encaixe pronto pra Hashing Dinâmico e Lista Invertida — na opção
7 do menu já dá pra escolher o tipo, mas por enquanto só a Árvore B+ está implementada; as outras
duas avisam que ainda não foram feitas.

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

- Sem o índice construído, busca/atualização/deleção por id ainda são varredura linear O(n) do
  arquivo. Construir o índice (opção 7) resolve, passando pra O(log n).
- O arquivo de índice nunca reaproveita espaço de nós descartados (por fusão numa remoção) — só
  cresce. Reconstruir o índice do zero (opção 7) resolve, se isso virar problema.
- Hashing Dinâmico e Lista Invertida ainda não foram implementados — o menu já pergunta qual tipo
  de índice criar, mas só a Árvore B+ está disponível por enquanto.
- `interfaces.RecordInput` existe no código mas não é implementada por `SongInputReader` — é uma
  interface órfã, sem impacto funcional hoje.
