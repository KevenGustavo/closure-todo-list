(ns todo.frontend.core
  "Interface reativa do Todo List em ClojureScript utilizando Reagent sobre React 19."
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [clojure.string :as str]))

;; --- Estado Reativo Local (r/atom) ---
;; Mantém os dados na memória do navegador de forma isolada nesta Fase 3.
(defonce app-state
  (r/atom {:next-id 1
           :input-text ""
           :todos []}))

;; --- Lógica de Negócios Local ---
(defn adicionar-todo-local
  "Adiciona uma nova tarefa ao estado local da aplicação."
  []
  (swap! app-state
         (fn [estado-atual]
           (let [novo-titulo (:input-text estado-atual)
                 novo-id (:next-id estado-atual)]
             (if (str/blank? novo-titulo)
               estado-atual
               {:next-id (inc novo-id)
                :input-text ""
                :todos (conj (:todos estado-atual)
                             {:id novo-id
                              :title novo-titulo})})))))

;; --- Componentes Hiccup (Reagent) ---

(defn todo-form
  "Componente de formulário para inserção de novas tarefas."
  []
  [:div.todo-input
   [:input
    {:type "text"
     :placeholder "O que precisa ser feito?"
     :value (:input-text @app-state)
     :on-change #(swap! app-state assoc :input-text (-> % .-target .-value))}]
   [:button
    {:on-click adicionar-todo-local}
    "Adicionar (Local)"]])

(defn todo-list
  "Componente de listagem reativa das tarefas existentes."
  []
  [:ul.todo-list
   (for [todo (:todos @app-state)]
     ^{:key (:id todo)}
     [:li.todo-item
      (:title todo)])])

(defn app
  "Componente principal que organiza a visualização da aplicação."
  []
  [:div.todo-app
   [:h1 "Todo App (Somente Frontend)"]
   [:p "Isto é 100% local. Recarregue (F5) para ver os dados sumirem."]
   [todo-form]
   [todo-list]])

;; --- Inicialização (React 18/19 via create-root) ---
(defn ^:export init
  "Ponto de entrada montado pelo shadow-cljs ao carregar a página."
  []
  (println "Frontend 'Todo App (Local)' inicializado...")
  (let [root (rdom/create-root (js/document.getElementById "app"))]
    (.render root (r/as-element [app]))))
