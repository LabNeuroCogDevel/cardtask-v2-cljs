(ns cardtask.http
  (:require [ajax.core :refer [POST GET]]))
;; 
;; AJAX/HTTP
(def HTTP-DEBUG false)
(defn get-url [rest] (str (if HTTP-DEBUG "http://0.0.0.0:3001/will/test/nover/1/1/" "") rest))

(defn send-info
  []
  "TODO: send system info (eg browser, system, resolution, window size"
  (POST (get-url "info") {:params {:info "TODO!"} :response-format :json}))

(defn send-resp [state]
  (println "sending state!")
  (POST (get-url "response")
        {;:params (.stringify js/JSON (clj->js @STATE))
         :params state
         :format :json
         })
  (println "state sent!"))
(defn send-finished [] (POST (get-url "finished")))
