(ns cognition-caps.data.s3
  (:require [cognition-caps.config :as config]
            [clojure.contrib.string :as s])
  (:use [clj-time [core :only (now plus years)] [format :only (formatter unparse)]]
        [clojure.tools.logging :only (debug)])
  (:import (org.jets3t.service.security.AWSCredentials)
           (org.jets3t.service.impl.rest.httpclient.RestS3Service)
           (org.jets3t.service.model.S3Object)))

(def *bucketname* "cognition-caps")
(def *folder-prefix* "images/")

(defn upload-image [file name-suffix]
  "Uploads the given file to S3. The key used is the MD5 with name-suffix appended.
   Returns the URL of the uploaded image (minus scheme prefix)"
  (let [cred (org.jets3t.service.security.AWSCredentials. (get config/config "amazon-access-id")
                                                          (get config/config "amazon-access-key"))
        aws       (org.jets3t.service.impl.rest.httpclient.RestS3Service. cred)
        bucket    (.getBucket aws *bucketname*)
        object    (org.jets3t.service.model.S3Object. file)
        ; RFC 2616: HTTP/1.1 servers SHOULD NOT send Expires dates more than
        ; one year in the future, and requires GMT (which JodaTime doesn't give us)
        next-year (s/replace-str "UTC" "GMT" ; JodaTime only gives UTC, RFC 1123 requires GMT
                                 (unparse (formatter "EEE, dd MMM yyyy HH:mm:ss z")
                                          (plus (now) (years 1))))]
    (doto object
      (.setKey (str *folder-prefix* (.getMd5HashAsHex object) name-suffix))
      (.addMetadata "Expires" next-year))
    (debug "Uploading object" (.getKey object))
    (. aws putObject bucket object)
    ; Use this form of URL so that we can use a robots.txt
    (str "http://" *bucketname* ".s3.amazonaws.com/" (.getKey object))))

