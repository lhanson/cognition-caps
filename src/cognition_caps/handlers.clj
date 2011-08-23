(ns cognition-caps.handlers
  (:use cognition-caps.data.simpledb)
  (:require [cognition-caps.data :as data]
            [net.cgrand.enlive-html :as html]))

(declare item-type)
(defmacro maybe-substitute
  ([expr] `(if-let [x# ~expr] (html/substitute x#) identity))
  ([expr & exprs] `(maybe-substitute (or ~expr ~@exprs))))

(defmacro maybe-content
  ([expr] (println "Doing " expr)`(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))

;; =============================================================================
;; Templates
;; =============================================================================

; Snippet to generate item markup
(html/defsnippet item-model "base.html" [:#items :.item] [cap]
  [:a] (html/set-attr :href (str "/" (item-type (:tags cap)) "/" (:url-title cap)))
  [:.itemName] (html/content (:nom cap))
  [:.itemPrice] (html/content "$666")
  [:img] (html/set-attr :src (first (:image-urls cap))))

(html/deftemplate base "base.html" [{:keys [title caps start-ts db-queries]}]
  [:title] (maybe-content title)
  [:#items] (html/content (map item-model caps))
  ; The last thing we do is to set the elapsed time
  [:#requestStats] (html/content (str "Response generated in "
                                      (/ (- (System/nanoTime) start-ts) 1000000.0)
                                      " ms with " @db-queries " SimpleDB queries")))

;; =============================================================================
;; Pages
;; =============================================================================

(defn index [stats]
  (let [query-count (:db-queries stats)
        caps (data/get-caps simpledb query-count)]
    (base (merge {:caps (take 7 caps) :title "NEW TITLE"} stats))))

(defn item [stats url-title]
  (let [query-count (:db-queries stats)]
    url-title))

;; =============================================================================
;; Utility functions
;; =============================================================================

(defn- item-type [tags]
  "Returns a string representing the item type present in tags"
  (let [t (if (string? tags) (hash-set tags) tags)]
    (cond
      (contains? t :item-type-cap) "caps")))
