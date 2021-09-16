(ns cardtask.cards
  (:require [cardtask.settings :refer [KEYS]]))

;; 2 way choice from 3 "cards"
;; Left high prob win, Right low prob win, Middle always win (but require multiple pushes)
;; 3 stages: 80/20/100, 20/80/100, 100/100/100 (habit test)


(def event-states [:instruction :start :card :feeback :done]) ; unused
(defonce SIDES [:left :middle :right])

;; TODO: principle counterbalence?
;; this is unused. see probs (card-side-zip ...)
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

(defn sort-side
  "put in order L to R order. overkill. keys are sorted themselve"
  [map-with-side]
  (let [o {:left 1 :middle 2 :right 3}]
    (sort-by #((:side %) o) map-with-side)))



(def CARDINFO (mk-card-info))

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

(def CARDSLIST (mk-card-list CARDINFO 3))

(defn cards-reset [reps-per-phase]
  "rerun random selection of card sym/img and color"
  (def CARDINFO (mk-card-info))
  (def CARDSLIST (mk-card-list CARDINFO reps-per-phase)))
