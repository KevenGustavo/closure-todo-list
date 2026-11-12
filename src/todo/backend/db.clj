(ns todo.backend.db
  "Gerencia os dados de tarefas e a infraestrutura SQLite."
  (:require [next.jdbc :as jdbc]))

;; Configuracao do banco SQLite
(def db-spec
  {:dbtype "sqlite"
   :dbname "prod.db"})

;; Inicializacao do banco de dados
(defn initialize-database!
  "Cria a tabela de tarefas caso ela nao exista."
  []
  (jdbc/execute! db-spec
    ["CREATE TABLE IF NOT EXISTS todos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT,
        description TEXT,
        completed BOOLEAN DEFAULT 0,
        created_at TEXT
      )"]))

;; Banco em memoria - mantido ate a L5
(def todos-db (atom {}))
(def next-id (atom 0))

(defn get-all-todos
  "Retorna todas as tarefas registradas em memoria."
  []
  (vec (vals @todos-db)))

(defn create-todo
  "Cria uma tarefa com ID unico em memoria."
  [todo-data]
  (let [id (swap! next-id inc)
        new-todo (assoc todo-data
                        :id id
                        :completed false
                        :created-at (str (java.time.Instant/now)))]
    (swap! todos-db assoc id new-todo)
    new-todo))