(defproject cognition-caps "1.0.0-SNAPSHOT"
  :description "The software behind the Cognition Caps website"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.6.5"]
                 [com.cemerick/rummage "0.0.2"]
                 [ring/ring-jetty-adapter "0.3.11"]] ; start on Heroku
  :dev-dependencies [[com.mysql/connectorj "5.1.12"] ; migration from Hostmonster/MySQL
                     [lein-ring "0.4.5"]             ; start via local lein ring plugin
                     [robert/hooke "1.1.2"]]
  :ring {:handler cognition-caps.core/app}           ; handler used to start local app
  :hooks [leiningen.hooks.classpath])                ; add classpath entries for local execution
