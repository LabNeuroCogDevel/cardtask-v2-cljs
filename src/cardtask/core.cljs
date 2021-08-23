(ns cardtask.core
  (:require
   [cljs.spec.alpha :as s]
   [goog.string :refer [unescapeEntities]]
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

(defn prob-gets-points?
  [prob]
  "prob is between 0 and 100. rand-int can return 0"
  ;   0    >= 1-100
  ;  100   >= 1-100
  (>= prob (inc (rand-int 99))))


(def event-states [:instruction :card :feeback]) ; unused
(defonce SIDES [:left :middle :right])
(defonce KEYS {:left [70 37] ;["f"] ; left arrow
               :middle [71 72 40] ; ["g", "h"]  ; down arrow
               :right [74 39] ;["j"] ; right arrow
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
(defn cards-empty [side] (sab/html [:div.card {:class (name side)} (unescapeEntities "&nbsp;")]))
(defn cards-disp-one
 [side {:keys [:img :push-seen] :as card}]
 "show only this card."
 (sab/html 
  [:div.card {:class [(name side)]} img]))
(defn cards-disp-side
  [side cards-cur]
  "show card or empty div"
  (sab/html [:div.card-container
             (if-let [card-info (side cards-cur)]
               (cards-disp-one side card-info)
               (cards-empty side))]))


(defn cards-disp
 "display cards. using states current card"
  [{:keys [cards-cur time-cur time-flip]}]
  (sab/html [:div.allcards
             ;[:h3 "yo"]])))
             (for [s SIDES] (cards-disp-side s cards-cur))]))

(defn feedback-disp
  [{:keys [:get-points :score]} as state]
  "feedback: win or not?"
  (sab/html
   [:div.feedbak
    (if get-points (sab/html [:h1 "Win!"])
        (sab/html [:h1 "no points!"]))
    [:h3.score score]]))


;; settings for events
(def EVENTDISPATCH {:card     {:dur 9000 :func #'cards-disp :next :feedback}
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
  (let [empty {:left nil :middle nil :right nil :picked nil}
        cur-cards (nth CARDSLIST trial)
        sidemap (map (fn [c] {(:side c) (dissoc c :side)}) cur-cards) ] 
    (merge empty (reduce merge sidemap))))


;; audo
(defonce audio-context (snd/audio-context))
(def SOUNDS {:reward [{:url "audio/cash.mp3"    :dur .5}
                      ;{:url "audio/cheer.mp3"   :dur .5}
                      ;{:url "audio/trumpet.mp3" :dur .5}
                     ]
             :empty  [{:url "audio/buzzer.mp3"  :dur .5}
                      ;{:url "audio/cry.mp3"     :dur .5}
                      ;{:url "audio/alarm.mp3"   :dur .5}
                      ]})
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

(defn score-with-sound
  [prob]
  "score and play sound. returns true (win) or false (no win)"
  (let [get-points (prob-gets-points? prob)
        rew-key  (if get-points :reward :empty)
        snd (first (shuffle (rew-key SOUNDS)))]
  (play-audio snd)
  get-points))


;; from flappy bird demo
(def starting-response {:rt nil :side nil :get-points 0 :keys []})
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
; was defonce but happy to reset when resourced
(def STATE (atom starting-state))
(defn task-next-trial
  "update trial. get new card. NB. nth is 0 based. trial is not(?)"
  [state]
  (let [trial (:trial state) next-trial (inc trial)]
    (-> state
        (assoc 
           :trial next-trial
           :cards-cur (cards-at-trial trial))
        ;; NB. trial is prev. but b/c indexing is zero based. 
        ;;     responses@trial refers to current.
        (update-in [:responses trial :choices] #(:cur-cards %)))))

(defn state-fresh
  "starting fresh. use starting-state but
  * update all the time vars
  * make a new sequence of cards
  * set running? to true. TODO: maybe this happens elsewhere
  NB. @STATE needs to be passed in so its updates are global?!"
 [_ time]
  (-> starting-state
      (assoc :time-start time :time-cur time :time-flip time
             :running? true
             :responses (vec (repeat (count CARDSLIST) starting-response)))
      (task-next-trial)))



(defn event-next?
  "based on current event and time-delta, do we need to update?
   returns updated state: trial, event name, and flip time
   task-next-trial also updates cards and trial"
  [state time]
  (let [cur (:time-cur state)
        last (:time-flip state)
        event-name (:event-name state)
        responsed-side (get-in state [:responses (dec (:trial state)) :side])
        dispatch (event-name EVENTDISPATCH)
        dur (:dur dispatch)
        next (:next dispatch)
        responded? (and (= :card event-name)
                        (not (nil? responsed-side)))]
    (if (or (> (- cur last) dur) responded?)
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

(defn key-resp
  "generate response map. to be appended to (-> state :responses n :keys)"
  [fliptime side pushed]
  (let [time (.getTime (js/Date.))
        rt (- time fliptime)]
  {:side side :key pushed :rt rt :time time}))

(defn response-score
  "when a side has been picked, update w/side rt prob and points
  scoring (w/prob) happens here!"
  [response picked prob]
  ; skip update if picked (keypush) is nill
  ; or if we already have a response
  (if (or (nil? picked) (not (nil? (:side response))))
    response
    (assoc response
           :side picked
           :rt  (-> response :keys last :rt)
           :prob  prob
           :get-points  (score-with-sound prob))))

(defn cards-cur-picked
  "check counts and pick a card
  update :picked in {:left {..}, ..., :picked nil}"
  [cards-cur side]
  (let [already-picked (:picked cards-cur)
        seen (-> cards-cur side :push-seen)
        need (-> cards-cur side :push-need)]
  ;(println "should pick?" side seen need)
    (if (and (>= seen need) (nil? already-picked))
      (assoc cards-cur :picked  side)
      cards-cur)))

(defn update-score
  [state]
  (let [win? (-> state :responses :get-points)]
    (println "did we win? " win?)
    (if win?
      (update-in state [:score] inc)
      state)))

(defn state-add-response
  "modify state with a given keypress
  * cards-cur gets updated push inside side, might also set picked
  * update response @ trial index
  expects to be used to (reset!) somewhere else"
  [state pushed side]
  (let [trialidx0 (dec (:trial state))
        card-prob (-> state :cards-cur side :prob)]
    ;(println "adding response w/" pushed " " side)
    (-> state
        ; ephemeral push count and ":picked" when n.pushs > need
        ; will be lost when trial changes
        (update-in [:cards-cur side :push-seen] inc)
        (update-in [:cards-cur] #(cards-cur-picked % side))
        ; append to responses using index. will stick around
        ; first add just the keypress
        ; then check to see if we should score it
        (update-in [:responses trialidx0 :keys]
                   conj
                   (key-resp (:time-flip-abs state) side pushed))
        (update-in [:responses trialidx0] #(response-score % side card-prob))
        (update-score))))

(defn keypress! [state-atom e]
  (let [pushed (.. e -keyCode)
        cards-cur (:cards-cur @state-atom)
        event-name (:event-name @state-atom)
        side (cards-pushed-side pushed cards-cur)]
  (when (and (not (nil? side)) (not(= event-name :feedback))) ;no keys on feedback
     (println "side keypush!" pushed side)
     (reset! state-atom (state-add-response @state-atom pushed side))
     ;(println "staet" @state-atom)
     (.preventDefault e))))
         


(defn show-debug "debug info. displayed on top of task"
[state]
(sab/html
 [:div.debug
  [:ul.bar {:class (if (:running? state) "running" "stoppped")}
   [:li {:on-click task-toggle} "tog"]
   [:li {:on-click task-stop}   "stop"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/cash.mp3" :dur .5}))}  "cash"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/buzzer.mp3" :dur .5}))} "buz"]
   [:li {:on-click (fn [_] (renderer (world state)))} "update-debug"]
   [:li {:on-click
         (fn [_] (reset! STATE (update-in @STATE [:no-debug-bar] not)))}
        "debug-bar"]
  ]
  ; double negative so default is true without explicity settings
  (when (not (:no-debug-bar state))
    (sab/html [:div.debug-extra-info
     [:p.time (.toFixed (/ (- (:time-cur state) (:time-flip state)) 1000) 1)]
     [:p.score "trial: " (:trial state)]
     [:p.score "score: " (:score state)]
     [:br]
     [:p.score "cards: " (str (:cards-cur state))]
     [:br]
     [:p.resp "nresp: " (-> state :responses count)]
     [:br]
     [:p.resp "resp: " (str (get-in state [:responses (dec (:trial state))]))]]))]))

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
   currelty doesn't do anything. could phone home, etc
   changes to state here do not get saved/updated with (reset!)"
  [state]
  (-> state))

(add-watch STATE :renderer (fn [_ _ _ n] (renderer (world n))))

; TODO: this should go into state-fresh?
(reset! STATE  @STATE) ; why ?
(task-start)


(gev/listen (KeyHandler. js/document)
            (-> KeyHandler .-EventType .-KEY) ; "key", not same across browsers?
            (partial keypress! STATE))
