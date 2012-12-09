(ns cognition-caps.config
  (:require [clojure.contrib.properties :as props]
            [clojure.tools.logging :as log]
            [clj-logging-config.log4j :as l]))

(defonce base-config
  (let [dev-mode (.exists (java.io.File. "datasource.properties"))]
    (if dev-mode
      (println "Dev mode triggered, properties file at" (.getAbsolutePath (java.io.File. "datasource.properties")))
      (println "Dev mode" dev-mode))
    {:dev-mode dev-mode
     :app-log-level (if dev-mode :debug :info)
     ; The length of the string used to represent display order in the database
     ; so that we can properly pad query values.
     :display-order-len 4
     ; SimpleDB values can only be 1024 characters long. We'll leave some
     ; headroom for prefix encoding and other such encoding
     :max-string-len 1000
     :url-base "http://www.wearcognition.com"
     :old-site-url "http://67.222.57.142"
     :facebook-url "http://www.facebook.com/pages/Cognition-Caps/165962633453031"
     :google-plus-url "http://wearcognition.com" }))

(defonce config
  (let [base-log {:out :console :level :info}]
    (l/set-loggers!
      :root base-log
      "com.amazonaws"     (assoc base-log :level :warn)
      "org.eclipse.jetty" (assoc base-log :level :info)
      "cognition-caps"    (assoc base-log :level (:app-log-level base-config)))
    (log/info "Loggers initialized")

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
                 "We appear to be running locally, but no datasource.properties is present"))))))
