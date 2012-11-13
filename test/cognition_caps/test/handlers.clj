(ns cognition-caps.test.handlers
  (:use cognition-caps.handlers
        clojure.test))

(deftest range-index-begin
  ; Make sure we don't blow up with wacky pagination values
  (let [default-limit 5
        query-fn (fn [begin per-page]
                   (assert (and 
                             (or (nil? begin)
                                 (and (string? begin)
                                      (>= (Integer/parseInt begin) 0)))
                             (>= per-page 1))))
        stats nil
        handler (partial base-ranged-query query-fn default-limit stats)]
    (handler {})
    (for [x ["0" "10" "-10" "" "-" "arf"]]
      (do (handler {:begin x})
      (handler {:limit x})))))
