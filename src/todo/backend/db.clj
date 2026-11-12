(ns todo.backend.db
  "Gerencia a persistencia dos todos com SQLite e next.jdbc."
  (:require [next.jdbc :as jdbc]))

;; Configuracao do banco SQLite
(def db-spec
  {:dbtype "sqlite"
   :dbname "prod.db"})

;; Inicializacao do banco de dados
(defn initialize-database!
  "Cria a tabela todos caso ela nao exista."
  []
  (jdbc/execute! db-spec
    ["CREATE TABLE IF NOT EXISTS todos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT,
        description TEXT,
        completed BOOLEAN DEFAULT 0,
        created_at TEXT
      )"]))

;; Listagem de tarefas
(defn get-all-todos
  "Retorna todas as tarefas persistidas no banco."
  []
  (jdbc/execute! db-spec
    ["SELECT * FROM todos ORDER BY created_at DESC"]))

;; Criacao de tarefas
(defn create-todo
  "Insere uma tarefa no SQLite e retorna o registro criado."
  [todo-data]
  (jdbc/execute-one! db-spec
    ["INSERT INTO todos
        (title, description, completed, created_at)
      VALUES (?, ?, ?, ?)
      RETURNING *"
     (:title todo-data)
     (:description todo-data)
     0
     (str (java.time.Instant/now))]))

(defn toggle-todo!
  "Alterna o status de conclusao de uma tarefa."
  [id]
  (jdbc/execute-one! db-spec
    ["UPDATE todos
      SET completed = (1 - completed)
      WHERE id = ?
      RETURNING *"
     id]))

(defn delete-todo!
  "Remove uma tarefa pelo ID e retorna o registro excluido."
  [id]
  (jdbc/execute-one! db-spec
    ["DELETE FROM todos
      WHERE id = ?
      RETURNING *"
     id]))