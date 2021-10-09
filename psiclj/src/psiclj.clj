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
   ;; command line args
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.java.io :as io]
   )
  (:gen-class))


;; global settings
;; this can be modified with command arguments
(def VERSION
  "pushed into DB so specific code can be annotated.
   think git checksum or tag"
  (atom "20210909-init"))
(def path-root
  "where to find resources like css javascript etc.
  likely out/ if in same dir as clojurescript project main"
   (atom "out/")
  )


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
    (create-run-sqll db info)
    (create-run-psql db info)))

(defn already-done? [run-data] (-> DB (run-by-id run-data) :finished_at nil? not))


;; HTTP
;; https://github.com/taylorwood/lein-native-image/tree/master/examples/http-api
;; https://github.com/weavejester/compojure/wiki/Nesting-routes

;; 20211008 removed io/resource from read-file, getting files outside of jar/exe
(defn read-file [file] (slurp file))

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
           (resp/response {:ok status})))

   ;; TODO: add /csv that RA can used to save output (in addition to DB)
   ))

(defroutes task-run-context
  (context "/:id/:task/:timepoint/:run"
           req
           (task-run (assoc-in req [:params :version] @VERSION))))

;; 20211008 these simple functions were source of great headache
;; https://stackoverflow.com/questions/69501727/updated-atom-value-not-used-in-compojure-route/69502052#69502052
;; need to pass them as var to route so we can use the update atom value
;; which we need because the default 'out/' can be change by command parameters
;; still TODO: confirm the same doesn't need to happen with @VERSION
;; -- that is: does defroutes evaluated each request and update @VERSION?
;;             will see in the DB after setting -v
(defn slurp-root [fname]
  (let [path (io/file @path-root fname) ]
    (if path
      (slurp (str path))
      (str "cannot find " fname "!"))))
(defn not-found-fn [req]
  {:status 404
   :body ;; (slurp-root "not-found.html")
   (str  "  \npwd=" (-> (java.io.File. ".") .getAbsolutePath)
         "  \nroot=" @path-root
         "  \nreq=" (:uri req))
   })
(defn find-root
  "root from global atom.
  prefix with extra / if we are an absolute path
  route/resources strips of the first leading / as we want it.
  so give it ^//"
  [& args]
  {:root @path-root :allow-symlinks? true})

(defroutes pages
  (context "/:id/:task/:timepoint/:run" req
           (GET "/" []
                ;; TODO: try to get agent and maybe viewport from browser
                ;; for :info
                (let [run-info (assoc (:params req) :info "" :version @VERSION)
                      index (str (io/file @path-root "index.html"))]
                  (if (already-done?  run-info)
                    (resp/response "already done!")
                    (do
                      (println "loading" index)
                      (println "sending runinfo to db" DB)
                      (create-run DB run-info)
                      (resp/response (read-file index))))))

           ;; main.js, style.css, img/*
           ;; route/resources depends on io/resource
           ;; only for inside jar/exe/src path
           ;; use files! (this took a long time to understand!)
           (route/files "/" (find-root))) )

(def app
  (routes
   pages
   (wrap-routes #'task-run-context json/wrap-json-response)
   (route/not-found not-found-fn)))


;; exercise
(defn excercise-db []
  (def run-data {:id "will" :task "test" :version "x" :run 1 :timepoint 1 :json "[]" :info "[]"})
  (create-run-table DB)
  (already-done? run-data)
  (create-run DB (assoc run-data :info "[{system: \"none\"}]"))
  (upload-json DB (assoc run-data :json "[{data: [1,3]}]"))
  (finish-run DB run-data))


;; Main
(defonce server-stop-fn (atom nil))
(defn check-file [fname]
    (when (-> @path-root (io/file fname) .exists not)
      (println (str "cannot find '" @path-root "/" fname "'. consier using -r"))
      (System/exit 1)))

(defn -main [& args]
  (let [opts [["-p" "--port PORT"
               "port. tries env PORT first. default 3001"
               :default "3001"]
              ["-r" "--root-path PATH/out. TOOD: DOESNT WORK. always looks for out relative to binary"
               "path to index.html, not-found.html, resources root"
               :default @path-root]
              ["-v" "--version VERSION"
               "set code version inserted in DB"
               :default @VERSION]
              ["-d" "--database DB"
               (str "psql url. looks to DATABASE_URL first. "
                    "like postgres://user:pass@host:port/db. "
                    "TODO: IMPLEMENT")
               :default nil]
              ["-h" "--help" "This message" :default false]]
        {:keys [options arguments summary errors]} (parse-opts args opts)
        port (Integer/parseInt (or (System/getenv "PORT")
                                   (:port options)))]

    ;; update settings from command parsing
    (reset! VERSION (:version options))
    (reset! path-root (:root-path options))
    (println "settings: v=" @VERSION " r=" @path-root)

    ;; shouldn't continue if there isn't anything to serve
    (check-file "index.html")
    (check-file "not-found.html")

    ;; test out the DB (or die w/error)
    (println (str "creating run table on " DB))
    (create-run-table DB)

    ;; serve it up
    ;; kill if already running (repl)
    (println (str "Running webserver on " port))
    (when @server-stop-fn (@server-stop-fn))
    (reset! server-stop-fn (srv/run-server #'app {:port port}))))
