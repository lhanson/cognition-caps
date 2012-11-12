(ns cognition-caps.handlers
  (:use [cognition-caps.config :only (config)]
        [cognition-caps.ring :only (redirect)]
        [clojure.contrib.string :only (lower-case replace-re)]
        [clojure.tools.logging :only (debug)]
        [clj-time.core :only (after? minus months now)])
  (:require [cognition-caps [data :as data] [urls :as urls]]
            [cognition-caps.data.simpledb :as sdb]
            [clojure.contrib.math :as math]
            [clj-time.coerce :as time-coerce]
            [clj-time.format :as time-format]
            [net.cgrand.enlive-html :as html]))

(declare formatted-price)

(def *title-base* "Cognition Caps")
(def *items-per-page* 32)
(def blog-entries-per-page 5)

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
(println "is-newing " item)
  (after? (time-coerce/from-long (* (java.lang.Long. (:date-added item)) 1000))
          (minus (now) (months 1))))

; Snippet to generate item markup for each item on the main page
(html/defsnippet item-model "mainContent.html" [:#items :.item]
  [item]
  [:*] (item-common item)
  [[:a :.url]] (html/set-attr :href (urls/relative-url item))
  [:img] (html/set-attr :src (:front-0 (:image-urls item)))
  [:.item] (html/do->
             (change-when (is-new? item)
                          (html/append {:tag :img
                                        :attrs {:class "itemBadge"
                                                :src "images/badge_new.png"
                                                :alt "New item"
                                                :width "59px"
                                                :height "59px"}}))))

(defn- offset [pagenum items-per-page]
  (* (dec pagenum) items-per-page))

(html/defsnippet paginate "pagination.html" [:#pagination]
  [url-root current-page page-count items-per-page]
  [:.previous :a]
    (html/set-attr :href (str url-root "?begin=" (offset (dec current-page) items-per-page) "&limit=" items-per-page))
  [:.previous] #(when (> current-page 1) %)
  [:.pageNum]
    (html/clone-for [pagenum (range 1 (inc page-count))]
      html/this-node (html/remove-attr :class)
      [:a] (html/do->
             (html/content (str pagenum))
             (if (= current-page pagenum)
               html/unwrap
               (html/set-attr :href (str url-root "?begin=" (offset pagenum items-per-page) "&limit=" items-per-page)))))
  [:.next :a]
    (html/set-attr :href (str url-root "?begin=" (offset (inc current-page) items-per-page) "&limit=" items-per-page))
  [:.next] #(when (< current-page page-count) %))


(html/defsnippet show-items "mainContent.html" [:#items]
  [items]
  [:ul] (html/content (map item-model items)))

; Snippet for generating markup for an individual item page
(html/defsnippet show-item "item.html" [:#itemDetails]
  [item]
  [:*] (item-common item)
  [:#itemImageWrapper :img] (html/set-attr :src (:main-0 (:image-urls item)))
  [:.description] (html/html-content (:description item))
  [:.description [:p html/last-of-type]] (html/add-class "itemMaterials")
  [#{:#sizeSelection [:label (html/attr= :for "sizeSelection")] :#sizingLink}]
  (change-when (nil? (:sizes item)) nil)
  [:#sizeSelection :option]
  (if-let [sizes (:sizes item)]
    (html/clone-for [size sizes]
                    (html/do->
                      (change-when (= (:id size) data/default-size)
                                   (html/set-attr :selected "selected"))
                      (html/set-attr :value (lower-case (:nom size)))
                      (html/content (lower-case (:nom size))))))
  [#{:#qtySelection [:label (html/attr= :for "qtySelection")]}]
  #(when (:item-type-merch (:tags item)) %)
  [:#qtySelection :option]
  (html/clone-for [qty (take 5 (iterate inc 1))]
                  (html/do->
                    (html/set-attr :value qty)
                    (html/content (str qty))))
  [:#thumbnails :img] (html/clone-for [img (filter #(.startsWith (name (key %)) "thumb-")
                                                   (:image-urls item))]
                                      (html/set-attr :src (val img)))
  ; Remove thumbnails if only one is shown
  [:#thumbnails] #(when (> (count (html/select % [:img])) 1) %)
  [:#itemInfoWrapper [:input (html/attr= :name "item_name")]]
  (html/set-attr :value (:nom item))
  [:#itemInfoWrapper [:input (html/attr= :name "item_number")]]
  (html/set-attr :value (:id item))
  [:#itemInfoWrapper [:input (html/attr= :name "amount")]]
  (html/set-attr :value (:price (first (:prices item))))
  [:#itemInfoWrapper :.g-plusone]
  (html/set-attr :data-href (urls/absolute-url item)))

; Snippet for generating markup for an individual blog entry
(html/defsnippet show-blog-entry "blog.html" [:.blogEntry]
  [link-title? entry]
  [:.title :.url] (html/do->
                    (html/content (:title entry))
                    (html/set-attr :href (str "/blog/" (:url-title entry))))
  [:.title :.url] (change-when (not  link-title?) html/unwrap)
  [:.author] (html/content (:username (:user entry)))
  [:.publishDate] (html/content (time-format/unparse (time-format/formatter "EEE, dd MMM yyyy") (time-coerce/from-long (* 1000 (:date-added entry)))))
  [:.body] (html/html-content (:body entry))
  [:.titlePhoto] (html/set-attr :src (:image-url entry)))

(html/defsnippet show-blog "blog.html" [:#entries]
  [entries]
  [:#entries] (html/content (map (partial show-blog-entry true) entries)))

(html/defsnippet fourohfoursnippet "404.html" [:#main :> :*]
  [url]
  [:code] (html/content url))

(defn- show-paginated [items current-page page-count items-per-page]
  "Renders a sequence of items, applying pagination as appropriate"
  (let [tags (:tags (first items))
        pair (if (or (:item-type-cap tags) (:item-type-merch tags))
               [show-items nil]
               [show-blog "/blog"])]
    (concat ((first pair) items) (paginate (second pair) current-page page-count items-per-page))))

(html/deftemplate base "base.html" [{:keys [title main stats]}]
  [:title] (if title (html/content title) (html/content *title-base*))
  [:#main] (maybe-append main)
  [:#main :> :a] (change-when (or (nil? title) (= title *title-base*)) html/unwrap)
  ; The last thing we do is to set the elapsed time
  [:#requestStats]
    (if stats
      (html/content (str "Response generated in "
                         (/ (- (System/nanoTime) (:start-ts stats)) 1000000.0)
                                 " ms with " @(:db-queries stats) " SimpleDB queries"))
      identity)
  [html/comment-node] nil)

;; =============================================================================
;; Pages
;; =============================================================================

(defn index [stats {:keys [begin limit] :or {begin "0"}}]
  "Renders the main item listing. Note that pagination assumes 0-based, consecutive
   display ordering of visible items."
  (let [items-per-page (if limit (Integer/parseInt limit) *items-per-page*)
        items (data/get-items-range sdb/simpledb (:db-queries stats) begin items-per-page)
        visible-item-count (data/get-visible-item-count sdb/simpledb (:db-queries stats))
        page-count         (math/ceil (/ visible-item-count items-per-page))
        current-page       (inc (math/floor (/ (Integer/parseInt begin) items-per-page)))]
    (base {:main (show-paginated items current-page page-count items-per-page)
           :stats stats})))

(defn caps [stats {:keys [begin limit] :or {begin "0"}}]
  "Renders the list of caps. See 'index' docstring for pagination details"
  (let [items-per-page (if limit (Integer/parseInt limit) *items-per-page*)
        items (data/get-items-range-filter sdb/simpledb (:db-queries stats) begin items-per-page :item-type-cap)
        visible-item-count (data/get-visible-item-count sdb/simpledb (:db-queries stats))
        page-count         (math/ceil (/ visible-item-count items-per-page))
        current-page       (inc (math/floor (/ (Integer/parseInt begin) items-per-page)))]
    (base {:main (show-paginated items current-page page-count items-per-page) 
           :stats stats})))

(defn merches [stats {:keys [begin limit] :or {begin "0"}}]
  "Renders the list of merchandise. See 'index' docstring for pagination details"
  (let [items-per-page (if limit (Integer/parseInt limit) *items-per-page*)
        items (data/get-items-range-filter sdb/simpledb (:db-queries stats) begin items-per-page :item-type-merch)
        visible-item-count (data/get-visible-item-count sdb/simpledb (:db-queries stats))
        page-count         (math/ceil (/ visible-item-count items-per-page))
        current-page       (inc (math/floor (/ (Integer/parseInt begin) items-per-page)))]
    (base {:main (show-paginated items current-page page-count items-per-page)
           :stats stats})))

(defn- handle-item [stats item url-title]
  (let [current-title (:url-title item)
        old-title     (:old-url-title item)]
    (if (and old-title (not= current-title url-title))
      (do
        (debug (str "Request for url-title '" url-title
                    "' being redirected to new location '" current-title "'"))
        (redirect (urls/relative-url item) 301))
      (base {:main (show-item item)
             :title (str (:nom item) " - " *title-base*)
             :stats stats}))))

(defn- item-by-url-title [stats url-title]
  (data/get-item sdb/simpledb (:db-queries stats) url-title))

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

(defn blog [stats {:keys [begin limit] :or {begin "0"}}]
  (let [entries-per-page (if limit (Integer/parseInt limit) blog-entries-per-page)
        entries (data/get-blog-range sdb/simpledb (:db-queries stats) begin entries-per-page)
        visible-blog-count (data/get-visible-blog-count sdb/simpledb (:db-queries stats))
        page-count         (math/ceil (/ visible-blog-count entries-per-page))
        current-page       (inc (math/floor (/ (Integer/parseInt begin) entries-per-page)))]
    (base {:main (show-paginated entries current-page page-count entries-per-page)
           :stats stats})))

(defn blog-entry [stats url-title]
  (if-let [entry (data/get-blog-entry sdb/simpledb (:db-queries stats) url-title)]
    (base {:main (show-blog-entry false entry)
           :title (str (:title entry) " - " *title-base*)
           :stats stats})))

(defn thanks [stats]
  (base {:main (html/html-resource "thanks.html")
         :title (str "Thanks for your order! - " *title-base*)
         :stats stats}))

(defn fourohfour [uri]
  (base {:title "Page Not Found"
         :main (fourohfoursnippet uri)}))

;; =============================================================================
;; Utility functions
;; =============================================================================

(defn- formatted-price [item]
  "Returns a price string formatted for display"
  (replace-re #"\..*" "" (:price (first (:prices item)))))

