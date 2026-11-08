# Trabalho Prático - AEDS3

CRUD de músicas do Spotify em arquivo binário, rodando no terminal, em Java, com:

- **TP1** — arquivo sequencial com lápide e ordenação externa (seleção por substituição +
  intercalação polifásica);
- **TP2** — índice primário em **Árvore B+** ou **Hashing Estendido** sobre o id, e **Lista
  Invertida** para busca por artista e por ano de lançamento;
- **TP3** — **compressão** da base com **Huffman** e **LZW** e **casamento de padrões** com **KMP** e **Boyer-Moore-Horspool**.

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

## Menu

| Opção | O que faz |
|---|---|
| 1 | Carrega o CSV na base (`files/songs.bin`), apagando a base, o índice e as listas anteriores |
| 2 – 5 | Criar, buscar, atualizar e deletar registro por id |
| 6 | Ordenação externa por nome da faixa (reescreve e compacta o `songs.bin`) |
| 7 | Cria/reconstrói o índice primário: 1 = Árvore B+, 2 = Hashing Estendido |
| 8 | Busca por artista, por ano, ou composta (artista E ano) — Lista Invertida |
| 9 | Comprime a base com Huffman e LZW, gerando uma nova versão X |
| 10 | Descomprime a versão X e substitui a base por ela |
| 11 | Busca por padrão em título, artista ou ambos usando KMP e Boyer-Moore-Horspool |
| 0 | Sair |

## Dataset

`dataset/Spotify Most Streamed Songs.csv` — carregado pela opção 1, que grava cada linha como um
registro em `files/songs.bin`. Linhas com campo numérico corrompido são puladas e contadas no
resumo final. Tudo dentro de `files/` é gerado pelo programa (ignorado pelo git).

## Estrutura do projeto

```
src/
├── App.java             # menu e ponto de entrada
├── RegressionSelfTest.java
├── model/               # Song — dado de domínio, serialização (toBytes/fromBytes)
├── interfaces/          # Recordable, RecordFile, RecordInput, Index, Compressor
├── storage/             # BinaryRecordFile + Header — persistência em arquivo binário
├── service/             # RecordService — camada entre o menu e o armazenamento
├── input/               # SongInputReader — leitura dos dados via terminal
├── sort/                # ordenação externa
├── index/               # Árvore B+, Hashing Estendido e Lista Invertida
├── compression/         # Huffman, LZW e leitura/escrita de bits
└── pattern/             # KMP, Boyer-Moore-Horspool e testes
```

## CRUD (opções 1-5)

`BinaryRecordFile` implementa `create`, `read`, `update` (delete + create do mesmo id) e `delete`
(lógico, via lápide `'*'`). O header de 8 bytes no início do arquivo guarda o último id usado e a
quantidade de registros ativos. Cada registro é `[lápide (1 byte)][tamanho (int)][dados]`.

Além do `readAll()`, há `iterator()`, que lê os registros válidos um por vez direto do disco —
é o que a ordenação externa e a reconstrução de índices usam.

## Ordenação externa (opção 6)

Implementada em `src/sort/`, em duas fases:

- **`ReplacementSelection`** — gera runs ordenadas com um heap de tamanho fixo, normalmente bem
  mais longas que o próprio heap.
- **`PolyphaseMerge`** — distribui as runs entre as fitas segundo Fibonacci generalizado e
  intercala em passadas, reaproveitando a fita que esvazia como a próxima saída (no máximo
  `ways + 1` fitas).
- **`Tape`** — uma fita: arquivo de trabalho sequencial. **`ExternalSort`** encadeia as fases.

O número de caminhos e o tamanho do heap são perguntados a cada execução. Ao final, o
`songs.bin` é **substituído** pela versão ordenada, já sem registros deletados. Como os offsets
mudam, o índice primário ativo é refeito durante a reescrita (as listas invertidas guardam ids,
não offsets, e não são afetadas).

## Índice primário (opção 7)

O índice mapeia `id -> posição do registro no songs.bin` e é opcional: sem ele, as operações por
id fazem varredura linear. Com ele, toda alteração no `songs.bin` atualiza o índice na hora, e
cada operação do menu mostra qual estrutura a resolveu. Só um tipo de índice existe por vez:
criar um apaga os arquivos do outro. Ao iniciar, o programa reabre automaticamente o índice que
estiver em disco.

### Árvore B+ (`files/songs.idx`)

**Por que B+ e não B ou B\***: na B+ só as folhas guardam a posição do registro; os nós internos
guardam apenas chaves separadoras, então cabem mais chaves por nó, a árvore fica mais rasa e a
busca lê menos páginas. A B\* melhoraria a ocupação redistribuindo antes de dividir, mas a
remoção da B+ já garante 50% de ocupação mínima, sem a complexidade extra.

- **`IndexHeader`** — ordem (fixada na criação), offset da raiz e próximo offset livre.
- **`Node`** — nó de tamanho fixo em disco, calculado pela ordem; folhas encadeadas.
- **`BPlusTreeIndex`** — busca `O(log n)`, inserção com split propagado até a raiz e remoção com
  redistribuição/fusão de irmãos.

### Hashing Estendido (`files/songs.dir` + `files/songs.bkt`)

- **`HashDirectory`** — profundidade global e o vetor de ponteiros para buckets; guarda também a
  capacidade do bucket, para o índice ser reaberto sem perguntar de novo.
- **`HashBucket`** — bucket de tamanho fixo com profundidade local, chaves e posições.
- **`ExtendibleHashIndex`** — `h(k) = k mod 2^p`. Bucket cheio é dividido (duplicando o diretório
  se a profundidade local já for igual à global) e a chave é reinserida, dividindo de novo se
  todas as chaves antigas tiverem caído no mesmo lado.

## Lista Invertida (opção 8)

Duas listas: por **artista** (`files/artist.dict` + `.blk`) e por **ano de lançamento**
(`files/year.dict` + `.blk`).

- **Dicionário** (`.dict`) — termo normalizado (minúsculo, sem espaços nas pontas) -> offset do
  primeiro bloco; carregado em memória ao abrir e regravado a cada termo novo.
- **Blocos** (`.blk`, `PostingBlock`) — até 20 ids por bloco, encadeados em disco quando um
  termo tem mais ids que isso.

A busca composta (artista E ano) faz a interseção das duas listas de ids. As listas são
atualizadas em create/update/delete, apagadas ao recarregar a base (opção 1) e reconstruídas ao
descomprimir uma versão (opção 10).

## Compressão (opções 9 e 10)

O arquivo comprimido é o `songs.bin` **inteiro, byte a byte** — header, lápides, indicadores de
tamanho e campos. Os dois algoritmos implementam `interfaces.Compressor`.

**Opção 9** comprime com os dois algoritmos e grava a próxima versão livre X:

```
files/songsHuffmanCompressaoX
files/songsLZWCompressaoX
```

Para cada um mostra o tamanho, o ganho (ou perda) em % e o tempo, e informa qual comprimiu mais e
qual foi mais rápido.

**Opção 10** pede a versão X, descomprime com os dois algoritmos, mostra os tempos e qual foi
mais rápido, e substitui o `songs.bin` pelo resultado. Depois reconstrói o índice primário e as
listas invertidas a partir da base restaurada. Se os dois algoritmos produzirem conteúdos
diferentes, a base não é alterada. Se um dos arquivos estiver faltando ou corrompido, usa o
outro.

### Huffman (`compression/Huffman.java`)

- Símbolos são os 256 valores de byte; a árvore é montada juntando sempre os dois nós de menor
  frequência (fila de prioridade).
- O arquivo gerado guarda o tamanho original e a **tabela de frequências** só dos bytes que
  aparecem (5 bytes cada). A descompressão monta a mesma árvore a partir dela, porque o desempate
  entre frequências iguais é determinístico.
- Arquivo com um único byte distinto recebe o código `0` de 1 bit.

### LZW (`compression/Lzw.java`)

- **Dicionário inicial:** os 256 valores de byte (códigos 0–255). Como o arquivo é binário
  (inteiros, lápides, tamanhos), qualquer byte pode aparecer — um dicionário só com caracteres
  de texto não serviria.
- **Códigos de tamanho variável**, de 9 a 16 bits: crescem 1 bit quando o dicionário deixa de
  caber no tamanho atual. Com 65.536 entradas o dicionário é congelado (para de aprender).
- O dicionário não é gravado: a descompressão o reconstrói, incluindo o caso especial em que o
  código lido é o que está sendo criado naquele passo (ex.: `aaa`).

### Resultado na base completa (81.029 bytes)

| Algoritmo | Tamanho | Ganho | Observação |
|---|---|---|---|
| Huffman | 52.260 B | 35,5% | compressão mais rápida |
| LZW | 43.684 B | 46,1% | melhor taxa e descompressão mais rápida |

O LZW ganha porque aproveita sequências repetidas (nomes de artistas, datas, campos de tamanho
fixo), enquanto o Huffman só aproveita a frequência de cada byte isolado. Os tempos variam a cada
execução.

## Casamento de padrões (opção 11)

Os algoritmos de busca foram escritos em Java em `src/pattern/` e implementam
`PatternMatcher`. **Não utilizam `String.contains`, `String.indexOf` nem expressões
regulares** nas implementações. Os dois algoritmos escolhidos são diferentes de força bruta.

### Como usar

1. Carregue a base pelo menu **1** (se for solicitado, confirme a substituição com `s`).
2. Selecione **11** e escolha o campo: **1** para títulos, **2** para artistas ou **3** para ambos.
3. Digite um texto parcial, por exemplo `love` (título) ou `Taylor` (artista).
4. O programa procura em **todos os registros ativos** do `songs.bin`, exibindo o número de
   ocorrências, campos e músicas encontrados por algoritmo, o tempo de cada busca e até
   **20 campos correspondentes** com as posições do padrão, começando em zero.

O programa também verifica se os dois algoritmos encontraram exatamente as mesmas
ocorrências. Para uma comparação justa, **a leitura do arquivo e a preparação do texto
são realizadas uma só vez, antes da medição**, e cada algoritmo recebe os mesmos campos.
As medições representam uma execução e podem variar com a JVM e o computador.

As buscas **ignoram diferenças entre letras maiúsculas e minúsculas**, mas **respeitam os
acentos**: `música` e `musica` são padrões distintos. As posições são medidas no texto
normalizado para minúsculas; em casos raros de Unicode, a normalização pode alterar o
número de caracteres, por isso a posição pode diferir da exibida no texto original.

A opção 11 lê os registros por `RecordService.readAll()`, que desserializa as músicas
armazenadas no arquivo binário e **desconsidera registros apagados** (marcados com lápide).
A busca em artistas percorre cada nome separadamente, inclusive quando uma faixa possui
vários artistas. O limite de 20 itens afeta somente a exibição, **não a contagem**.

### Escolha dos algoritmos de casamento de padrões e justificativa

Para o TP3, foram escolhidos **Knuth-Morris-Pratt (KMP)** e
**Boyer-Moore-Horspool (BMH)**. Ambos são algoritmos de casamento de padrões
**diferentes da força bruta**, conforme a exigência do trabalho. A escolha permite
comparar duas estratégias: uma que evita repetir comparações por meio da estrutura
do padrão (KMP) e outra que procura avançar várias posições de cada vez no texto
(BMH). Os dois foram implementados pelo grupo em Java e são utilizados na opção
**11** do menu para pesquisar títulos e nomes de artistas.

#### 1. Knuth-Morris-Pratt (KMP) — `src/pattern/KmpMatcher.java`

O KMP foi escolhido por apresentar **desempenho previsível**, inclusive quando o
padrão contém muitas repetições de caracteres. Antes da busca, ele constrói um
vetor **LPS** (*Longest Proper Prefix which is also Suffix*). Em cada posição,
esse vetor armazena o tamanho do maior prefixo próprio do padrão que também é
sufixo do trecho já examinado. Quando ocorre uma divergência, o algoritmo usa o
LPS para continuar de uma posição que aproveita as comparações anteriores, sem
retroceder no texto nem recomeçar a análise do padrão do zero.

#### 2. Boyer-Moore-Horspool (BMH) — `src/pattern/BoyerMooreHorspoolMatcher.java`

O BMH foi escolhido para representar uma estratégia diferente: ele compara o
padrão **da direita para a esquerda** e utiliza uma **tabela de deslocamentos**,
preparada antes da busca, para determinar quanto a janela de comparação pode
avançar quando há correspondência ou divergência. Em situações favoráveis, isso
permite ignorar posições intermediárias e realizar menos comparações que uma
busca que avança sempre um caractere.

## Testes

Não há framework de teste no projeto; cada teste é uma classe executável que falha com
`AssertionError`:

```bash
java -cp bin index.BPlusTreeIndexSelfTest           # inserção, busca e remoção na B+
java -cp bin RegressionSelfTest                     # hash, índice após ordenação, listas ao recarregar
java -cp bin compression.CompressionSelfTest files/songs.bin   # ida e volta de Huffman e LZW
java -cp bin pattern.PatternMatchingSelfTest            # casos normais, sobrepostos, Unicode e aleatórios
```

`CompressionSelfTest` cobre entrada vazia, 1 byte, byte repetido, os 256 valores, o caso especial
do LZW, 3 MB de texto (enche o dicionário do LZW) e arquivos truncados (precisam dar erro). O
argumento é opcional e inclui a base real no teste.

## Limitações conhecidas

- A compressão carrega o arquivo inteiro em memória (limite prático ~2 GB, bem acima da base).
- O arquivo da B+ não reaproveita nós descartados por fusão — só cresce. Reconstruir o índice
  (opção 7) resolve.
- O dicionário da Lista Invertida fica inteiro em memória enquanto o programa roda.
- `interfaces.RecordInput` não é implementada por `SongInputReader` (interface sem uso).
