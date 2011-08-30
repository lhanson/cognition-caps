(ns cognition-caps.core
  (:use compojure.core
        ring.adapter.jetty
        ring.middleware.reload
        cognition-caps.handlers)
  (:require [cognition-caps.data :as data]
            [clojure.string :as s]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :as response]))

;; Routes that redirect requests to the old site's URL scheme
(defroutes redirect-routes
  (GET ["/index.php/caps/caps-description/:old-url-title", :old-url-title #".+?/?"] [old-url-title]
       (response/redirect (str "/caps/" (s/replace old-url-title #"-cap$" "")))))

(defroutes all-routes
  (GET "/" {stats :stats} (index stats))
  (GET "/caps/:url-title" [url-title & params :as request] (item (:stats request) url-title))

  redirect-routes

  (route/resources "/")
  (route/not-found "Page not found"))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(def app (-> (handler/site all-routes)
             wrap-stats
             (wrap-reload '(cognition-caps.core
                            cognition-caps.handlers
                            cognition-caps.data.simpledb))))

(defn start [port]
  (run-jetty app {:port port}))

(defn -main []
  (start (Integer/parseInt (or (System/getenv "PORT") "3000"))))
