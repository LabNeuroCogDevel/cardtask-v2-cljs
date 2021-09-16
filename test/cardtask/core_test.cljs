(ns cardtask.core-test
  (:require [cljs.test :refer-macros [async deftest is testing run-tests]]
            [cardtask.core :refer [cards-pushed-side]]
            [cardtask.model :refer [cards-cur-picked]]
))

(deftest cards-pushed-side-test
  (let [cards-cur {:left {:keys [1 2] :middle nil :right {:keys [3 4]}}}]
    (is (= :left  (cards-pushed-side 2 cards-cur)))
    (is (= :right (cards-pushed-side 4 cards-cur)))
    (is (nil?     (cards-pushed-side 5 cards-cur)))))


(deftest cards-cur-picked-test
  (let [cards-cur {:left {:push-need 1 :push-seen 1}
                   :right {:push-need 3 :push-seen 1}
                   :picked nil}]
    (is (= :left (:picked (cards-cur-picked cards-cur :left))))
    (is (nil? (:picked (cards-cur-picked cards-cur :right))))))
