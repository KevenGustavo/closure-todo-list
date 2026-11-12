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

(defn toggle-todo-handler
  "Alterna o status de conclusao de uma tarefa pelo ID."
  [request]
  (let [id (Integer/parseInt
             (get-in request [:path-params :id]))]
    (if-let [updated-todo (db/toggle-todo! id)]
      {:status 200
       :body updated-todo}
      {:status 404
       :body {:error "Todo nao encontrado"}})))

(defn delete-todo-handler
  "Exclui uma tarefa pelo ID."
  [request]
  (let [id (Integer/parseInt
             (get-in request [:path-params :id]))]
    (if-let [deleted-todo (db/delete-todo! id)]
      {:status 200
       :body deleted-todo}
      {:status 404
       :body {:error "Todo nao encontrado"}})))