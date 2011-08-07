(ns cognition-caps.migrations.mysql-to-simpledb
  (:require [cognition-caps.data :as data]
            [cognition-caps.data.mysql :as mysql]
            [cognition-caps.data.simpledb :as simpledb]))

(defn migrate-data []
  (println "Migrating...")
  (let [mysql-data (mysql/make-MySQLAccess) ; ExpressionEngine database for old site
        simpledb-data (simpledb/make-SimpleDBAccess)
        caps (data/get-caps mysql-data)]
    (println "Loaded" (count caps) "from MySQL and" (count (data/get-caps simpledb-data)) "SimpleDB")
    (data/put-caps simpledb-data caps)
    ;(doseq [cap caps]
    ;  (println "\tName: " (:nom cap)))
    )
  )
