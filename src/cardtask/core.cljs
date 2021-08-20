(ns cardtask.core
  (:require
   [cljs.spec.alpha :as s]
   [goog.events.KeyCodes :as keycodes]
   [goog.events :as gev]
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
(defonce CARDPUSHES {:left 1 :right 1 :middle 3})

;; TODO: counterbalence. switch element 1 and 2
(defonce SCHEME
 {:p8020   {:left 80  :middle 100 :right 20  :rep 3}
  :p2080   {:left 20  :middle 100 :right 20  :rep 3}
  :p100100 {:left 100 :middle 100 :right 100 :rep 3}})

(defonce CARDIMGS (zipmap SIDES (take 3 (shuffle ["✿", "❖", "✢", "⚶", "⚙", "✾"]))))

;; how to handle each event
(defn cards-disp
 "display cards. using states current card"
 [{:keys [cards-cur time-cur time-flip]}]
  (sab/html [:div.cards
   [:h1 "CARDS"]
   [:h2 "new3"]
]))

(defn feedback-disp [state]
  (sab/html [:h1 "FEEDBACK"]))


;; settings for events
(def EVENTDISPATCH {:card {:dur 1500 :func #'cards-disp :next :feedback}
                    :feedback {:dur 1500 :func #'feedback-disp :next :card}})

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
                 (sort-side (list
                             {:side s1 :prob (s1 block) :npush (s1 CARDPUSHES)}
                             {:side s2 :prob (s2 block) :npush (s2 CARDPUSHES)}))))]
    (shuffle (apply concat (repeat (:rep block) trials)))))

(defn cardseq "" [p3_and_rep]
  ; [{:left 80 :middle 100 :right 20 :rep 3}]
  (apply concat (for [b p3_and_rep] (mkblock b))))


(def event-states [:instruction :card :feeback]) ; unused

;; from flappy bird demo
(def starting-state {:running? false
                     :event-name :card ; :instruction :card :feedback
                     :time-start 0
                     :time-cur 0
                     :time-delta 0
                     :time-flip 0

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
      (assoc :time-start time :time-cur time :time-flip time
             :cards-list (cardseq (vals SCHEME))
             :running? true)))

(defn task-next-trial [state]
  "update trial. get new card. NB. nth is 0 based. trial is not(?)"
  (let [trial (:trial state) next-trial (inc trial)]
    (assoc state
           :trial next-trial
           :cards-cur (nth (:cards-list state) trial))))

(defn event-next?
  "based on current event and time-delta, do we need to update?
   returns updated state"
  [state time]
  (let [cur (:time-cur state)
        last (:time-flip state)
        dispatch (-> state :event-name EVENTDISPATCH)
        dur (:dur dispatch)
        next (:next dispatch)]
    (if (> (- cur last) dur)
      (assoc
       (if (= next :card) (task-next-trial state) state)
       :event-name next
       :time-flip time)
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

; was defonce but happy to reset when resourced
(def STATE (atom starting-state))


(defn run-loop [time]
  "recursive loop to keep the task running.
   only changes time, which is picked up by watcher that runs render"
  (let [new-state (swap! STATE (partial time-update time))]
  (when (:running? new-state)
    (go (<! (timeout 30))
            (.requestAnimationFrame js/window run-loop)))))

(defn task-start
  "kick of run-loop. will stop if not :running?"
  []
  (.requestAnimationFrame
   js/window
   (fn [time]
     (if (= (-> @STATE :cards-cur count) 0)
       (reset! STATE (state-fresh @STATE time)))
     (run-loop time))))

(defn task-toggle "start and stop the timer. does not clear time" []
  (let [toggle (not (:running? @STATE))]
  (reset! STATE (assoc @STATE :running? toggle))
  (if toggle (task-start))))

(defn task-stop "turn off the task. resets STATE/clears time" []
  (reset! STATE
         (-> @STATE
             (state-fresh 0)
             (assoc :running? false))))


(defn keypress [e state]
  (println e)
  (.preventDefault e))

(defn task-display
  "html to render for display. updates for any change in display
   "
  [{:keys [cards-cur event-name score] :as state}]

  (let [f (:func (event-name EVENTDISPATCH))]
   (sab/html
    [:div.board
     {:onKeyPress (fn [e] (keypress e @STATE))}
     [:h1.score (str "SCORE: " score)]
     [:h3.run "running? " (str (:running? state))]
     (if f (f state))
     ; lookup what todo for this state
     [:p.time {:style {:size "smaller"}} "time: " (:time-cur state)]
])))


(let [node (.getElementById js/document "task")]
  (defn showme-this
  "show a sab/html element where we'd normally run task-display"
  [reactdom]
    (.render js/ReactDOM reactdom node)))

(defn renderer
  "used by watcher. triggered by animation step's change to time"
  [full-state]
  (showme-this (task-display full-state)))

(defn world
  "function for anything used to wrap state.
   currelty doesn't do anything. could score. phone home. etc"
  [state]
  (-> state))

(add-watch STATE :renderer (fn [_ _ _ n] (renderer (world n))))

(reset! STATE @STATE) ; why?
(task-start)
