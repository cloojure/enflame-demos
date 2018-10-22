(ns todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require
    [devtools.core :as devtools]
    [goog.events :as events]
    [re-frame.core :as rf]
    [reagent.core :as reagent]
    [secretary.core :as secretary]
    [todomvc.components]
    [todomvc.enflame :as flame]
    [todomvc.events] ; These two are only required to make the compiler
    [todomvc.topics] ; load them (see docs/Basic-App-Structure.md)
    )
  (:import [goog History]
           [goog.history EventType]))

; -- Debugging aids ----------------------------------------------------------
(devtools/install!) ; we love https://github.com/binaryage/cljs-devtools

; Put an initial value into app-db.
; The event handler for `:initialise-db` can be found in `events.cljs`
; Using the sync version of dispatch means that value is in
; place before we go onto the next step.
(flame/dispatch-event-sync [:initialise-db])
; #todo remove this - make a built-in :init that every event-handler verifies & waits for (top priority)
; #todo add concept of priority to event dispatch

; -- Routes and History ------------------------------------------------------
; Although we use the secretary library below, that's mostly a historical accident. You might also consider using:
;   - https://github.com/DomKM/silk
;   - https://github.com/juxt/bidi
; We don't have a strong opinion.

; #todo  make an `event` type & factory fn: (event :set-showing :all) instead of bare vec:  [:set-showing :all]
(defroute "/"        []       (flame/dispatch-event [:set-showing :all]))
(defroute "/:filter" [filter] (flame/dispatch-event [:set-showing (keyword filter)]))
; #todo fix secretary (-> bidi?) to avoid dup (:filter x2) and make more like pedestal

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
      (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

; -- Entry Point -------------------------------------------------------------
; Within ../../resources/public/index.html you'll see this code
;    window.onload = function () {
;      todomvc.core.main();
;    }
; So this is the entry function that kicks off the app once the HTML is loaded.
(defn ^:export main
  []
  (enable-console-print!) ; so that println writes to `console.log`
  ; Render the UI into the HTML's <div id="app" /> element
  ; The view function `todomvc.views/todo-app` is the
  ; root view for the entire UI.
  (reagent/render [todomvc.components/todo-app]
    (.getElementById js/document "app")))

