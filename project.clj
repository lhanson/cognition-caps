(defproject cognition-caps "1.0.0-SNAPSHOT"
  :description "The software behind the Cognition Caps website"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-time "0.3.0"]
                 [compojure "1.0.1"]
                 [enlive "1.0.0"]
                 [com.cemerick/rummage "0.0.2"]
                 [net.java.dev.jets3t/jets3t "0.8.1"]
                 [commons-codec "1.6"]
                 [clj-logging-config "1.6"]
                 [log4j "1.2.16"]
                 [org.slf4j/slf4j-api "1.6.1"]
                 [org.slf4j/slf4j-log4j12 "1.6.1"]
                 [ring/ring-jetty-adapter "0.3.11"]] ; start on Heroku
  :dev-dependencies [[com.mysql/connectorj "5.1.12"] ; migration from Hostmonster/MySQL
                     [lein-ring "0.4.5"]             ; start via local lein ring plugin
                     [robert/hooke "1.1.2"]]
  :ring {:handler cognition-caps.core/app}           ; handler used to start local app
  :hooks [leiningen.hooks.classpath])                ; add classpath entries for local execution
