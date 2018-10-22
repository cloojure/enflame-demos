(ns todomvc.views
  (:require
    [clojure.string :as str]
    [re-frame.core :as rf ]
    [reagent.core :as r]
    [todomvc.enflame :as flame] ))

; These functions are all Reagent components

(defn input-field
  [{:keys [title on-save on-stop]}] ; #todo -> (with-map-vals [title on-save on-stop] ...)
  (let [text-val (r/atom title) ; local state
        stop-fn  (fn []
                   (reset! text-val "")
                   (when on-stop (on-stop)))
        save-fn  (fn []
                   (on-save (-> @text-val str str/trim))
                   (stop-fn))]
    (fn [props]
      [:input
       (merge (dissoc props :on-save :on-stop :title)
         {:type        "text"
          :value       @text-val
          :auto-focus  true
          :on-blur     save-fn
          :on-change   #(reset! text-val (flame/event-val %))
          :on-key-down #(let [rcvd (.-which %)] ; KeyboardEvent property
                          (condp = rcvd
                                flame/ascii-code-return (save-fn)
                                flame/ascii-code-escape (stop-fn)))})])))

(defn task-list-row []
  (let [editing (r/atom false)]
    (fn [{:keys [id done title]}]
      [:li {:class (cond-> ""
                     done (str " completed")
                     @editing (str " editing"))}
       [:div.view
        [:input.toggle
         {:type      :checkbox
          :checked   done
          :on-change #(flame/dispatch-event [:toggle-done id])}]
        [:label
         {:on-double-click #(reset! editing true)}
         title]
        [:button.destroy
         {:on-click #(flame/dispatch-event [:delete-todo id])}]]
       (when @editing
         [input-field
          {:class   "edit"
           :title   title
           :on-save #(if (seq %)
                       (flame/dispatch-event [:save id %])
                       (flame/dispatch-event [:delete-todo id]))
           :on-stop #(reset! editing false)}])])))

(defn task-list []
  (let [visible-todos (flame/from-topic [:visible-todos])
        all-complete? (flame/from-topic [:all-complete?])]
    [:section#main
     [:input#toggle-all
      {:type      "checkbox"
       :checked   all-complete?
       :on-change #(flame/dispatch-event [:complete-all-toggle])}]
     [:label        ; #todo this does not seem to work (as a tooltip?)
      {:for "toggle-all"}
      "Mark all as complete"]
     [:ul#todo-list
      (for [todo-curr visible-todos]
        ^{:key (:id todo-curr)} [task-list-row todo-curr])]])) ; delegate to task-list-row component

(defn footer-controls []
  (let [[num-active num-done] (flame/from-topic [:footer-counts])
        showing (flame/from-topic [:showing])
        a-fn    (fn [filter-kw txt]
                  [:a {:class (when (= filter-kw showing) "selected")
                       :href  (str "#/" (name filter-kw))} txt])]
    [:footer#footer
     [:span#todo-count
      [:strong num-active] " " (case num-active 1 "item" "items") " left"]
     [:ul#filters
      [:li (a-fn :all "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done "Completed")]]
     (when (pos? num-done)
       [:button#clear-completed
        {:on-click #(flame/dispatch-event [:clear-completed])}
        "Clear completed"])]))

(defn task-entry []
  [:header#header
   [:h1 "todos"]
   [input-field
    {:id          "new-todo"
     :placeholder "What needs to be done?"
     :on-save     #(when-not (empty? (str/trim %))
                     (flame/dispatch-event [:add-todo %]))}]])

(defn todo-app []
  [:div
   [:section#todoapp
    [task-entry]
    (when-not (empty? (flame/from-topic [:todos]))
      [task-list])
    [footer-controls]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])
