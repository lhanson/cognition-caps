;;; DataAccess implementation against the MySQL ExpressionEngine database from
;;; the old version of the site. This is purely for the initial data migration;
;;; MySQL won't be used on Heroku.
(ns cognition-caps.data.mysql
  (:use [cognition-caps.data]
        [clj-time.core :only (date-time)]
        [clj-time.coerce :only (to-string)]
        [clojure.contrib.str-utils :only (re-split)])
  (:require [cognition-caps.config :as config]
            [clojure.string :as s]
            [clojure.contrib.string :as cs]
            [clojure.contrib.sql :as sql]))

(declare get-cap-rows get-cap-count get-price-map mapcap)
(def *caps-weblog-id* 3)
(def *image-url-prefix* "http://wearcognition.com/images/uploads/")

(defrecord MySQLAccess []
  DataAccess
  (get-cap [this queryCount url-title]
    (throw (UnsupportedOperationException.
             "Looking up a single cap from ExpressionEngine is not supported")))
  (get-caps   [this queryCount]
    (map #(mapcap queryCount %) (get-cap-rows queryCount)))
  (put-caps   [this caps queryCount]
    (throw (UnsupportedOperationException.
             "Writing to ExpressionEngine is not supported")))
  (get-sizes  [this queryCount]
    (throw (UnsupportedOperationException.
             "Not yet implemented since we're not using ExpressionEngine sizing")))
  (get-prices [this queryCount]
    (throw (UnsupportedOperationException.
             "Not yet implemented since we're not using ExpressionEngine pricing")))
  (update-cap [this queryCount id attr-name attr-value]
    (throw (UnsupportedOperationException.
             "Writing to ExpressionEngine is not supported"))))
(defn make-MySQLAccess [] (MySQLAccess.))

(defonce db
  (let [db-host (get config/config "mysql-host")
        db-port (get config/config "mysql-port")
        db-name (get config/config "mysql-name")]
    {:classname "com.mysql.jdbc.Driver"
     :subprotocol "mysql"
     :subname (str "//" db-host ":" db-port "/" db-name)
     :user (get config/config "mysql-user")
     :password (get config/config "mysql-pass")}))

(defn- parse-size-string [size-str]
  "Parses a string possibly containing multiple size IDs and returns a Long
   version of the first one"
  (let [id (first (filter #(not (empty? %))
                          (re-split #"\W+" size-str)))]
    (if id (Long/parseLong id))))

(defn- get-price-id [price]
  "Finds the price ID for the given price from our default prices"
  (let [price-record (some #(if (= price (:price %)) %) default-prices)]
    (:id price-record)))

(defn- get-cap-rows [queryCount]
  (let [query (str "SELECT t.entry_id AS \"id\", t.title AS \"nom\",
                           t.url_title AS \"url-title\", d.field_id_4 AS \"description\",
                           t.year, t.month, t.day, d.field_id_5 AS \"sizes\",
                           d.field_id_8 AS \"image1\", d.field_id_18 AS \"image2\",
                           d.field_id_19 AS \"image3\", d.field_id_20 AS \"image4\",
                           d.field_id_30 AS \"display-order\",
                           t.author_id AS \"user-id\", t.status
                    FROM exp_weblog_titles t
                    JOIN exp_weblog_data d ON t.entry_id = d.entry_id
                    WHERE t.weblog_id =  '" *caps-weblog-id* "'
                    ORDER BY `display-order` DESC")]
    (sql/with-connection db
      (sql/with-query-results rs [query]
        (let [ee-price-map (get-price-map queryCount)]
          (doall (map #(assoc % :price-id (get-price-id
                                            (get ee-price-map (parse-size-string (:sizes %)))))
                      (vec rs))))))))

(defn- select-single-result [query]
  "For a SELECT statement yielding a single value, returns that result"
  (sql/with-query-results rs [query]
    (assert (= 1 (count rs)))
    (val (ffirst rs))))

(defn- get-cap-count []
  "Returns the total number of caps"
  (sql/with-connection db
    (let [hats-weblog-id (select-single-result "select weblog_id from exp_weblogs where blog_name='hats'")]
      (select-single-result (str "select count(*) from exp_weblog_data where weblog_id='" hats-weblog-id "'")))))

(defn get-price-map [queryCount]
  "Looks up a map of size-ids to prices"
  (let [query (str "SELECT r.rel_id, d.field_id_9 AS \"price\"
                    FROM exp_weblog_data d
                    JOIN exp_relationships r ON d.entry_id = r.rel_child_id")]
    (sql/with-connection db
      (sql/with-query-results rs [query]
        (if queryCount (swap! queryCount inc))
        (doall (reduce #(assoc %1 (:rel_id %2) (:price %2)) {} rs))))))

(defn mapcap [queryCount capmap]
  "Does a little massaging of the data from the SQL database and creates a Cap"
  (let [{:keys [year month day image1 image2 image3 image4]} capmap
        nom (s/replace (:nom capmap) #"(?i)\s*cap\s*$" "")
        date-added (apply date-time (map #(Integer. %) [year month day]))
        images (if (or image1 image2 image3 image4)
                 (map #(cs/trim %) (filter #(not (s/blank? %)) (vector image1 image2 image3 image4))))
        check-price-id (fn [c]
                         (if (:price-id c) c
                             (do
                               (println (str "WARNING: no price ID for cap " (:id c) ": " (:nom c)
                                             ", defaulting to base cap price"))
                               (assoc c :price-id "2"))))
        map-images (fn [image-urls]
                     (loop [m {}
                            idx 0
                            urls (if (seq? image-urls) image-urls [image-urls])]
                       (if (empty? urls)
                          m
                         (recur (assoc m
                                       (keyword (str "main-" idx))
                                       (str "http://wearcognition.com/images/uploads/" (first urls)))
                                (inc idx)
                                (rest urls)))))
        cap (make-Cap (-> capmap
                          (assoc :nom nom)
                          (assoc :url-title (url-title nom))
                          (assoc :description (cs/trim (:description capmap)))
                          (assoc :date-added (to-string date-added))
                          (assoc :image-urls (map-images images))
                          (assoc :tags (hash-set :item-type-cap))
                          (check-price-id)))]
    (if (not= (:url-title capmap) (:url-title cap))
      (do
        (println (str "WARNING: existing URL title '" (:url-title capmap) "' "
                      "will now be '" (:url-title cap) "'"))
        ; Store the old URL title so we can redirect requests to the new one
        (assoc cap :old-url-title (:url-title capmap)))
      cap)))
