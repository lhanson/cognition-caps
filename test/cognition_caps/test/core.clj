(ns cognition-caps.test.core
  (:use cognition-caps.core :reload
        clojure.test))

(defn- request
  "Performs a Ring request on the specified web app"
  ([resource &{ :keys [method params]
                :or {method :get params {}}}]
    (app {:request-method method :uri resource :params params})))

(defn- assert-status [status response]
  "Verifies that the expected status code matches that of the given response"
  (is (= status (:status response))))

(deftest basic-routes
  (assert-status 200 (request "/"))
  (assert-status 404 (request "/foo")))

(deftest old-cap-redirect
  (let [url  "/index.php/caps/caps-description/wi-river-cap"]
    (assert-status 302 (request url))
    (is (="/caps/wi-river" (get (:headers (request url)) "Location")))
    (assert-status 302 (request (str url "/")))))
