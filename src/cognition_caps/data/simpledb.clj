;;; DataAccess implementation against Amazon SimpleDB
(ns cognition-caps.data.simpledb
  (:use [cognition-caps.data]
        [clojure.contrib.string :only (split)]
        [clojure.tools.logging])
  (:require [cognition-caps.config :as config]
            [cemerick.rummage :as sdb]
            [cemerick.rummage.encoding :as enc]
            [clj-logging-config.log4j :as l]))

(declare annotate-ordered-values unannotate-ordered-values change-key
         dereference-price dereference-sizes marshal-cap
         merge-large-descriptions unmarshal-cap unmarshal-ids
         split-large-descriptions string-tags-to-keywords)

(def *caps-domain* "items")

(defonce config
  (let [base {:out :console :level :info}]
    (l/set-loggers!
      :root base
      "com.amazonaws"  (assoc base :level :warn)
      "org.mortbay"    (assoc base :level (:app-log-level config/config))
      "cognition-caps" (assoc base :level (:app-log-level config/config)))
    (info "Loggers initialized, creating sdb client")
    (assoc enc/keyword-strings
           :client (sdb/create-client (get config/config "amazon-access-id")
                                      (get config/config "amazon-access-key")))))

(defn- select-cap [queryCount field-name field-value prices sizes]
  (swap! queryCount inc)
  (if-let [result (sdb/query config
                             `{select * from items
                               where (= ~field-name ~field-value)})]
    (unmarshal-cap (first result) prices sizes)))

(defrecord SimpleDBAccess []
  DataAccess
  (get-cap [this queryCount url-title]
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)
          cap (select-cap queryCount :url-title url-title prices sizes)]
      (if cap
        cap
        (do
          (debug (str "No cap found for url-title '" url-title "', querying for a name change"))
          (select-cap queryCount :old-url-title url-title prices sizes)))))

  (get-caps [this queryCount]
    (swap! queryCount inc)
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)]
      (map #(unmarshal-cap % prices sizes)
           (sdb/query-all config '{select * from items
                                   where (not-null :display-order)
                                   order-by [:display-order desc]}))))

  (put-caps [this queryCount caps]
    (println "Persisting" (count caps) "caps to SimpleDB")
    (swap! queryCount inc)
    (sdb/batch-put-attrs config *caps-domain* (map marshal-cap caps)))

  (get-sizes [this queryCount]
    (swap! queryCount inc)
    (map unmarshal-ids (sdb/query-all config '{select * from sizes})))

  (get-prices [this queryCount]
    (swap! queryCount inc)
    (map unmarshal-ids (sdb/query-all config '{select * from prices}))))

(def simpledb (SimpleDBAccess.))

(defn populate-defaults! []
  "Sets up SimpleDB with our basic set of predefined values"
  (sdb/create-domain config "items")
  (sdb/create-domain config "sizes")
  (sdb/batch-put-attrs config "sizes" (map #(change-key % :id ::sdb/id) default-sizes))
  (sdb/create-domain config "prices")
  (sdb/batch-put-attrs config "prices" (map #(change-key % :id ::sdb/id) default-prices)))

(defn- annotate-ordered-values [cap]
  "If any of the values in the given map are sequences, prepends each of the
  items in the sequence with a value which will allow the ordering to be
  preserved upon unmarshalling"
  (into {}
    (map (fn [entry]
        (if (seq? (val entry))
          [(key entry) (map #(str (format "%02d" %1) "_" %2)
                            (iterate inc 1)
                            (val entry))]
          entry))
      (seq cap))))

(defn- unannotate-ordered-values [cap]
  "Finds multi-valued attributes which have had an order prefixed and
  reconstitutes them into an ordered sequence"
  (into {}
    (map (fn [entry]
        (if (seq? (val entry))
          [(key entry) (map #(subs % (inc (.indexOf % "_"))) (sort (val entry)))]
          entry))
      (seq cap))))

(defn marshal-cap [cap]
  "Preprocesses the given cap before persisting to SimpleDB"
  (-> cap
      (change-key :id ::sdb/id)
      (split-large-descriptions)
      (annotate-ordered-values)))

(defn unmarshal-cap [cap prices sizes]
  "Reconstitutes the given cap after reading from SimpleDB"
  (-> cap
      (unmarshal-ids)
      (merge-large-descriptions)
      (string-tags-to-keywords)
      (dereference-price prices)
      (dereference-sizes sizes)
      (unannotate-ordered-values)))

(defn- unmarshal-ids [m]
  (change-key m ::sdb/id :id))

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

(defn- change-key [m old-key new-key]
  (dissoc (assoc m new-key (old-key m)) old-key))

(defn string-tags-to-keywords [m]
  (let [t (:tags m)
        tags (if (string? t) (hash-set t) t)]
    (-> m
      (dissoc :tags)
      (assoc :tags (set (map #(if (= \: (.charAt % 0)) (keyword (.substring % 1)) %)
                             tags))))))
(defn dereference-price [m prices]
  "Associates the full price map for the given cap's price-id"
  (assoc m :price (some #(if (= (:price-id m) (:id %)) %) prices)))

(defn dereference-sizes [m sizes]
  "Associates the a parsed size map for the given cap's encoded size-id:quantity string"
  (let [size-id-qty (map #(split #":" %) (:sizes m))
        available-sizes (filter #(not (nil? %))
                                (map #(if (not= 0 (Integer/parseInt (second %)))
                                          (first %))
                                     size-id-qty))
        sorted-sizes (apply sorted-set available-sizes)
        ; Create a list of size maps applicable to this cap
        size-map (reduce #(if (get sorted-sizes (:id %2)) (concat %1 (list %2)) %1)
                         '() sizes)]
    (assoc m :sizes size-map)))

