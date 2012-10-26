(defproject cognition-caps "1.0.0-SNAPSHOT"
  :description "The software behind the Cognition Caps website"
  :main cognition-caps.core
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-time "0.3.0"]
                 [compojure "1.1.3"]
                 [enlive "1.0.0"]
                 [com.cemerick/rummage "1.0.1"]
                 [net.java.dev.jets3t/jets3t "0.8.1"]
                 [commons-codec "1.6"]
                 [clj-logging-config "1.6"]
                 [log4j "1.2.16"]
                 [org.slf4j/slf4j-api "1.6.1"]
                 [org.slf4j/slf4j-log4j12 "1.6.1"]
                 [ring "1.1.6"]
                 [rome "1.0"]]
  :dev-dependencies [[com.mysql/connectorj "5.1.12"] ; migration from Hostmonster/MySQL
                     [robert/hooke "1.1.2"]]
  :hooks [leiningen.hooks.classpath])                ; add classpath entries for local execution

