(ns cognition-caps.config
  (:require [clojure.contrib.properties :as props]))

(defonce base-config
  (let [dev-mode (.exists (java.io.File. "datasource.properties"))]
    {:dev-mode dev-mode
     :app-log-level (if dev-mode :debug :info)
     ; The length of the string used to represent display order in the database
     ; so that we can properly pad query values.
     :display-order-len 4
     :url-base "http://wearcognition.com"
     :old-site-url "http://67.222.57.142"}))

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
