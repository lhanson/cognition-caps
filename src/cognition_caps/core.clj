(ns cognition-caps.core
  (:use compojure.core
        ring.adapter.jetty
        [clojure.contrib.string :only (replace-re)]
        [cognition-caps.config :only (config)])
  (:require [cognition-caps [data :as data] [handlers :as handlers]]
            [clojure.string :as s]
            [compojure.route :as route]
            [compojure.handler :as handler]))

(defn- redirect [location] {:status 301 :headers {"Location" location}})

;; Routes that redirect requests to the old site's URL scheme
(defroutes redirect-routes
  ; Canonicalize on lowercase and no trailing slashes
  (GET [":url", :url #".+/|.+?\p{Upper}.*"] [url]
    (redirect (->> url (replace-re #"/+$" "") (s/lower-case))))
  ; Redirect URLs from the old site
  (GET ["/index.php/caps/caps-description/:old-url-title", :old-url-title #".+?"]
    [old-url-title]
    (redirect (str (:cap-url-prefix config)
                   (-> old-url-title
                       (s/replace #"-cap/?$" "")
                       (s/lower-case))))))

(defroutes all-routes
  (GET "/" [& query-params :as request] (handlers/index (:stats request) query-params))
  redirect-routes
  (GET "/:item-type/:url-title" [item-type url-title & params :as request]
       (cond (= "caps" item-type)  (handlers/cap (:stats request) url-title)
             (= "merch" item-type) (handlers/merch (:stats request) url-title)))
  (GET "/sizing" {stats :stats} (handlers/sizing stats))
  (GET "/faq" {stats :stats} (handlers/faq stats))
  (route/resources "/")
  (route/not-found "Page not found"))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(def app (-> all-routes
             handler/site
             wrap-stats))

(defn start [port]
  (run-jetty app {:port port}))

(defn -main []
  (start (Integer/parseInt (or (System/getenv "PORT") "3000"))))
