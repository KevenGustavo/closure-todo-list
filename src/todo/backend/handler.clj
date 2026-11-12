(ns todo.backend.handler
  "Handlers HTTP da aplicação.")

(defn hello-handler
  "Retorna uma mensagem de boas-vindas."
  [_request]
  {:status 200
   :body "Hello, World!"})