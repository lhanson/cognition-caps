(ns cognition-caps.handlers
  (:use cognition-caps.data.simpledb)
  (:require [cognition-caps.data :as data]
            ;[cognition-caps.data.simpledb] ;:only (simpledb)]
            [net.cgrand.enlive-html :as html]))

(html/deftemplate index "index.html" [ctx]
  ; The last thing we do is to set the elapsed time
  [:#requestStats]
    (html/content (str "Response generated in "
                       (/ (- (System/nanoTime) (:start-ts ctx)) 1000000.0)
                       " ms with " @(:db-queries ctx) " SimpleDB queries")))

(defn root [request]
  (let [query-count (get-in request [:stats :db-queries])
        caps (data/get-caps simpledb query-count)]
    (index (merge (:stats request) {:db-queries query-count}))))
