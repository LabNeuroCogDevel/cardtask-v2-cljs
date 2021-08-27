(ns psiclj
  (:require [hugsql.core :as hugsql])
  (:gen-class))

;; 
;; DB

(def DB
  {:subprotocol "sqlite"
   :subname "psiclj.sqlite3"})

(hugsql/def-db-fns "all.sql")

(defn already-done? [run-data] (-> DB (run-by-id run-data) :finished_at nil? not))

;; 
;; Main
(defn -main [& args]
  (def run-data {:id "will" :task "test" :version "x" :run 1 :timepoint 1 :json "[]" :info "[]"})

  (create-run-table DB)
  (already-done? run-data)
  (create-run DB (assoc run-data :info "[{system: \"none\"}]"))
  (upload-json DB (assoc run-data :json "[{data: [1,3]}]"))
  (finish-run DB run-data)
)
