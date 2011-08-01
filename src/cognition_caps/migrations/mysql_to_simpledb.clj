(ns cognition-caps.migrations.mysql-to-simpledb
  (:require [cognition-caps.data :as data]
            [cognition-caps.data.mysql :as mysql]))

(defn migrate-data []
  (let [mysql-data (mysql/make-MySQLAccess)] ; ExpressionEngine database for old site
    (println "Caps:" (count (data/get-caps mysql-data)))))
