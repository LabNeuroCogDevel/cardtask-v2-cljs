(ns cardtask.core
  (:require
   [cljs.spec.alpha :as s]
   [goog.events.KeyCodes :as keycodes]
   [goog.events :as gev]
   [cljsjs.react]
   [cljsjs.react.dom]
   [cljs-bach.synthesis :as snd]
   [sablono.core :as sab :include-macros true]
   [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]])
  (:require-macros  [cljs.core.async.macros :refer [go-loop go]])
  (:import [goog.events EventType KeyHandler]))
(enable-console-print!)
(set! *warn-on-infer* false) ; maybe want to be true

;; 2 way choice from 3 "cards"
;; Left high prob win, Right low prob win, Middle always win (but require multiple pushes)
;; 3 stages: 80/20/100, 20/80/100, 100/100/100 (habit test)


(def event-states [:instruction :card :feeback]) ; unused
(defonce CARDDUR 1500)
(defonce SIDES [:left :middle :right])
(defonce KEYS {:left [70 37] ;["f"] ; left arrow
               :middle [71 72 40] ; ["g", "h"]  ; down arrow
               :right [74 29] ;["j"] ; right arrow
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

(defn mkcard
  "various lookups to create a card map. TODO: use record?
   side is :left :middle :right. prob is 0-100"
  [side prob]
  {:side side :prob prob
   :push-seen 0
   :push-need (side CARDPUSHES)
   :keys (side KEYS)
   :img (side CARDIMGS)})

(defn mkblock
  "all combinations. kludey sort+distinct to remove repeated permutations
   wants map with probs and reps like {:left 20 :right 80 :middle 100 :rep 3}"
  [block]
  (let [trials (distinct (for [s1 SIDES s2 SIDES
                     :when (not= s1 s2)]
                 (sort-side (list
                             (mkcard s1 (s1 block))
                             (mkcard s2 (s2 block))))))]
    (shuffle (apply concat (repeat (:rep block) trials)))))

(defn cardseq "" [p3_and_rep]
  ; [{:left 80 :middle 100 :right 20 :rep 3}]
  ; TODO: add stage to each group
  (apply concat (for [b p3_and_rep] (mkblock b))))

(def CARDSLIST (cardseq (vals SCHEME)))


(defn cards-at-trial
  "rearrange from vect to map. depends on CARDSLIST
  ({card1} {card2}) to {:left {card1} :middle nil :right {card2}"
  [trial]
  (let [empty {:left nil :middle nil :right nil}
        cur-cards (nth CARDSLIST trial)
        sidemap (map (fn [c] {(:side c) (dissoc c :side)}) cur-cards) ] 
    (merge empty (reduce merge sidemap))))


;; audo
(defonce audio-context (snd/audio-context))
(def SOUNDS {:reward [{:url "audio/cash.mp3"    :dur .5}
                      {:url "audio/cheer.mp3"   :dur .5}
                      {:url "audio/trumpet.mp3" :dur .5}]
             :empty  [{:url "audio/buzzer.mp3"  :dur .5}
                      {:url "audio/cry.mp3"     :dur .5}
                      {:url "audio/alarm.mp3"   :dur .5}]})
; preload
(for [url (map #(:url %) (-> SOUNDS :reward))] snd/sample)
; NB. could use e.g. (snd/square 440) for beeeeep
(defn play-audio
  [{:keys [url dur]}]
  (snd/run-with
   (snd/connect-> (snd/sample url) snd/destination)
   audio-context
   (snd/current-time audio-context)
   dur))

;; from flappy bird demo
(def starting-state {:running? false
                     :event-name :card ; :instruction :card :feedback
                     :time-start 0
                     :time-cur 0
                     :time-delta 0
                     :time-flip 0      ; animation time
                     :time-flip-abs 0  ; epoch time

                     :cards-cur []
                     ; this probably doesn't have to travel around with the state
                     ; once shuffled, it'll never change

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
             :running? true)))

(defn task-next-trial [state]
  "update trial. get new card. NB. nth is 0 based. trial is not(?)"
  (let [trial (:trial state) next-trial (inc trial)]
    (assoc state
           :trial next-trial
           :cards-cur (cards-at-trial trial))))

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
       :time-flip time
       :time-flip-abs (.getTime (js/Date.)))
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


(defn cards-pushed-side
  "is a pushed key for any card in current state?"
  [pushed cards-cur]
  (first (mapcat
   (fn [side] (keep #(if (= pushed %) side nil)
                   (-> cards-cur side :keys)))
   SIDES)))

(defn mkresp []
  ;{:side :score :prob :keys [{:side :rt :key :time}]}
)
(defn keypress [state e]
  (let [pushed (.. e -keyCode)
        cards-cur (:cards-cur state)
        side (cards-pushed-side pushed cards-cur)]
  (when (not (nil? side))
     (-> state
         (update-in [:cards-cur side :pushes] inc)
         (update-in :push-finished
            #(if (>= (-> state :cards-cur :side :push-seen)
                     (-> cards-cur :push-need))
                 true
                 %))))
         
  
  ;(.preventDefault e)
))

(defn show-debug "debug info. displayed on top of task"
[state]
(sab/html
 [:div.debug
  [:ul.bar {:class (if (:running? state) "running" "stoppped")}
   [:li {:on-click task-toggle} "tog"]
   [:li {:on-click task-stop}   "stop"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/cash.mp3" :dur .5}))}  "cash"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/buzzer.mp3" :dur .5}))} "buz"]
  ]
  [:p.time (.toFixed (/ (- (:time-cur state) (:time-flip state)) 1000) 1)]
  [:p.score "trial: " (:trial state)]
  [:p.score "score: " (:score state)]
  [:p.score "cards: " (str (:cards-cur state))]]))

(defn task-display
  "html to render for display. updates for any change in display
   "
  [{:keys [cards-cur event-name score] :as state}]
  (let [f (:func (event-name EVENTDISPATCH))]
   (sab/html
    [:div.board
     (show-debug state) ; todo, hide behind (when DEBUG)
     (if f (f state))])))


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

;(let [kh (KeyHandler. js/window)]
;  (gev/listen kh (-> KeyHandler .-EventType .-Key) (partial keypress @STATE)))


(gev/listen (KeyHandler. js/document)
            (-> KeyHandler .-EventType .-KEY) ; "key", not same across browsers?
            (partial keypress @STATE))
