(ns todo.backend.core
  (:require [ring.adapter.jetty :as jetty]
            [reitit.ring :as ring]
            [ring.middleware.json :refer [wrap-json-response
                                          wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [todo.backend.handler :as handler]
            [todo.backend.db :as db])
  (:gen-class))

(def app-routes
  (ring/router
   [["/api/hello"
     {:get {:handler handler/hello-handler}}]

    ["/api/todos"
     {:get {:handler handler/list-todos-handler}
      :post {:handler handler/create-todo-handler}}]]

   {:data
    {:middleware
     [wrap-json-response
      [wrap-json-body {:keywords? true}]
      wrap-params
      wrap-keyword-params]}}))

(def app
  (ring/ring-handler
   app-routes
   (ring/create-default-handler)))

(defn start-server [port]
  (println (str "Servidor iniciado na porta " port))
  (jetty/run-jetty #'app {:port port :join? false}))

(defn -main [& args]
  (let [port (Integer/parseInt (or (first args) "3000"))]
    (db/initialize-database!)
    (start-server port)))