;;; DataAccess implementation against Amazon SimpleDB
(ns cognition-caps.data.simpledb
  (:use [cognition-caps.data])
  (:require [com.cemerick/rummage :as sdb]))

(defrecord SimpleDBAccess []
  DataAccess
  (get-caps [this]
            (println "Getting caps from SimpleDB")))

(defn make-SimpleDBAccess []
  (SimpleDBAccess.))
