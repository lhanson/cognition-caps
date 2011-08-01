;;; DataAccess implementation against the MySQL ExpressionEngine database from
;;; the old version of the site. This is purely for the migration and MySQL
;;; won't be used on Heroku.
(ns cognition-caps.data.mysql
  (:use [cognition-caps.data])
  (:require [clojure.contrib.properties :as props]
            [clojure.contrib.sql :as sql]))

(declare get-cap-count)

(defonce db
  (let [db-config (#(props/read-properties "datasource.properties"))
        db-host (get db-config "mysql-host")
        db-port (get db-config "mysql-port")
        db-name (get db-config "mysql-name")]
    {:classname "com.mysql.jdbc.Driver"
     :subprotocol "mysql"
     :subname (str "//" db-host ":" db-port "/" db-name)
     :user (get db-config "mysql-user")
     :password (get db-config "mysql-pass")}))

(defrecord MySQLAccess []
  DataAccess
  (get-caps [this] (println "Getting caps from MySQL, have" (get-cap-count))))

(defn MySQLAccess-instance []
  (MySQLAccess.))

(defn- select-single-result [query]
  "For a SELECT statement yielding a single value, returns that result"
  (sql/with-query-results rs [query]
    (assert (= 1 (count rs)))
    (val (ffirst rs))))

(defn- get-cap-count []
  (sql/with-connection db
    (let [hats-weblog-id (select-single-result "select weblog_id from exp_weblogs where blog_name='hats'")]
      (select-single-result (str "select count(*) from exp_weblog_data where weblog_id='" hats-weblog-id "'")))))
