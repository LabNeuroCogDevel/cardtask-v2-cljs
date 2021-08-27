(ns psiclj
  (:require
   ; DB
   [hugsql.core :as hugsql]
   ; HTTP - server
   [org.httpkit.server :as srv]
   ; HTTP - routes
   [clojure.java.io :as io]
   [compojure.core :refer [routes wrap-routes defroutes GET POST]]
   [compojure.route :as route]
   [ring.middleware.json :as json]
   [ring.util.response :as resp]
)
  (:gen-class))

;; 
;; DB

(def DB
  {:subprotocol "sqlite"
   :subname "psiclj.sqlite3"})

(hugsql/def-db-fns "all.sql")

(defn already-done? [run-data] (-> DB (run-by-id run-data) :finished_at nil? not))

;;
;; HTTP
;; https://github.com/taylorwood/lein-native-image/tree/master/examples/http-api
(defroutes my-routes
  (GET "/:id/:task/:version/:timepoint/:run" req
       (let [run-info (assoc (:params req) :info "") ]
       (if (already-done?  run-info)
         (resp/response "already done!")
         (do
           (create-run DB run-info)
           (resp/response "running")))))
    ;(if (
    ;(resp/response {:hi "world" :info id :version version}))

  ;(POST "/:id/:task/:version/:timepoint/:run"
  ;      [id task version timepoint run json]
  ;  (resp/response
  ;   {:post-data json 
  ;    :url {:id id :task task :version version :timepoint timepoint :run run}}))
  (POST "/:id/:task/:version/:timepoint/:run"
        req
        (let [status
              (upload-json DB (assoc (:params req)
                                     :json (-> req :body slurp)))]
         (resp/response {:ok status}))))

(def app
  (routes
   (wrap-routes #'my-routes json/wrap-json-response)
   (route/not-found {:status 404 :body (slurp (io/resource "not-found.html"))})))

;; 
;; Main
(defn -main [& args]
  (def run-data {:id "will" :task "test" :version "x" :run 1 :timepoint 1 :json "[]" :info "[]"})

  (create-run-table DB)
  (already-done? run-data)
  (create-run DB (assoc run-data :info "[{system: \"none\"}]"))
  (upload-json DB (assoc run-data :json "[{data: [1,3]}]"))
  (finish-run DB run-data)

  (println "Hello, Web!")
  (def server (srv/run-server #'app {:port 3000}))
)
