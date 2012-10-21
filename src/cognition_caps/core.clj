(ns cognition-caps.core
  (:use compojure.core
        ring.adapter.jetty
        ring.middleware.reload
        [clojure.contrib.string :only (replace-re)]
        [clojure.tools.logging :only (info)]
        [cognition-caps.config :only (config)]
        [cognition-caps.ring :only (redirect)])
  (:require [cognition-caps [data :as data] [handlers :as handlers] [feed :as feed]]
            [clojure.string :as s]
            [compojure.route :as route]
            [compojure.handler :as handler])
  (:import (org.eclipse.jetty.server NCSARequestLog)
           (org.eclipse.jetty.server.handler AbstractHandler HandlerCollection RequestLogHandler)))

;; Routes that redirect requests to the old site's URL scheme
(defroutes redirect-routes
  ;;; Temporariy redirects to specific files on our old site
  (GET ["/images/:imagepath", :imagepath #"(?i)(?:uploads|cache)/.+\.(?:jpg|jpeg|png)$"] {uri :uri}
       (do
         (info (str "Forwarding request to old site: " uri))
         (redirect (str (:old-site-url config) uri) 302)))

  ;;; Permanent redirects to specific files on our old site
  (GET "/images/favicon(4).ico" [] (redirect "/favicon.ico"))
  ; Redirect URLs from the old site
  (GET ["/:path", :path #"index.php(?:/about)?"] [] (redirect "/"))
  ; Caps or merch categories
  (GET ["/index.php/:path", :path #"(?:caps|merch)"] [path] (redirect (str "/" path)))
  ; Specific item pages
  (GET ["/index.php/:path1/:path2-description/:old-url-title", :path1 #"(?:caps|merch)" :path2 #"(?:caps|merchandise)" :old-url-title #".+?"]
    [old-url-title path1]
    (redirect (str "/" path1 "/"
                   (-> old-url-title
                       (s/replace #"-cap/?|/$" "")
                       (s/lower-case)))))
  ; Specific blog entries
  (GET ["/:path/:old-url-title", :path #"index.php/blog(?:/comments)??" :old-url-title #"[^/]+?"] [old-url-title]
    (redirect (str "/blog/" (s/lower-case old-url-title))))
  ; Paginated blog listings just redirect to main blog page because although ../P0, ../P5, ...
  ; appear consistent in Expression Engine (start most recent, skip by 5*n items), ../P1, ../P2
  ; are inconsistent. So let's not go out of our way to accomodate people bookmarking paginated
  ; blog listings.
  (GET ["/index.php/blog/:blog-page", :blog-page #"P\d+"] [_] (redirect "/blog"))
  ; Thank you page
  (GET "/index.php/thank_you" [_] (redirect "/thanks"))
  ; For remaining requests we will serve here, canonicalize on lowercase and no trailing slashes
  (GET [":url", :url #".+/|.+?\p{Upper}.*"] [url]
    (redirect (->> url (replace-re #"/+$" "") (s/lower-case)))))

(defroutes all-routes
  (GET "/" [& query-params :as request] (handlers/index (:stats request) query-params))
  ; Legacy RSS
  (GET "/index.php/caps/caps.rss" [] (feed/rss-legacy-caps))
  (GET "/index.php/blog/rss_2.0" [] (feed/rss-legacy-blog))
  redirect-routes
  (GET "/:item-type" [item-type & params :as request]
       (cond (= "caps" item-type)  (handlers/caps (:stats request) request)
             (= "merch" item-type) (handlers/merches (:stats request) request)))
  (GET "/:item-type/:url-title" [item-type url-title & params :as request]
       (cond (= "caps" item-type)  (handlers/cap (:stats request) url-title)
             (= "merch" item-type) (handlers/merch (:stats request) url-title)))
  (GET "/sizing" {stats :stats} (handlers/sizing stats))
  (GET "/faq" {stats :stats} (handlers/faq stats))
  (GET "/blog" {stats :stats} (handlers/blog stats))
  (GET "/blog/:url-title" [url-title & params :as request]
       (handlers/blog-entry (:stats request) url-title))
  (GET "/feeds/all-atom.xml" {stats :stats {accept "accept"} :headers}
       (feed/wrap-content-type accept (feed/atom-all stats)))
  (GET "/feeds/store-atom.xml" {stats :stats {accept "accept"} :headers}
       (feed/wrap-content-type accept (feed/atom-store stats)))
  (GET "/feeds/blog-atom.xml" {stats :stats {accept "accept"} :headers}
       (feed/wrap-content-type accept (feed/atom-blog stats)))
  (GET "/thanks" {stats :stats} (handlers/thanks stats))
  (route/resources "/")
  (ANY "*" {uri :uri} (route/not-found (handlers/fourohfour uri))))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(def app (-> (var all-routes)
             (wrap-reload '(cognition-caps.core cognition-caps.handlers cognition-caps.feed))
             handler/site
             wrap-stats))

(defn start [port]
  (run-jetty app {:port port
                  :configurator (fn [server]
                                  (doto server
                                    (.setHandler
                                      (doto (new HandlerCollection)
                                        (.addHandler (.getHandler server))
                                        (.addHandler (doto (new RequestLogHandler)
                                                       (.setRequestLog (NCSARequestLog.))))))))}))

(defn -main []
  (start (Integer/parseInt (or (System/getenv "PORT") "3000"))))

