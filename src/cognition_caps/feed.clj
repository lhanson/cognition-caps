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

(def *feed-entry-count* 10)

(defn atom-all [stats]
  "ALL"
  ; TODO: intersperse the last X entries from the store and from the blog
)

(defn- create-entries [maps title-key contents-key]
  "Returns a collection of com.sun.syndication.feed.atom.Entry from the given maps"
  (map (fn [entry]
         (let [date-added (get-date (:date-added entry))]
           (doto (new Entry)
             (.setTitle (title-key entry))
             (.setId (atom-id-gen entry))
             (.setPublished date-added)
             (.setUpdated   date-added)
             (.setAuthors [(doto (new Person)
                             (.setName (get-in entry [:user :username]))
                             (.setEmail (get-in entry [:user :email])))])
             (.setAlternateLinks [(doto (new Link) (.setHref (str (:url-base config) "/" )))])
             (.setContents [(doto (new Content) (.setType "html") (.setValue (contents-key entry)))]))))
       maps))

(defn- create-feed [entries title-suffix feed-filename]
  "Returns a com.sun.syndication.feed.synd.SyndFeedImpl from the given entries"
  (new SyndFeedImpl
       (doto (new Feed)
         (.setFeedType "atom_1.0")
         (.setGenerator (doto (Generator.)
                          (.setUrl "https://github.com/lhanson/cognition-caps")
                          (.setValue "Cognition Caps Backend")))
         (.setTitle (str "Cognition Caps - " title-suffix))
         (.setSubtitle (doto (Content.)
                         (.setValue "Handmade cycling caps from Madison, WI")
                         (.setType "text")))
         (.setId (str (:url-base config) "/"))
         (.setIcon (str (:url-base config) "/favicon.ico"))
         (.setLogo (str (:url-base config) "/images/top_animation.gif"))
         (.setUpdated (.getPublished (first entries)))
         (.setOtherLinks [(doto (new Link) (.setHref (str (:url-base config) (str "/feeds/" feed-filename))) (.setRel "self"))])
         (.setAlternateLinks [(doto (new Link) (.setHref (:url-base config)))])
         (.setEntries entries))))

(defn atom-store [stats]
  "Returns an Atom feed for store items"
  (let [items (take *feed-entry-count* (data/get-items simpledb (:db-queries stats) :date-added 'desc))
        entries (create-entries items :nom :description)
        feed (create-feed entries "Store" "store-atom.xml")]
    (.. (new SyndFeedOutput) (outputString feed))))

(defn atom-blog [stats]
  (let [entries (take *feed-entry-count* (data/get-blog simpledb (:db-queries stats)))
        feed-entries (create-entries entries :title :body)
        feed (create-feed feed-entries "Blog" "blog-atom.xml")]
    (.. (new SyndFeedOutput) (outputString feed))))

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

