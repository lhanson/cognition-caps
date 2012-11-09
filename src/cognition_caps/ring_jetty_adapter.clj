(ns cognition-caps.ring-jetty-adapter
  "Adapter for the Jetty webserver. Adapted from Ring's Jetty adapter, but tweaked for my usage."
  (:import (org.eclipse.jetty.server Server Request NCSARequestLog)
           (org.eclipse.jetty.server.handler AbstractHandler RequestLogHandler GzipHandler HandlerCollection)
           (org.eclipse.jetty.server.nio SelectChannelConnector)
           (org.eclipse.jetty.util.thread QueuedThreadPool)
           (javax.servlet.http HttpServletRequest HttpServletResponse))
  (:require [ring.util.servlet :as servlet]))

(defn- proxy-handler
  "Returns an Jetty Handler implementation for the given Ring handler."
  [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request request response]
      (let [request-map  (servlet/build-request-map request)
            response-map (handler request-map)]
        (when response-map
          (servlet/update-servlet-response response response-map)
          (.setHandled base-request true))))))

(defn- configurator [server ring-handler]
  "The default configurator for the Jetty server"
  (.setHandler server (doto (new HandlerCollection)
                        (.addHandler (doto (new GzipHandler)
                                       (.setHandler (proxy-handler ring-handler))
                                       (.setMimeTypes "text/html,text/plain,text/xml,application/xhtml+xml,text/css,application/javascript,text/javascript,image/svg+xml")))
                        (.addHandler (doto (new RequestLogHandler) (.setRequestLog (NCSARequestLog.)))))))

(defn- create-server
  "Construct a Jetty Server instance."
  [options]
  (let [connector (doto (SelectChannelConnector.)
                    (.setPort (options :port 80))
                    (.setHost (options :host)))
        server    (doto (Server.)
                    (.addConnector connector)
                    (.setSendDateHeader true))]
    server))

(defn ^Server run-jetty
  "Start a Jetty webserver to serve the given handler according to the
  supplied options:

  :port         - the port to listen on (defaults to 80)
  :host         - the hostname to listen on
  :join?        - blocks the thread until server ends (defaults to true)
  :max-threads  - the maximum number of threads to use (default 50)"
  [ring-handler options]
  (let [^Server s (create-server (dissoc options :configurator))]
    (.setThreadPool s (QueuedThreadPool. (options :max-threads 50)))
    (configurator s ring-handler)
    (.start s)
    (when (:join? options true)
      (.join s))
    s))
