(ns cognition-caps.config
  (:require [clojure.contrib.properties :as props]))

(defonce base-config
  (let [dev-mode (.exists (java.io.File. "datasource.properties"))]
    {:cap-url-prefix "/caps/"
     :dev-mode dev-mode
     :app-log-level (if dev-mode :debug :info)}))

(defonce config
  (if (:dev-mode base-config)
    ; We're running locally, read properties from a file
    (merge base-config
           (props/read-properties "datasource.properties"))
    (if (not (.exists (java.io.File. "datasource.properties.example")))
      ; We're running on Heroku, read properties from the environment
      (merge base-config
             {"amazon-access-id"  (System/getenv "AMAZON_ACCESS_ID")
              "amazon-access-key" (System/getenv "AMAZON_ACCESS_KEY")})
      (throw (java.lang.IllegalStateException.
               "We appear to be running locally, but no datasource.properties is present")))))
