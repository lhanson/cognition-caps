(ns cognition-caps.test.data.simpledb
  (:use [clojure.test]
        [cognition-caps.data])
  (:require [cognition-caps.data.simpledb :as sdb]))

;;; Tests that caps with descriptions longer than SimpleDB's 1024 byte limit
;;; are spit into multiple attributes
(deftest split-large-descriptions
  (let [max-size 1024
        short-cap (make-Cap {:description "short description"})
        long-description (str (apply str (repeat (- max-size 2) "."))
                              " wordSpansBreak" (apply str (repeat 100 ".")))
        long-cap (make-Cap {:description long-description})
        long-cap-transformed (sdb/split-large-descriptions long-cap)]
    (is (= short-cap (sdb/split-large-descriptions short-cap))
        (str "Descriptions <= " max-size " bytes should not be split up"))
    (is (and (< max-size (count (:description long-cap)))
             (not (:description long-cap-transformed)))
        (str "Descriptions over " max-size " bytes should be split into integer-suffixed fields"))
    (let [description-keys (filter #(re-matches #"description_\d+" (name %))
                                   (keys long-cap-transformed))]
      (is (> (count description-keys) 1)
          "Expecting multiple description_ keys"))))

;;; Tests that caps with long descriptions split up into multiple attributes
;;; are correctly reassembled
(deftest merge-large-descriptions
  (let [cap {:description_1 "1 " :description_2 "2345 " :description_3 "6789 "}
        merged-cap (sdb/merge-large-descriptions cap)]
    (is (= "1 2345 6789 " (:description merged-cap))
        "Expected multiple integer-suffixed descriptions to be merged into one")
    (let [description-keys (filter #(re-matches #"description_\d+" (name %))
                                   (keys merged-cap))]
      (is (empty? description-keys)
          "Expecting intermediate description attributes to be removed"))))
