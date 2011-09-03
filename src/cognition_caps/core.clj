(ns cognition-caps.core
  (:use compojure.core
        ring.adapter.jetty
        [cognition-caps.config :only (config)]
        cognition-caps.handlers)
  (:require [cognition-caps.data :as data]
            [clojure.string :as s]
            [compojure.route :as route]
            [compojure.handler :as handler]))

(defn- redirect [location] {:status 301 :headers {"Location" location}})

;; Routes that redirect requests to the old site's URL scheme
(defroutes redirect-routes
  ; Canonicalize on no trailing slashes
  (GET [":url/", :url #".+"] [url] (redirect url))
  ; Redirect URLs from the old site
  (GET ["/index.php/caps/caps-description/:old-url-title", :old-url-title #".+?"]
    [old-url-title]
    (redirect (str (:cap-url-prefix config) (s/replace old-url-title #"-cap/?$" "")))))

(defroutes all-routes
  (GET "/" {stats :stats} (index stats))
  redirect-routes
  (GET "/caps/:url-title" [url-title & params :as request] (item (:stats request) url-title))
  (route/resources "/")
  (route/not-found "Page not found"))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(def app (-> (handler/site all-routes)
             wrap-stats))

(defn start [port]
  (run-jetty app {:port port}))

(defn -main []
  (start (Integer/parseInt (or (System/getenv "PORT") "3000"))))
