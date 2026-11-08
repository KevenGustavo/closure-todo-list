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
      (get-todos)
      (catch js/Error e
        (swap! app-state assoc :error (.-message e) :loading false)))))

(defn toggle-todo
  "Dispara a alternância de status de conclusão (concluído/pendente) de uma tarefa."
  [id]
  (go
    (try
      (<p! (fetch-json (str api-url "/todos/" id "/toggle")
                       {:method "POST"}))
      (get-todos)
      (catch js/Error e
        (swap! app-state assoc :error (.-message e) :loading false)))))

(defn delete-todo
  "Dispara a remoção definitiva de uma tarefa da lista."
  [id]
  (go
    (try
      (<p! (fetch-json (str api-url "/todos/" id)
                       {:method "DELETE"}))
      (get-todos)
      (catch js/Error e
        (swap! app-state assoc :error (.-message e) :loading false)))))

;; --- Componentes da Interface (Reagent / Hiccup) ---

(defn todo-form
  "Formulário para inserção de novas tarefas com suporte a Enter e limpeza de espaços."
  []
  (let [submeter (fn []
                   (let [titulo (:input-text @app-state)]
                     (when-not (str/blank? titulo)
                       (create-todo {:title (str/trim titulo)})
                       (swap! app-state assoc :input-text ""))))]
    [:div.todo-input
     [:input
      {:type "text"
       :placeholder "O que precisa ser feito? (Pressione Enter)"
       :value (:input-text @app-state)
       :on-change #(swap! app-state assoc :input-text (-> % .-target .-value))
       :on-key-down #(when (= (.-key %) "Enter")
                       (submeter))}]
     [:button
      {:on-click submeter}
      "Adicionar"]]))

(defn todo-list
  "Listagem interativa com feedback de lista vazia e contador reativo."
  []
  (let [todos (:todos @app-state)
        total (count todos)
        concluidas (count (filter (fn [t]
                                    (let [c (or (:todos/completed t) (:completed t) 0)]
                                      (if (boolean? c) c (not= 0 c))))
                                  todos))
        pendentes (- total concluidas)]
    [:div
     (if (empty? todos)
       (when-not (:loading @app-state)
         [:p {:style {:text-align "center" :color "#94a3b8" :padding "20px 0" :font-size "14px"}}
          "Nenhuma tarefa cadastrada. Adicione uma tarefa acima!"])
       [:ul.todo-list
        (for [todo todos]
          (let [id (or (:todos/id todo) (:id todo))
                titulo (or (:todos/title todo) (:title todo))
                completed-val (or (:todos/completed todo) (:completed todo) 0)
                is-completed (if (boolean? completed-val)
                               completed-val
                               (not= 0 completed-val))]
            ^{:key id}
            [:li.todo-item {:class (when is-completed "completed")}
             [:input.todo-checkbox
              {:type "checkbox"
               :checked is-completed
               :on-change #(toggle-todo id)}]
             [:span.todo-title titulo]
             [:button.delete-btn
              {:on-click #(delete-todo id)}
              "X"]]))])
     (when (pos? total)
       [:div {:style {:display "flex"
                      :justify-content "space-between"
                      :margin-top "16px"
                      :padding-top "12px"
                      :border-top "1px solid #edf2f7"
                      :font-size "13px"
                      :color "#64748b"}}
        [:span (str pendentes " pendente(s)")]
        [:span (str concluidas " concluída(s) | Total: " total)]])]))

(defn app
  "Componente raiz com indicadores de carregamento e mensagem de erro amigável."
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
  "Inicializa o React 19 e dispara a carga inicial das tarefas."
  []
  (println "Frontend Todo List inicializado...")
  (let [root (rdom/create-root (js/document.getElementById "app"))]
    (.render root (r/as-element [app])))
  (get-todos))
