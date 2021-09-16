(ns cardtask.settings)

(defonce KEYS {:left [70 37] ;["f"] ; left arrow
               :middle [71 72 40] ; ["g", "h"]  ; down arrow
               :right [74 39] ;["j"] ; right arrow
               :next [32] ;; space
               })

(defn any-accept-key [pushed]
  (first (mapcat
   (fn [keykey] (if (some #(= pushed %) (keykey KEYS)) [keykey] nil))
   (keys KEYS))))
