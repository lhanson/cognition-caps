(ns cognition-caps.core
  (:use compojure.core
        ring.middleware.reload
        [ring.middleware.session :only (wrap-session)]
        [ring.middleware.flash :only (wrap-flash)]
        [clojure.contrib.string :only (replace-re)]
        [clojure.tools.logging :only (info)]
        [cognition-caps.ring :only (redirect)])
  (:require [cognition-caps [data :as data] [handlers :as handlers] [feed :as feed] [config :as c] [dates :as dates] [ring-jetty-adapter :as jetty]]
            [clojure.string :as s]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :as ring-response]))

;; Routes that redirect requests to the old site's URL scheme
(defroutes redirect-routes
  ;;; Temporariy redirects to specific files on our old site
  (GET ["/images/:imagepath", :imagepath #"(?i)(?:uploads|cache)/.+\.(?:jpg|jpeg|png)$"] {uri :uri}
       (let [new-url (str (:old-site-url c/config) uri)]
         (info (str "Forwarding request to old site: " new-url))
         (redirect new-url 302)))

  ;;; Permanent redirects to specific files on our old site
  (GET "/images/favicon(4).ico" [] (redirect "/favicon.ico"))
  ; There should be no reason anyone's hitting the old ExpressionEngine member pages unless it's
  ; a bot looking for an exploit. 404 them without even sending a body.
  (ANY ["/index.php:path" :path #"(?:/member/register)?"] {{path :path} :params query-string :query-string}
       (if (or (and path         (.contains (s/lower-case path)         "member/register"))
               (and query-string (.contains (s/lower-case query-string) "member/register")))
         (ring-response/not-found nil)))
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
  ; Paginated blog listings just redirect to main blog page because although ../P0, ../P5, ...
  ; appear consistent in Expression Engine (start most recent, skip by 5*n items), ../P1, ../P2
  ; are inconsistent. So let's not go out of our way to accomodate people bookmarking paginated
  ; blog listings.
  (GET ["/index.php/blog/:blog-page", :blog-page #"(?i)P\d+"] [_] (redirect "/blog"))
  ; Specific blog entries
  (GET ["/:path/:old-url-title", :path #"index.php/blog(?:/comments)??" :old-url-title #"[^/]+?"] [old-url-title]
    (redirect (str "/blog/" (s/lower-case old-url-title))))
  ; Thank you page
  (GET "/index.php/thank_you" [_] (redirect "/thanks"))
  ; For remaining requests we will serve here, canonicalize on lowercase and no trailing slashes
  (GET [":url", :url #".+/|.+?\p{Upper}.*"] [url]
    (redirect (->> url (replace-re #"/+$" "") (s/lower-case)))))

(defmacro redef
  "Redefine an existing value, keeping the metadata intact."
  [name value]
  `(let [m# (meta #'~name)
         v# (def ~name ~value)]
     (alter-meta! v# merge m#)
     v#))

(defmacro decorate
  "Wrap a function in one or more decorators."
  [func & decorators]
  `(redef ~func (-> ~func ~@decorators)))

(defmacro decorate-with
  "Wrap multiple functions in a single decorator."
  [decorator & funcs]
  `(do ~@(for [f funcs]
          `(redef ~f (~decorator ~f)))))

(defn- with-header [handler header value]
  (fn [request]
    (when-let [response (handler request)]
      (assoc-in response [:headers header] value))))

(defroutes resource-routes
  (route/resources "/"))

(decorate resource-routes (with-header "Expires" (dates/next-year)))

(defroutes all-routes
  (GET "/" [& params :as request] (handlers/index (:stats request) params))
  ; Legacy RSS
  (GET "/index.php/caps/caps.rss" [] (feed/rss-legacy-caps))
  (GET "/index.php/blog/rss_2.0" [] (feed/rss-legacy-blog))
  redirect-routes
  (GET "/:item-type" [item-type & params :as request]
       (cond (= "caps" item-type)  (handlers/caps (:stats request) params)
             (= "merch" item-type) (handlers/merches (:stats request) params)))
  (GET "/:item-type/:url-title" [item-type url-title & params :as request]
       (cond (= "caps" item-type)  (handlers/cap (:stats request) url-title)
             (= "merch" item-type) (handlers/merch (:stats request) url-title)))
  (PUT "/:item-type/:url-title/:field" [item-type url-title field & params :as request]
       (handlers/update-item item-type url-title field params request))
  (GET "/sizing" {stats :stats} (handlers/sizing stats))
  (GET "/faq" {stats :stats} (handlers/faq stats))
  (GET "/blog" [& params :as request] (handlers/blog (:stats request) params))
  (GET "/blog/:url-title" [url-title & params :as request]
       (handlers/blog-entry (:stats request) url-title))
  (GET "/feeds/all-atom.xml" {stats :stats {accept "accept"} :headers}
       (feed/wrap-content-type accept (feed/atom-all stats)))
  (GET "/feeds/store-atom.xml" {stats :stats {accept "accept"} :headers}
       (feed/wrap-content-type accept (feed/atom-store stats)))
  (GET "/feeds/blog-atom.xml" {stats :stats {accept "accept"} :headers}
       (feed/wrap-content-type accept (feed/atom-blog stats)))
  (GET "/thanks" {stats :stats} (handlers/thanks stats))
  (GET "/skullbong" [:as request] (handlers/admin request (= "invalid-login" (:query-string request))))
  (POST "/skullbong/login" [& params :as request] (handlers/admin-login params request))
  (POST "/skullbong/logout" [:as request] (handlers/admin-logout request))
  resource-routes
  (ANY "*" {uri :uri} (route/not-found (handlers/fourohfour uri))))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(defn- wrap-reload-if-dev [routes]
  (if (:dev-mode c/config)
    (wrap-reload routes '(cognition-caps.core cognition-caps.handlers cognition-caps.feed cognition-caps.simpledb))
    routes))

(def app (-> (var all-routes)
             (wrap-reload-if-dev)
             handler/api
             (wrap-flash)
             (wrap-session)
             wrap-stats))

(defn -main []
  (jetty/run-jetty app { :port (Integer/parseInt (or (System/getenv "PORT") "3000")) }))

