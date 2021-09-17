(ns psiclj
  (:require
   ;; DB
   [hugsql.core :as hugsql]
   ;; HTTP - server
   [org.httpkit.server :as srv]
   ;; HTTP - routes
   [clojure.java.io :as io]
   [compojure.core :refer [routes wrap-routes context defroutes GET POST] :as comp]
   [compojure.route :as route]
   ;;[ring.util.resposne :refer [resource-response]]
   [ring.middleware.json :as json]
   [ring.util.response :as resp]
   ;; str split DATABASE_URL
   [clojure.string :as str]
   )
  (:gen-class))

(def VERSION "20210909-init")

;; 
;; DB
(defn dburl-map
  "extract db info from postgres://user:pass@host:port/db"
  [dburl]
  (zipmap [:dbtype :user :password :host :port :db]
          (str/split dburl #"[:/@]+")))
(defn jdbc-url
  "make from map: //host:port/db?user=X&password=Y"
  [{:keys [host port db user password]}]
      (str ;; "jdbc:postgresql:"
           "//" host ":" port
           "/" db
           "?user="  user
           "&password=" password
           ;; "&sslmode=require"
           ))
(defn db-to-jdbc
  "parse DATABASE_URL to JDBC_DATABASE_URL
  -> postgres://user:pass@host:port/db
  <- //host:port/db?user=X&password=Y  ; NB no jdbc:postgresql:
  "
  [dburl] (-> dburl dburl-map jdbc-url))
(defn get-db-params
  "setup database default to hardcoded psiclj.sqlite3. otherwise use postgres"
  []
  (let [db (System/getenv "DATABASE_URL")]
    (if db
        {:subprotocol "postgresql"
         :subname (db-to-jdbc db)}
        {:subprotocol "sqlite"
         :classname "org.sqlite.JDBC"
         :subname "psiclj.sqlite3"})))

(def DB (get-db-params))


(hugsql/def-db-fns "all.sql")
(hugsql/def-sqlvec-fns "all.sql")
(defn create-run [db info]
  (if (= "sqlite" (:subprotocol db))
    (create-run db info)
    (create-run-psql db info)))

(defn already-done? [run-data] (-> DB (run-by-id run-data) :finished_at nil? not))

;;
;; HTTP
;; https://github.com/taylorwood/lein-native-image/tree/master/examples/http-api
;; https://github.com/weavejester/compojure/wiki/Nesting-routes
(defn read-file [file] (slurp (io/resource file)))
(defn task-run [req]
  (routes
  (POST "/response" []
        (let [ task-resp (-> req :body slurp)
              run-info (assoc (:params req) :json task-resp)]
         (resp/response {:ok (upload-json DB run-info)})))
        ;(let [run-info (:params req)]
        ;  (println run-info)
        ;  (resp/response {:ok (-> req :body slurp)})))

  (POST "/info" []
        (let [status
              (upload-system-info
               DB (assoc (:params req)
                         :info (-> req :body slurp)))]
         (resp/response {:ok status})))

  (POST "/finish" []
        (let [status (finish-run DB (:params req))]
         (resp/response {:ok status})))))

(defroutes task-run-context
  (context "/:id/:task/:timepoint/:run" req (task-run (assoc-in req [:params :version] VERSION))))

(defroutes pages
  (context "/:id/:task/:timepoint/:run" req
    (GET "/" []
        (let [run-info (assoc (:params req) :info "" :version VERSION) ]
        (if (already-done?  run-info)
            (resp/response "already done!")
            (do
              (create-run DB run-info)
              (resp/response (read-file "out/index.html"))))))
    ; main.js, style.css, img/*
    (route/resources "/" {:root "out/"})))

(def app
  (routes
   pages
   (wrap-routes #'task-run-context json/wrap-json-response)
   (route/not-found {:status 404 :body (read-file "not-found.html")})))

;; 
;; exercise
(defn excercise-db []
  (def run-data {:id "will" :task "test" :version "x" :run 1 :timepoint 1 :json "[]" :info "[]"})
  (create-run-table DB)
  (already-done? run-data)
  (create-run DB (assoc run-data :info "[{system: \"none\"}]"))
  (upload-json DB (assoc run-data :json "[{data: [1,3]}]"))
  (finish-run DB run-data))

;; 
;; Main
(defonce server (atom nil))
(defn -main [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3001"))]
    (println (str "creating run table on " DB))
    (create-run-table DB)
    (println (str "Running webserver on " port))
    (reset! server (srv/run-server #'app {:port port}))))
