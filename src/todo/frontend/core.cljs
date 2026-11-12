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
  (swap! app-state assoc :error nil)
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
  (swap! app-state assoc :error nil)
  (go
    (try
      (<p! (fetch-json (str api-url "/todos/" id)
                       {:method "DELETE"}))
      (get-todos)
      (catch js/Error e
        (swap! app-state assoc :error (.-message e) :loading false)))))

;; --- Ícones SVG Inline ---

(defn icon-logo
  "Ícone vetorial principal de lista de tarefas."
  []
  [:svg {:width 22 :height 22 :viewBox "0 0 24 24" :fill "none"
         :stroke "currentColor" :stroke-width 2
         :stroke-linecap "round" :stroke-linejoin "round"
         :aria-hidden "true"}
   [:path {:d "M9 11l3 3L22 4"}]
   [:path {:d "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"}]])

(defn icon-plus
  "Ícone vetorial de adição."
  []
  [:svg {:width 18 :height 18 :viewBox "0 0 24 24" :fill "none"
         :stroke "currentColor" :stroke-width 2.2
         :stroke-linecap "round" :stroke-linejoin "round"
         :aria-hidden "true"}
   [:path {:d "M12 5v14"}]
   [:path {:d "M5 12h14"}]])

(defn icon-check
  "Ícone vetorial de conclusão para o checkbox customizado."
  []
  [:svg {:width 13 :height 13 :viewBox "0 0 24 24" :fill "none"
         :stroke "currentColor" :stroke-width 3
         :stroke-linecap "round" :stroke-linejoin "round"
         :aria-hidden "true"}
   [:path {:d "M20 6L9 17l-5-5"}]])

(defn icon-trash
  "Ícone vetorial de lixeira para exclusão de tarefa."
  []
  [:svg {:width 16 :height 16 :viewBox "0 0 24 24" :fill "none"
         :stroke "currentColor" :stroke-width 2
         :stroke-linecap "round" :stroke-linejoin "round"
         :aria-hidden "true"}
   [:path {:d "M3 6h18"}]
   [:path {:d "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"}]
   [:path {:d "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"}]
   [:path {:d "M10 11v6"}]
   [:path {:d "M14 11v6"}]])

(defn icon-empty
  "Ícone vetorial para o estado de lista vazia."
  []
  [:svg {:width 24 :height 24 :viewBox "0 0 24 24" :fill "none"
         :stroke "currentColor" :stroke-width 1.8
         :stroke-linecap "round" :stroke-linejoin "round"
         :aria-hidden "true"}
   [:rect {:x 8 :y 2 :width 8 :height 4 :rx 1 :ry 1}]
   [:path {:d "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"}]
   [:path {:d "M9 12h6"}]
   [:path {:d "M9 16h4"}]])

(defn icon-alert
  "Ícone vetorial de aviso para mensagens de erro."
  []
  [:svg {:width 16 :height 16 :viewBox "0 0 24 24" :fill "none"
         :stroke "currentColor" :stroke-width 2
         :stroke-linecap "round" :stroke-linejoin "round"
         :aria-hidden "true"}
   [:circle {:cx 12 :cy 12 :r 10}]
   [:path {:d "M12 8v4"}]
   [:path {:d "M12 16h.01"}]])

;; --- Componentes da Interface (Reagent / Hiccup) ---

(defn todo-form
  "Formulário para inserção de novas tarefas com suporte a Enter e limpeza de espaços."
  []
  (let [titulo (:input-text @app-state)
        vazio? (str/blank? titulo)
        submeter (fn []
                   (when-not vazio?
                     (create-todo {:title (str/trim titulo)})
                     (swap! app-state assoc :input-text "")))]
    [:div.todo-input
     [:input
      {:id "todo-title-input"
       :name "title"
       :type "text"
       :placeholder "O que precisa ser feito? (Pressione Enter)"
       :value titulo
       :on-change #(swap! app-state assoc :input-text (-> % .-target .-value))
       :on-key-down #(when (= (.-key %) "Enter")
                       (submeter))}]
     [:button.add-btn
      {:type "button"
       :disabled vazio?
       :on-click submeter}
      [icon-plus]
      [:span "Adicionar"]]]))

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
         [:div.empty-state
          [:div.empty-icon [icon-empty]]
          [:p.empty-title "Nenhuma tarefa cadastrada"]
          [:p.empty-hint "Adicione uma tarefa acima ou pressione Enter para começar."]])
       [:ul.todo-list
        (for [todo todos]
          (let [id (or (:todos/id todo) (:id todo))
                titulo (or (:todos/title todo) (:title todo))
                completed-val (or (:todos/completed todo) (:completed todo) 0)
                is-completed (if (boolean? completed-val)
                               completed-val
                               (not= 0 completed-val))
                checkbox-id (str "todo-check-" id)]
            ^{:key id}
            [:li.todo-item {:class (when is-completed "completed")}
             [:label.todo-checkbox-label
              [:input.todo-checkbox-input
               {:id checkbox-id
                :name (str "todo-" id)
                :type "checkbox"
                :checked is-completed
                :on-change #(toggle-todo id)}]
              [:span.todo-checkbox-box
               (when is-completed [icon-check])]]
             [:label.todo-title {:for checkbox-id} titulo]
             [:button.delete-btn
              {:type "button"
               :title "Excluir tarefa"
               :aria-label (str "Excluir tarefa: " titulo)
               :on-click #(delete-todo id)}
              [icon-trash]]]))])
     (when (pos? total)
       [:div.todo-footer
        [:span.stat-pill (str pendentes " pendente(s)")]
        [:div.stat-group
         [:span.stat-pill (str concluidas " concluída(s)")]
         [:span (str "Total: " total)]]])]))

(defn app
  "Componente raiz com indicadores de carregamento e mensagem de erro amigável."
  []
  [:div.todo-app
   [:header.app-header
    [:div.app-badge [icon-logo]]
    [:h1 "Todo List"]
    [:p.app-subtitle "Organize suas tarefas diárias"]]
   (when (:loading @app-state)
     [:div.status-banner.loading "Carregando tarefas..."])
   (when-let [erro (:error @app-state)]
     [:div.status-banner.error
      [icon-alert]
      [:span (str "Aviso: " erro)]])
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
