(ns cardtask.sound
(:require 
   [cardtask.utils :as utils ]
   [cljs-bach.synthesis :as syn]))

;; audio
(defonce audio-context (syn/audio-context))
(def SOUNDS {:reward [{:url "audio/cash.mp3"    :dur .5}
                      ;{:url "audio/cheer.mp3"   :dur .5}
                      ;{:url "audio/trumpet.mp3" :dur .5}
                     ]
             :empty  [{:url "audio/buzzer.mp3"  :dur .5}
                      ;{:url "audio/cry.mp3"     :dur .5}
                      ;{:url "audio/alarm.mp3"   :dur .5}
                      ]})
(defn preload-sounds
  "load all the sounds w/out playing them first"
  []
    (for [url (map #(:url %) (flatten (vals SOUNDS)))] syn/sample))

; NB. could use e.g. (syn/square 440) for beeeeep
(defn play-audio
  [{:keys [url dur]}]
  (syn/run-with
   (syn/connect-> (syn/sample url) syn/destination)
   audio-context
   (syn/current-time audio-context)
   dur))


(defn score-with-sound
  [prob]
  "score and play sound. returns true (win) or false (no win)"
  (let [get-points (utils/prob-gets-points? prob)
        rew-key  (if get-points :reward :empty)
        snd (first (shuffle (rew-key SOUNDS)))]
  (play-audio snd)
  get-points))
