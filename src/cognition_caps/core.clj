(ns cognition-caps.core
  (:use compojure.core
        ring.adapter.jetty
        ring.middleware.reload
        [clojure.contrib.string :only (replace-re)]
        [clojure.tools.logging :only (info)]
        [cognition-caps.config :only (config)])
  (:require [cognition-caps [data :as data] [handlers :as handlers]]
            [clojure.string :as s]
            [compojure.route :as route]
            [compojure.handler :as handler])
  (:import (org.mortbay.jetty NCSARequestLog)
           (org.mortbay.jetty.handler RequestLogHandler)))

(defn- redirect
  ([location] (redirect location 301))
  ([location status] {:status status :headers {"Location" location}}))

;; Routes that redirect requests to the old site's URL scheme
(defroutes redirect-routes
  ; Temporariy redirects to specific files on our old site
           ; jpg jpeg png
  (GET ["/images/:imagepath", :imagepath #"(?i)(?:uploads|cache)/.+\.(?:jpg|jpeg|png)$"] {uri :uri}
       (do
         (info (str "Forwarding request to old site: " uri))
         (redirect (str "http://67.222.57.142" uri) 302)))
  ; Permanent redirects to specific files on our old site
  (GET "/images/favicon(4).ico" [] (redirect "/favicon.ico"))
  ; Redirect URLs from the old site
  (GET "/index.php" [] (redirect "/"))
  (GET "/index.php/caps" [] (redirect "/caps"))
  (GET ["/index.php/caps/caps-description/:old-url-title", :old-url-title #".+?"]
    [old-url-title]
    (redirect (str (:cap-url-prefix config)
                   (-> old-url-title
                       (s/replace #"-cap/?$" "")
                       (s/lower-case)))))
  ; TODO: index.php* redirect to old site?
  ; todo: what about the blog? Have a "coming soon" for that. rss should point to the old RSS feed for now, later redirecting
  ; For remaining requests we will serve here, canonicalize on lowercase and no trailing slashes
  (GET [":url", :url #".+/|.+?\p{Upper}.*"] [url]
    (redirect (->> url (replace-re #"/+$" "") (s/lower-case))))
)

(defroutes all-routes
  (GET "/" [& query-params :as request] (handlers/index (:stats request) query-params))
  redirect-routes
  (GET "/:item-type/:url-title" [item-type url-title & params :as request]
       (cond (= "caps" item-type)  (handlers/cap (:stats request) url-title)
             (= "merch" item-type) (handlers/merch (:stats request) url-title)))
  (GET "/sizing" {stats :stats} (handlers/sizing stats))
  (GET "/faq" {stats :stats} (handlers/faq stats))
  (route/resources "/")
  (ANY "*" {uri :uri} route/not-found (handlers/fourohfour uri)))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(def app (-> (var all-routes)
             (wrap-reload '(cognition-caps.core cognition-caps.handlers))
             handler/site
             wrap-stats))

(defn start [port]
  (run-jetty app {:port port
                  :configurator (fn [server]
                                  (doto server
                                    (.addHandler (doto (RequestLogHandler.)
                                                   (.setRequestLog (NCSARequestLog.))))))
                  }))

(defn -main []
  (start (Integer/parseInt (or (System/getenv "PORT") "3000"))))

