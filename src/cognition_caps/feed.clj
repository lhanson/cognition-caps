(ns cognition-caps.feed
  (:use [clojure.tools.logging :only (debug)]
        [clojure.java.io :only (file)]
        [cognition-caps.config :only (config)])
  (:require [cognition-caps.data :as data]
            [cognition-caps.data.simpledb :as sdb]
            [clj-time.coerce :as tc]
            [clj-time.format :as tf])
  (:import (com.sun.syndication.io SyndFeedOutput)
           (com.sun.syndication.feed.synd SyndFeedImpl SyndLinkImpl)
           (com.sun.syndication.feed.atom Feed Generator Link Entry Content Person)))
(declare atom-id-gen get-date wrap-content-type)

(def *feed-entry-count* 10)

(defn- create-entries [maps]
  "Returns a collection of com.sun.syndication.feed.atom.Entry from the given maps"
  (map (fn [entry]
         (let [date-added (get-date (:date-added entry))
               ; Items have :nom, BlogEntries have :title
               title (or (:nom entry) (:title entry))
               ; Items have :description, BlogEntries have :body
               content (or (:description entry) (:body entry)) ]
           (doto (new Entry)
             (.setTitle title)
             (.setId (atom-id-gen entry))
             (.setPublished date-added)
             (.setUpdated   date-added)
             (.setAuthors [(doto (new Person)
                             (.setName (get-in entry [:user :username]))
                             (.setEmail (get-in entry [:user :email])))])
             (.setAlternateLinks [(doto (new Link) (.setHref (str (:url-base config) "/" )))])
             (.setContents [(doto (new Content) (.setType "html") (.setValue content))]))))
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
  (let [items (take *feed-entry-count* (data/get-items sdb/simpledb (:db-queries stats) :date-added 'desc))
        entries (create-entries items)
        feed (create-feed entries "Store" "store-atom.xml")]
    (.. (new SyndFeedOutput) (outputString feed))))

(defn atom-blog [stats]
  (let [entries (take *feed-entry-count* (data/get-blog sdb/simpledb (:db-queries stats)))
        feed-entries (create-entries entries)
        feed (create-feed feed-entries "Blog" "blog-atom.xml")]
    (.. (new SyndFeedOutput) (outputString feed))))

(defn atom-all [stats]
  (let [items (take *feed-entry-count* (data/get-items sdb/simpledb (:db-queries stats) :date-added 'desc))
        blog-entries (take *feed-entry-count* (data/get-blog sdb/simpledb (:db-queries stats)))
        ; It might be more efficient to loop *feed-entry-count* times and pull the most recent element
        ; of the two collections, but this is simple
        recent (take *feed-entry-count* (sort-by :date-added > (concat items blog-entries)))
        feed-entries (create-entries recent)
        feed (create-feed feed-entries "All Updates" "all-atom.xml")]
    (.. (new SyndFeedOutput) (outputString feed))))

(defn rss-legacy-caps []
  "Produces the old RSS feed snapshot for caps"
  { :headers {"Content-Type" "application/rss+xml" }
    :body  (file "resources/caps-rss.xml") })

(defn rss-legacy-blog []
  "Produces the old RSS feed snapshot for the blog"
  { :headers {"Content-Type" "application/rss+xml" }
    :body  (file "resources/blog-rss.xml") })

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

