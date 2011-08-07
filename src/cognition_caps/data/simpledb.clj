;;; DataAccess implementation against Amazon SimpleDB
(ns cognition-caps.data.simpledb
  (:use [cognition-caps.data])
  (:require [cognition-caps.config :as config]
            [cemerick.rummage :as sdb]
            [cemerick.rummage.encoding :as enc]))

(def *caps-domain* "items")
(declare change-key split-large-descriptions merge-large-descriptions)

(defonce config
  (do
    (.setLevel (java.util.logging.Logger/getLogger "com.amazonaws")
               java.util.logging.Level/WARNING)
    (assoc enc/keyword-strings
           :client (sdb/create-client (get config/db-config "amazon-access-id")
                                      (get config/db-config "amazon-access-key")))))

(defrecord SimpleDBAccess []
  DataAccess
  (get-caps [this]
            (println "Getting caps from SimpleDB")
            (println "DOMAINS: " + (sdb/list-domains config))
            )
  (put-caps [this caps]
    (let [scan-cap-attrs (fn [cap]
                           ;(println "Checking out cap of type" (type cap) ": " cap)
                           (if (> (count (:description cap)) 1024)
                             (str "Description is too large for cap " (:id cap) ":" (:nom cap) " at " (count (:description cap)))
                             nil))]
      (println "Putting caps to SimpleDB!!!")
      (println "Mapping scan-cap-attrs over" (count caps) "caps of type" (type caps) "of" (type (first caps)))
      (println (filter #(not (nil? %)) (map scan-cap-attrs caps)))
      ;(sdb/batch-put-attrs config *caps-domain* (map #(change-key :id ::sdb/id %) caps))
      )))
(defn make-SimpleDBAccess [] (SimpleDBAccess.))

(defn- long-split [re maxlen s]
  "Splits s on the provided regex returning a lazy sequence of substrings of
  up to maxlen each"
  (if (<= (count s) maxlen)
    (vector s)
    (let [matcher (re-matcher re (.substring s 0 (min (count s) maxlen)))]
      (if (re-find matcher)
        (lazy-seq (cons (.substring s 0 (.end matcher)) 
                        (long-split re maxlen (.substring s (.end matcher)))))
        (throw (IllegalStateException. "Can't split the string into substrings, no regex match found"))))))

(defn split-large-descriptions [m]
  "If the given map has a :description value larger than 1024 bytes, it is
  split into multiple integer-suffixed attributes"
  (if (> (count (:description m)) 1024)
    (dissoc
      (reduce (fn [m- descr]
                (assoc (assoc m- (keyword (str "description_" (:_index m-))) descr)
                       :_index (inc (:_index m-))))
              (assoc m :_index 1)
              ; split up the description on whitespace in chunks of up to 1024
              (long-split #"(?:\S++\s++)+" 1024 (:description m)))
      :description :_index)
    m))

(defn merge-large-descriptions [m]
  "If the given map has multiple integer-suffixed :description attributes, they
   are merged into one :description"
  (let [descr-keys (filter #(re-matches #"description_\d+" (name %)) (keys m))
        sorted-keys (sort-by #(Integer. (.substring (name %) (inc (.indexOf (name %) "_")))) descr-keys)]
    (reduce (fn [m- k] (dissoc m- k))
            (assoc m :description (reduce #(str %1 ((keyword %2) m)) "" sorted-keys))
            sorted-keys)))

(defn- change-key [old-key new-key m]
  (dissoc (assoc m new-key (old-key m) old-key)))
