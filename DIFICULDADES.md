# Dificuldades Encontradas e Soluções Técnicas — Todo List (Clojure)

Este documento registra as **principais dificuldades técnicas reais** enfrentadas durante o desenvolvimento incremental da aplicação full-stack em Clojure/ClojureScript, diretamente associadas aos marcos de commits do projeto. Este material fundamenta a reflexão crítica da dupla no vídeo de apresentação.

---

## 1. Bloqueio de CORS e Requisições Preflight `OPTIONS` (Fase 4 — Commit 5)

- **O Problema:** Ao conectar o frontend na porta `8000` com a API REST na porta `3000`, as chamadas `fetch` falhavam silenciosamente e o console do navegador exibia o erro de bloqueio por política de CORS (`Cross-Origin Resource Sharing`), embora requisições diretas via `curl` no terminal funcionassem perfeitamente.
- **Causa Técnica:** O bloqueio de CORS é imposto pelo navegador por segurança ao comunicar origens diferentes (portas distintas). Ao enviar uma requisição `POST` com o cabeçalho `Content-Type: application/json`, o navegador dispara previamente uma requisição de sondagem (**preflight**) via método HTTP `OPTIONS` consultando se o servidor aceita aquele cabeçalho e método. O servidor Jetty/Reitit não possuía rota para `OPTIONS` e retornava `404 Not Found`.
- **Solução:** Adicionamos a biblioteca `ring-cors/ring-cors` (`0.1.13`) e registramos o middleware `wrap-cors` como o **primeiro** (mais externo) no vetor de middlewares do `core.clj`, configurando explicitamente a origem confiável (`http://localhost:8000`), os métodos permitidos (`:get`, `:post`, `:put`, `:delete`) e o header `:access-control-allow-headers ["Content-Type"]`. Sendo o primeiro da cadeia, o `wrap-cors` intercepta o preflight e responde `200 OK` antes do roteador Reitit processar a rota.

---

## 2. Incompatibilidade de Dialeto de Dados: Keywords Qualificadas do `next.jdbc` (Fase 5 — Commit 6)

- **O Problema:** Logo após substituir o banco em memória por SQLite com `next.jdbc`, a lista de tarefas no navegador passou a renderizar itens vazios (bullet points sem texto) e o console gerou avisos de chave ausente no React (`Each child in a list should have a unique 'key' prop`).
- **Causa Técnica:** Por convenção idiomática em Clojure, a biblioteca `next.jdbc` mapeia as colunas SQL para mapas com chaves qualificadas pelo nome da tabela de origem: `{:todos/id 1, :todos/title "Estudar", :todos/completed 0}`. O frontend, implementado anteriormente na Fase 3, tentava ler chaves simples (`:id` e `:title`), resultando em valores `nil`.
- **Solução:** Atualizamos o componente `todo-list` em `src/todo/frontend/core.cljs` para extrair os dados utilizando as chaves qualificadas retornadas pelo banco:
  - Metadado de identidade do React: `^{:key (:todos/id todo)}`
  - Renderização do texto: `(:todos/title todo)`

---

## 3. Armazenamento de Booleanos no SQLite (0 e 1) vs. Checkbox HTML (Fase 6 — Commit 7)

- **O Problema:** Na implementação da funcionalidade de Toggle, as tarefas concluídas não exibiam o checkbox assinalado visualmente na tela, gerando comportamento inconsistente ao clicar.
- **Causa Técnica:** O SQLite não possui tipo de dados booleano nativo no disco, armazenando valores lógicos como inteiros `0` (falso) e `1` (verdadeiro). Contudo, o atributo `:checked` de um elemento `<input type="checkbox">` no Virtual DOM do React exige estritamente um valor booleano (`true` ou `false`).
- **Solução:** Introduzimos uma conversão funcional explícita no componente Reagent utilizando o operador `not=`:
  ```clojure
  :checked (not= 0 (:todos/completed todo))
  ```
  Dessa forma, se o valor for `1`, `(not= 0 1)` avalia para `true`; se for `0`, `(not= 0 0)` avalia para `false`, sincronizando perfeitamente o estado visual do DOM com o banco de dados.

---

## 4. Concorrência Segura na Geração Incremental de IDs (Fase 2 — Commit 3)

- **O Problema:** Ao desenhar o banco de dados em memória na Fase 2, havia o risco de colisão de identificadores (`id`) se duas requisições de criação de tarefas chegassem no mesmo instante em threads diferentes do servidor Jetty.
- **Causa Técnica:** Fazer a leitura do valor atual do contador com `@next-id` e, em um segundo momento, incrementá-lo com `(swap! next-id inc)` criaria uma condição de corrida (*race condition*), na qual duas threads leriam o mesmo ID e uma sobrescreveria a outra no mapa `todos-db`.
- **Solução:** Aproveitamos a garantia atômica da função `swap!`:
  ```clojure
  (let [id (swap! next-id inc)] ...)
  ```
  A função `swap!` executa a leitura, a aplicação de `inc` e a atualização do atom em uma única operação indivisível garantida pela JVM via instrução de hardware *Compare-And-Swap* (CAS), retornando com segurança o valor exclusivo para a nova tarefa.
