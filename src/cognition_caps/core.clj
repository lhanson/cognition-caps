(ns cognition-caps.core
  (:use compojure.core
        cognition-caps.handlers)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(defroutes main-routes
  (GET "/" [] root)
  (route/resources "/")
  (route/not-found "Page not found"))

(defn wrap-time [handler]
  "Ring middleware which inserts the start time (in nanos) into the request"
  (fn [request]
    (handler (merge request {:start-ts (System/nanoTime)}))))

(def app (-> (handler/site main-routes)
             wrap-time))
