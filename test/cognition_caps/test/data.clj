(ns cognition-caps.test.data
  (:use [cognition-caps.data])
  (:use [clojure.test]))

(deftest item-constructor
  (let [orig-url-title "portland-cap"
        input-data {:url-title orig-url-title}
        item (make-Item input-data)]
    (is (= (url-title orig-url-title) (:url-title item)))
    (is (:date-added item) "Expected a default date to be set")
    (is (= (:display-order item) 0) "Expected a default display order to be set")
    (is (= (:hide item) false) "Expected items to be displayed by default")))

(deftest url-title-generation
  (is (= "plaid-envy" (url-title "Plaid Envy")))
  (is (= "portland" (url-title " PortlaNd ")))
  (is (= "black-w-red-stripe" (url-title "Black w/Red Stripe")))
  (is (= "jens" (url-title "Jen's")))
  (is (= "purple-w-blue.com" (url-title "Purple w/ Blue.com"))))

