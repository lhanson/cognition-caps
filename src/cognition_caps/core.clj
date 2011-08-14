(ns cognition-caps.core
  (:use compojure.core
        cognition-caps.handlers)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(defroutes main-routes
  (GET "/" [] root)
  (route/resources "/")
  (route/not-found "Page not found"))

(defn wrap-stats [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:stats {:start-ts (System/nanoTime)
                                     :db-queries (atom 0)}}))))

(def app (-> (handler/site main-routes)
             wrap-stats))
