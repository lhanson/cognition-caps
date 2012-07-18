(ns cognition-caps.rss
  (:use [clojure.tools.logging :only (debug)]
        [clojure.java.io :only (file reader)] ; TODO: remove reader
        [cognition-caps.data.simpledb :only (simpledb)])
  (:require [cognition-caps.data :as data])
  (:import (com.sun.syndication.io SyndFeedInput SyndFeedOutput)))

(defn- get-syndfeed [filename]
    "Returns a com.sun.syndication.io.SyndFeed for the given URL"
    (. (SyndFeedInput.) build (reader filename)))

(defn rss-all [stats]
  "ALL"
  ; TODO: intersperse the last X entries from the store and from the blog
)

; First just return legacy RSS feed
(defn rss-store [stats]
  (let [items (data/get-items simpledb (:db-queries stats) :date-added 'desc)]
    (println "got " (count items) "items for RSS"))
  ; Return latest 10 items.
  ; TODO: for atom it's application/atom+xml
;  { :headers {"Content-Type" "application/rss+xml" }
;    :body  (file "resources/caps.rss") }
  ;(let [feed (get-syndfeed "resources/caps.rss")]
  ;  (doseq [entry (.getEntries feed)]
  ;      (println "Type:"(type entry))
  ;      (println "Title:" (.getTitle entry)))
  ;  (.. (SyndFeedOutput.) (outputString feed)))
)
(defn rss-blog [stats]
  "BLOG"
  ; TODO: Return new blog feed
)

(defn rss-legacy-caps []
  "Produces the old RSS feed snapshot for caps"
  { :headers {"Content-Type" "application/rss+xml" }
    :body  (file "resources/caps.rss") })

(defn rss-legacy-blog []
  "Produces the old RSS feed snapshot for the blog"
  { :headers {"Content-Type" "application/rss+xml" }
    :body  (file "resources/blog.rss") })

