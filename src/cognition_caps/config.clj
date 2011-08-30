(ns cognition-caps.config
  (:require [clojure.contrib.properties :as props]))

(defonce config
  (if (.exists (java.io.File. "datasource.properties"))
    ; We're running locally, read properties from a file
    (into {:app-log-level :debug :dev-mode true}
          (props/read-properties "datasource.properties"))
    (if (not (.exists (java.io.File. "datasource.properties.example")))
      ; We're running on Heroku, read properties from the environment
      {:app-log-level :info
       "amazon-access-id"  (System/getenv "AMAZON_ACCESS_ID")
       "amazon-access-key" (System/getenv "AMAZON_ACCESS_KEY")}
      (throw (java.lang.IllegalStateException.
               "We appear to be running locally, but no datasource.properties is present")))))
