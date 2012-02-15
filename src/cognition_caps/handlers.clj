(ns cognition-caps.handlers
  (:use [cognition-caps.data.simpledb :only (simpledb)]
        [cognition-caps.config :only (config)]
        [clojure.contrib.string :only (lower-case replace-re split-lines)]
        [clojure.tools.logging :only (debug)]
        [clj-time.core :only (after? minus months now)])
  (:require [cognition-caps.data :as data]
            [clj-time.coerce :as time-coerce]
            [net.cgrand.enlive-html :as html]))

(declare item-type formatted-price wrap-paragraphs)

(def *title-base* "Cognition Caps")

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

(defn cap-common [cap]
  (html/transformation
    [:.fn] (html/content (:nom cap))
    [:.price] (html/content (formatted-price cap))))

(defn- is-new? [cap]
  (after? (time-coerce/from-long (* (java.lang.Long. (:date-added cap)) 1000))
          (minus (now) (months 1))))

; Snippet to generate item markup for each item on the main page
(html/defsnippet item-model "mainContent.html" [:#items :.item]
  [cap]
  [:*] (cap-common cap)
  [[:a :.url]] (html/set-attr :href (str "/" (item-type (:tags cap)) "/" (:url-title cap)))
  [:img] (html/set-attr :src (:front-0 (:image-urls cap)))
  [:.item] (change-when (is-new? cap)
                        (html/append {:tag :img
                                      :attrs {:class "itemBadge"
                                              :src "images/badge_new.png"
                                              :alt "New item"
                                              :width "59px"
                                              :height "59px"}})))

(html/defsnippet show-caps "mainContent.html" [:#main :> :*]
  [caps]
  [:#items :ul] (html/content (map item-model caps)))

; Snippet for generating markup for an individual item page
(html/defsnippet show-cap "item.html" [:#itemDetails]
  [cap]
  [:*] (cap-common cap)
  [:#itemImageWrapper :img] (html/set-attr :src (:main-0 (:image-urls cap)))
  [:.description] (html/html-content (wrap-paragraphs (:description cap)))
  [:.description [:p html/last-of-type]] (html/add-class "itemMaterials")
  [:#sizeSelection :option] (html/clone-for [size (:sizes cap)]
                              (html/do->
                                (change-when (= (:id size) (str data/default-size))
                                             (html/set-attr :selected "selected"))
                                (html/set-attr :value (:id size))
                                (html/content (lower-case (:nom size)))))
  [:#thumbnails :img] (html/clone-for [img (filter #(.startsWith (name (key %)) "thumb-")
                                                   (:image-urls cap))]
                                      (html/set-attr :src (val img))))
;
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

(defn index [stats]
  (let [;SCHEME FOR IMAGES IS ALWAYS HTTP:// for now
        ;add-scheme (fn [image-map scheme]
        ;             (into {} (map #(vector (first %) (str scheme (second %)))
        ;                           (seq image-map))))
        caps (data/get-caps simpledb (:db-queries stats))]
    (debug "Got images" (:image-urls (first caps)))
    (base {:main (show-caps (take 7 caps))
           :stats stats})))

(defn item [stats url-title]
  (if-let [cap (data/get-cap simpledb (:db-queries stats) url-title)]
    (let [current-title (:url-title cap)
          old-title     (:old-url-title cap)]
      (if (and old-title (not= current-title url-title))
        (do
          (debug (str "Request for url-title '" url-title
                      "' being redirected to new location '" current-title "'"))
          ; TODO: prime the get-cap cache with this result so the redirect is fast
          {:status 301
           :headers {"Location" (str (:cap-url-prefix config) current-title)}})
        (do
          (debug "Loaded cap for url-title" url-title ": " cap)
          (base {:main (show-cap cap)
                 :title (str (:nom cap) " - " *title-base*)
                 :stats stats}))))))

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
      (contains? t :item-type-cap) "caps")))

(defn- wrap-paragraphs [text]
  "Converts newline-delimited text into <p> blocks"
  (let [paragraphs (filter #(not (empty? %)) (split-lines text))]
    (reduce str (map #(str "<p>" % "</p>") paragraphs))))

(defn- formatted-price [cap]
  (replace-re #"\..*" "" (get-in cap [:price :price])))
