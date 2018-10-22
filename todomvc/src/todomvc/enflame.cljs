(ns todomvc.enflame ; #todo => re-state ???
  (:require
    [re-frame.core :as rf] ))


(defn dissoc-in
  "A sane version of dissoc-in that will not delete intermediate keys.
   When invoked as (dissoc-in the-map [:k1 :k2 :k3... :kZ]), acts like
   (clojure.core/update-in the-map [:k1 :k2 :k3...] dissoc :kZ). That is, only
   the map entry containing the last key :kZ is removed, and all map entries
   higher than kZ in the hierarchy are unaffected."
  [the-map keys-vec ]
  (let [num-keys     (count keys-vec)
        key-to-clear (last keys-vec)
        parent-keys  (butlast keys-vec)]
    (cond
      (zero? num-keys) the-map
      (= 1 num-keys) (dissoc the-map key-to-clear)
      :else (update-in the-map parent-keys dissoc key-to-clear))))

;---------------------------------------------------------------------------------------------------
(def ascii-code-return 13) ; #todo => tupelo.ascii
(def ascii-code-escape 27)
(defn event-val [event]  (-> event .-target .-value))

(defn from-topic [topic] @(rf/subscribe topic)) ; #todo was (listen ...)

;---------------------------------------------------------------------------------------------------

; #todo need macro  (definterceptor todos-done {:name ...   :enter ...   :leave ...} )

(defn event-handler-for! [& args] (apply rf/reg-event-fx args))

(defn dispatch-event [& args] (apply rf/dispatch args) )

(defn dispatch-event-sync [& args] (apply rf/dispatch-sync args) )

(defn define-topic! [& forms] (apply rf/reg-sub forms))

; #todo need macro  (with-path ctx [:db :todos] ...) ; extract and replace in ctx
; #todo need macro  (with-db ctx ...) ; hardwired for path of [:db]

; #todo remember this (modify into `(definterceptor trim-event { ... } )`
(comment
  (def trim-event
    (re-frame.core/->interceptor ; takes a naked map
      :id     :trim-event
      :before (fn [context]
                (let [trim-fn (fn [event] (-> event rest vec))]
                  (update-in context [:coeffects :event] trim-fn)))))
  )
