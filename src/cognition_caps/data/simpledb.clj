;;; DataAccess implementation against Amazon SimpleDB
(ns cognition-caps.data.simpledb
  (:use [cognition-caps.data]
        [clojure.tools.logging])
  (:require [cognition-caps.config :as config]
            [clojure.contrib.string :as str]
            [clojure.stacktrace :as st]
            [cemerick.rummage :as sdb]
            [cemerick.rummage.encoding :as enc]
            [clj-logging-config.log4j :as l]))

(declare annotate-ordered-values unannotate-ordered-values change-key
         dereference-price dereference-sizes marshal-item
         merge-large-descriptions unmarshal-item unmarshal-ids
         split-large-descriptions ensure-tags-set)

(def *items-domain* "items")

(defonce config
  (let [base {:out :console :level :info}]
    (l/set-loggers!
      :root base
      "com.amazonaws"  (assoc base :level :warn)
      "org.mortbay"    (assoc base :level (:app-log-level config/config))
      "cognition-caps" (assoc base :level (:app-log-level config/config)))
    (info "Loggers initialized, creating sdb client")
    (assoc (enc/all-prefixed-config)
           :client (sdb/create-client (get config/config "amazon-access-id")
                                      (get config/config "amazon-access-key")))))

(defn- select-item [queryCount field-name field-value prices sizes]
  (swap! queryCount inc)
  (if-let [result (sdb/query config
                             `{select * from items
                               where (= ~field-name ~field-value)})]
    (unmarshal-item (first result) prices sizes)))

(defrecord SimpleDBAccess []
  DataAccess
  (get-item [this queryCount url-title]
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)
          item (select-item queryCount :url-title url-title prices sizes)]
      (if item
        item
        (do
          (debug (str "No item found for url-title '" url-title "', querying for a name change"))
          (select-item queryCount :old-url-title url-title prices sizes)))))

  (get-items [this queryCount]
    (swap! queryCount inc)
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)]
      (map #(unmarshal-item % prices sizes)
           (sdb/query-all config '{select * from items
                                   where (not-null :display-order)
                                   order-by [:display-order asc]}))))

  (get-items-range [this queryCount begin limit]
    (swap! queryCount inc)
    (let [prices       (.get-prices this queryCount)
          sizes        (.get-sizes this queryCount)
          begin-padded (str (str/repeat (- (get config/config :display-order-len)
                                           (count begin))
                                        "0")
                            begin)
          query        `{select * from items
                         where (>= :display-order ~begin-padded)
                         limit ~limit
                         order-by [:display-order asc]}]
      (map #(unmarshal-item % prices sizes) (sdb/query config query))))

  (get-visible-item-count [this queryCount]
    (swap! queryCount inc)
    (sdb/query config '{select count from items
                        where (not-null :display-order)}))

  (put-items [this queryCount items]
    (println "Persisting" (count items) "items to SimpleDB")
    (swap! queryCount inc)
    (try
      (sdb/batch-put-attrs config *items-domain* (map marshal-item items))
      (catch Exception e (println (st/print-stack-trace e)))))

  (get-sizes [this queryCount]
    (swap! queryCount inc)
    (map unmarshal-ids (sdb/query-all config '{select * from sizes})))

  (get-prices [this queryCount]
    (swap! queryCount inc)
      (map unmarshal-ids (sdb/query-all config '{select * from prices})))

  (update-item [this queryCount id attr-name attr-value]
    (swap! queryCount inc)
    (sdb/put-attrs config *items-domain* {::sdb/id id (keyword attr-name) attr-value})))

(def simpledb (SimpleDBAccess.))

(defn populate-defaults! []
  "Sets up SimpleDB with our basic set of predefined values"
  (sdb/create-domain config "items")
  (sdb/create-domain config "sizes")
  (sdb/batch-put-attrs config "sizes" (map #(change-key % :id ::sdb/id) default-sizes))
  (sdb/create-domain config "prices")
  (sdb/batch-put-attrs config "prices" (map #(change-key % :id ::sdb/id) default-prices)))

(defn- annotate-ordered-values [item]
  "If any of the values in the given map are sequences, prepends each of the
  items in the sequence with a value which will allow the ordering to be
  preserved upon unmarshalling"
  (into {}
    (map (fn [entry]
           ; We're just hardcoding which attribute(s) are ordered
           (if (= :image-urls (key entry))
             [(key entry) (map #(str (format "%02d" %1) "_" %2)
                               (iterate inc 1)
                               (val entry))]
             entry))
         (seq item))))

(defn- unannotate-ordered-values [item]
  "Finds multi-valued attributes which have had an order prefixed and
  reconstitutes them into an ordered sequence"
  (into {}
    (map (fn [entry]
           (let [attr-name (key entry) attr-value (val entry)]
             ; We're just hardcoding which attribute(s) are ordered
             (if (= :image-urls attr-name)
               [attr-name (map #(subs % (inc (.indexOf % "_")))
                               (if (coll? attr-value)
                                 (sort attr-value)
                                 [attr-value])) ]
               entry)))
         (seq item))))

(def *flat-image-prefix* "_img_")

(defn- flatten-image-urls [item]
  "Takes the map of image urls and stores them as top-level attributes"
  (loop [c item
         url-map (:image-urls item)]
    (if (empty? url-map)
      (dissoc c :image-urls)
      (recur (assoc c (keyword (str *flat-image-prefix* (name (key (first url-map))))) (val (first url-map)))
             (rest url-map)))))

(defn- unflatten-image-urls [item]
  "Takes top-level image urls from the map and stores them all under :image-urls"
  (loop [flat-item item new-item {} image-urls {}]
    (let [entry (first flat-item)]
      (if (= entry nil) ; last entry of flattened item
        (assoc new-item :image-urls (into (sorted-map) image-urls))
        (if (.startsWith (name (key entry)) *flat-image-prefix*)
          (recur (rest flat-item)
                 new-item
                 (assoc image-urls
                        (keyword (subs (name (key entry)) (count *flat-image-prefix*)))
                        (val entry)))
          (recur (rest flat-item)
                 (merge new-item entry)
                 image-urls))))))

(defn- ensure-tags-set [m]
  "Ensures that (:tags m) is a set of keywords and not just a single one"
  (if (keyword? (:tags m))
    (assoc m :tags (hash-set (:tags m)))
    m))

(defn- remove-empty-values [item]
  "Removes entries for which the value is nil"
  (loop [k (keys item) i item]
    (if (empty? k)
      i
      (if (get i (first k))
        (recur (rest k) i)
        (recur (rest k) (dissoc i (first k)))))))

(defn marshal-item [item]
  "Preprocesses the given item before persisting to SimpleDB"
  (-> item
    (change-key :id ::sdb/id)
    (remove-empty-values)
    (split-large-descriptions)
    (flatten-image-urls)))

(defn unmarshal-item [item prices sizes]
  "Reconstitutes the given item after reading from SimpleDB"
  (-> item
      (unmarshal-ids)
      (merge-large-descriptions)
      (dereference-price prices)
      (ensure-tags-set)
      (dereference-sizes sizes)
      (unflatten-image-urls)))

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

(defn dereference-price [m prices]
  "Associates the full price map(s) for the given item's price-ids"
  (let [price-ids (:price-ids m)
        price-id-seq (if (coll? price-ids) (seq price-ids) (seq [price-ids]))]
    (assoc m :prices (map (fn [price-id]
                            (some #(if (= price-id (:id %)) %)
                                  prices))
                          price-id-seq))))

(defn dereference-sizes [m sizes]
  "Associates a parsed size map for the given item's encoded size-id:quantity string"
  (if (:item-type-cap (:tags m))
    (let [size-id-qty (map #(str/split #":" %) (:sizes m))
          available-sizes (set (filter #(not (nil? %))
                                       (map #(if (not= 0 (Long/parseLong (second %)))
                                               (Long/parseLong (first %)))
                                            size-id-qty)))
          ; Create a list of size maps applicable to this item
          size-map (reduce #(if (get available-sizes (:id %2)) (cons %2 %1) %1)
                           '()
                           sizes)]
      ; Need to go from a Cons to a list to get the proper order, for some reason
      (assoc m :sizes (into '() size-map)))
    m))

