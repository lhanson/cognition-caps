;;; DataAccess implementation against Amazon SimpleDB
(ns cognition-caps.data.simpledb
  (:use [cognition-caps.data]
        [clojure.tools.logging]
        [clojure.pprint])
  (:require [cognition-caps.config :as config]
            [clojure.contrib.string :as str]
            [clojure.stacktrace :as st]
            [cemerick.rummage :as sdb]
            [cemerick.rummage.encoding :as enc]))

(declare annotate-ordered-values unannotate-ordered-values change-key
         dereference-price dereference-sizes dereference-user marshal-item
         merge-large-field unmarshal-item marshal-blog unmarshal-blog
         unmarshal-ids unmarshal-user split-large-field ensure-tags-set)

(def *items-domain* "items")
(def *blog-domain* "blog")

(defonce sdb-conf
  (do
    (info "Creating sdb client")
    (assoc (enc/all-prefixed-config)
           :client (sdb/create-client (get config/config "amazon-access-id")
                                      (get config/config "amazon-access-key")))))

(defn- select-item [queryCount field-name field-value prices sizes users]
  (swap! queryCount inc)
  (if-let [result (sdb/query sdb-conf
                             `{select * from items
                               where (= ~field-name ~field-value)})]
    (unmarshal-item (first result) prices sizes users)))

(defrecord SimpleDBAccess []
  DataAccess
  (get-item [this queryCount url-title]
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)
          users (.get-users this queryCount)
          item (select-item queryCount :url-title url-title prices sizes users)]
      (if item
        item
        (do
          (debug (str "No item found for url-title '" url-title "', querying for a name change"))
          (select-item queryCount :old-url-title url-title prices sizes users)))))

  (get-items [this queryCount]
    (.get-items this queryCount :display-order 'asc))

  (get-items [this queryCount sort-key order]
    (swap! queryCount inc)
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)
          users (.get-users this queryCount)]
      (map #(unmarshal-item % prices sizes users)
           (sdb/query-all sdb-conf `{select * from items
                                     where (and (not-null :display-order) (not-null ~sort-key))
                                     order-by [~sort-key ~order]}))))

  (get-items-range [this queryCount begin limit]
    (swap! queryCount inc)
    (let [prices       (.get-prices this queryCount)
          sizes        (.get-sizes this queryCount)
          users        (.get-users this queryCount)
          begin-padded (str (str/repeat (- (get config/config :display-order-len)
                                           (count begin))
                                        "0")
                            begin)
          query        `{select * from items
                         where (>= :display-order ~begin-padded)
                         limit ~limit
                         order-by [:display-order asc]}]
      (map #(unmarshal-item % prices sizes users) (sdb/query-all sdb-conf query))))

  (get-items-range-filter [this queryCount begin limit filter-tag]
    (swap! queryCount inc)
    (let [prices       (.get-prices this queryCount)
          sizes        (.get-sizes this queryCount)
          users        (.get-users this queryCount)
          begin-padded (str (str/repeat (- (get config/config :display-order-len)
                                           (count begin))
                                        "0")
                            begin)
          query        `{select * from items
                         where (and (>= :display-order ~begin-padded)
                                    (= :tags ~filter-tag))
                         limit ~limit
                         order-by [:display-order asc]}]
      (map #(unmarshal-item % prices sizes users) (sdb/query sdb-conf query))))


  (get-visible-item-count [this queryCount]
    (swap! queryCount inc)
    (sdb/query sdb-conf '{select count from items
                        where (not-null :display-order)}))

  (put-items [this queryCount items]
    (println "Persisting" (count items) "items to SimpleDB")
    (swap! queryCount inc)
    (try
      (sdb/batch-put-attrs sdb-conf *items-domain* (map marshal-item items))
      (catch Exception e (println (st/print-stack-trace e)))))

  (get-sizes [this queryCount]
    (swap! queryCount inc)
    (map unmarshal-ids (sdb/query-all sdb-conf '{select * from sizes})))

  (get-prices [this queryCount]
    (swap! queryCount inc)
      (map unmarshal-ids (sdb/query-all sdb-conf '{select * from prices})))

  (update-item [this queryCount id attr-name attr-value]
    (swap! queryCount inc)
    (sdb/put-attrs sdb-conf *items-domain* {::sdb/id id (keyword attr-name) attr-value}))

  (get-blog [this queryCount]
    (swap! queryCount inc)
    (let [users (.get-users this queryCount)]
      (map #(unmarshal-blog % users)
           (sdb/query-all sdb-conf '{select * from blog
                                       where (not-null :date-added)
                                       order-by [:date-added desc]}))))

  (put-blog [this queryCount items]
    (swap! queryCount inc)
    (try
      (debug "Persisting" (pprint (map marshal-blog items)))
      (sdb/batch-put-attrs sdb-conf *blog-domain* (map marshal-blog items))
      (catch Exception e (println (st/print-stack-trace e)))))

  (get-blog-entry [this queryCount url-title]
    (swap! queryCount inc)
    (if-let [raw-entry (first (sdb/query sdb-conf `{select * from blog
                                                      where (= :url-title ~url-title)}))]
      (let [users (.get-users this queryCount)]
        (unmarshal-blog raw-entry users))))

  (get-users [this queryCount]
    (swap! queryCount inc)
    (map #(unmarshal-user %)
         (sdb/query-all sdb-conf '{select * from users 
                                     where (not-null ::sdb/id)
                                     order-by [::sdb/id asc]}))))

(defonce simpledb (SimpleDBAccess.))

(defn populate-defaults! []
  "Sets up SimpleDB with our basic set of predefined values"
  (sdb/create-domain sdb-conf *items-domain*)
  (sdb/create-domain sdb-conf *blog-domain*)
  (sdb/create-domain sdb-conf "sizes")
  (sdb/batch-put-attrs sdb-conf "sizes" (map #(change-key % :id ::sdb/id) default-sizes))
  (sdb/create-domain sdb-conf "prices")
  (sdb/batch-put-attrs sdb-conf "prices" (map #(change-key % :id ::sdb/id) default-prices))
  (sdb/create-domain sdb-conf "users")
  (sdb/batch-put-attrs sdb-conf "users" (map #(change-key % :id ::sdb/id) default-users)))

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
    (split-large-field :description)
    (flatten-image-urls)))

(defn unmarshal-item [item prices sizes users]
  "Reconstitutes the given item after reading from SimpleDB"
  (-> item
      (unmarshal-ids)
      (merge-large-field :description)
      (dereference-price prices)
      (ensure-tags-set)
      (dereference-sizes sizes)
      (dereference-user users)
      (unflatten-image-urls)))

(defn marshal-blog [entry]
  (-> entry
    (change-key :id ::sdb/id)
    (split-large-field :body)))

(defn unmarshal-blog [entry users]
  "Reconstitutes the given entry after reading from SimpleDB"
  (-> entry
      (unmarshal-ids)
      (merge-large-field :body)
      (dereference-user users)))

(defn unmarshal-user [user]
  (-> user
    (unmarshal-ids)))

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

(defn split-large-field [m field]
  "If the given map has a :value for `field` larger than the max, it is
  split into multiple integer-suffixed attributes. Assumes that `field` is a
  keyword."
  (if (> (count (field m)) (:max-string-len config/config))
    (do
      (debug "Splitting" field "on" m)
      (dissoc
        (reduce (fn [m- value]
                  (assoc (assoc m- (keyword (subs (str field "_" (:_index m-)) 1)) value)
                         :_index (inc (:_index m-))))
                (assoc m :_index 1)
                ; split up the description on whitespace in chunks of up to our maximum configured value
                (long-split #"(?:\S++\s++)+" (:max-string-len config/config) (field m)))
        field :_index))
    m))

(defn merge-large-field [m field]
  "If the given map has multiple integer-suffixed :'field' attributes, they
   are merged into one :'field'"
  (let [field-keys (filter #(re-matches (re-pattern (str field "_\\d+")) (str %)) (keys m))
        sorted-keys (sort-by #(Integer. (.substring (name %) (inc (.indexOf (name %) "_")))) field-keys)]
    (if (empty? sorted-keys)
      m
      (reduce (fn [m- k] (dissoc m- k))
            (assoc m field (reduce #(str %1 ((keyword %2) m)) "" sorted-keys))
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

(defn dereference-user [m users]
  "Associates a user based on :user-id in m"
  (dissoc (assoc m :user (first (filter #(= (:user-id m) (:id %)) users))) :user-id))

