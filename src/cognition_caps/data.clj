(ns cognition-caps.data
  (:use [clj-time.core :only (now)])
  (:require [clojure.string :as s]))

(defprotocol DataAccess
  "A protocol abstracting access to product data"
  (get-caps [this] [this queryCount] "Provides a sequence of caps currently stored")
  (put-caps [this caps] "Persists caps")
  (get-sizes [this] "Provides a list of available sizes"))

(defrecord Cap [id nom url-title description image-urls price sizes tags user-id date-added display-order hide])
(defn make-Cap
  "Creates a Cap from the given map, setting defaults when not present"
  [{:keys [id nom url-title description image-urls price sizes tags user-id date-added display-order hide]
     :or {url-title id date-added (now) display-order 0 hide false}}]
    (Cap. id nom url-title description image-urls price sizes tags user-id date-added display-order hide))

(defrecord Size [id nom])

(defrecord User [id nom nickname])
(defn make-User [id nom nickname] (User. id nom nickname))

(defn url-title [nom]
  "Returns the url-title for the given cap name"
  (-> nom s/trim s/lower-case (s/replace #"'" "") (s/replace #"[\W&&[^\.]]+" "-")))
