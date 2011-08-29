(ns cognition-caps.migrations.mysql-to-simpledb
  (:require [cognition-caps.data :as data]
            [cognition-caps.data.mysql :as mysql]
            [cognition-caps.data.simpledb :as simpledb]
            [cemerick.rummage :as sdb]))

(declare add-sizes)

(defn migrate-data []
  (println "Migrating...")
  (println "Populating defaults in SimpleDB")
  (simpledb/populate-defaults!)
  (println "Querying data from ExpressionEngine/MySQL")
  (let [mysql-count (atom 0)
        sdb-count (atom 0)
        mysql-data (mysql/make-MySQLAccess) ; ExpressionEngine database for old site
        simpledb-data simpledb/simpledb
        sizes (data/get-sizes simpledb-data sdb-count)
        caps (map #(add-sizes % sizes) (data/get-caps mysql-data mysql-count))]
    (println "Loaded" (count caps) "caps from MySQL and"
             (count (data/get-caps simpledb-data sdb-count)) "from SimpleDB")
    (data/put-caps simpledb-data sdb-count caps)))

(defn- add-sizes [cap sizes]
  "We're not carrying over the size/price relations from ExpressionEngine,
   so set up each cap with the default sizing for the new system.
   Note that individual size entries are coded SIZE_ID:QTY, with -1 representing
   'unlimited' inventory."
  (assoc cap :sizes (apply vector (map #(str (::sdb/id %) ":-1") sizes))))
