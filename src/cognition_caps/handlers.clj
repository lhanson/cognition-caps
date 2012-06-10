(ns cognition-caps.handlers
  (:use [cognition-caps.data.simpledb :only (simpledb)]
        [cognition-caps.config :only (config)]
        [clojure.contrib.string :only (lower-case replace-re split-lines)]
        [clojure.tools.logging :only (debug)]
        [clj-time.core :only (after? minus months now)])
  (:require [cognition-caps.data :as data]
            [clojure.contrib.math :as math]
            [clj-time.coerce :as time-coerce]
            [net.cgrand.enlive-html :as html]))

(declare item-type formatted-price wrap-paragraphs)

(def *title-base* "Cognition Caps")
(def *items-per-page* 4) ;TODO: live version start with 32

(defmacro maybe-append
  ([expr] `(if-let [x# ~expr] (html/append x#) identity))
  ([expr & exprs] `(maybe-append (or ~expr ~@exprs))))
(defmacro maybe-content
  ([expr] `(if-let [x# ~expr] (html/content x#) identity))
  ([expr & exprs] `(maybe-content (or ~expr ~@exprs))))
(defmacro maybe-substitute
  ([expr] `(if-let [x# ~expr] (html/substitute x#) identity))
  ([expr & exprs] `(maybe-substitute (or ~expr ~@exprs))))
(defmacro change-when [test & body]
  `(if ~test (do ~@body) identity))

;; =============================================================================
;; Templates
;; =============================================================================

(defn item-common [item]
  (html/transformation
    [:.fn] (html/content (:nom item))
    [:.price] (html/content (formatted-price item))))

(defn- is-new? [item]
  (after? (time-coerce/from-long (* (java.lang.Long. (:date-added item)) 1000))
          (minus (now) (months 1))))

; Snippet to generate item markup for each item on the main page
(html/defsnippet item-model "mainContent.html" [:#items :.item]
  [item]
  [:*] (item-common item)
  [[:a :.url]] (html/set-attr :href (str "/" (item-type (:tags item)) "/" (:url-title item)))
  [:img] (html/set-attr :src (:front-0 (:image-urls item)))
  [:.item] (html/do->
             ;(html/set-attr :data-display-order (:display-order item))
             (change-when (is-new? item)
                          (html/append {:tag :img
                                        :attrs {:class "itemBadge"
                                                :src "images/badge_new.png"
                                                :alt "New item"
                                                :width "59px"
                                                :height "59px"}}))))

(defn- offset [pagenum items-per-page]
  (* (dec pagenum) items-per-page))

(html/defsnippet show-items "mainContent.html" [:#main :> :*]
  [items current-page page-count items-per-page]
  [:#items :ul] (html/content (map item-model items))
  [:#pagination :.previous :a]
    (html/set-attr :href (str "/?begin=" (offset (dec current-page) items-per-page)
                              "&limit=" items-per-page))
  [:#pagination :.previous] #(when (> current-page 1) %)
  [:#pagination :.pageNum]
    (html/clone-for [pagenum (range 1 (inc page-count))]
      html/this-node (html/remove-attr :class)
      [:a] (html/do->
             (html/content (str pagenum))
             (if (= current-page pagenum)
               html/unwrap
               (html/set-attr :href (str "/?begin=" (offset pagenum items-per-page)
                                         "&limit=" items-per-page)))))
  [:#pagination :.next :a]
    (html/set-attr :href (str "/?begin=" (offset (inc current-page) items-per-page)
                              "&limit=" items-per-page))
  [:#pagination :.next] #(when (< current-page page-count) %))

; Snippet for generating markup for an individual item page
(html/defsnippet show-item "item.html" [:#itemDetails]
  [item]
  [:*] (item-common item)
  [:#itemImageWrapper :img] (html/set-attr :src (:main-0 (:image-urls item)))
  [:.description] (html/html-content (wrap-paragraphs (:description item)))
  [:.description [:p html/last-of-type]] (html/add-class "itemMaterials")
  [:#sizeSelection :option]
  (if-let [sizes (:sizes item)]
    (html/clone-for [size sizes]
                    (html/do->
                      (change-when (= (:id size) (str data/default-size))
                                   (html/set-attr :selected "selected"))
                      (html/set-attr :value (lower-case (:nom size)))
                      (html/content (lower-case (:nom size))))))
  [:#thumbnails :img] (html/clone-for [img (filter #(.startsWith (name (key %)) "thumb-")
                                                   (:image-urls item))]
                                      (html/set-attr :src (val img)))
  [:#itemInfoWrapper [:input (html/attr= :name "item_name")]]
  (html/set-attr :value (:nom item))
  [:#itemInfoWrapper [:input (html/attr= :name "item_number")]]
  (html/set-attr :value (:id item))
  [:#itemInfoWrapper [:input (html/attr= :name "amount")]]
  (html/set-attr :value (get-in item [:price :price]))
  [:#itemInfoWrapper :.g-plusone]
  (html/set-attr :data-href
                 (str "http://wearcognition.com/"
       (if (:item-type-cap (:tags item))
         "caps/"
         "merch/")
       (:url-title item))))

(html/deftemplate base "base.html" [{:keys [title main stats]}]
  [:title] (if title (html/content title) (html/content *title-base*))
  [:#main] (maybe-append main)
  [:#main :> :a] (change-when (or (nil? title) (= title *title-base*)) html/unwrap)
  ; The last thing we do is to set the elapsed time
  [:#requestStats] (html/content (str "Response generated in "
                                      (/ (- (System/nanoTime) (:start-ts stats)) 1000000.0)
                                      " ms with " @(:db-queries stats) " SimpleDB queries")))

;; =============================================================================
;; Pages
;; =============================================================================

(defn index [stats {:keys [begin limit] :or {begin "0"}}]
  "Renders the main item listing. Note that pagination assumes 0-based, consecutive
   display ordering of visible items."
  (let [items-per-page (if limit (Integer/parseInt limit) *items-per-page*)
        items (data/get-items-range simpledb (:db-queries stats) begin items-per-page)
        visible-item-count (data/get-visible-item-count simpledb (:db-queries stats))
        page-count         (math/ceil (/ visible-item-count items-per-page))
        current-page       (inc (math/floor (/ (Integer/parseInt begin) items-per-page)))]
    (base {:main (show-items items current-page page-count items-per-page)
           :stats stats})))

(defn- handle-item [stats item url-title]
  (let [current-title (:url-title item)
        old-title     (:old-url-title item)]
    (if (and old-title (not= current-title url-title))
      (do
        (debug (str "Request for url-title '" url-title
                    "' being redirected to new location '" current-title "'"))
        ; TODO: prime the get-item cache with this result so the redirect is fast
        {:status 301
         :headers {"Location" (str (if (:item-type-cap (:tags item))
                                     (:cap-url-prefix config)
                                     (:merch-url-prefix config))
                                   current-title)}})
      (do
        (debug "Loaded item for url-title" url-title ": " item)
        (println "PRICE:" (:price item))
        (println "PRICES:" (:prices (:price item)))
        (println "# PRICES:" (count (:prices (:price item))))
        (println "TYPEEEE" (type (first (:prices (:price item)))))
        ;(println "TYPEEEE" (type (first (:price item))))
        ;(println "FFFF" (first (:price item)))
        (println "Formatted price: " (formatted-price item))
        ;(base {:main (show-item item)
        ;       :title (str (:nom item) " - " *title-base*)
        ;       :stats stats})
        ))))

(defn- item-by-url-title [stats url-title]
  (data/get-item simpledb (:db-queries stats) url-title))

(defn cap [stats url-title]
  (if-let [cap (item-by-url-title stats url-title)]
    (if (:item-type-cap (:tags cap))
      (handle-item stats cap url-title))))

(defn merch [stats url-title]
  (if-let [merch (item-by-url-title stats url-title)]
    (if (:item-type-merch (:tags merch))
      (handle-item stats merch url-title))))

(defn sizing [stats]
  (base {:main (html/html-resource "sizing.html")
         :title (str "Sizing - " *title-base*)
         :stats stats}))

(defn faq [stats]
  (base {:main (html/html-resource "faq.html")
         :title (str "FAQ - " *title-base*)
         :stats stats}))

;; =============================================================================
;; Utility functions
;; =============================================================================

(defn- item-type [tags]
  "Returns a string representing the item type present in tags"
  (let [t (if (string? tags) (hash-set tags) tags)]
    (cond
      (contains? t :item-type-cap) "caps"
      (contains? t :item-type-merch) "merch")))

(defn- wrap-paragraphs [text]
  "Converts newline-delimited text into <p> blocks"
  (let [paragraphs (filter #(not (empty? %)) (split-lines text))]
    (reduce str (map #(str "<p>" % "</p>") paragraphs))))

(defn- formatted-price [item]
  "Returns a price string formatted for display"
  ; Works for merch, need to re-test for caps
  (replace-re #"\..*" "" (:price (first (:prices item)))))
