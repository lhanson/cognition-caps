(ns cognition-caps.config
  (:require [cognition-caps.data :as data]
            [cognition-caps.data.simpledb :as simpledb]
            [clojure.contrib.properties :as props]
            [clojure.tools.logging :as log]
            [clj-logging-config.log4j :as l]
            [cemerick.rummage :as rummage]
            [cemerick.rummage.encoding :as enc]))

(defonce base-config
  (let [dev-mode (.exists (java.io.File. "datasource.properties"))]
    {:dev-mode dev-mode
     :test-mode (= (System/getProperty "test.mode") "true") ; running unit tests
     :heroku-mode (= (System/getenv "LEIN_NO_DEV") "yes")
     :app-log-level (if dev-mode :debug :info)
     :url-base "http://www.wearcognition.com"
     :old-site-url "http://67.222.57.142"
     :facebook-url "http://www.facebook.com/pages/Cognition-Caps/165962633453031"
     :google-plus-url "http://wearcognition.com" }))

(defn- make-sdb-client [conf]
  (log/info "Creating sdb client")
  (simpledb/make-SimpleDBAccess
    (assoc (enc/all-prefixed-config)
           :client (rummage/create-client (conf "amazon-access-id")
                                          (conf "amazon-access-key")))))

(defonce config
  (let [base-log {:out :console :level :info}]
    (l/set-loggers!
      :root base-log
      "com.amazonaws"     (assoc base-log :level :warn)
      "org.eclipse.jetty" (assoc base-log :level :info)
      "cognition-caps"    (assoc base-log :level (:app-log-level base-config)))
    (log/info "Loggers initialized")

    (let [c (cond
              (:test-mode base-config)   ; Running unit tests, don't load credentials
                (merge base-config
                       {"amazon-access-id"  "n/a"
                        "amazon-access-key" "n/a" })
              (:dev-mode base-config)    ; We're running locally, read properties from a file
                (merge base-config
                       (props/read-properties "datasource.properties"))
              (:heroku-mode base-config) ; We're running on Heroku, read properties from the environment
                (merge base-config
                       {"amazon-access-id"  (System/getenv "AMAZON_ACCESS_ID")
                        "amazon-access-key" (System/getenv "AMAZON_ACCESS_KEY")})
              :else
                (throw (java.lang.IllegalStateException. "No local datasource.properties or environment-specific flags found.")))]
      (merge c { :db-impl (if (:test-mode c)
                            (data/make-stubbed-client)
                            (make-sdb-client c))}))))
