(ns todo.backend.db
  "Gerencia os dados de tarefas em memoria usando atom.")

(def todos-db (atom {}))
(def next-id (atom 0))

(defn get-all-todos
  "Retorna todas as tarefas registradas."
  []
  (vec (vals @todos-db)))

(defn create-todo
  "Cria uma tarefa com ID unico."
  [todo-data]
  (let [id (swap! next-id inc)
        new-todo (assoc todo-data
                        :id id
                        :completed false
                        :created-at (str (java.time.Instant/now)))]
    (swap! todos-db assoc id new-todo)
    new-todo))