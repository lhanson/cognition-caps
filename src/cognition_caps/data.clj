(ns cognition-caps.data)

(defprotocol DataAccess
  "A protocol abstracting access to product data"
  (get-caps [this] "Provides a sequence of caps currently stored"))
