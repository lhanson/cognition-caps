(ns cognition-caps.data
  (:use [clj-time.core :only (now)]))

(defprotocol DataAccess
  "A protocol abstracting access to product data"
  (get-caps [this] "Provides a sequence of caps currently stored")
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
(defn make-User [id nom nickname] (User. id nom nickname));
