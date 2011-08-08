(ns cognition-caps.handlers
  (:use [net.cgrand.enlive-html :only (deftemplate) :as html]))

(html/deftemplate index "index.html" [ctx])

(defn root [uri]
  (index {}))
