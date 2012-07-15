(ns cognition-caps.test.core
  (:use cognition-caps.core :reload
        [cognition-caps.config :only (base-config)]
        clojure.test
        [clojure.string :only (upper-case)]))

(def *old-cap-url* "/index.php/caps/caps-description/wi-river-cap")
(def *new-cap-url* "/caps/wi-river")

(defn- request
  "Performs a Ring request on the specified web app"
  ([resource &{ :keys [method params]
                :or {method :get params {}}}]
    (app {:request-method method :uri resource :params params})))

(defn- assert-status [status response msg]
  "Verifies that the expected status code matches that of the given response"
  (if msg
      (is (= status (:status response)) msg)
      (is (= status (:status response)))))

(defn- assert-redirect [response status new-url msg]
  "Verifies that the given request gives the provided status code and redirects to new-url"
  (if msg (is (and (= status (:status response))
                   (= new-url (get (:headers response) "Location"))) msg)
          (is (and (= status (:status response))
                   (= new-url (get (:headers response) "Location"))))))

(deftest basic-routes
  (assert-status 200 (request "/") "Root URL returns 200")
  (assert-status 404 (request "/foo") "Nonexistent URL returns 404"))

(deftest canonicalization
  (let [url (str (:cap-url-prefix base-config) "some-cap")
        url-trailing (str url "/")]
    (assert-redirect (request url-trailing) 301 url
                     "Trailing slashes redirect to URL without them")
    (assert-redirect (request (upper-case url)) 301 url
                     "Uppercase characters in URLs redirect to lower-case")
    (assert-redirect (request (str *old-cap-url* "/")) 301 *old-cap-url*
                     "Canonicalization redirect takes place before legacy URL redirect")))

(deftest old-cap-redirect
  (assert-redirect (request *old-cap-url*) 301 *new-cap-url*
                   "Old URL scheme redirects to the current one"))

(deftest test-redirects
  (is (= false "TODO: Load old_urls and make sure each URL is redirected appropriately")))

