(ns cognition-caps.handlers
  (:require [net.cgrand.enlive-html :as html]))

(html/deftemplate index "index.html" [ctx]
  ; The last thing we do is to set the elapsed time
  [:#requestStats]
    (html/content (str "Response generated in "
                  (/ (- (System/nanoTime) (:start-ts ctx)) 1000000.0)
                  " ms")))

(defn root [request] (index request))

(defn insert-elapsed-time [response]
  (let [resource (html/html-resource (java.io.StringReader. (apply str (:body response))))
        template (html/deftemplate t resource [] [:#requestStats]
                                   (html/content (str "Response generated in " (:elapsed-millis response) "ms")))]
    (assoc response :body (template))))
