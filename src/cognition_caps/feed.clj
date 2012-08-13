(ns cognition-caps.feed
  (:use [clojure.tools.logging :only (debug)]
        [clojure.java.io :only (file)]
        [cognition-caps.data.simpledb :only (simpledb)]
        [cognition-caps.config :only (config)])
  (:require [cognition-caps.data :as data]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf])
  (:import (com.sun.syndication.io SyndFeedOutput)
           (com.sun.syndication.feed.synd SyndFeedImpl SyndLinkImpl)
           (com.sun.syndication.feed.atom Feed Generator Link Entry Content Person)))
(declare atom-id-gen get-date wrap-content-type)

(defn atom-all [stats]
  "ALL"
  ; TODO: intersperse the last X entries from the store and from the blog
)

(defn atom-store [stats]
  "Returns an Atom feed for store items"
  (let [items (take 10 (data/get-items simpledb (:db-queries stats) :date-added 'desc))
        entries (map (fn [item]
                       (doto (new Entry)
                         (.setTitle (:nom item))
                         (.setId (atom-id-gen item))
                         (.setPublished (get-date (:date-added item)))
                         (.setUpdated   (get-date (:date-added item)))
                         (.setAuthors [(doto (new Person)
                                         (.setName (get-in item [:user :username]))
                                         (.setEmail (get-in item [:user :email])))])
                         (.setAlternateLinks [(doto (new Link) (.setHref (str (:url-base config) "/" )))])
                         (.setContents [(doto (new Content) (.setType "html") (.setValue (:description item)))])))
                     items)
        feed (new SyndFeedImpl
                  (doto (new Feed)
                    (.setFeedType "atom_1.0")
                    (.setGenerator (doto (Generator.)
                                     (.setUrl "https://github.com/lhanson/cognition-caps")
                                     (.setValue "Cognition Caps Backend")))
                    (.setTitle "Cognition Caps - Store")
                    (.setSubtitle (doto (Content.)
                                    (.setValue "Handmade cycling caps from Madison, WI")
                                    (.setType "text")))
                    (.setId (str (:url-base config) "/"))
                    (.setIcon (str (:url-base config) "/favicon.ico"))
                    (.setLogo (str (:url-base config) "/images/top_animation.gif"))
                    (.setUpdated (get-date (:date-added (first items))))
                    (.setOtherLinks [(doto (new Link) (.setHref (str (:url-base config) "/feeds/store-atom.xml")) (.setRel "self"))])
                    (.setAlternateLinks [(doto (new Link) (.setHref (:url-base config)))])
                    (.setEntries entries)))]
    (.. (new SyndFeedOutput) (outputString feed))))

(defn atom-blog [stats]
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

(defn- atom-id-gen [item]
  "Generates an RFC 4151 tag URI to uniquely identify an Atom entry"
  (str "tag:"
       (.substring (:url-base config) (count "http://")) ; authorityName
       ",2012-07-19:"                                    ; date
       (:url-title item)                                 ; specific
       ":"
       (tf/unparse (tf/formatters :year-month-day) (tc/from-long (* 1000 (:date-added item))))))

(defn- get-date [unix-timestamp]
  "Returns a java.util.Date instance for the given unix timestamp"
  (tc/to-date (tc/from-long (* unix-timestamp 1000))))

(defn wrap-content-type [accept atom-feed]
  "Returns a Ring response with the provided feed and an appropriate
  Content-Type set based on the accept headers"
  { :headers {"Content-Type" "application/atom+xml"}
    :body atom-feed })

