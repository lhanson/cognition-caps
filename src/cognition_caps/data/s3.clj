(ns cognition-caps.data.s3
  (:require [cognition-caps.config :as config])
  (:use [cognition-caps.dates :only (next-year)]
        [clojure.tools.logging :only (debug)])
  (:import (org.jets3t.service.security.AWSCredentials)
           (org.jets3t.service.impl.rest.httpclient.RestS3Service)
           (org.jets3t.service.model.S3Object)))

(def *bucketname* "cognition-caps")
(def *folder-prefix* "images/")

(defn upload-image [file item-id upload-filename]
  "Uploads the given file to S3. The key used is the filename given.
   Returns the URL of the uploaded image (minus scheme prefix)"
  (let [cred (org.jets3t.service.security.AWSCredentials. (get config/config "amazon-access-id")
                                                          (get config/config "amazon-access-key"))
        aws       (org.jets3t.service.impl.rest.httpclient.RestS3Service. cred)
        bucket    (.getBucket aws *bucketname*)
        object    (org.jets3t.service.model.S3Object. file)]
    (doto object
      (.setKey (str *folder-prefix* upload-filename))
      (.addMetadata "Expires" (next-year))
      (.addMetadata "item-id" (.toString item-id)))
    (debug "Uploading object" (.getKey object))
    (. aws putObject bucket object)
    ; Use this form of URL so that we can use a robots.txt
    (str "http://" *bucketname* ".s3.amazonaws.com/" (.getKey object))))

