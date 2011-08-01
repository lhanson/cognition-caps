(ns leiningen.heroku-config
  "Push our database settings up to Heroku"
  (:use [clojure.contrib.shell-out])
  (:require [cognition-caps.config :as config]
            [clojure.contrib.string :as s]))

(defn heroku-config []
  (if (.. (java.io.File. "datasource.properties") (exists))
    (let [conf (select-keys config/db-config ["amazon-access-id" "amazon-access-key"])
          config-values (map #(str (s/replace-char \- \_ (s/upper-case (key %))) "=" (val %)) conf)]
      (println "Pushing configuration to Heroku:")
      (println (apply str "heroku config:add " (s/join " " config-values)))
      (println (apply sh "heroku" "config:add" config-values))
      (println "Heroku config complete!")
      0)
    (do
      (println "ERROR: I don't see datasource.properties. Are we running locally and are settings defined?")
      1)))
