(ns todomvc.events
  (:require
    [cljs.spec.alpha :as s]
    [re-frame.core :as rf]
    [todomvc.db :as todo-db]
    [todomvc.enflame :as flame]))

; context map (ctx):   { :coeffects   {:db {...}
;                                      :other {...}}
;                        :effects     {:db {...}
;                                      :dispatch [...]}
;                      }
; intceptors' :before fns should accumulate data into :coeffects map
; intceptors' :after  fns should accumulate data into   :effects map

;-----------------------------------------------------------------------------
; #todo unify interceptors/handlers:  all accept & return
; #todo   ctx => {:state {...}   ; was `:db`
; #todo           ... ...}       ; other info here
; #todo unify coeffects (input vals) and effects (output vals)
; #todo interceptors must fill in or act up vals (keys) they care about

; #todo (definterceptor my-intc  ; added to :id field as kw
; #todo   "doc string"
; #todo   {:enter (fn [ctx] ...)              tx: ctx => ctx
; #todo    :leave (fn [ctx] ...) } )

;-----------------------------------------------------------------------------
; #todo :before    => :enter       to match pedestal
; #todo :after     => :leave

; coeffects  =>  state-in
;   effects  =>  state-out

; #todo   maybe rename interceptor chain to intc-chain, proc-chain, transform-chain

; #todo   unify [:dispatch ...] effect handlers
; #todo     {:do-effects [  ; <= always a vector param, else a single effect
; #todo        {:effect/id :eff-tag-1  :par1 1  :par2 2}
; #todo        {:effect/id :eff-tag-2  :effect/delay {:value 200  :unit :ms} ;
; #todo         :some-param "hello"  :another-param :italics } ] }

; #todo make all routes define an intc chain.
; #todo each intc is {:id ...  :enter ...  :leave ...} (coerce if :before/:after found - strictly)
; #todo each :enter/:leave fn is (fn [params-map] ...)
; #todo    where params-map  =>  {:event {:event/id ...  :param1 <val1>  :param2 <val2> ...}
; #todo                           :state {:app          ...
; #todo                                   :local-store  ...
; #todo                                   :datascript   ... }}

; #todo replace (reg-cofx ...)  =>  (definterceptor ...)  ; defines a regular fn

; #todo [:delete-item 42] => {:event/id :delete-item :value 42}
; #todo   {:event/id :add-entry  :key :name :value "Joe"}
; #todo   {:event/id :set-timer  :units :ms :value 50 :action (fn [] (js/alert "Expired!") }

; #todo (dispatch-event {:event/id <some-id> ...} )   => event map
; #todo (add-effect ctx {:effect/id <some-id> ...} )  => updated ctx


; -- Interceptors --------------------------------------------------------------
;
; Interceptors are a more advanced topic. So, we're plunging into the deep
; end here.
;
; There is a tutorial on Interceptors in re-frame's `/docs`, but to get
; you going fast, here's a very high level description ...
;
; Every event handler can be "wrapped" in a chain of interceptors. A
; "chain of interceptors" is actually just a "vector of interceptors". Each
; of these interceptors can have a `:before` function and an `:after` function.
; Each interceptor wraps around the "handler", so that its `:before`
; is called before the event handler runs, and its `:after` runs after
; the event handler has run.
;
; Interceptors with a `:before` action, can be used to "inject" values
; into what will become the `coeffects` parameter of an event handler.
; That's a way of giving an event handler access to certain resources,
; like values in LocalStore.
;
; Interceptors with an `:after` action, can, among other things,
; process the effects produced by the event handler. One could
; check if the new value for `app-db` correctly matches a Spec.

; -- First Interceptor ------------------------------------------------------
;
; Event handlers change state, that's their job. But what happens if there's
; a bug in the event handler and it corrupts application state in some subtle way?
; First, we create an interceptor called `check-spec-interceptor`. Then,
; we use this interceptor in the interceptor chain of all event handlers.
; When included in the interceptor chain of an event handler, this interceptor
; runs `check-and-throw` `after` the event handler has finished, checking
; the value for `app-db` against a spec.
; If the event handler corrupted the value for `app-db` an exception will be
; thrown. This helps us detect event handler bugs early.
; Because all state is held in `app-db`, we are effectively validating the
; ENTIRE state of the application after each event handler runs.  All of it.
(defn check-and-throw
  "Throws an exception if `db` doesn't match the Spec `a-spec`."
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

; create an `after` interceptor
(def check-spec-intc (rf/after (partial check-and-throw :todomvc.db/db)))

; -- Interceptor Chain ------------------------------------------------------
; Each event handler can have its own chain of interceptors.
; We now create the interceptor chain shared by all event handlers
; which manipulate todos. A chain of interceptors is a vector of interceptors.
; Explanation of the `path` Interceptor is given further below.
(def std-interceptors
  [
   ; #awt check-spec-intc      ; ensure the spec is still valid  (rf/after)

   ; Part of the TodoMVC is to store todos in local storage. Here we define an interceptor to do this.
   ; This interceptor runs `after` an event handler. It stores the current todos into local storage.
   (rf/after todo-db/todos->local-store)]) ; write todos to localstore  (rf/after)

; -- Helpers -----------------------------------------------------------------
(defn allocate-next-id
  "Returns the next todo id. Assumes todos are sorted.
  Returns one more than the current largest id."
  [todos]
  ((fnil inc 0) (last (keys todos))))

; -- Event Handlers ----------------------------------------------------------
; usage:  (flame/dispatch-event [:initialise-db])
;
; This event is dispatched when the app's `main` ns is loaded (todomvc.core).
; It establishes initial application state in `app-db`. That means merging:
;   1. Any todos stored in LocalStore (from the last session of this app)
;   2. Default initial values
;
; Advanced topic:  we inject the todos currently stored in LocalStore
; into the first, coeffect parameter via use of the interceptor
;    `(rf/inject-cofx :local-store-todos)`
;
; To fully understand this advanced topic, you'll have to read the tutorials
; and look at the bottom of `db.cljs` for the `:local-store-todos` cofx registration.
(flame/event-handler-for! :initialise-db
  ; #todo   => (event-handler-set!    :evt-name  (fn [& args] ...)) or subscribe-to  subscribe-to-event
  ; #todo   => (event-handler-clear!  :evt-name)
  ; #todo option for log wrapper (with-event-logging  logger-fn
  ; #todo                          (event-handler-set! :evt-name  (fn [& args] ...)))

  ; the interceptor chain (a vector of 2 interceptors in this case)
  [(rf/inject-cofx :local-store-todos) ; gets todos from localstore, and puts value into coeffects arg
   ; #awt check-spec-intc
   ]  ; after event handler runs, check app-db for correctness. Does it still match Spec?
  ; #todo  context -> event
  ; #todo    :event/id
  ; #todo    :event/params
  ; #todo    coeffects -> inputs    ; data
  ; #todo      effects -> outputs   ; result

  ; the event handler being registered
  (fn [ {:keys [db local-store-todos]} <> ]                  ; take 2 values from coeffects. Ignore event vector itself.
    (println :initialise-db )
    (let [result {:db  todo-db/default-db ; #awt
                  ; (assoc todo-db/default-db :todos local-store-todos)
                  }]
      (println :initialise-db :leave result )
      result)))   ; all hail the new state to be put in app-db

; Need a way to document event names and args
;    #todo (defevent set-showing [state])

; usage:  (flame/dispatch-event [:set-showing :active])
; This event is dispatched when the user clicks on one of the 3 filter buttons at the bottom of the display.
  ; #todo #awt merge => global state (old cofx)
(flame/event-handler-for! :set-showing

  ; only one interceptor
  ; #awt [check-spec-intc]  ; after event handler runs, check app-db for correctness. Does it still match Spec?

  ; handler
  (fn [ctx [_ new-filter-kw]]     ; new-filter-kw is one of :all, :active or :done
    (assoc-in ctx [:db :showing] new-filter-kw)))


; #todo event handlers take only params-map (fn [params :- tsk/Map] ...)
(flame/event-handler-for! :add-todo
  ; Use the standard interceptors, defined above, which we use for all todos-modifying
  ; event handlers. Looks after writing todos to LocalStore, etc.
  std-interceptors

  ; The event handler function.
  ; The "path" interceptor in `std-interceptors` means 1st parameter is the
  ; value at `:todos` path within `db`, rather than the full `db`.
  ; And, further, it means the event handler returns just the value to be
  ; put into the `[:todos]` path, and not the entire `db`.
  ; So, againt, a path interceptor acts like clojure's `update-in`
  (fn [ctx [_ text]] ; => {:global-state xxx   :event {:event-name xxx  :arg1 yyy  :arg2 zzz ...}}
    (let [todos (get-in ctx [:db :todos])
          id    (allocate-next-id todos)
      result (assoc-in ctx [:db :todos id] {:id id :title text :done false})]
      (println :add-todo :leave result )
      result)))


(flame/event-handler-for! :toggle-done
  std-interceptors
  (fn [ctx [_ id]]
    (let [result (update-in ctx [:db :todos id :done] not)]
      (println :toggle-done :leave result )
      result)))

(flame/event-handler-for! :save
  std-interceptors
  (fn [ctx [_ id title]]
    (let [result (assoc-in ctx [:db :todos id :title] title)]
      (println :save :leave result )
      result)))

(flame/event-handler-for! :delete-todo
  std-interceptors
  (fn [ctx [_ id]]
    (let [result (flame/dissoc-in ctx [:db :todos id])]
      (println :delete-todo :leave result )
      result)))

(flame/event-handler-for! :clear-completed
  std-interceptors
  (fn [ctx _]
    (let [todos     (get-in ctx [:db :todos])
          done-ids  (->> (vals todos) ; find id's for todos where (:done -> true)
                      (filter :done)
                      (map :id))
          todos-new (reduce dissoc todos done-ids) ; delete todos which are done
          result    (assoc-in ctx [:db :todos] todos-new)]
      (println :clear-completed :leave result)
      result)))

(flame/event-handler-for! :complete-all-toggle
  std-interceptors
  (fn [ctx _]
    (let [todos     (get-in ctx [:db :todos])
          new-done  (not-every? :done (vals todos)) ; work out: toggle true or false?
          todos-new (reduce #(assoc-in %1 [%2 :done] new-done)
                      todos
                      (keys todos))
          result    (assoc-in ctx [:db :todos] todos-new)]
      (println :complete-all-toggle :leave result)
      result)))
