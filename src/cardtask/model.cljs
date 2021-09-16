(ns cardtask.model
(:require [cardtask.sound :as sound]
   [debux.cs.core :as d :refer-macros [clog clogn dbg dbgn dbg-last break]]
))
;;; 
;;; state
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

(def STATE (atom starting-state))
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

(defn key-resp
  "generate response map. to be appended to (-> state :responses n :keys)"
  [fliptime side pushed]
  (let [time (.getTime (js/Date.))
        rt (- time fliptime)]
  {:side side :key pushed :rt rt :time time}))

(dbgn (defn response-score
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
           :get-points (sound/score-with-sound prob)))))



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

