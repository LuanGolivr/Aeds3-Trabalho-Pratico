package pattern;

import java.util.List;

// Contrato para os algoritmos de casamento de padrões do TP3.

// O padrão é informado na criação do algoritmo, permitindo calcular suas
// estruturas auxiliares apenas uma vez e reaproveitá-las em várias músicas.
// A busca retorna os índices (iniciados em zero) de TODAS as ocorrências,
// inclusive quando elas se sobrepõem.

public interface PatternMatcher {

    // Nome exibido ao usuário ao comparar os algoritmos. 
    String name();

    // Procura o padrão no texto informado, sem usar busca pronta da biblioteca
    List<Integer> findAll(String text);
}
