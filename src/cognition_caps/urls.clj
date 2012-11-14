(ns cognition-caps.urls
  (:use [cognition-caps.config :only (config)]))

(defn- item-type [item]
  "Returns a string representing the item type present in tags"
  (let [tags (if (keyword? (:tags item))
               (hash-set (:tags item))
               (:tags item))]
    (cond
      (contains? tags :item-type-cap) "caps"
      (contains? tags :item-type-merch) "merch"
      (:body item) "blog")))

(defn relative-url [item]
  "Returns the relative URL of the item"
  (str "/" (item-type item) "/" (:url-title item)))

(defn absolute-url [item]
  "Returns the absolute URL of the item"
  (str (:url-base config) (relative-url item)))

