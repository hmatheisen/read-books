(ns read-books.main
  (:gen-class)
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [compojure.core :refer [defroutes POST GET]]
            [compojure.route :as route]
            [read-books.epub :as epub])
  (:import [java.io FileNotFoundException]))

(defonce server (atom nil))

(defroutes app

  (POST "/epub/upload" req
    (let [file (get-in req [:params "file"])]
      (-> (response "ok")
          (assoc :session file))))

  (GET "/epub/name" req
    (let [file (-> req :session :tempfile)]
      (response {:name (.getName file)})))

  (GET "/epub/content" req
    (let [file (get-in req [:params "file"])]
      (when-not file
        (throw (FileNotFoundException. "epub file not found")))
      (epub/list-content-as-html (epub/load-epub (:tempfile file)))))

  (route/not-found "<h1>Page not found</h1>"))

(def handler (-> app
                 wrap-stacktrace
                 wrap-session
                 wrap-params
                 wrap-multipart-params
                 (wrap-resource "public")))

(defn start-server
  ([]
   (start-server handler {:port 8000}))
  ([app opts]
   (swap! server (fn [_] (run-jetty app opts)))))

(defn stop-server
  []
  (.stop @server))

(defn -main
  [& _args]
  (start-server))
