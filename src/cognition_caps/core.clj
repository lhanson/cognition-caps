(ns cognition-caps.core
  (:use compojure.core
        cognition-caps.handlers)
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(defroutes main-routes
  (GET "/" [] root)
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (handler/site main-routes))
