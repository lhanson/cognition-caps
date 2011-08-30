(ns cognition-caps.handlers
  (:use cognition-caps.data.simpledb
        clojure.tools.logging)
  (:require [cognition-caps.data :as data]
            [net.cgrand.enlive-html :as html]))

(declare item-type)
(defmacro maybe-append
  ([expr] `(if-let [x# ~expr] (html/append x#) identity))
  ([expr & exprs] `(maybe-append (or ~expr ~@exprs))))
(defmacro maybe-content
  ([expr] `(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))
(defmacro maybe-substitute
  ([expr] `(if-let [x# ~expr] (html/substitute x#) identity))
  ([expr & exprs] `(maybe-substitute (or ~expr ~@exprs))))

;; =============================================================================
;; Templates
;; =============================================================================

; Snippet to generate item markup
(html/defsnippet item-model "mainContent.html" [:#items :.item]
  [cap]
  [[:a :.url]] (html/set-attr :href (str "/" (item-type (:tags cap)) "/" (:url-title cap)))
  [:.fn] (html/content (:nom cap))
  [:.price] (html/content "666")
  [:img] (html/set-attr :src (first (:image-urls cap))))

(html/defsnippet show-caps "mainContent.html" [:#main]
  [caps]
  [:#items :ul] (html/content (map item-model caps))
  [:#itemDetails] nil)

(html/defsnippet item-details "mainContent.html" [:#itemDetails]
  [cap]
  [:#itemImageWrapper :img] (html/set-attr :src (first (:image-urls cap)))
  [:.fn] (html/content (:nom cap))
  [:.price] (html/content "666")
  [:.description] (html/content (:description cap)))

(html/defsnippet show-cap "mainContent.html" [:#main]
  [cap]
  [:#featureWrapper] nil
  [:#items] nil
  [:#itemDetails] (maybe-substitute (item-details cap)))

(html/deftemplate base "base.html" [{:keys [title main stats]}]
  [:title] (maybe-content title)
  [:#main] (maybe-substitute main)
  ; The last thing we do is to set the elapsed time
  [:#requestStats] (html/content (str "Response generated in "
                                      (/ (- (System/nanoTime) (:start-ts stats)) 1000000.0)
                                      " ms with " @(:db-queries stats) " SimpleDB queries")))

;; =============================================================================
;; Pages
;; =============================================================================

(defn index [stats]
  (debug "Rendering index")
  (let [caps (data/get-caps simpledb (:db-queries stats))]
    (base {:main (show-caps (take 7 caps))
           :stats stats})))

(defn item [stats url-title]
  (let [cap (data/get-cap simpledb (:db-queries stats) url-title)]
    (base {:main (show-cap cap)
           :title (:nom cap)
           :stats stats})))

;; =============================================================================
;; Utility functions
;; =============================================================================

(defn- item-type [tags]
  "Returns a string representing the item type present in tags"
  (let [t (if (string? tags) (hash-set tags) tags)]
    (cond
      (contains? t :item-type-cap) "caps")))
