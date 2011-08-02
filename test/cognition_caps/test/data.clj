(ns cognition-caps.test.data
  (:use [cognition-caps.data])
  (:use [clojure.test]))

(deftest cap-constructor
  (let [input-data {}
        cap (make-Cap input-data)]
    (is (:date-added cap) "Expected a default date to be set")
    (is (= (:display-order cap) 0) "Expected a default display order to be set")
    (is (= (:hide cap) false) "Expected caps to be displayed by default")))
