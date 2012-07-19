(ns cognition-caps.urls
  (:use [cognition-caps.config :only (config)]))

(defn- item-type [tags]
  "Returns a string representing the item type present in tags"
  (let [t (if (keyword? tags) (hash-set tags) tags)]
    (cond
      (contains? t :item-type-cap) "caps"
      (contains? t :item-type-merch) "merch")))

(defn relative-url [item]
  "Returns the relative URL of the item"
  (str "/" (item-type (:tags item)) "/" (:url-title item)))

(defn absolute-url [item]
  "Returns the absolute URL of the item"
  (str (:url-base config) (relative-url item)))

