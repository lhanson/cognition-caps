(ns cognition-caps.core
  (:use compojure.core
        ring.adapter.jetty
        ring.middleware.reload
        [clojure.contrib.string :only (replace-re)]
        [clojure.tools.logging :only (info)]
        [cognition-caps.config :only (config)])
  (:require [cognition-caps [data :as data] [handlers :as handlers] [rss :as rss]]
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
  (GET ["/images/:imagepath", :imagepath #"(?i)(?:uploads|cache)/.+\.(?:jpg|jpeg|png)$"] {uri :uri}
       (do
         (info (str "Forwarding request to old site: " uri))
         (redirect (str (:old-site-url config) uri) 302)))
  ; Permanent redirects to specific files on our old site
  (GET "/images/favicon(4).ico" [] (redirect "/favicon.ico"))
  ; Redirect URLs from the old site
  (GET ["/:path", :path #"index.php(?:/about)?"] [] (redirect "/"))
  ; Caps or merch categories
  (GET ["/index.php/:path", :path #"(?:caps|merch)"] [path] (redirect (str "/" path)))
  ; Specific item pages
  (GET ["/index.php/:path1/:path2-description/:old-url-title", :path1 #"(?:caps|merch)" :path2 #"(?:caps|merchandise)" :old-url-title #".+?"]
    [old-url-title path1]
    (redirect (str "/" path1 "/" ;(:cap-url-prefix config)
                   (-> old-url-title
                       (s/replace #"-cap/?$" "")
                       (s/lower-case)))))
  ; For remaining requests we will serve here, canonicalize on lowercase and no trailing slashes
  (GET [":url", :url #".+/|.+?\p{Upper}.*"] [url]
    (redirect (->> url (replace-re #"/+$" "") (s/lower-case)))))

(defroutes all-routes
  (GET "/" [& query-params :as request] (handlers/index (:stats request) query-params))
  redirect-routes
  (GET "/:item-type/:url-title" [item-type url-title & params :as request]
       (cond (= "caps" item-type)  (handlers/cap (:stats request) url-title)
             (= "merch" item-type) (handlers/merch (:stats request) url-title)))
  (GET "/sizing" {stats :stats} (handlers/sizing stats))
  (GET "/faq" {stats :stats} (handlers/faq stats))
  (GET "/blog" {stats :stats} (handlers/blog stats))
  (GET "/feeds/all-atom.xml" {stats :stats} (rss/rss-all stats))
  (GET "/feeds/store-atom.xml" {stats :stats} (rss/rss-store stats))
  (GET "/feeds/blog-atom.xml" {stats :stats} (rss/rss-blog stats))
  ; Legacy RSS
  (GET "/index.php/caps/caps.rss" [] (rss/rss-legacy-caps))
  (GET "/index.php/blog/rss_2.0" [] (rss/rss-legacy-blog))
  (route/resources "/")
  (ANY "*" {uri :uri} (route/not-found (handlers/fourohfour uri))))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(def app (-> (var all-routes)
             (wrap-reload '(cognition-caps.core cognition-caps.handlers cognition-caps.rss))
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

