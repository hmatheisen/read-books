(ns user
  (:use [ring.adapter.jetty :refer [run-jetty]]
        [ring.middleware.reload :refer [wrap-reload]]
        [read-books.main :refer [handler server start-server stop-server]]))

(defn start!
  []
  (start-server (wrap-reload #'handler) {:port 8000 :join? false}))

(defn stop!
  []
  (stop-server))

(defn reset-server!
  []
  (when @server
    (stop!))
  (reset! server nil)
  (start!))
