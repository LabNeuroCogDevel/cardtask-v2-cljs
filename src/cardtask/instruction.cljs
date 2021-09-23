(ns cardtask.instruction (:require
   [cljsjs.react]
   [cljsjs.react.dom]
   [cardtask.cards :refer [MAXPUSH MINPUSH CARDINFO] :as cards]
   [cardtask.sound :refer [play-audio] :as sound]
   [cardtask.view :refer [cards-disp-one]]
   [cardtask.settings :refer [any-accept-key]]
   [sablono.core :as sab :include-macros true])
  (:require-macros [devcards.core :refer [defcard]]))

(defn fresh-example [] {
 :left   {:img "←" :push-seen 0 :push-need (get-in CARDINFO [:pushes :left]   )}
 :middle {:img "↓" :push-seen 0 :push-need (get-in CARDINFO [:pushes :middle] )}
 :right  {:img "→" :push-seen 0 :push-need (get-in CARDINFO [:pushes :right]  )}})

(defn card-rm-arrow-keys
  "depreicated. use iconds instead"
  [cards]
 (reduce #(assoc-in %1 [%2 :img ] " ") cards (keys cards)))
(defn card-set-imgs
  "replace whatever in the example cards with the images we'll use"
  [cards]
 (reduce #(assoc-in %1 [%2 :img ]
                    (get-in CARDINFO [:img %2]))
         cards (keys cards)))


(def example-atom (atom (fresh-example)))

(defn example-cards
  [atom]
  (sab/html [:div {:style {:height "220px" :text-align "center"}}
     [:div.card-container
      (cards-disp-one :left   (:left @atom))
      (cards-disp-one :middle (:middle @atom))
      (cards-disp-one :right  (:right @atom ))]]))

(def INSTRUCTIONS
  [
   {:html (fn[] (sab/html [:div [:h2 "Welcome to our card game!"]
              [:p "The game is to get as many points as you can "
                  "by choosing the better card."]
              [:br]
              [:p "You can click the buttons below"
               " or use the spacebar for more insturctions."]]))
   } {:html (fn[] (sab/html [:div
              [:p "First lets test your speakers!" [:br]
               "Do you hear sounds when you push these buttons?"]
              [:button
               {:on-click (fn [_] (play-audio {:url "audio/cash.mp3" :dur .5} 1))}
               "points"]
              [:button
               {:on-click (fn [_] (play-audio {:url "audio/buzzer.mp3" :dur .5} 1))}
               "no points"]]))
   } {:pre (fn[state] (card-set-imgs state))
      :html (fn[] (sab/html [:div
              [:h3 "Rules of the game"]
              [:ul
               [:li "Some cards are better at getting points than others."]
               [:li "One of the three cards will need "
                [:b "more than one key push"] " to pick." [:br]
                "The progress bar under this card will be wider."]
               [:li "As the game progresses some cards will "
                [:b "change how often "] "they give points."]]
              [:h3 "Sneak peak"]
              (example-cards example-atom)]))
   } {:html (fn[] (sab/html [:div
              [:p "The cards will always be in the same order"]
              [:p "Choose a card using the arrow keys. Push"]
              [:ul
               [:li "Left for the left card"]
               [:li "Down for the centercard"]
               [:li "Right for the card on the right"]]
              (example-cards example-atom)] ))

   }
; {:html (sab/html [:div "Once you pick a card, you'll learn if it gave you any points "])}
])




(defn instruction-html [instruction inst-idx after-func state-atom]
  (let [idx (max 0 inst-idx)
        ninstruction (count INSTRUCTIONS)
        instruction-go (fn [i] (instruction i after-func state-atom))
        prefnc (get-in INSTRUCTIONS [idx :pre])
        ]

    (println prefnc)
    (when (not= idx (:instruction-idx @state-atom))
      (reset! example-atom (if prefnc (prefnc (fresh-example)) (fresh-example))))
    (reset! state-atom (assoc  @state-atom :instruction-idx idx))

    ;; play sound w/o volume after first keypress
    ;; kludge to load sound before they're used
    (when (= idx 0)
      (println "preloading sounds")
      (doall (sound/preload-sounds)))


    ;TODO: maybe example trial here?

     (if (< idx ninstruction)
       (sab/html
        [:div.instructions ;(show-debug @state-atom)
         ((:html (nth INSTRUCTIONS idx)))
         [:br]
         [:button {:on-click (fn [_] (instruction-go (dec idx)))} "prev"]
         [:button {:on-click (fn [_] (instruction-go (inc idx)))} "next"]])
       (sab/html [:div
                  [:p "Find a comfortable way to rest your fingers on the arrow keys!"]
                  [:p "You can click the buttons below or use the spacebar to start!"]
                  [:button {:on-click (fn [_] (instruction-go (dec idx)))} "prev"]
                  [:button {:on-click (fn [_] (after-func))}
                   "I'm ready!"]]))))
