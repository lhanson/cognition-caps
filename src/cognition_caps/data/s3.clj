(ns cognition-caps.data.s3
  (:require [cognition-caps.config :as config])
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
        aws (org.jets3t.service.impl.rest.httpclient.RestS3Service. cred)
        bucket (.getBucket aws *bucketname*)
        object (org.jets3t.service.model.S3Object. file)]
    (.setKey object (str *folder-prefix* (.getMd5HashAsHex object) name-suffix))
    (. aws putObject bucket object)
    (str "s3.amazonaws.com/" *bucketname* "/" (.getKey object))))

