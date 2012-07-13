(ns cognition-caps.data
  (:use [clj-time.core :only (now)])
  (:require [clojure.string :as s]))

(defprotocol DataAccess
  "A protocol abstracting access to product data"
  (get-item   [this queryCount url-title]   "Provides the item corresponding to the given url-title")
  (get-items  [this queryCount]             "Provides a sequence of all items currently stored")
  (get-items-range [this queryCount begin limit]
                  "Provides a sequence of 'limit' visible items beginning with the given display-order")
  (get-visible-item-count [this queryCount] "Provides a count of available items for viewing")
  (put-items  [this queryCount items]       "Persists items")
  (get-sizes  [this queryCount]             "Provides a list of available sizes")
  (get-prices [this queryCount]             "Provides a list of price categories")
  (update-item [this queryCount id
               attr-name attr-value]        "Updates the given attribute"))

;; =============================================================================
;; Field notes:
;; display-order - The order in which items will be displayed to users.
;;                 If nil, the item is hidden.
;; =============================================================================
(defrecord Item [id nom url-title description image-urls price-ids sizes tags user-id date-added display-order])
(defn make-Item
  "Creates an Item from the given map, setting defaults when not present"
  [{:keys [id nom url-title description image-urls price-ids sizes tags user-id date-added display-order]
     :or {url-title id date-added (now) display-order nil}}]
    (Item. id nom url-title description image-urls price-ids sizes tags user-id date-added display-order))

(defrecord Size [id nom])

(defrecord User [id nom nickname])
(defn make-User [id nom nickname] (User. id nom nickname))

(defrecord Price [id price qty description])

;; =============================================================================
;; Default data 
;; =============================================================================
(def default-size (long 2))
(def default-sizes [{:id 1 :nom "Small-ish"}
                    {:id 2 :nom "One Size Fits Most"}
                    {:id 3 :nom "Large"}])

(def default-prices [{:id 1  :price "22.00" :description "Basic Cap"}
                     {:id 2  :price "24.00" :description "Cap w/Ribbon"}
                     {:id 3  :price "25.00" :description "Screenprinted"}
                     {:id 4  :price "27.00" :description "Wool"}
                     {:id 5  :price "30.00" :description "Winter Wool w/Earflaps"}
                     {:id 6  :price "33.00" :description "Gnome Fest"}
                     {:id 7  :price "35.00" :description "Faux Fur"}
                     {:id 8  :price "20.00" :description "FixedRiders.com"}
                     {:id 9  :price "1.00"  :description "Stickers & Buttons"}])

;; =============================================================================
;; Utility functions
;; =============================================================================
(defn url-title [nom]
  "Returns the url-title for the given item name"
  (-> nom
      s/trim
      s/lower-case
      (s/replace #"'" "")
      (s/replace #"[\W&&[^\.]]+" "-")))

