(ns todo.frontend.core
  "Interface reativa do Todo List em ClojureScript consumindo API REST via core.async."
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [clojure.string :as str]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]))

;; --- Configurações de API ---
(def api-url "http://localhost:3000/api")

;; --- Estado Reativo Global (r/atom) ---
(defonce app-state
  (r/atom {:input-text ""
           :todos []
           :loading false
           :error nil}))

;; --- Camada de Comunicação Assíncrona (fetch + core.async) ---

(defn fetch-json
  "Executa requisição HTTP fetch assíncrona e converte a resposta JSON em mapas Clojure."
  [url options]
  (-> (js/fetch url (clj->js options))
      (.then (fn [response]
               (when-not (.-ok response)
                 (throw (js/Error. (str "Erro HTTP: " (.-status response)))))
               (.json response)))
      (.then #(js->clj % :keywordize-keys true))))

(defn get-todos
  "Busca a lista de tarefas da API REST e atualiza o app-state."
  []
  (swap! app-state assoc :loading true :error nil)
  (go
    (try
      (let [response (<p! (fetch-json (str api-url "/todos") {:method "GET"}))]
        (swap! app-state assoc :todos (:todos response) :loading false))
      (catch js/Error e
        (swap! app-state assoc :error (.-message e) :loading false)))))

(defn create-todo
  "Envia uma nova tarefa para a API REST via método POST."
  [todo-data]
  (swap! app-state assoc :loading true :error nil)
  (go
    (try
      (<p! (fetch-json (str api-url "/todos")
                       {:method "POST"
                        :headers {"Content-Type" "application/json"}
                        :body (js/JSON.stringify (clj->js todo-data))}))
      ;; Ao confirmar a criação, atualiza a lista chamando get-todos:
      (get-todos)
      (catch js/Error e
        (swap! app-state assoc :error (.-message e) :loading false)))))

;; --- Componentes da Interface (Reagent / Hiccup) ---

(defn todo-form
  "Formulário para inserção de novas tarefas com disparo assíncrono."
  []
  [:div.todo-input
   [:input
    {:type "text"
     :placeholder "O que precisa ser feito?"
     :value (:input-text @app-state)
     :on-change #(swap! app-state assoc :input-text (-> % .-target .-value))}]
   [:button
    {:on-click (fn []
                 (let [titulo (:input-text @app-state)]
                   (when-not (str/blank? titulo)
                     (create-todo {:title titulo})
                     (swap! app-state assoc :input-text ""))))}
    "Adicionar"]])

(defn todo-list
  "Listagem das tarefas recebidas da API."
  []
  [:ul.todo-list
   (for [todo (:todos @app-state)]
     ^{:key (or (:id todo) (:todos/id todo))}
     [:li.todo-item
      [:span.todo-title (or (:title todo) (:todos/title todo))]])])

(defn app
  "Componente raiz da aplicação com tratamento de status de carregamento e erro."
  []
  [:div.todo-app
   [:h1 "Todo List"]
   (when (:loading @app-state)
     [:p {:style {:color "#64748b" :text-align "center"}} "Carregando tarefas..."])
   (when-let [erro (:error @app-state)]
     [:p {:style {:color "#ef4444" :text-align "center"}} (str "Aviso: " erro)])
   [todo-form]
   [todo-list]])

;; --- Ponto de Entrada da Aplicação ---
(defn ^:export init
  "Inicializa o React 19 e dispara a carga inicial de tarefas da API."
  []
  (println "Frontend Todo List inicializado...")
  (let [root (rdom/create-root (js/document.getElementById "app"))]
    (.render root (r/as-element [app])))
  (get-todos))
