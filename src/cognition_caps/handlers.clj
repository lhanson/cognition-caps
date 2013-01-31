(ns cognition-caps.handlers
  (:use [cognition-caps.config :only (config)]
        [cognition-caps.ring :only (redirect)]
        [clojure.contrib.string :only (lower-case replace-re trim)]
        [clojure.tools.logging :only (debug)]
        [clj-time.core :only (after? minus months now)])
  (:require [cognition-caps [data :as data] [urls :as urls]]
            [clojure.contrib.math :as math]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [clj-time.coerce :as time-coerce]
            [clj-time.format :as time-format]
            [net.cgrand.enlive-html :as html])
  (import [java.net URL]
          [java.io BufferedReader InputStreamReader IOException StringReader]))

(declare formatted-price)

(def db (:db-impl config))

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

(defn admin-item [item]
  (let [snippet      (html/at (html/html-snippet "<div/>")
                              [:*] (html/content (html/html-snippet (:description item))))
        descr        (apply str (html/emit* (html/select snippet [[:p html/first-of-type]])))
        description  (if (> (count descr) 80)
                         (str (trim (subs descr 0 80)) "...")
                         descr)
        hidden (= "-" (:display-order item))]
    (html/transformation
      [:.item] (html/do->
                 (item-common item)
                 (change-when hidden (html/add-class "disabled")))
      [:img] (let [thumb-urls (filter #(.startsWith (name (key %)) "thumb-") (:image-urls item))
                   thumb-url (val (first thumb-urls))]
               (html/set-attr :src thumb-url))
      [:form] (let [display-order (if hidden "last" "-")
                    alt-text      (if hidden "Mark item available" "Mark item unavailable")
                    img-class     (if hidden "icon-plus-sign" "icon-minus-sign")]
                (html/do->
                  (html/transformation [[:input (html/attr= :name "display-order")]]
                                       (html/set-attr :value display-order))
                  (html/transformation [[:input (html/attr= :type "image")]]
                                       (html/do->
                                         (html/set-attr :alt alt-text)
                                         (html/set-attr :title alt-text)
                                         (html/add-class img-class)))
                  (fn [node]
                    ((html/set-attr :action (s/replace (first (html/attr-values node :action)) ":id" (:url-title item)))
                       node))))
      [:.description]
        (html/html-content description))))

(defn- is-new? [item]
  (after? (time-coerce/from-long (* (java.lang.Long. (:date-added item)) 1000))
          (minus (now) (months 1))))

; Snippet to generate item markup for each item on the main page
(defn- item-model [item]
  (html/transformation
    [:.item] (html/do->
               (item-common item)
               (change-when (is-new? item)
                            (html/append {:tag :img
                                          :attrs {:class "itemBadge"
                                                  :src "images/badge_new.png"
                                                  :alt "New item"
                                                  :width "59px"
                                                  :height "59px"}})))
    [[:a :.url]] (html/set-attr :href (urls/relative-url item))
    [:img] (html/set-attr :src (:front-0 (:image-urls item)))))

(defn- offset [pagenum items-per-page]
  (* (dec pagenum) items-per-page))

(html/defsnippet paginate "pagination.html" [:#pagination]
  [url-root current-page page-count items-per-page]
  [:.previous :a]
    (html/set-attr :href (str url-root "?begin=" (offset (dec current-page) items-per-page) "&limit=" items-per-page))
  [:.previous] #(when (> current-page 1) %)
  [:.pageNum]
    (html/clone-for [pagenum (range 1 (inc page-count))]
      html/this-node (html/do->
                       (html/remove-attr :class)
                       (if (= current-page pagenum)
                         (html/add-class "active")
                         identity))
      [:a] (html/do->
             (html/content (str pagenum))
             (if (= current-page pagenum)
               html/unwrap
               (html/set-attr :href (str url-root "?begin=" (offset pagenum items-per-page) "&limit=" items-per-page)))))
  [:.next :a]
    (html/set-attr :href (str url-root "?begin=" (offset (inc current-page) items-per-page) "&limit=" items-per-page))
  [:.next] #(when (< current-page page-count) %))

(html/defsnippet social-media "social-media.html" [:.socialMedia :> :*]
  [fb-like-url fb-send-button plusone-url]
  [:.fb-like] (html/do->
                (html/set-attr :data-href fb-like-url)
                (html/set-attr :data-send fb-send-button))
  [:.g-plusone] (html/set-attr :data-href plusone-url))

(html/defsnippet show-items "mainContent.html" [:#main :> :*]
  [items]
  [:.item] (html/clone-for [item items] (item-model item)))

; Snippet for generating markup for an individual item page
(html/defsnippet show-item "item.html" [:#itemDetails]
  [item]
  [:*] (item-common item)
  [:#itemImageWrapper :img] (html/set-attr :src (:main-0 (:image-urls item)))
  [:.description] (html/html-content (:description item))
  [:.description :> [:p html/last-of-type]] (html/add-class "itemMaterials")
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
  ; TODO: the next two transformations are directly dependent on each other. Consolidate logic.
  [[:form (html/attr= :target "paypal")]] (change-when (data/hidden? item) nil)
  [:#unavailable] (change-when (not (data/hidden? item)) nil)
  [:.socialMedia] (let [current-url (urls/absolute-url item)]
                    (html/prepend (social-media current-url true current-url))))

; Snippet for generating markup for an individual blog entry
(html/defsnippet show-blog-entry "blog.html" [:.blogEntry]
  [link-title? entry]
  [:.title :.url] (html/do->
                    (html/content (:title entry))
                    (html/set-attr :href (str "/blog/" (:url-title entry))))
  [:.title :.url] (change-when (not link-title?) html/unwrap)
  [:.author] (html/content (:username (:user entry)))
  [:.publishDate] (html/content (time-format/unparse (time-format/formatter "EEE, dd MMM yyyy") (time-coerce/from-long (* 1000 (:date-added entry)))))
  [:.body] (html/html-content (:body entry))
  [:.socialMedia] (html/append (social-media (urls/absolute-url entry) true (urls/absolute-url entry)))
  [:.titlePhoto] (html/set-attr :src (:image-url entry)))

(html/defsnippet show-blog "blog.html" [:#entries]
  [entries]
  [:#entries] (html/content (map (partial show-blog-entry true) entries)))

(html/defsnippet fourohfoursnippet "404.html" [:#main :> :*]
  [url]
  [:code] (html/content url))
;
(html/defsnippet show-admin "admin.html" [:#admin]
  [invalid-login user items]
  [:#admin]
  (change-when invalid-login
               (html/append {:tag "div"
                             :attrs {:class "error"}
                             :content "Error: Unable to verify authentication with Facebook"}))
  [#{:#items :#blogs}] (if user identity nil)
  [:.item] (html/clone-for [item items] (admin-item item)))

(defn- show-paginated [items current-page page-count items-per-page]
  "Renders a sequence of items, applying pagination as appropriate"
  (let [tags (:tags (first items))
        pair (if (or (:item-type-cap tags) (:item-type-merch tags))
               [show-items nil]
               [show-blog "/blog"])
        nodes ((first pair) items)]
    (if (> page-count 1)
      (concat nodes (paginate (second pair) current-page page-count items-per-page))
      nodes)))

(defn- render-flash [flash]
  (fn [node]
    (if flash
      ((html/prepend (html/html-snippet (str "<div id=\"message\" class=\""
                                            (if (:success flash) "success" "failure")
                                            "\">" (:message flash) "</div>"))) node)
      node)))

(html/deftemplate base "base.html" [{:keys [title main stats flash admin?]}]
  [:title] (if title (html/content title) (html/content *title-base*))
  [:#main] (html/do->
             (render-flash flash)
             (maybe-append main))
  [:#main :> :a] (if admin?
                   nil
                   (change-when (or (nil? title) (= title *title-base*)) html/unwrap))
  [:#footerContent] (let [social (social-media (:facebook-url config) false (:google-plus-url config))
                          fb-like (html/select social [:.fb-like]) ]
                      ; reorder the Facebook and +1 buttons in the source to accomodate floated order
                      (html/prepend (html/at social
                                             [:.fb-like] nil
                                             [:.plusone] (html/after fb-like))))
  [:#facebookLink] (html/set-attr :href (:facebook-url config))
  ; The last thing we do is to set the elapsed time
  [:#requestStats]
    (change-when stats
      (html/content (str "Response generated in "
                         (/ (- (System/nanoTime) (:start-ts stats)) 1000000.0)
                                 " ms with " @(:db-queries stats) " SimpleDB queries")))
  [#{:#adminCSS :#adminJS}] (if admin? (html/remove-attr :id) nil)
  [html/comment-node] nil)

;; =============================================================================
;; Pages
;; =============================================================================

(defn- valid-index? [s minimum]
  (try
    (let [intval (Integer/parseInt s)]
      (if (>= intval minimum)
        intval))
    (catch NumberFormatException e false)))
(defn- valid-begin? [s] (valid-index? s 0))
(defn- valid-limit? [s] (valid-index? s 1))

(defn paginated-query [query-fn visible-count default-limit stats {:keys [begin limit title]}]
  "Takes a `query-fn` which accepts a begin index and a count. An argument map
   is processed, applying `default-limit` as appropriate, and `query-fn` is
   executed with the computed range. The retrieved items are then passed to the
   base rendering template.

   Note that the begin index is a string (eventually padded) so we can use it for
   sort value in SimpleDB, while limit is a number."
  (let [items-per-page (or (valid-limit? limit) default-limit)
        begin-index (if (valid-begin? begin) begin "0")
        items (query-fn begin-index items-per-page)
        page-count   (math/ceil (/ visible-count items-per-page))
        current-page (inc (math/floor (/ (Integer/parseInt begin-index) items-per-page)))
        render-map {:main (show-paginated items current-page page-count items-per-page)
                    :stats stats}]
    (base (if title
            (assoc render-map :title title)
            render-map))))

(defn index [stats args]
  "Renders the main item listing. Note that pagination assumes 0-based, consecutive
   display ordering of visible items."
  (paginated-query
    (partial data/get-items-range db (:db-queries stats))
    (data/get-visible-item-count db (:db-queries stats) nil)
    *items-per-page*
    stats
    args))

(defn caps [stats args]
  "Renders the list of caps. See 'index' docstring for pagination details"
  (paginated-query
    (partial data/get-items-range-filter db (:db-queries stats) :item-type-cap)
    (data/get-visible-item-count db (:db-queries stats) :item-type-cap)
    *items-per-page*
    stats
    (assoc args :title (str "Caps - " *title-base*))))

(defn merches [stats args]
  "Renders the list of merchandise. See 'index' docstring for pagination details"
  (paginated-query
    (partial data/get-items-range-filter db (:db-queries stats) :item-type-merch)
    (data/get-visible-item-count db (:db-queries stats) :item-type-merch)
    *items-per-page*
    stats
    (assoc args :title (str "Merch - " *title-base*))))

(defn blog [stats args]
  "Displays the main blog page"
  (paginated-query
    (partial data/get-blog-range db (:db-queries stats))
    (data/get-visible-blog-count db (:db-queries stats))
    blog-entries-per-page
    stats
    (assoc args :title (str "Blog - " *title-base*))))

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
  (data/get-item db (:db-queries stats) url-title))

(defn cap [stats url-title]
  (if-let [cap (item-by-url-title stats url-title)]
    (if (:item-type-cap (:tags cap))
      (handle-item stats cap url-title))))

(defn merch [stats url-title]
  (if-let [merch (item-by-url-title stats url-title)]
    (if (:item-type-merch (:tags merch))
      (handle-item stats merch url-title))))

(defn blog-entry [stats url-title]
  (if-let [entry (data/get-blog-entry db (:db-queries stats) url-title)]
    (base {:main (show-blog-entry false entry)
           :title (str (:title entry) " - " *title-base*)
           :stats stats})))

(defn sizing [stats]
  (base {:main (html/html-resource "sizing.html")
         :title (str "Sizing - " *title-base*)
         :stats stats}))

(defn faq [stats]
  (base {:main (html/html-resource "faq.html")
         :title (str "FAQ - " *title-base*)
         :stats stats}))

(defn thanks [stats]
  (base {:main (html/html-resource "thanks.html")
         :title (str "Thanks for your order! - " *title-base*)
         :stats stats}))

(defn fourohfour [uri]
  (base {:title "Page Not Found"
         :main (fourohfoursnippet uri)}))

(defn admin [{:keys [session flash stats]} invalid-login]
  (let [user (if (:user-id session)
               (data/get-user sdb/simpledb (:db-queries stats) (:user-id session)))
        items (if user (data/get-items sdb/simpledb (:db-queries stats)))
        disabled (if user (data/get-disabled-items sdb/simpledb (:db-queries stats)))]
    (base {:title (str "Admin - " *title-base*)
           :main (show-admin invalid-login user (concat items disabled))
           :flash flash
           :admin? true
           :stats stats})))

(defn- valid-login? [fb-id fb-access-token]
  (try
    (with-open [stream (.openStream (java.net.URL. (str "https://graph.facebook.com/me?access_token=" fb-access-token)))]
      (let  [buf (java.io.BufferedReader.
                   (java.io.InputStreamReader. stream))
             json (json/read-str (apply str (line-seq buf)))]
        (= fb-id (get json "id"))))
    (catch IOException e false)))

(defn admin-login [{:keys [fb-id fb-access-token]} {:keys [stats session] {referer "referer"} :headers}]
  (if (valid-login? fb-id fb-access-token)
    (let [user (data/get-user-by sdb/simpledb (:db-queries stats) :facebook-id fb-id)]
      (assoc (redirect referer 302) :session (assoc session :user-id (:id user))))
    (redirect "/skullbong?invalid-login" 302)))

(defn admin-logout [{:keys [session] {referer "referer"} :headers}]
  (assoc (redirect referer 302) :session nil))

(defn update-item [item-type-str url-title field params {:keys [stats session] {referer "referer"} :headers}]
  (when-let [item-type (cond (= "caps" item-type-str) :item-type-cap
                             (= "merch" item-type-str) :item-type-merch)]
    (println "update item type" item-type "url-title" url-title "field" field "value" (params (keyword field)))
    (println "Session" session)
    (data/update-item sdb/simpledb (:db-queries stats) url-title field (params (keyword field)))
    (assoc (redirect referer 303) :flash {:success true :message "Item updated successfully"})))

;; =============================================================================
;;
;; Utility functions
;; =============================================================================

(defn- formatted-price [item]
  "Returns a price string formatted for display"
  (replace-re #"\..*" "" (:price (first (:prices item)))))

