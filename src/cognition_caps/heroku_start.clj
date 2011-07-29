(ns cognition-caps.heroku-start
  (:use ring.adapter.jetty)
  (:require [cognition-caps.core :as core]))

(defn -main []
  ; Heroku will put the port number in the environment
  (let [port (Integer/parseInt (System/getenv "PORT"))]
    (run-jetty core/app {:port port})))


