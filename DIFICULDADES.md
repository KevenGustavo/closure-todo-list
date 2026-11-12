# Dificuldades Encontradas e Soluções Técnicas — Todo List (Clojure)

Este documento registra as **principais dificuldades técnicas reais** enfrentadas durante o desenvolvimento incremental da aplicação full-stack em Clojure/ClojureScript, diretamente associadas aos marcos de commits do projeto. Este material fundamenta a reflexão crítica da dupla no vídeo de apresentação.

---

## 1. Bloqueio de CORS e Requisições Preflight `OPTIONS` (Fase 4 — Commit 5)

- **O Problema:** Ao conectar o frontend na porta `8000` com a API REST na porta `3000`, as chamadas `fetch` falhavam silenciosamente e o console do navegador exibia o erro de bloqueio por política de CORS (`Cross-Origin Resource Sharing`), embora requisições diretas via `curl` no terminal funcionassem perfeitamente.
- **Causa Técnica:** O bloqueio de CORS é imposto pelo navegador por segurança ao comunicar origens diferentes (portas distintas). Ao enviar uma requisição `POST` com o cabeçalho `Content-Type: application/json`, o navegador dispara previamente uma requisição de sondagem (**preflight**) via método HTTP `OPTIONS` consultando se o servidor aceita aquele cabeçalho e método. O servidor Jetty/Reitit não possuía rota para `OPTIONS` e retornava `404 Not Found`.
- **Solução:** Adicionamos a biblioteca `ring-cors/ring-cors` (`0.1.13`) em `deps.edn` e envolvemos a aplicação Ring (`app` em `src/todo/backend/core.clj`) com o middleware `wrap-cors` como camada mais externa ao redor do `ring/ring-handler`, configurando explicitamente a origem confiável (`#"http://localhost:8000"`), os métodos permitidos (`:get`, `:post`, `:put`, `:delete`, `:options`) e o header `:access-control-allow-headers ["Content-Type"]`. Estando por fora do roteador Reitit, o `wrap-cors` intercepta o preflight `OPTIONS` e responde `200 OK` antes que a requisição caia no handler padrão `404`.

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


---

## 5. Problemas no Roteamento da API com Reitit

**Problema:** Durante a implementação da API REST, o endpoint `/api/hello` respondia corretamente, enquanto `/api/todos` retornava `404 Not Found`, mesmo após reiniciar o servidor.

**Causa técnica:** A estrutura inicial de agrupamento das rotas não estava sendo interpretada como esperado pelo roteador Reitit.

**Solução:** Reorganizamos as rotas, declarando explicitamente os caminhos `/api/hello` e `/api/todos` no `ring/router`. Também verificamos as rotas registradas antes de continuar os testes HTTP.

**Aprendizado:** A organização das rotas é essencial para o funcionamento da API. Reiniciar o servidor não resolve problemas de configuração do roteador.

---

## 6. Erros na Inicialização do Backend

**Problema:** Durante a implementação dos endpoints de Toggle e DELETE, o comando `clj -M:run` apresentou uma exceção `NullPointerException` no ponto de entrada do Clojure.

**Causa técnica:** Ao alterar o arquivo `core.clj`, algumas definições importantes da aplicação foram removidas acidentalmente. Em uma das verificações, a resolução da função `todo.backend.core/-main` retornou `nil`.

**Solução:** Restauramos as definições necessárias:

- `def app`: conecta as rotas Reitit à aplicação Ring.
- `start-server`: inicializa o servidor Jetty.
- `-main`: inicializa o banco SQLite e inicia o backend.

Também utilizamos o comando abaixo para verificar se o ponto de entrada estava disponível:

```bash
clj -M -e "(require 'todo.backend.core) (println (resolve 'todo.backend.core/-main))"
```

**Aprendizado:** Em Clojure, é importante preservar a estrutura do namespace e validar as funções de inicialização após alterações nas rotas.

---

## 7. Problemas com Comandos Clojure no Terminal Bash

**Problema:** Durante a inicialização do SQLite, um comando executado com `clj -M -e` apresentou o erro `bash: !\: event not found`.

**Causa técnica:** O Bash tentou interpretar um ponto de exclamação dentro de uma string como parte do seu mecanismo de expansão do histórico de comandos.

**Solução:** Reformulamos o comando utilizando aspas simples externas, evitando a interpretação do caractere especial pelo Bash.

**Aprendizado:** Além da sintaxe do Clojure, é necessário considerar como o shell interpreta comandos e caracteres especiais.

---

## 8. Tratamento de Identificadores nas Requisições DELETE

**Problema:** Durante os testes de exclusão, uma requisição DELETE retornou `500 Server Error`.

**Causa técnica:** O comando de teste foi executado com o texto `ID` no lugar do identificador numérico da tarefa. O handler utilizava `Integer/parseInt`, que gera uma exceção quando recebe um valor não numérico.

**Solução aplicada:** Inicialmente consultamos as tarefas cadastradas por meio de `GET /api/todos` e utilizamos um ID numérico existente para executar a exclusão corretamente. Em seguida, aprimoramos `src/todo/backend/handler.clj` extraindo o helper privado `parse-id` (utilizando `parse-long` e validação `pos?`), fazendo com que tanto `toggle-todo-handler` quanto `delete-todo-handler` retornem `400 Bad Request` (`{:error "ID invalido"}`) em vez de `500 Server Error` quando recebem IDs inválidos.

**Aprendizado:** Uma API deve validar dados recebidos antes de processá-los, tratando entradas inválidas com códigos HTTP apropriados.
