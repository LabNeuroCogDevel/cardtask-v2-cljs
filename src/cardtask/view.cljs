(ns cardtask.view
  (:require 
   [cardtask.cards :refer [SIDES CARDINFO MAXPUSH]]
   [sablono.core :as sab :include-macros true]))

(defn color-to-planet [color] (str "url('img/DawTask/card_" color "planet.jpg"))

(defn img-url "img name to url" [img] 
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
