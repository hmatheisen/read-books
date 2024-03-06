(ns read-books.main
  (:gen-class)
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response redirect]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [compojure.core :refer [defroutes POST GET]]
            [compojure.route :as route]
            [read-books.epub :as epub]
            [hiccup.core :as h]
            [hiccup.form :as form]))

(defonce server (atom nil))

(defroutes app

  (GET "/" []
    (response
     (h/html
         [:div
          [:h1 "Select an epub file"]
          (form/form-to
           {:enctype "multipart/form-data"}
           [:post "/epub/upload"]
           (form/file-upload "epub")
           (form/submit-button "Send"))])))

  (POST "/epub/upload" req
    (let [file (get-in req [:params "epub"])]
      (-> (redirect "/epub/content")
          (assoc :session file))))

  (GET "/epub/content" {session :session}
    (let [file (:tempfile session)]
      (response
       (-> file
           epub/load-epub
           epub/list-content-as-hiccup
           h/html))))

  (GET "/epub/:page" [page]
    ;; TODO: return the html content of the page
    (response page))

  (route/not-found "<h1>Page not found</h1>"))

(def handler (-> app
                 wrap-stacktrace
                 wrap-session
                 wrap-params
                 wrap-multipart-params))

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
