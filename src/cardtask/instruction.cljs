(ns cardtask.instruction (:require
   [cardtask.sound :refer [play-audio] :as sound]
   [cardtask.view :refer [cards-disp-one]]
   [cardtask.settings :refer [any-accept-key]]
   [sablono.core :as sab :include-macros true])
  (:require-macros [devcards.core :refer [defcard]]))

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
    [:div {:style {:height "220px" :text-align "center"}}
     [:div.card-container
      (cards-disp-one :left   {:img "←" :push-seen 0 :push-need 1})
      (cards-disp-one :middle {:img "↓" :push-seen 0 :push-need 1})
      (cards-disp-one :right  {:img "→" :push-seen 0 :push-need 1})]]])])

; (defn instruction [inst-idx after-func state-atom & show-debug]
;   (let [idx (max 0 inst-idx)
;         ninstruction (count INSTRUCTIONS)]
; 
;     (reset! state-atom (assoc  @state-atom :instruction-idx idx))
; 
;     ;TODO: maybe example trial here?
; 
;     (showme-this
;      (if (< idx ninstruction)
;        (sab/html
;         [:div.instructions
;          (if show-debug (show-debug @state-atom) (nth INSTRUCTIONS idx))
;          [:br]
;          [:button {:on-click (fn [_] (instruction (dec idx) after-func state-atom))} "prev"]
;          [:button {:on-click (fn [_] (instruction (inc idx) after-func state-atom))} "next"]])
;        (sab/html [:div
;                   [:p "Find a comfortable way to rest your fingers on the arrow keys!"]
;                   [:p "Push the space key when you're ready"]
;                   [:button {:on-click (fn [_] (after-func))}
;                    "I'm ready!"]])))))
; 
; 
; ; TODO: add eg {:side-key-only :left :pushed 0 :need 3} to state
; (defn instruction-keypress [state-atom after-func key]
;   (let [iidx (:instruction-idx @state-atom)
;         ninstruction (count INSTRUCTIONS)
;         instruction-done? (>= iidx (inc ninstruction))
;         pushed (any-accept-key key)]
;   (when (and (not instruction-done?)  pushed)
;     (if (= ninstruction iidx)
;       (after-func)
;       (case pushed
;         :next  (instruction (min ninstruction (inc iidx)) after-func state-atom)
;         ;:left  (instruction (max 0 (dec iidx)))
;         ;:right (instruction (inc iidx))
;         (println "instuctions: pushed unused key " pushed))))))

;(defcard instruction-cards
;  (let [state {}]
;  (instruction (5 (fn[_] nil) state))))
