(ns cognition-caps.handlers
  (:use cognition-caps.data.simpledb)
  (:require [cognition-caps.data :as data]
            [net.cgrand.enlive-html :as html]))

(defn- item-type [tags]
  "Returns a string representing the item type present in tags"
  (let [t (if (string? tags) (hash-set tags) tags)]
    (cond
      (contains? t :item-type-cap) "caps")))

; Snippet to generate item markup
(html/defsnippet item-model "index.html" [:#items :.item] [cap]
  [:a] (html/set-attr :href (str "/" (item-type (:tags cap)) "/" (:url-title cap)))
  [:.itemName] (html/content (:nom cap))
  [:.itemPrice] (html/content "$666")
  [:img] (html/set-attr :src (first (:image-urls cap))))

(html/deftemplate index "index.html" [ctx]
  [:#items] (html/content (map item-model (:caps ctx)))
  ; The last thing we do is to set the elapsed time
  [:#requestStats]
    (html/content (str "Response generated in "
                       (/ (- (System/nanoTime) (:start-ts ctx)) 1000000.0)
                       " ms with " @(:db-queries ctx) " SimpleDB queries")))

(defn root [request]
  (let [query-count (get-in request [:stats :db-queries])
        caps (data/get-caps simpledb query-count)]
    (index (merge {:caps (take 7 caps)} (:stats request)))))
