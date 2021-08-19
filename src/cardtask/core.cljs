(ns cardtask.core
  (:require [sablono.core :as sab :refer [html]]))
(enable-console-print!)
(set! *warn-on-infer* true)

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

(defn singlecard "div for a card. mostly handled by css" [side]
  (str "<div class='card " (name side) "' "
       "onclick=simkey(" (first (side KEYS)) ")>"
       (side CARDIMGS)
       "</div>"))

(defn showchoices "html for showing cards" [& {:keys [left middle right]}]
 (str (singlecard :left )
      (singlecard :middle)
      (singlecard :right)))


(defn cardStart [trial] nil)
(defn cardLoad [trial] nil)
(defn cardFinish [^js/object data]
  (set! (.-testme data) "xx")
)

(defn trialCard [stim]
  (let [availKeys nil]
    {:type "html-keyboard-response"
     :stimulus stim
     ;:post_trial_gap SCOREANIMATEDUR,
     :choices availKeys
     :trial_duration CARDDUR
     :prompt ""
     :response_ends_trial false
     :on_start cardStart
     :on_load cardLoad
     :on_finish cardFinish}))


(defn runOne [trial] (js/jsPsych.init (clj->js {:timeline [trial]})))
;; run!
(def trials {:timeline [{:type "html-keyboard-response" :stimulus "hi"}, (trialCard "stim")]})
(js/jsPsych.init (clj->js trials))

