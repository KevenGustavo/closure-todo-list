(ns todo.backend.handler
  "Define os handlers HTTP da aplicacao."
  (:require [todo.backend.db :as db]
            [clojure.string :as str]))

(defn hello-handler
  "Retorna uma mensagem de boas-vindas."
  [_request]
  {:status 200
   :body "Hello, World!"})

(defn list-todos-handler
  "Lista todas as tarefas."
  [_request]
  {:status 200
   :body {:todos (db/get-all-todos)}})

(defn create-todo-handler
  "Valida e cria uma nova tarefa."
  [request]
  (let [todo-data (:body request)
        title (:title todo-data)]
    (if (and (string? title)
             (not (str/blank? title)))
      (let [new-todo (db/create-todo todo-data)]
        {:status 201
         :body new-todo})
      {:status 400
       :body {:error "O titulo (title) e obrigatorio"}})))