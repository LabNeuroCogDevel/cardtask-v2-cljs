(ns cardtask.core
  (:require
   [cardtask.model :as model :refer [STATE]]
   [cardtask.settings :refer [KEYS any-accept-key]]
   [cardtask.cards :refer [SIDES CARDINFO CARDSLIST MAXPUSH cards-reset ]]
   [cardtask.view :as view :refer [cards-disp text-or-img img-url]]
   [cardtask.http :refer [send-info send-resp send-finished]]
   [cardtask.sound :refer [play-audio] :as sound]
   [cardtask.instruction :as instruct :refer [INSTRUCTIONS]];instruction-keypress instruction]]
   [cardtask.keypress2 :as key :refer [KEYPRESSTIME keypress-init]]
   [cljs.spec.alpha :as s]
   [goog.string :refer [unescapeEntities]]
   [goog.events.KeyCodes :as keycodes]
   [goog.events :as gev]
   [cljsjs.react]
   [cljsjs.react.dom]
   [sablono.core :as sab :include-macros true]
   [debux.cs.core :as d :refer-macros [clog clogn dbg dbgn dbg-last break]]
   [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]]
   )
  (:require-macros  [cljs.core.async.macros :refer [go-loop go]])
  (:import [goog.events EventType]))
(enable-console-print!)
(set! *warn-on-infer* false) ; maybe want to be true
(declare showme-this)
(declare show-debug)

;;; 
;;; local keypress funcs
(defn cards-pushed-side
  "is a pushed key for any card in current state?"
  [pushed cards-cur]
  (first (mapcat
   (fn [side] (keep #(if (= pushed %) side nil)
                   (-> cards-cur side :keys)))
   SIDES)))

(defn keydown-card! [pushed]
  (let [cards-cur (:cards-cur @STATE)
        trial (:trial @STATE)
        event-name (:event-name @STATE)
        side (cards-pushed-side pushed cards-cur)]
  (when (and (not (nil? side))
             (= event-name :card))
     (reset! STATE (model/state-add-response @STATE pushed side)))))

(defn keyhold-card! [key] (println "hold key" key))

(defn keypress-init-card [] (assoc (keypress-init)
                                   :reset #'keypress-init-card
                                   :waiting (flatten (vals KEYS))
                                   :callback-first #'keydown-card!
                                   :callback-hold #'keydown-card!))
;; 
;; how to handle each event


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
  [dur {:keys [:trial-last-win :trial :score :time-cur :time-flip] :as state}]
  "feedback: win or not?
   using sprite that is 11 frames of 99x99"
  (let [
        side (-> state :cards-cur :picked)
        ; dur (-> EVENTDISPATCH :feedback :dur) ; remove cyclic depend
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
(defn start-disp [dur {:keys [:time-since :event-name] :as state}]
  (sab/html [:p (str "Starting in ... "
                     (-> dur
                         (- time-since)
                         (/ 1000)
                         (.toFixed 1)) " seconds")]))

;; settings for events
(def EVENTDISPATCH {:start    {:dur 2000 :func (partial start-disp 2000) :next :card}
                    :card     {:dur 2000 :func #'cards-disp :next :feedback}
                    :feedback {:dur 1500 :func (partial feedback-disp 1500) :next :card}
                    :last-msg {:dur 1 :func #'finished-disp :next :done}
                    ; repeat last-message. maybe we don't need it
                    :done     {:dur 0 :func #'finished-disp :next :done}})

(defn state-out-of-trials? [trial cards-list]
  (>= trial (count cards-list)))

(defn cards-at-trial
  "rearrange from vect to map. depends on CARDSLIST
  ({card1} {card2}) to {:left {card1} :middle nil :right {card2}"
  [trial cards-list]
  (let [empty {:left nil :middle nil :right nil :picked nil}
        cur-cards (if (state-out-of-trials? trial cards-list) nil (nth cards-list trial))
        sidemap (map (fn [c] {(:side c) (dissoc c :side)}) cur-cards) ]
    (merge empty (reduce merge sidemap))))



;; from flappy bird demo
; was defonce but happy to reset when resourced
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
  (-> model/starting-state
      (assoc :time-start time :time-cur time :time-flip time
             :running? true
             :responses (vec (repeat (count CARDSLIST) model/starting-response)))))


;;; 

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
     (event-next? time)
     ; TODO: update based on KEYPRESSTIME
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
  (cards-reset 3)
  (task-start))

;;; 
;;; instructions

(defn instruction [inst-idx after-func state-atom]
  (let [idx (max 0 inst-idx)
        ninstruction (count INSTRUCTIONS)
        instruction-go (fn [i] (instruction i after-func state-atom) )]

    (reset! state-atom (assoc  @state-atom :instruction-idx idx))

    ;TODO: maybe example trial here?

    (showme-this
     (if (< idx ninstruction)
       (sab/html
        [:div.instructions (show-debug @state-atom) (nth INSTRUCTIONS idx)
         [:br]
         [:button {:on-click (fn [_] (instruction-go (dec idx)))} "prev"]
         [:button {:on-click (fn [_] (instruction-go (inc idx)))} "next"]])
       (sab/html [:div
                  [:p "Find a comfortable way to rest your fingers on the arrow keys!"]
                  [:p "Push the space key when you're ready"]
                  [:button {:on-click (fn [_] (after-func))}
                   "I'm ready!"]])))))


; TODO: add eg {:side-key-only :left :pushed 0 :need 3} to state
(defn instruction-keypress [state-atom after-func key]
  (let [iidx (:instruction-idx @state-atom)
        ninstruction (count INSTRUCTIONS)
        instruction-done? (>= iidx (inc ninstruction))
        pushed (any-accept-key key)]
  (when (and (not instruction-done?)  pushed)
    (if (= ninstruction iidx)
      (after-func)
      (case pushed
        :next  (instruction (min ninstruction (inc iidx)) after-func state-atom)
        ;:left  (instruction (max 0 (dec iidx)))
        ;:right (instruction (inc iidx))
        (println "instuctions: pushed unused key " pushed))))))

;;; 
;;; view

(defn state-toggle-setting [setting] (reset! STATE (update-in @STATE setting not)))

(defn show-debug "debug info. displayed on top of task"
[{:keys [:trial :event-name :score :time-cur :time-flip] :as state}]
(sab/html
 [:div.debug
  [:ul.bar {:class (if (:running? state) "running" "stoppped")}
   [:li {:on-click task-toggle} "tog"]
   [:li {:on-click task-restart}   "restart"]
   [:li {:on-click (fn [_] cards-reset 3)}   "cards-reset"]
   [:li {:on-click (fn [_] ((task-stop) (instruction 0 #'task-start STATE)))} "instructions"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/cash.mp3" :dur .5}))}  "cash"]
   [:li {:on-click (fn [_] (play-audio {:url "audio/buzzer.mp3" :dur .5}))} "buz"]
   ;[:li {:on-click (fn [_] (renderer (world state)))} "update-debug"]
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

;;; 
;;; display/render funcs
(let [node (.getElementById js/document "task")]
  (defn showme-this
  "show a sab/html element where we'd normally run task-display"
  [reactdom]
    (.render js/ReactDOM reactdom node)))

(defn task-display
  "html to render for display. updates for any change in display
   "
  [{:keys [cards-cur event-name score] :as state}]
  (let [f (:func (event-name EVENTDISPATCH))]
   (sab/html
    [:div.board
     (show-debug state) ; todo, hide behind (when DEBUG)
     (if f (f state))])))


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


(defn keypress-init-instruction []
  (assoc (keypress-init)
         :reset #'keypress-init-instruction
         :waiting (:next KEYS)
         ;:callback-first #'keydown-card!
         ;:callback-hold #'keyhold-card!
         :callback-up (partial instruction-keypress STATE #'task-start)))



(defn -main []
  (add-watch STATE :renderer (fn [_ _ _ n] (renderer (world n))))
  ; doesn't work
  (sound/preload-sounds)

  ; TODO: this should go into state-fresh?
  (reset! STATE  @STATE) ; why ?


  (send-info)
  (instruction 0 #'task-start STATE) ;(task-start)

  ; should this go in instructions?
  (reset! key/KEYPRESSTIME (keypress-init-instruction))

  (.addEventListener js/document "keydown" (partial key/keypress-updown! :down))
  (.addEventListener js/document "keyup" (partial key/keypress-updown! :up)))

(-main)
