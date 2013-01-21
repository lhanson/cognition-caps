;;; DataAccess implementation against the MySQL ExpressionEngine database from
;;; the old version of the site. This is purely for the initial data migration;
;;; MySQL won't be used on Heroku.
(ns cognition-caps.data.mysql
  (:use [cognition-caps.data]
        [clojure.contrib.str-utils :only (re-split)]
        [clojure.contrib.string :only (split-lines)])
  (:require [cognition-caps.config :as config]
            [clojure.string :as s]
            [clojure.contrib [string :as cs] [sql :as sql]]))

(declare get-cap-rows get-merch-rows get-blog-rows get-item-count get-price-map
         mapitem map-blog-entry map-display-orders)
(def *blog-weblog-id* 2)
(def *caps-weblog-id* 3)
(def *merch-weblog-id* 4)

(defrecord MySQLAccess []
  DataAccess
  (get-item [this queryCount url-title]
    (throw (UnsupportedOperationException.
             "Looking up a single item from ExpressionEngine is not supported")))
  (get-items   [this queryCount]
    (map #(mapitem queryCount %)
         (map-display-orders (concat (get-cap-rows queryCount)
                                     (get-merch-rows queryCount)))))
  (get-items   [this queryCount sort-key order]
    (throw (UnsupportedOperationException.
             "Custom-sorted queries to ExpressionEngine are not supported")))
  (get-items-range [this queryCount begin limit]
    (throw (UnsupportedOperationException.
             "Paginated queries to ExpressionEngine are not supported")))
  (get-items-range-filter [this queryCount filter-tag begin limit]
    (throw (UnsupportedOperationException.
             "Paginated queries to ExpressionEngine are not supported")))
  (get-visible-item-count [this queryCount filter-tag]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine")))
  (get-disabled-items [this queryCount]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine")))
  (put-items   [this items queryCount]
    (throw (UnsupportedOperationException.
             "Writing to ExpressionEngine is not supported")))
  (get-sizes  [this queryCount]
    (throw (UnsupportedOperationException.
             "Not yet implemented since we're not using ExpressionEngine sizing")))
  (get-prices [this queryCount]
    (throw (UnsupportedOperationException.
             "Not yet implemented since we're not using ExpressionEngine pricing")))
  (update-item [this queryCount id attr-name attr-value]
    (throw (UnsupportedOperationException.
             "Writing to ExpressionEngine is not supported")))
  (get-blog [this queryCount]
    (map #(map-blog-entry queryCount %) (get-blog-rows queryCount)))
  (put-blog [this queryCount items]
    (throw (UnsupportedOperationException.
             "Writing to ExpressionEngine is not supported")))
  (get-blog-range [this queryCount begin limit]
    (throw (UnsupportedOperationException.
             "Paginated queries to ExpressionEngine are not supported")))
  (get-blog-entry [this queryCount url-title]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine")))
  (get-visible-blog-count [this queryCount]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine")))
  (get-users [this queryCount]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine")))
  (get-user [this queryCount id]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine")))
  (get-user-by [this queryCount attr value]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine")))
  (put-user [this queryCount user]
    (throw (UnsupportedOperationException.
             "Not implemented for ExpressionEngine"))))

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
  (:id (some #(if (= price (:price %)) %) default-prices)))

(defn- get-cap-rows [queryCount]
  (let [query (str "SELECT t.entry_id AS \"id\", t.title AS \"nom\",
                           t.url_title AS \"url-title\", d.field_id_4 AS \"description\",
                           t.entry_date AS \"entry-date\", d.field_id_5 AS \"sizes\",
                           d.field_id_8 AS \"image1\", d.field_id_18 AS \"image2\",
                           d.field_id_19 AS \"image3\", d.field_id_20 AS \"image4\",
                           d.field_id_30 AS \"display-order\",
                           t.author_id AS \"user-id\", t.status, t.weblog_id AS \"weblog-id\"
                    FROM exp_weblog_titles t
                    JOIN exp_weblog_data d ON t.entry_id = d.entry_id
                    WHERE t.weblog_id = '" *caps-weblog-id* "'
                    ORDER BY `display-order` DESC")]
    (sql/with-connection db
      (sql/with-query-results rs [query]
        (let [ee-price-map (get-price-map queryCount)]
          (doall (map (fn [%]
                        (-> %
                         (assoc :price-ids (get-price-id (get ee-price-map (parse-size-string (:sizes %)))))
                         ; Now overlay our default sizing here in size-id:qty strings
                         (assoc :sizes (map #(str (:id %) ":-1") default-sizes))))
                       (vec rs))))))))

(defn- get-merch-rows [queryCount]
  (let [query (str "SELECT t.entry_id AS \"id\", t.title AS \"nom\",
                           t.url_title AS \"url-title\", d.field_id_25 AS \"description\",
                           t.entry_date AS \"entry-date\",
                           d.field_id_21 AS \"image1\",
                           d.field_id_31 AS \"display-order\",
                           t.author_id AS \"user-id\", t.status, t.weblog_id
                    FROM exp_weblog_titles t
                    JOIN exp_weblog_data d ON t.entry_id = d.entry_id
                    WHERE t.weblog_id = '" *merch-weblog-id* "'
                    ORDER BY `display-order` DESC")]
    (sql/with-connection db
      (sql/with-query-results rs [query]
        (let [ee-price-map (get-price-map queryCount)]
          ; Don't bother mapping prices since we'll just assign to the defaults in the new database
          (doall (map #(assoc % :price-ids (doall (range 9 13)))
                      (vec rs))))))))

(defn- get-blog-rows [queryCount]
  (let [query (str "SELECT t.entry_id AS \"id\", t.title AS \"title\", t.url_title AS \"url-title\",
                           t.entry_date AS \"entry-date\", t.author_id AS \"user-id\", t.status,
                           d.field_id_10 AS \"body\", d.field_id_11 AS \"image-url\"
                    FROM exp_weblog_titles t
                    JOIN exp_weblog_data d ON t.entry_id = d.entry_id
                    WHERE t.weblog_id = '" *blog-weblog-id* "'
                    ORDER BY `entry-date` DESC")]
    (sql/with-connection db
      (sql/with-query-results rs [query] (vec rs)))))

(defn- select-single-result [query]
  "For a SELECT statement yielding a single value, returns that result"
  (sql/with-query-results rs [query]
    (assert (= 1 (count rs)))
    (val (ffirst rs))))

(defn- get-item-count []
  "Returns the total number of items"
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

(defn- wrap-paragraphs [text]
  "Converts newline-delimited text into <p> blocks"
  (let [paragraphs (filter #(not (empty? %)) (split-lines text))]
    (reduce str (map #(str "<p>" % "</p>") paragraphs))))

(defn mapitem [queryCount itemmap]
  "Does a little massaging of the data from the SQL database and creates an Item"
  (let [{:keys [entry-date image1 image2 image3 image4]} itemmap
        nom (s/replace (:nom itemmap) #"(?i)\s*cap\s*$" "")
        images (if (or image1 image2 image3 image4)
                 (map #(cs/trim %) (filter #(not (s/blank? %)) (vector image1 image2 image3 image4))))
        check-price-ids (fn [c]
                         (if (:price-ids c) c
                             (do
                               (println (str "WARNING: no price ID for item " (:id c) ": " (:nom c)
                                             ", defaulting to base price"))
                               (if (:item-type-cap (:tags c))
                                 (assoc c :price-ids "2")
                                 (assoc c :price-ids (map str (range 9 13)))))))
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
        item (make-Item (-> itemmap
                          (assoc :nom nom)
                          (assoc :user-id (dec (:user-id itemmap))) ; Our hardcoded users line up this way
                          (assoc :url-title (url-title nom))
                          (assoc :description (wrap-paragraphs (cs/trim (:description itemmap))))
                          (assoc :date-added entry-date)
                          (assoc :image-urls (map-images images))
                          (assoc :tags (if (= *caps-weblog-id* (:weblog-id itemmap))
                                              (hash-set :item-type-cap)
                                              (hash-set :item-type-merch)))
                          (check-price-ids)))]
    (if (not= (:url-title itemmap) (:url-title item))
      (do
        (println (str "WARNING: existing URL title '" (:url-title itemmap) "' "
                      "will now be '" (:url-title item) "'"))
        ; Store the old URL title so we can redirect requests to the new one
        (assoc item :old-url-title (:url-title itemmap)))
      item)))

(defn map-blog-entry [queryCount blogmap]
  (make-BlogEntry (-> blogmap
                    (assoc :user-id (dec (:user-id blogmap))) ; Our hardcoded users line up this way
                    (assoc :body (wrap-paragraphs (:body blogmap)))
                    (assoc :date-added (:entry-date blogmap)))))

(defn map-display-orders [proto-items]
  "Set 0-based, consecutive display-order values for visible items and none for hidden ones"
  (loop [display-order 0
         items []
         proto-items proto-items]
    (let [display-order-padded (str (cs/repeat (- (get config/config :display-order-len)
                                                  (count (String/valueOf display-order)))
                                               "0")
                                    display-order)
          item (first proto-items)]
      (if (empty? proto-items)
        items
        (if (= "closed" (:status item))
          (recur display-order (conj items (assoc item :display-order nil)) (rest proto-items))
          (recur (inc display-order)
                 (conj items (assoc item :display-order display-order-padded))
                 (rest proto-items)))))))

