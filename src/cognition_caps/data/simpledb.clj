;;; DataAccess implementation against Amazon SimpleDB
(ns cognition-caps.data.simpledb
  (:use [cognition-caps.data])
  (:require [cognition-caps.config :as config]
            [cemerick.rummage :as sdb]
            [cemerick.rummage.encoding :as enc]))

(declare change-key marshal-cap merge-large-descriptions unmarshal-cap
         split-large-descriptions string-tags-to-keywords)

(def *caps-domain* "items")

(defonce config
  (do
    (.setLevel (java.util.logging.Logger/getLogger "com.amazonaws")
               java.util.logging.Level/WARNING)
    (assoc enc/keyword-strings
           :client (sdb/create-client (get config/db-config "amazon-access-id")
                                      (get config/db-config "amazon-access-key")))))

(defrecord SimpleDBAccess []
  DataAccess
  (get-cap [this queryCount url-title]
            (if queryCount (swap! queryCount inc))
           (if-let [result (sdb/query config '{select * from items
                                               where (= :url-title url-title)})]
             (unmarshal-cap result)
             ;TODO: what if we have to check old-url-title?
             ))
  (get-caps [this querycount]
            (if querycount (swap! querycount inc))
            (map unmarshal-cap (sdb/query-all config '{select * from items
                                                       where (not-null :display-order)
                                                       order-by [:display-order desc]})))
  (put-caps [this caps]
      (println "Persisting" (count caps) "caps to SimpleDB")
      (sdb/batch-put-attrs config *caps-domain* (map marshal-cap caps)))
  (get-sizes [this]
             (sdb/query-all config '{select * from sizes})))
(def simpledb (SimpleDBAccess.))

(defn populate-defaults! []
  "Sets up SimpleDB with our basic set of predefined values"
  (sdb/create-domain config "items")
  (sdb/create-domain config "sizes")
  (sdb/batch-put-attrs config "sizes" [{::sdb/id "1" :nom "Small-ish"}
                                       {::sdb/id "2" :nom "One Size Fits Most"}
                                       {::sdb/id "3" :nom "Large"}]))

(defn- marshal-cap [cap]
  "Preprocesses the given cap before persisting to SimpleDB"
  (split-large-descriptions (change-key :id ::sdb/id cap)))

(defn- unmarshal-cap [cap]
  "Reconstitutes the given cap after reading from SimpleDB"
  (->> cap
       (change-key ::sdb/id :id)
       (merge-large-descriptions)
       (string-tags-to-keywords)))

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
    (if (empty? sorted-keys)
      m
      (reduce (fn [m- k] (dissoc m- k))
            (assoc m :description (reduce #(str %1 ((keyword %2) m)) "" sorted-keys))
            sorted-keys))))

(defn- change-key [old-key new-key m]
  (dissoc (assoc m new-key (old-key m) old-key)))

(defn string-tags-to-keywords [m]
  (let [t (:tags m)
        tags (if (string? t) (hash-set t) t)]
    (-> m
      (dissoc :tags)
      (assoc :tags (set (map #(if (= \: (.charAt % 0)) (keyword (.substring % 1)) %)
                             tags))))))
