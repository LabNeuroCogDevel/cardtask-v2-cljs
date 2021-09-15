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
   [debux.cs.core :as d :refer-macros [clog clogn dbg dbgn dbg-last break]]
   [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]]
   [ajax.core :refer [POST GET]])
  (:require-macros  [cljs.core.async.macros :refer [go-loop go]])
  (:import [goog.events EventType]))
(enable-console-print!)
(set! *warn-on-infer* false) ; maybe want to be true
;; 
;; AJAX/HTTP
(def HTTP-DEBUG false)
(defn get-url [rest] (str (if HTTP-DEBUG "http://0.0.0.0:3001/will/test/nover/1/1/" "") rest))

(defn send-info
  []
  "TODO: send system info (eg browser, system, resolution, window size"
  (POST (get-url "info") {:params {:info "TODO!"} :response-format :json}))

(defn send-resp [state]
  (println "sending state!")
  (POST (get-url "response")
        {;:params (.stringify js/JSON (clj->js @STATE))
         :params @STATE
         :format :json
         }))
(defn send-finished [] (POST (get-url "finished")))

;;
;; CARDS

;; 2 way choice from 3 "cards"
;; Left high prob win, Right low prob win, Middle always win (but require multiple pushes)
;; 3 stages: 80/20/100, 20/80/100, 100/100/100 (habit test)

(defn prob-gets-points?
  [prob]
  "prob is between 0 and 100. rand-int can return 0"
  ;   0    >= 1-100
  ;  100   >= 1-100
  (>= prob (inc (rand-int 99))))


(def event-states [:instruction :start :card :feeback :done]) ; unused
(defonce SIDES [:left :middle :right])
(defonce KEYS {:left [70 37] ;["f"] ; left arrow
               :middle [71 72 40] ; ["g", "h"]  ; down arrow
               :right [74 39] ;["j"] ; right arrow
               :next [32] ;; space
               })

;; TODO: principle counterbalence?
(def card-probs (shuffle
                   [[80   20 100]
                    [20   80 100]
                    [100 100 100]]))

(def MAXPUSH "most push that'd be required" 5)
(defn npush-by-prob
  [probs]
  "require 5 pushes if always 100% prob of correct. 3 otherwise"
  (if (every? #(= 100 %) probs) 3 MAXPUSH))
(defn card-side-zip [vals] (zipmap SIDES (take 3 (shuffle vals))))
(defn mk-card-info []
  (let [;colors (card-side-zip ["lightblue", "red", "yellow", "lightgreen", "orange"])
        colors (card-side-zip ["blue" "green" "purple" "red" "yellow"])
        ;syms   (card-side-zip ["✿", "❖", "✢", "⚶", "⚙", "✾"])
        ;line-imgs   (card-side-zip ["cerberus" "cockatrice" "dragon" "fish" "griffin" "serpant" "snake" "phoenix" "unicorn"])
        imgs (card-side-zip ["blue" "brightyellow" "darkgreen" "forestgreen" "purple" "red" "teal" "yellow1"])
        probs  (card-side-zip [[80   20 100] [20   80 100] [100 100 100]])]
     {:color  colors
      :img    imgs
      :probs  probs
      :pushes (zipmap SIDES (map npush-by-prob (vals probs)))}))

(defn mk-card-scheme
  [cardinfo reps]
  "scheme structure for generating a list of trials.
  used by mk-card-list.
  cardinfo probably from mk-card-info"
  {:p8020   {:left   (get-in cardinfo [:probs :left   0])
             :middle (get-in cardinfo [:probs :middle 0])
             :right  (get-in cardinfo [:probs :right  0])
             :rep reps}
   :p2080   {:left   (get-in cardinfo [:probs :left   1])
             :middle (get-in cardinfo [:probs :middle 1])
             :right  (get-in cardinfo [:probs :right  1])
             :rep reps}
   :p100100 {:left   (get-in cardinfo [:probs :left   2])
             :middle (get-in cardinfo [:probs :middle 2])
             :right  (get-in cardinfo [:probs :right  2])
             :rep reps}})

;; how to handle each event
(defn img-url [img] "img name to url"
 (str "img/DawTask/alien_" img ".svg"))

(defn text-or-img
  [img & {:keys [width height] :or {width 90 height nil}}]
  (if (= (count img) 1)
    img
    (sab/html [:img {:src (img-url img)
                     :width (str width "px")
                     :height (if height (str height "px") "auto")}])))
(defn cards-empty [side]
  (sab/html [:div.card {:class (name side)}
             ;(unescapeEntities "&nbsp;")
             [:img {:src (img-url "yellow1") :width "90x" :style {:opacity 0}}]
             [:div.dots [:span.nopush ""]]]))
(defn color-to-planet [color] (str "url('img/DawTask/card_" color "planet.jpg"))

(defn cards-resp-pos-dots [push-seen push-need]
(sab/html [:div.dots
    (map #(sab/html [:span {:class (if (> push-seen %) "fill" "empty")}]) (range push-need))
    ]))

(defn cards-resp-pos-bar
  "show a progress bar"
  [push-seen push-need]
  (let [percent (-> push-seen (/ push-need) (* 100))
        parent-width-px (-> push-need (/ MAXPUSH) (* 60))
        color (cond (> percent 80) "green"
                    (> percent 50) "blue"
                    (> percent 20) "orange"
                    :else "white")]
    (sab/html [:div.respbar {:style {:width (str parent-width-px "px")}}
               [:div {:style {:background-color color
                                   :width (str percent "%")}}]])))

(defn cards-disp-one
 "show only this card."
 [side {:keys [:img :push-seen :push-need] :as card}]
 (let [scale (min 2 (+ 1 (/ push-seen push-need)))]
 (sab/html
  [:div.card {:class [(name side)]
              :style {:background-image (color-to-planet (get-in CARDINFO [:color side]))
                      ;:transform (str "scale("scale","scale")")
                      }}
   (text-or-img img)
   (cards-resp-pos-bar push-seen push-need)
   ])))
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
  (sab/html [:div.container [:div.allcards
             ;[:h3 "yo"]])))
             (for [s SIDES] (cards-disp-side s cards-cur))]]))

(defn animate-star
  [step]
  "animate sprite. step is 0 to 1"
  (let [frames 11
        frame-size 99
        total-size (* frames frame-size)
        x% (* 100 step)
        sprite (* -1 frame-size (int (mod (* step total-size) frames)))]
    (sab/html [:div.sprite
               {:width "97px"
                :style {:position "relative"
                        :left (str x% "%")
                                        ;:top (str x% "%")
                        :width "99px"
                        :height "99px"
                        :margin-left "-50px"
                        :background-image "url(img/star_spin_sprite.png)"
                        :background-position sprite
                        :background-repeat "no-repeat"
                        }}])))
(defn animate-wrong [step]
  (let [size (* 72 (if step (- 1 step) 1))]
    (sab/html [:div {:style {:position "absolute" :width "100%" :text-align "center"}}
               [:span
               {:style {:font-size (str size "px")}}
               "🥺"]])))

(defn image-small [side win?]
  (let [img (get-in CARDINFO [:img side])
        color (-> CARDINFO :color side)]
    (sab/html [:span
                 {:style (if win?
                           {:background  "url(img/trophy-small.png)"
                            :display "inline-block"
                            :width 67
                            :height 63}
                           {})}
               [:span
                {:style {
                         :background-color color
                         :border-radius "50%"
                         :dispaly "inline-block"
                         :border "solid black 1px"}} (text-or-img img :width 30 :height 30)]])))

(defn feedback-trophy-sym [side]
  (sab/html [:h1 [:span (image-small side true)]]))

(defn feedback-trophy [side]
  (let [img (get-in CARDINFO [:img side])
        color (-> CARDINFO :color side)]
  (sab/html [:div
             [:img {:src "img/trophy-small.png"} ]
             [:img {:src (img-url img) :width "90px" :style {:padding "2px" :margin "0 20px 5px 20px" :background color}}]
             ; masking is slow! color isn't useful
             ;[:span {:style {:background color :margin "0 20px 0 20px"
             ;                :mask-size "contain" :mask (str "url('img/creatures/" img ".svg')") :mask-repeat "no-repeat"
             ;                :height "90px"
             ;                :width "90px"
             ;                :mask-position "center"
             ;                :display "inline-block" }}]
             [:img {:src "img/trophy-small.png"} ]])))

(defn feedback-disp
  [{:keys [:trial-last-win :trial :score :time-cur :time-flip] :as state}]
  "feedback: win or not?
   using sprite that is 11 frames of 99x99"
  (let [
        side (-> state :cards-cur :picked)
        dur (-> EVENTDISPATCH :feedback :dur)
        delta (- time-cur time-flip)
        time (mod delta dur)
        step (/ time dur) ; 0 started to 1 finished
        ]
  (sab/html
   [:div.feedbak
    (if (= trial-last-win trial)
      (sab/html [:div.container
                 (animate-star (- 1 step))
                 [:div.win
                  (feedback-trophy side)
                  [:span {:style {
                               :display "inline-block"
                               :background "url(img/ribbon.png)"
                               :width "400px"
                               :height "100px"
                               :color "white"
                               :font-weight "bold"
                               :padding-top "24px"
                               :font-size "26px"}} "You won a point!"]]
                 (animate-star step)
                 ])
      (sab/html [:div.container
                 (if (nil? side)
                  [:h1 "Too slow! No points!"]
                  [:h1 (image-small side false) [:br]
                   [:span {:style {:color "red"}} "No points!"]])
                 [:br]
                 (animate-wrong step)]))
    [:p.score "total points: " score]])))


(defn finished-disp [{:keys [:score] :as state}]
  ; (reset! STATE (assoc @STATE :running? false))
  ; cannot do this here! causes recursion
  (sab/html [:div.finished
             [:h1.finished "Thanks for playing!"]
             [:br]
             [:h1.finishedPoints "You scored " score " points!"]]))

; maybe could do "Waiting for scanner trigger" here.
; but better as a own thing between instructions and starting?
(defn start-disp [{:keys [:time-since :event-name] :as state}]
  (sab/html [:p (str "Starting in ... "
                     (-> event-name EVENTDISPATCH :dur
                         (- time-since)
                         (/ 1000)
                         (.toFixed 1)) " seconds")]))

;; settings for events
(def EVENTDISPATCH {:start    {:dur 2000 :func #'start-disp :next :card}
                    :card     {:dur 2000 :func #'cards-disp :next :feedback}
                    :feedback {:dur 1500 :func #'feedback-disp :next :card}
                    :last-msg {:dur 1 :func #'finished-disp :next :done}
                    ; repeat last-message. maybe we don't need it
                    :done     {:dur 0 :func #'finished-disp :next :done}})

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
   :push-need (get-in CARDINFO [:pushes side])
   :keys (side KEYS)
   :img (get-in CARDINFO [:img side])})

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
(defn mk-card-list
  [card-info reps]
  (cardseq (vals (mk-card-scheme card-info reps))))


(defn cards-reset []
  "rerun random selection of card sym/img and color"
  (def CARDINFO (mk-card-info))
  (def CARDSLIST (mk-card-list CARDINFO 3)))

(cards-reset)

(defn cards-at-trial
  "rearrange from vect to map. depends on CARDSLIST
  ({card1} {card2}) to {:left {card1} :middle nil :right {card2}"
  [trial cards-list]
  (let [empty {:left nil :middle nil :right nil :picked nil}
        cur-cards (if (state-out-of-trials? trial cards-list) nil (nth cards-list trial))
        sidemap (map (fn [c] {(:side c) (dissoc c :side)}) cur-cards) ]
    (merge empty (reduce merge sidemap))))

;;; 

;; audio
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
(def starting-response {:rt nil :side nil :get-points false :keys []})
(def starting-state {:running? false
                     :event-name :start ; :instruction :card :feedback
                     :time-start 0
                     :time-cur 0
                     :time-delta 0
                     :time-flip 0      ; animation time
                     :time-flip-abs 0  ; epoch time
                     :time-since 0     ; since last flip

                     :cards-cur []
                     ;; this probably doesn't have to travel around with the state
                     ;; once shuffled, it'll never change

                     :trial 0
                     :trial-last-win -1
                     :responses [] ; {:side :rt :score :prob :keys [{:time :kp $k}]}
                     :score 0

                     :instruction-idx 0

                      ;; debugging
                     :no-debug-bar true
                     :debug-no-advance false
})
; was defonce but happy to reset when resourced
(def STATE (atom starting-state))
(defn task-next-trial
  "update trial. get new card. NB. nth is 0 based. trial is not(?)"
  [{:keys [:trial] :as state}]
  (println "next trial " trial)
  (let [next-trial (inc trial)
        ;; NB. trial is soon to be prev trial
        ;; but b/c indexing is zero based,
        ;; responses@trial refers to future, soon to be current trial.
        cards-cur (cards-at-trial trial CARDSLIST)
        next-state (-> state (assoc :trial next-trial :cards-cur cards-cur))]
    (reset! KEYPRESSTIME (keypress-init-card))
    (println "updating keypress accepted" @KEYPRESSTIME)
    (-> next-state
      ; remove picked (always null at start of trial)
      ;so it doesn't conflict with true source of info ":side"
      (assoc-in [:responses trial :choices] (dissoc cards-cur :picked))
      ; again trial is 0index current or 1index past
      (assoc-in [:responses trial :trial] next-trial))))

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
             :responses (vec (repeat (count CARDSLIST) starting-response)))))




(defn state-out-of-trials? [trial cards-list]
  (>= trial (count cards-list)))


(defn task-wrap-up [state]
  ; update server
  (send-resp state)
  (send-finished)
  ; stop animation
  (assoc state :running? false))

(defn event-next?
  "based on current event and time-delta, do we need to update?
   returns updated state: trial, event name, and flip time
   task-next-trial also updates cards and trial.
   called from time-update in run-loop"
  [{:keys [:time-cur :time-flip :event-name :trial] :as state} time-animation]
  (let [cur time-cur
        last time-flip
        responsed-side (get-in state [:cards-cur :picked])
        dispatch (event-name EVENTDISPATCH)
        dur (:dur dispatch)
        finished? (and (= :feedback event-name) (state-out-of-trials? trial CARDSLIST))
        next (if finished? :last-msg (:next dispatch))
        responded? (and (= :card event-name)
                        (not (nil? responsed-side)))]
    (if (and (or (> (- cur last) dur) responded?)
             (not (:debug-no-advance state)))
      (assoc
       (case next
         :card     (task-next-trial state)
         :feedback (do (send-resp state) state)
         :done     (task-wrap-up state)
                   state) ; :last-msg :start :feedback
       :event-name next
       :time-flip time-animation
       :time-flip-abs (.getTime (js/Date.)))
      state)))


(defn time-update
  "what to do at each timestep of AnimationFrame (run-loop).
   first call will be on unintialized time values"
  [time state]
  (-> state
     (assoc :time-cur time
            :time-delta (- time (:start-time state))
            :time-since (- time (:time-flip state)))
     (event-next? time)))

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
  ;(reset! KEYPRESSTIME (keypress-init-card)) ; done in task-next-trial
  (.requestAnimationFrame
   js/window
   (fn [time]
     (if (= (-> @STATE :cards-cur count) 0)
       (reset! STATE (state-fresh @STATE time)))
     (run-loop time))))

(defn task-toggle "start and stop the timer. does not clear time" []
  (let [toggle (not (:running? @STATE))]
  (reset! STATE (assoc @STATE :running? toggle))
  (when toggle
    (task-start))))

(defn task-stop "turn off the task. resets STATE/clears time" []
  (reset! STATE
         (-> @STATE
             (state-fresh 0)
             (assoc :running? false
                    :no-debug-bar (:no-debug-bar STATE)))))

(defn task-restart []
  (task-stop)
  (def CARDINFO (mk-card-info))
  (def CARDSLIST (mk-card-list CARDINFO 3))
  (task-start))


(defn any-accept-key [pushed]
  (first (mapcat
   (fn [keykey] (if (some #(= pushed %) (keykey KEYS)) [keykey] nil))
   (keys KEYS))))

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
  ; skip update if not enough pushes or already assigned (no undos)
  (if (or (nil? picked) (not (nil? (:side response))))
    response
    (assoc response
           :side picked
           :rt  (-> response :keys last :rt)
           :prob  prob
           :get-points (score-with-sound prob))))

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
  [{:keys [:responses :trial] :as state}]
  "update score and bring get-points into top level as :trial-last-win"
  (let [win? (get-in responses [ (dec trial) :get-points])]
    (if win?
      (-> state
          (update-in [:score] inc)
          (assoc :trial-last-win trial))
      state)))

(defn state-add-response
  "modify state with a given keypress
  * cards-cur gets updated push inside side, might also set picked
  * update response @ trial index
  expects to be used to (reset!) somewhere else"
  [state pushed side]
  (let [trialidx0 (dec (:trial state))
        card-prob (-> state :cards-cur side :prob)
        next-state (-> state
                   ; ephemeral push count and ":picked" when n.pushs > need
                   ; will be lost when trial changes
                       (update-in [:cards-cur side :push-seen] inc)
                       (update-in [:cards-cur] #(cards-cur-picked % side))
                       ; append to responses using index. will stick around
                       ; first add just the keypress
                       ; then check to see if we should score it
                       (update-in [:responses trialidx0 :keys]
                                  conj
                                  (key-resp (:time-flip-abs state) side pushed)))
        picked-side (get-in next-state [:cards-cur :picked])]

    (-> next-state
        (update-in [:responses trialidx0] #(response-score % picked-side card-prob))
        (update-score))))


;;; 
(def INSTRUCTIONS
  [
   (sab/html [:div [:h2 "Welcome to our card game!"]
              [:p "The game is to get as many points as you can by choosing the better card"]
              [:p "Push the spacebar for the next instruction"]])
   (sab/html [:div
              [:p "First lets test your speakers! Do you hear sounds when you push these buttons?"]
              [:button
               {:on-click (fn [_] (play-audio {:url "audio/cash.mp3" :dur .5}))}
               "points"]
              [:button
               {:on-click (fn [_] (play-audio {:url "audio/buzzer.mp3" :dur .5}))}
               "no points"]])
   (sab/html [:div [:h3 "Rules of the game"]
              [:ul [:li "Some cards are better at getting points than others"]
               [:li " Some cards require " [:b "more than one key push"]]
               [:li "You'll have to learn this as you go."]
               [:li [:b "As the game progresses, some cards will change how often they give points"]]]])
   (sab/html [:div [:p "The cards will always be in the same order"]
              [:p "Choose a card using the arrow keys. Push"]
              [:ul
               [:li "Left for the left card"]
               [:li "Down for the centercard"]
               [:li "Right for the card on the right"]]])
   (sab/html
    [:div
     [:p "Here's an example with arrow keys instead of the card symbols you'll see later"]
    [:div.allcards
     [:div.card-container-instructions
      (cards-disp-one :left   {:img "←" :push-seen 0 :push-need 1})
      (cards-disp-one :middle {:img "↓" :push-seen 0 :push-need 1})
      (cards-disp-one :right  {:img "→" :push-seen 0 :push-need 1})]]])])

(defn instruction [inst-idx]
  (let [idx (max 0 inst-idx)
        ninstruction (count INSTRUCTIONS)]

    (reset! STATE (assoc  @STATE :instruction-idx idx))

    ;TODO: maybe example trial here?

    (showme-this
     (if (< idx ninstruction)
       (sab/html
        [:div.instructions (show-debug @STATE) (nth INSTRUCTIONS idx)
         [:br]
         [:button {:on-click (fn [_] (instruction (dec idx)))} "prev"]
         [:button {:on-click (fn [_] (instruction (inc idx)))} "next"]])
       (sab/html [:div
                  [:p "Find a comfortable way to rest your fingers on the arrow keys!"]
                  [:p "Push the space key when you're ready"]
                  [:button {:on-click (fn [_] (task-start))}
                   "I'm ready!"]])))))

; TODO: add eg {:side-key-only :left :pushed 0 :need 3} to state
(defn instruction-keypress [state-atom key]
  (let [iidx (:instruction-idx @state-atom)
        ninstruction (count INSTRUCTIONS)
        instruction-done? (>= iidx (inc ninstruction))
        pushed (any-accept-key key)]
  (when (and (not instruction-done?)  pushed)
    (if (= ninstruction iidx) (task-start)
    (case pushed
      :next  (instruction (min ninstruction (inc iidx)))
      (println "instuctions: have " pushed)
      ;:left  (instruction (max 0 (dec iidx)))
      ;:right (instruction (inc iidx))
  )))))


;;; 
;;; keypresses get their own atom/state
;; concerns:
;; need up to clear what key is currently down
;; can be lost if e.g. ctrl-tab away
(defn keydown-card! [pushed]
  (let [cards-cur (:cards-cur @STATE)
        trial (:trial @STATE)
        event-name (:event-name @STATE)
        side (cards-pushed-side pushed cards-cur)]
  (when (and (not (nil? side))
             (= event-name :card))
     (reset! STATE (state-add-response @STATE pushed side)))))

(defn keyhold-card! [key] (println "hold key" key))

(defn keypress-init [] {:key nil
                        :first nil
                        :up nil
                        :callback-up nil
                        :callback-first nil
                        :callback-hold nil
                        :reset #'keypress-init
                        :count 0
                        :max-wait 0
                        :waiting []})
(defn keypress-init-card [] (assoc (keypress-init)
                                   :reset #'keypress-init-card
                                   :waiting (flatten (vals KEYS))
                                   :callback-first #'keydown-card!
                                   :callback-hold #'keydown-card!))
(defn keypress-init-instruction []
  (assoc (keypress-init)
         :reset #'keypress-init-instruction
         :waiting (:next KEYS)
         ;:callback-first #'keydown-card!
         ;:callback-hold #'keyhold-card!
         :callback-up (partial instruction-keypress STATE)))

(def KEYPRESSTIME (atom (keypress-init)))
(defn keypress-updown! [direction e]
  "passthrough function. partial used on listener wont get repl updates.
  so this intermidate exists and dispatches to approprate up or down"
  (let [key (.. e -keyCode)
        time (.getTime (js/Date.))]
    (case direction
      :up   (keypress-up! STATE key time)
      :down (keypress-down! STATE key time)
      nil)))

(defn run-if [fnc & rest] (when fnc (apply fnc rest)))
(defn keypress-callback [keystate cbname key] (run-if (cbname keystate) key))

(defn keypress-down! [state-atom key time]
  (let [waitingkey? (some #(= key %) (:waiting @KEYPRESSTIME))
        prev (:key @KEYPRESSTIME)
        count (:count @KEYPRESSTIME)
        new? (or (not prev) (not= prev key)) ]
    ;(println key waitingkey? new? prev count )
    ; missed a keyup (lost focus)
    (when (and prev new?)
      (keypress-up! state-atom prev time))
    ; hit a key we wanted
    (when waitingkey?
      (if new?
          (do
              (swap! KEYPRESSTIME assoc :first time :key key)
              (keypress-callback @KEYPRESSTIME :callback-first key))
          (do
              (swap! KEYPRESSTIME assoc :count (inc count))
              (keypress-callback @KEYPRESSTIME :callback-hold key))))))

(defn keypress-up! [state-atom key time]
  (println "key up" key @KEYPRESSTIME)
  ; if callback is specified, send key to function
  (swap! KEYPRESSTIME assoc :up time)
  (keypress-callback @KEYPRESSTIME :callback-up key)
  (println "reset?" (:reset @KEYPRESSTIME))
  (reset! KEYPRESSTIME ((:reset @KEYPRESSTIME)))
  (println "reset"))


(defn state-toggle-setting [setting] (reset! STATE (update-in @STATE setting not)))

(defn show-debug "debug info. displayed on top of task"
[{:keys [:trial :event-name :score :time-cur :time-flip] :as state}]
(sab/html
 [:div.debug
  [:ul.bar {:class (if (:running? state) "running" "stoppped")}
   [:li {:on-click task-toggle} "tog"]
   [:li {:on-click task-restart}   "restart"]
   [:li {:on-click cards-reset}   "cards-reset"]
   [:li {:on-click (fn [_] ((task-stop) (instruction 0)))} "instructions"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/cash.mp3" :dur .5}))}  "cash"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/buzzer.mp3" :dur .5}))} "buz"]
   [:li {:on-click (fn [_] (renderer (world state)))} "update-debug"]
   [:li {:on-click (fn [_] (state-toggle-setting [:no-debug-bar]))} "debug-bar"]
   [:li {:on-click (fn [_] (state-toggle-setting [:debug-no-advance]))} "no-advance"]
  ]
  ; double negative so default is true without explicity settings
  (when (not (:no-debug-bar state))
    (sab/html [:div.debug-extra-info
     [:p.time (.toFixed (/ (- time-cur time-flip) 1000) 1)]
     [:p.time (:time-since state)]
     [:p.info (str "trial: " trial " @ " (name event-name))]
     [:p.info "score: " score]
     [:p.info "cards: " (str (:cards-cur state))]
     [:p.info "no-adv: " (:debug-no-advance state) " " (:no-debug-bar state)]
     [:p.resp "nresp: " (-> state :responses count)]
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


(defn key-disbatch! [dir e]
  (println "disbatch" dir))

(defn main []
    (add-watch STATE :renderer (fn [_ _ _ n] (renderer (world n))))

    ; TODO: this should go into state-fresh?
    (reset! STATE  @STATE) ; why ?


    (send-info)
    (instruction 0)
    ; should this go in instructions?
    (reset! KEYPRESSTIME (keypress-init-instruction))

    ;(task-start)
   (.addEventListener js/document "keydown" (partial keypress-updown! :down))
   (.addEventListener js/document "keyup" (partial keypress-updown! :up)))

(main)
