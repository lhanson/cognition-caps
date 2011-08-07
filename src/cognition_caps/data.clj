(ns cognition-caps.data
  (:use [clj-time.core :only (now)]))

(defprotocol DataAccess
  "A protocol abstracting access to product data"
  (get-caps [this] "Provides a sequence of caps currently stored")
  (put-caps [this caps] "Persists caps"))

; Things to think about incorporating:
;   - where do we track inventory/availability? when we display a cap, we need
;     to show which sizes of it are in stock
(defrecord Cap [id nom description image-urls price tags user-id date-added display-order hide])
(defn make-Cap
  "Creates a Cap from the given map, setting defaults when not present"
  [{:keys [id nom description image-urls price tags user-id date-added display-order hide]
     :or {date-added (now) display-order 0 hide false}}]
    (Cap. id nom description image-urls price tags user-id date-added display-order hide))

(defrecord User [id nom nickname])
(defn make-User [id nom nickname] (User. id nom nickname));
