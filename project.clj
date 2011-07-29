(defproject cognition-caps "1.0.0-SNAPSHOT"
  :description "The software behind the Cognition Caps website"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.6.5"]]
  :dev-dependencies [[lein-ring "0.4.5"]]
  :ring {:handler cognition-caps.core/app})
