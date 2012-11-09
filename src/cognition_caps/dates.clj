(ns cognition-caps.dates
  (:use [clj-time [core :only (now plus years)] [format :only (formatter unparse)]])
  (:require [clojure.contrib.string :as s]))

(defn next-year []
  "Returns an RFC 1123 date string one year in the future"
  ; RFC 2616: HTTP/1.1 servers SHOULD NOT send Expires dates more than
  ; one year in the future, and requires GMT (which JodaTime doesn't give us)
  (s/replace-str "UTC" "GMT" ; JodaTime only gives UTC, RFC 1123 requires GMT
                 (unparse (formatter "EEE, dd MMM yyyy HH:mm:ss ZZZ")
                          (plus (now) (years 1)))))
