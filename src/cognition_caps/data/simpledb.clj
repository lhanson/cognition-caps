;;; DataAccess implementation against Amazon SimpleDB
(ns cognition-caps.data.simpledb
  (:use [clojure.tools.logging]
        [clojure.pprint])
  (:require [cognition-caps.data :as data]
            [clojure.contrib.string :as str]
            [clojure.stacktrace :as st]
            [cemerick.rummage :as sdb]))

(declare change-key dereference-price dereference-sizes dereference-user
         flatten-user flatten-price marshal-item merge-large-field
         unmarshal-item marshal-blog unmarshal-blog unmarshal-ids marshal-user
         unmarshal-user split-large-field ensure-tags-set)

(def *items-domain* "items")
(def *blog-domain* "blog")
(def users-domain "users")

; SimpleDB values can only be 1024 characters long. We'll leave some
; headroom for prefix encoding and other such encoding
(def *max-string-len* 1000)
; The length of the string used to represent display order in the database
; so that we can properly pad query values.
(def *display-order-len* 4)

(defn- select-item [queryCount conf field-name field-value prices sizes users]
  (swap! queryCount inc)
  (if-let [result (sdb/query conf
                             `{select * from items
                               where (= ~field-name ~field-value)})]
    (unmarshal-item (first result) prices sizes users)))

(defn- pad-begin-index [begin]
  (str (str/repeat (- *display-order-len* (count begin)) "0")
       begin))

(defrecord SimpleDBAccess [conf]
  data/DataAccess
  (get-item [this queryCount url-title]
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)
          users (.get-users this queryCount)
          item (select-item queryCount conf :url-title url-title prices sizes users)]
      (if item
        item
        (do
          (debug (str "No item found for url-title '" url-title "', querying for a name change"))
          (select-item queryCount conf :old-url-title url-title prices sizes users)))))

  (get-items [this queryCount]
    (.get-items this queryCount :display-order 'asc))

  (get-items [this queryCount sort-key order]
    (swap! queryCount inc)
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)
          users (.get-users this queryCount)]
      (map #(unmarshal-item % prices sizes users)
           (sdb/query-all conf `{select * from items
                                 where (and (not (= :display-order "-"))
                                            (not-null ~sort-key))
                                 order-by [~sort-key ~order]}))))

  (get-items-range [this queryCount begin limit]
    (swap! queryCount inc)
    (let [prices       (.get-prices this queryCount)
          sizes        (.get-sizes this queryCount)
          users        (.get-users this queryCount)
          begin-padded (pad-begin-index begin)]
      (map #(unmarshal-item % prices sizes users)
           (sdb/query conf `{select * from items
                             where (>= :display-order ~begin-padded)
                             limit ~limit
                             order-by [:display-order asc]}))))

  (get-items-range-filter [this queryCount filter-tag begin limit]
    (swap! queryCount inc)
    (let [prices       (.get-prices this queryCount)
          sizes        (.get-sizes this queryCount)
          users        (.get-users this queryCount)
          begin-padded (pad-begin-index begin)
          query        `{select * from items
                         where (and (>= :display-order ~begin-padded)
                                    (= :tags ~filter-tag))
                         limit ~limit
                         order-by [:display-order asc]}]
      (map #(unmarshal-item % prices sizes users) (sdb/query conf query))))

  (get-visible-item-count [this queryCount filter-tag]
    (swap! queryCount inc)
    (let [query (if filter-tag
                    `{select count from items
                      where (and (not (= :display-order "-"))
                                 (= :tags ~filter-tag))}
                    '{select count from items
                      where (not (= :display-order "-"))})]
    (sdb/query conf query)))

  (get-disabled-items [this queryCount]
    (swap! queryCount inc)
    (let [prices (.get-prices this queryCount)
          sizes (.get-sizes this queryCount)
          users (.get-users this queryCount)]
      (map #(unmarshal-item % prices sizes users)
           (sdb/query-all conf '{select * from items
                                     where (= :display-order "-")
                                     order [:date-added desc]}))))

  (put-items [this queryCount items]
    (debug "Persisting" (count items) "items to SimpleDB")
    (swap! queryCount inc)
    (try
      (sdb/batch-put-attrs conf *items-domain* (map marshal-item items))
      (catch Exception e (println (st/print-stack-trace e)))))

  (get-sizes [this queryCount]
    (swap! queryCount inc)
    (map unmarshal-ids (sdb/query-all conf '{select * from sizes})))

  (get-prices [this queryCount]
    (swap! queryCount inc)
      (map unmarshal-ids (sdb/query-all conf '{select * from prices})))

  (update-item [this queryCount url-title attr-name attr-value]
    (swap! queryCount inc)
    (let [id (first (sdb/query conf `{select id from items where (= :url-title ~url-title)}))]
      (swap! queryCount inc)
      (debug (str "Updating" url-title ", " attr-name "=" "\"" attr-value "\""))
      (sdb/put-attrs conf *items-domain* {::sdb/id id (keyword attr-name) attr-value})))

  (get-blog [this queryCount]
    (swap! queryCount inc)
    (let [users (.get-users this queryCount)]
      (map #(unmarshal-blog % users)
           (sdb/query-all conf '{select * from blog
                                 where (not-null :date-added)
                                 order-by [:date-added desc]}))))

  (put-blog [this queryCount items]
    (swap! queryCount inc)
    (try
      (debug "Persisting" (pprint (map marshal-blog items)))
      (sdb/batch-put-attrs conf *blog-domain* (map marshal-blog items))
      (catch Exception e (println (st/print-stack-trace e)))))

  (get-blog-range [this queryCount begin limit]
    (swap! queryCount inc)
    (let [users (.get-users this queryCount)
          begin-padded (pad-begin-index begin)]
      (map #(unmarshal-blog % users)
           (sdb/query conf `{select * from blog
                             where (>= :display-order ~begin-padded)
                             order-by [:display-order asc]
                             limit ~limit}))))

  (get-blog-entry [this queryCount url-title]
    (swap! queryCount inc)
    (if-let [raw-entry (first (sdb/query conf `{select * from blog
                                                where (= :url-title ~url-title)}))]
      (let [users (.get-users this queryCount)]
        (unmarshal-blog raw-entry users))))

  (get-visible-blog-count [this queryCount]
    (swap! queryCount inc)
    (sdb/query conf '{select count from blog
                      where (not (= :display-order "-"))}))

  (get-users [this queryCount]
    (swap! queryCount inc)
    (map #(unmarshal-user %)
         (sdb/query-all conf `{select * from ~users-domain
                                   where (not-null ::sdb/id)
                                   order-by [::sdb/id asc]})))

  (get-user [this queryCount id]
    (swap! queryCount inc)
    (unmarshal-user (first (sdb/query conf `{select * from ~users-domain
                                                 where (= ::sdb/id ~id)}))))

  (get-user-by [this queryCount attr value]
    "Returns a user where attr=value. Note that this cannot be used to
     query for a user by ::sdb/id, since the query encoding tries to encode
     the special ID key as well."
    (swap! queryCount inc)
    (if (= attr (keyword ":sdb" "id"))
      (throw (IllegalArgumentException. "Can't query by ::sdb/id")))
    (unmarshal-user (first (sdb/query conf `{select * from ~users-domain
                                                 where (= ~attr ~value)}))))

  (put-user [this queryCount user]
    (swap! queryCount inc)
    (try
      (sdb/put-attrs conf users-domain (marshal-user user))
      (catch Exception e (println (st/print-stack-trace e))))))

(defn make-SimpleDBAccess [conf] (SimpleDBAccess. conf))

(defn populate-defaults! [conf]
  "Sets up SimpleDB with our basic set of predefined values"
  (sdb/create-domain conf *items-domain*)
  (sdb/create-domain conf *blog-domain*)
  (sdb/create-domain conf "sizes")
  (sdb/batch-put-attrs conf "sizes" (map #(change-key % :id ::sdb/id) data/default-sizes))
  (sdb/create-domain conf "prices")
  (sdb/batch-put-attrs conf "prices" (map #(change-key % :id ::sdb/id) data/default-prices))
  (sdb/create-domain conf "users")
  (sdb/batch-put-attrs conf "users" (map #(change-key % :id ::sdb/id) data/default-users)))

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

(defn- flatten-sizes [item]
  "Takes the map of sizes and stores them a set of id-qty strings"
  ; Hardcode "unlimited" until we actually track inventory
  (assoc item :sizes (map #(str (:id %) ":-1") (:sizes item))))

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
    (flatten-image-urls)
    (flatten-sizes)
    (flatten-price)
    (flatten-user)))

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
    (flatten-user)
    (split-large-field :body)))

(defn unmarshal-blog [entry users]
  "Reconstitutes the given entry after reading from SimpleDB"
  (-> entry
      (unmarshal-ids)
      (merge-large-field :body)
      (dereference-user users)))

(defn marshal-user [user]
  (change-key user :id ::sdb/id))

(defn unmarshal-user [user]
  (unmarshal-ids user))

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
  (if (> (count (field m)) *max-string-len*)
    (do
      (debug "Splitting" field "on" m)
      (dissoc
        (reduce (fn [m- value]
                  (assoc (assoc m- (keyword (subs (str field "_" (:_index m-)) 1)) value)
                         :_index (inc (:_index m-))))
                (assoc m :_index 1)
                ; split up the description on whitespace in chunks of up to our maximum configured value
                (long-split #"(?:\S++\s++)+" *max-string-len* (field m)))
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
    (dissoc (assoc m :prices (map (fn [price-id]
                                    (some #(if (= price-id (:id %)) %)
                                          prices))
                                  price-id-seq))
            :price-ids)))

(defn flatten-price [m]
  (dissoc (assoc m :price-ids (map :id (:prices m))) :prices))

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

(defn flatten-user [m]
  (dissoc (assoc m :user-id (or (:user-id m) (:id (:user m)))) :user))

