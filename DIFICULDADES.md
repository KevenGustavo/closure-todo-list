# Dificuldades Encontradas e Soluções Técnicas — Todo List (Clojure)

Este documento registra as principais dificuldades técnicas, desafios conceituais de arquitetura funcional e particularidades do ecossistema Clojure/ClojureScript enfrentados durante o desenvolvimento da aplicação **Todo List Full-Stack**. Este material serve de fundamentação técnica para a reflexão crítica da dupla no vídeo de apresentação (critério avaliativo de 2,0 pontos).

---

## 1. Bloqueio de Política de Mesma Origem (CORS) e Requisições Preflight `OPTIONS`

- **Problema:** Na Fase 4, ao conectar o frontend ClojureScript rodando na porta `8000` (`http://localhost:8000`) com o backend Ring na porta `3000` (`http://localhost:3000`), as requisições `fetch` falharam silenciosamente na interface e o console do navegador disparou o erro:
  `Access to fetch at 'http://localhost:3000/api/todos' from origin 'http://localhost:8000' has been blocked by CORS policy`.
  Curiosamente, requisições via `curl` funcionavam perfeitamente no terminal.
- **Causa Técnica:** O bloqueio de CORS é uma restrição de segurança imposta pelo navegador (e não pelo servidor ou pelo `curl`) quando duas origens (mesmo protocolo, domínio ou porta diferentes) tentam compartilhar recursos. Mais ainda: ao enviar uma requisição `POST` contendo o cabeçalho `Content-Type: application/json`, o navegador dispara antes uma requisição de sondagem preliminar (**preflight**) via método HTTP `OPTIONS` para consultar se o servidor aceita receber aquele payload.
- **Solução:** Adicionamos a biblioteca `ring-cors/ring-cors` (`0.1.13`) e registramos o middleware `wrap-cors` como o **primeiro** (mais externo) no pipeline de middlewares do `core.clj`. Foi essencial configurar explicitamente `:access-control-allow-origin [#"http://localhost:8000"]`, os métodos permitidos (`:get`, `:post`, `:put`, `:delete`) e, em especial, o cabeçalho `:access-control-allow-headers ["Content-Type"]`. Sendo o primeiro da pilha, o `wrap-cors` intercepta o preflight `OPTIONS` e responde com `200 OK` antes mesmo que o roteador Reitit acuse rota inexistente (404).
- **Aprendizado:** Compreendemos a mecânica interna do protocolo HTTP moderno e o papel da arquitetura em pipeline do Ring, onde a ordem dos middlewares determina quem processa ou intercepta a requisição na descida e a resposta na subida.

---

## 2. Incompatibilidade e Descompasso de Versões do Shadow-CLJS (*Version Mismatch*)

- **Problema:** Ao tentar iniciar o frontend ClojureScript com `npx shadow-cljs watch app`, o terminal exibia advertências críticas de *version mismatch* e falhas de compilação de namespaces internos.
- **Causa Técnica:** O `shadow-cljs` é uma ferramenta híbrida: atua como cliente de linha de comando no ecossistema Node.js (`package.json`) e como compilador Clojure/Maven (`deps.edn`). Quando executado `npm install shadow-cljs` sem travar a versão, o npm instala a versão major mais recente (3.x), divergindo da versão declarada no `deps.edn` (2.28.23).
- **Solução:** Fixamos rigorosamente a versão `2.28.23` em ambos os arquivos:
  - No `package.json`: `"shadow-cljs": "2.28.23"`.
  - No `deps.edn`: `thheller/shadow-cljs {:mvn/version "2.28.23"}` com a chave `:deps true` habilitada no `shadow-cljs.edn`.
- **Aprendizado:** Aprendemos a importância de gerenciar dependências de forma coordenada em projetos poliglitas que interligam o ambiente Java/Clojure e o runtime JavaScript/Node.

---

## 3. Incompatibilidade de Dialeto de Dados: Keywords Qualificadas do `next.jdbc`

- **Problema:** Na Fase 5, logo após migrar o backend de banco em memória para SQLite, a interface web passou a renderizar itens vazios na lista de tarefas (bullet points sem texto) e o console do navegador gerou alertas contínuos de `:key` ausente nos elementos React.
- **Causa Técnica:** Ao consultar tabelas SQL, a biblioteca moderna `next.jdbc` adota como convenção idiomática de Clojure o retorno de mapas com chaves qualificadas pelo nome da tabela de origem:
  `{:todos/id 1, :todos/title "Estudar", :todos/completed 0}`.
  O frontend, por sua vez, havia sido implementado na Fase 3 esperando keywords simples (`:id` e `:title`), resultando em avaliações de `nil`.
- **Solução:** Atualizamos o componente `todo-list` em `src/todo/frontend/core.cljs` em dois pontos cruciais:
  1. A chave de identificação do React no metadado: `^{:key (:todos/id todo)}`.
  2. O acesso ao título da tarefa: `(:todos/title todo)`.
- **Aprendizado:** Experimentamos na prática o conceito de acoplamento de schema entre camadas e a importância de contratos claros de serialização JSON em APIs REST funcionais.

---

## 4. Representação Numérica de Booleanos no SQLite (0 e 1) vs Checkbox HTML

- **Problema:** Na implementação da funcionalidade de Toggle (Fase 6), as tarefas marcadas como concluídas não exibiam o checkbox assinalado na tela, ou exibiam comportamento errático ao clicar.
- **Causa Técnica:** O banco de dados SQLite não possui um tipo `BOOLEAN` nativo real no disco; ele armazena booleanos internamente como inteiros `0` (falso) e `1` (verdadeiro). No entanto, a propriedade `:checked` de um elemento `<input type="checkbox">` no DOM exige um booleano estrito (`true` ou `false`). Em JavaScript, o valor `0` ou `1` não se comporta da mesma forma que em Clojure, e passar inteiros diretos desestabiliza o estado controlado do React.
- **Solução:** Introduzimos no componente Reagent uma conversão explícita de inteiro para booleano com o operador funcional `not=`:
  `:checked (not= 0 (:todos/completed todo))`.
  Dessa forma:
  - Se for `1`: `(not= 0 1)` resulta em `true`.
  - Se for `0`: `(not= 0 0)` resulta em `false`.
- **Aprendizado:** Entendemos as nuances de interoperabilidade entre sistemas de tipos de bancos relacionais e interfaces web, e a importância de normalizar dados na camada de apresentação.

---

## 5. Concorrência e Risco de Colisão de IDs no Estado em Memória (`atom`)

- **Problema:** Na Fase 2, ao implementar a geração de IDs para novas tarefas, havia a tentação de consultar o valor atual do ID e depois incrementá-lo em dois passos separados:
  `id @next-id` seguido de `(swap! next-id inc)`.
- **Causa Técnica:** Em um servidor web multithreaded (como o Jetty), se duas requisições de criação de tarefa chegam simultaneamente em threads diferentes, ambas poderiam ler o mesmo valor em `@next-id` antes de qualquer uma gravar o incremento. Isso faria com que a segunda requisição sobrescrevesse a primeira no mapa `todos-db`.
- **Solução:** Utilizamos a natureza atômica do `swap!`:
  `(let [id (swap! next-id inc)] ...)`.
  A função `swap!` executa a leitura, aplicação da função `inc` e atualização em um único passo indivisível garantido pela JVM (usando *Compare-And-Swap* - CAS), além de retornar imediatamente o novo valor incrementado para ser associado à tarefa.
- **Aprendizado:** Compreendemos os mecanismos de concorrência segura em Clojure sem necessidade de locks manuais complexos (*mutexes*).

---

## 6. Diferença Conceitual entre o `atom` do Clojure e o `r/atom` do Reagent

- **Problema:** Durante a construção da interface reativa, criar o estado do formulário com o `atom` comum de Clojure (`clojure.core/atom`) não atualizava a tela quando o usuário digitava no input.
- **Causa Técnica:** O `atom` padrão de Clojure é uma estrutura pura de dados em memória que não possui ligação com o ciclo de vida do navegador. O Reagent necessita de uma estrutura observável para saber quando disparar o recálculo do Virtual DOM.
- **Solução:** Utilizamos `reagent.core/atom` (apelidado como `r/atom`). Ele implementa um mecanismo de publicação/assinatura onde qualquer componente que faça a dereferência `@app-state` é automaticamente re-renderizado sempre que um `swap!` ou `reset!` alterar os dados daquela caixa.
- **Aprendizado:** Dominamos o padrão reativo funcional do Reagent, entendendo a separação entre estado de dados (modelo) e projeção de visualização (visão).

---

## 7. Retorno Imediato de Linhas Afetadas com a Cláusula SQL `RETURNING *`

- **Problema:** Ao inserir, alternar ou deletar tarefas no SQLite via `next.jdbc`, o retorno padrão de uma operação de escrita (`INSERT`/`UPDATE`/`DELETE`) é apenas a contagem de registros afetados (`#:next.jdbc{:update-count 1}`), o que não atende ao contrato REST de responder com o objeto criado ou modificado.
- **Causa Técnica:** Tradicionalmente em SQL, recuperar o objeto recém-inserido ou deletado exigiria duas operações: primeiro executar a escrita e, em seguida, fazer um novo `SELECT` baseado em `last_insert_rowid()`. Essa abordagem é ineficiente e sujeita a condições de corrida.
- **Solução:** Aproveitamos o suporte moderno do SQLite (a partir da versão 3.35) à cláusula `RETURNING *`. Combinada com a função `jdbc/execute-one!`, o comando SQL:
  `INSERT INTO todos (...) VALUES (...) RETURNING *` ou `UPDATE todos SET ... RETURNING *`
  executa a mutação no banco e devolve em uma única viagem de rede a linha completa exatamente como gravada.
- **Aprendizado:** Aprofundamos o conhecimento em dialetos SQL modernos e evitamos sobrecarga desnecessária na camada de persistência.

---

## 8. Idempotência e Ordem de Inicialização do Banco no Ciclo de Vida da Aplicação

- **Problema:** Ao subir o servidor backend pela primeira vez em uma máquina sem o arquivo `prod.db`, chamadas às rotas resultavam em erros de "tabela inexistente" (`no such table: todos`).
- **Causa Técnica:** A inicialização do banco de dados (DDL) não deve depender de ações do usuário via navegador. Ela deve ser executada obrigatoriamente antes do servidor começar a aceitar requisições de rede.
- **Solução:** Criamos a função `initialize-database!` com a instrução SQL `CREATE TABLE IF NOT EXISTS todos (...)`. No ponto de entrada `-main` de `core.clj`, inserimos a chamada a `(db/initialize-database!)` imediatamente antes do disparo de `(start-server port)`.
- **Aprendizado:** Compreendemos a importância de garantir idempotência e ordem determinística de inicialização na infraestrutura de aplicações web funcionais.
