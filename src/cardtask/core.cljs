(ns cardtask.core
  (:require
   [cljsjs.react]
   [cljsjs.react.dom]
   [sablono.core :as sab :include-macros true]
   [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]])
  (:require-macros  [cljs.core.async.macros :refer [go-loop go]]))
(enable-console-print!)
(set! *warn-on-infer* false) ; maybe want to be true

;; 2 way choice from 3 "cards"
;; Left high prob win, Right low prob win, Middle always win (but require multiple pushes)
;; 3 stages: 80/20/100, 20/80/100, 100/100/100 (habit test)

(defonce CARDDUR 1500)
(defonce SIDES [:left :middle :right])
(defonce KEYS {:left ["f"] ;; 70
               :middle ["g", "h"] ;; 71 72
               :right ["j"] ;; 74
               :next [32] ;; space
               })

;; TODO: counterbalence. switch element 1 and 2
(defonce SCHEME
 {:p8020   {:left 80  :middle 100 :right 20  :rep 3}
  :p2080   {:left 20  :middle 100 :right 20  :rep 3}
  :p100100 {:left 100 :middle 100 :right 100 :rep 3}})

(defonce CARDIMGS (zipmap SIDES (take 3 (shuffle ["✿", "❖", "✢", "⚶", "⚙", "✾"]))))

(defn sort-side
  "put in order L to R order. overkill. keys are sorted themselve"
  [map-with-side]
  (let [o {:left 1 :middle 2 :right 3}]
    (sort-by #((:side %) o) map-with-side)))

(defn mkblock
  "all combinations. kludey sort+distinct to remove repeated permutations
   wants map with probs and reps like {:left 20 :right 80 :middle 100 :rep 3}"
  [block]
  (let [trials (distinct (for [s1 SIDES s2 SIDES
                     :when (not= s1 s2)]
                 (sort-side (list {:side s1 :prob (s1 block)}
                       {:side s2 :prob (s2 block)}))))]
    (shuffle (apply concat (repeat (:rep block) trials)))))

(defn cardseq "" [p3_and_rep]
  ; [{:left 80 :middle 100 :right 20 :rep 3}]
  (for [b p3_and_rep] (mkblock b)))


(def event-states [:instruction :card :feeback])
;; from flappy bird demo
(def starting-state {:running? false
                     :event-name :card ; :instruction :card :feedback
                     :time-start 0
                     :time-cur 0
                     :time-delta 0
                     :last-flip 0

                     :cards-cur []
                     :cards-list [] ; [ [{card1}, {card2}], [], ... ]

                     :trial 0
                     :responses [] ; {:side :rt :score :prob :keys [{:time :kp $k}]}
                     :score 0})

(defn state-fresh
  "starting fresh. use starting-state but
  * update all the time vars
  * make a new sequence of cards
  * set running? to true. TODO: maybe this happens elsewhere
  NB. @STATE needs to be passed in so its updates are global?!"
 [_ time]
  (-> starting-state
      (assoc :time-start time :time-cur time :last-flip time
             :card-list (cardseq SCHEME)
             :running? true)))

(defn event-next?
  "based on current event and time-dela, do we need to update?
   returns updated state"
  [state time]
  (let [cur (:time-cur state)
        last (:time-flip state)
        card-list (:card-list state)]
    (if (> (- cur last) CARDDUR)
      state ;(assoc state :card-list (pop card-list))
      state)))

(defn time-update
  "what to do at each timestep of AnimationFrame.
   first call will be on unintialized time values"
  [time state]
 (-> state
     (assoc :time-cur time :time-delta (- time (:start-time state)))
     (event-next? time)
     ;additional updates to state
))

(defonce STATE (atom starting-state))

(defn run-loop [time]
  "recursive loop to keep the task running"
  (let [new-state (swap! STATE (partial time-update time))]
  (when (:running? new-state)
    (go (<! (timeout 30))
            (.requestAnimationFrame js/window run-loop)))))

(defn task-start
  "will stop if not :running?"
  []
  (println "starting task!")
  (println STATE)
  (.requestAnimationFrame
   js/window
   (fn [time]
     (reset! STATE (state-fresh @STATE time))
     (run-loop time))))

(defn keypress [e state]
  (println e)
  (.preventDefault e))


(defn task-display
  "html to render for display. updates for any change in display
   TODO. different display for card, feedback, and instructions"
  [{:keys [cards event score] :as state}]
  (sab/html
   [:div.board
    {:onKeyPress (fn [e] (keypress e STATE))}
    [:h1.score (str "SCORE: " score)]
    [:h3.run "running? " (str (:running? state))]
    ;[:h2.time "time: " (:time-cur state)]
]))


(let [node (.getElementById js/document "task")]
  (defn renderer
    "what id to fill w/#task-display"
    [full-state]
    (.render js/ReactDOM (task-display full-state) node)))

(defn world
  "function for anything used to wrap state.
   currelty doesn't do anything. could score. phone home. etc"
  [state]
  (-> state))

(add-watch STATE :renderer (fn [_ _ _ n] (renderer (world n))))

; force a change that starts rendering
(reset! STATE @STATE)
(task-start)
