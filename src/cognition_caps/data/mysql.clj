;;; DataAccess implementation against the MySQL ExpressionEngine database from
;;; the old version of the site. This is purely for the initial data migration;
;;; MySQL won't be used on Heroku.
(ns cognition-caps.data.mysql
  (:use [cognition-caps.data])
  (:require [cognition-caps.config :as config]
            [clojure.contrib.sql :as sql]))

(declare get-cap-rows get-cap-count)

(def *caps-weblog-id* 3)
(def *merch-weblog-id* 4)

(defonce db
  (let [db-host (get config/db-config "mysql-host")
        db-port (get config/db-config "mysql-port")
        db-name (get config/db-config "mysql-name")]
    {:classname "com.mysql.jdbc.Driver"
     :subprotocol "mysql"
     :subname (str "//" db-host ":" db-port "/" db-name)
     :user (get config/db-config "mysql-user")
     :password (get config/db-config "mysql-pass")}))

(defrecord MySQLAccess []
  DataAccess
  (get-caps [this] (map make-Cap (get-cap-rows))))
(defn make-MySQLAccess [] (MySQLAccess.))

(defn- get-cap-rows []
  ; TODO: handle t.url-title
  (let [query (str "SELECT t.entry_id AS \"id\", t.title AS \"nom\",
                           t.url_title AS \"url-title\", d.field_id_4 AS \"description\",
                           t.year, t.month, t.day, d.field_id_8 AS \"image1\",
                           d.field_id_18 AS \"image2\", d.field_id_19 AS \"image3\",
                           d.field_id_20 AS \"image4\", d.field_id_30 AS \"display-order\",
                           t.author_id AS \"user-id\", t.status
                    FROM exp_weblog_titles t
                    JOIN exp_weblog_data d ON t.entry_id = d.entry_id
                    WHERE t.weblog_id =  '" *caps-weblog-id* "'
                    ORDER BY `display-order` DESC")]
    (sql/with-connection db
      (sql/with-query-results rs [query]
        (vec rs)))))

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
