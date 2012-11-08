(ns cognition-caps.test.core
  (:use cognition-caps.core :reload
        [cognition-caps.config :only (base-config)]
        clojure.test
        [clojure.string :only (upper-case)]))

(defn- request
  "Performs a Ring request on the specified web app"
  ([resource & { :keys [method params query-string]
                 :or {method :get params {} query-string nil}}]
    (app {:request-method method :uri resource :params params :query-string query-string})))

(defn- assert-status [status response msg]
  "Verifies that the expected status code matches that of the given response"
  (if msg
      (is (= status (:status response)) msg)
      (is (= status (:status response)))))

(defn- assert-redirect
  "Verifies that the given request gives the provided status code and redirects to new-url"
  ([response status new-url] (assert-redirect response status new-url nil))
  ([response status new-url msg]
    (if msg (is (and (= status (:status response))
                     (= new-url (get (:headers response) "Location"))) msg)
            (is (and (= status (:status response))
                     (= new-url (get (:headers response) "Location")))))))

(deftest basic-routes
  (assert-status 200 (request "/") "Root URL returns 200")
  (assert-status 404 (request "/foo") "Nonexistent URL returns 404")
  (let [error-msg "Exploits trying to hit ExpressionEngine member pages should 404"]
    (assert-status 404 (request "/index.php/member/register") error-msg)
    (assert-status 404 (request "/index.php/member/register" :method :post) error-msg)
    (assert-status 404 (request "/index.php" :query-string "/member/register") error-msg)
    (assert-status 404 (request "/index.php" :method :post :query-string "/member/register") error-msg)))

(deftest canonicalization
  (let [url (str (:cap-url-prefix base-config) "some-cap")
        url-trailing (str url "/")]
    (assert-redirect (request url-trailing) 301 url
                     "Trailing slashes redirect to URL without them")
    (assert-redirect (request (upper-case url)) 301 url
                     "Uppercase characters in URLs redirect to lower-case")))

(deftest test-redirects
  ; 301 root redirects
  (doseq [url ["/index.php"
               "/index.php/about"]]
    (assert-redirect (request url) 301 "/"))
  ; Various other 301s to specific pages
  (assert-redirect (request "/index.php/caps") 301 "/caps")
  (assert-redirect (request "/index.php/caps/caps-description/wi-river-cap") 301 "/caps/wi-river")
  (assert-redirect (request "/index.php/merch") 301 "/merch")
  (assert-redirect (request "/index.php/merch/merchandise-description/cognition-buttons-colorful") 301 "/merch/cognition-buttons-colorful")
  (assert-redirect (request "/index.php/blog/comments/blog-entry") 301 "/blog/blog-entry")
  (assert-redirect (request "/index.php/blog/P25") 301 "/blog")
  (assert-redirect (request "/index.php/blog/p40") 301 "/blog")
  (assert-redirect (request "/images/favicon(4).ico") 301 "/favicon.ico")
  ; 302 image redirects
  (doseq [url ["/images/uploads/00445d34233252c8d6510dfab2217bda.JPG"
               "/images/uploads/00445d34233252c8d6510dfab2217bda.jpg"
               "/images/uploads/00445d34233252c8d6510dfab2217bda.jpeg"
               "/images/uploads/00445d34233252c8d6510dfab2217bda.png"
               "/images/uploads/00445d34233252c8d6510dfab2217bda.png"
               "/images/uploads/cache/00445d34233252c8d6510dfab2217bda-100x117.JPG"]]
    (assert-redirect (request url) 302 (str (:old-site-url base-config) url)
                     "Links to images on the old site are redirected there")))
