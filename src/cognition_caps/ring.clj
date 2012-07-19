(ns cognition-caps.ring)

(defn redirect
  "Returns a Ring response map redirecting the request. Defaults to a permanent redirect unless status code is supplied"
  ([location] (redirect location 301))
  ([location status] {:status status :headers {"Location" location}}))

