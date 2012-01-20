(ns cognition-caps.data.s3
  (:require [cognition-caps.config :as config])
  (:use     [clojure.tools.logging :only (debug)])
  (:import (org.jets3t.service.security.AWSCredentials)
           (org.jets3t.service.impl.rest.httpclient.RestS3Service)
           (org.jets3t.service.model.S3Object)
           (java.util.Calendar)
           (java.text.SimpleDateFormat)))

(def *bucketname* "cognition-caps")
(def *folder-prefix* "images/")

(defn upload-image [file name-suffix]
  "Uploads the given file to S3. The key used is the MD5 with name-suffix appended.
   Returns the URL of the uploaded image (minus scheme prefix)"
  (debug "Uploading" file "to" name-suffix)
  (let [cred (org.jets3t.service.security.AWSCredentials. (get config/config "amazon-access-id")
                                                          (get config/config "amazon-access-key"))
        aws       (org.jets3t.service.impl.rest.httpclient.RestS3Service. cred)
        bucket    (.getBucket aws *bucketname*)
        object    (org.jets3t.service.model.S3Object. file)
        cal       (doto (java.util.Calendar/getInstance (java.util.TimeZone/getTimeZone "GMT")
                                                        (java.util.Locale/ENGLISH))
                        (.add java.util.Calendar/YEAR 1))
        rfc-1123  (new java.text.SimpleDateFormat "EEE, dd MMM yyyyy HH:mm:ss GMT")
        next-year (.format rfc-1123 (.getTime cal))]
    ; WORKING HERE: need to format this in 1123 with GMT, something like:
    ; (s/replace-str (unparse (format/formatter "EEE, dd MMM yyyy HH:mm:ss zzz") (time/now)) "UTC" "GMT)
    ; since JodaTime only deals in UTC and not GMT
    (println "Next-year:" next-year)
    (throw (new IllegalStateException "foo"))
    (doto object
      (.setKey (str *folder-prefix* (.getMd5HashAsHex object) name-suffix))
      ; RFC 2616: HTTP/1.1 servers SHOULD NOT send Expires dates more than one year in the future
      (.addMetadata "Expires" next-year))
    (debug "Uploading object" (.getKey object))
    (. aws putObject bucket object)
    (str "s3.amazonaws.com/" *bucketname* "/" (.getKey object))))

