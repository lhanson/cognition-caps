(ns cognition-caps.test.data.simpledb
  (:use [clojure.test]
        [cognition-caps.data])
  (:require [cognition-caps.data.simpledb :as sdb]))

; Tests that items with fields longer than SimpleDB's 1024 byte limit
; are spit into multiple attributes
(deftest split-large-fields
  (let [max-size 1024
        short-cap (make-Item {:description "short description"})
        long-description (str (apply str (repeat (- max-size 2) "."))
                              " wordSpansBreak" (apply str (repeat 100 ".")))
        long-cap (make-Item {:description long-description})
        long-cap-transformed (sdb/split-large-field long-cap :description)]
    (is (= short-cap (sdb/split-large-field short-cap :description))
        (str "Descriptions <= " max-size " bytes should not be split up"))
    (is (and (< max-size (count (:description long-cap)))
             (not (:description long-cap-transformed)))
        (str "Descriptions over " max-size " bytes should be split into integer-suffixed fields"))
    (let [description-keys (filter #(re-matches #"description_\d+" (name %))
                                   (keys long-cap-transformed))]
      (is (> (count description-keys) 1)
          "Expecting multiple description_ keys"))))

;;; Tests that items with long fields split up into multiple attributes
;;; are correctly reassembled
(deftest merge-large-field
  (let [item {:description_1 "1 " :description_2 "2345 " :description_3 "6789 "}
        merged-item (sdb/merge-large-field item :description)]
    (is (= "1 2345 6789 " (:description merged-item))
        "Expected multiple integer-suffixed descriptions to be merged into one")
    (let [description-keys (filter #(re-matches #"description_\d+" (name %))
                                   (keys merged-item))]
      (is (empty? description-keys)
          "Expecting intermediate description attributes to be removed"))))

;;; Verifies that image URLs are unmarshalled into a sorted map
(deftest sort-image-url-keys
  (let [unmarshalled (array-map (keyword (str sdb/*flat-image-prefix* "thumb-2")) "thumb-2.jpg"
                                (keyword (str sdb/*flat-image-prefix* "thumb-1")) "thumb-1.jpg"
                                (keyword (str sdb/*flat-image-prefix* "thumb-0")) "thumb-0.jpg"
                                (keyword (str sdb/*flat-image-prefix* "thumb-3")) "thumb-3.jpg")
        cap  (sdb/unmarshal-item unmarshalled nil nil nil)]
    (is (= (seq (:image-urls cap))
           (seq (array-map :thumb-0 "thumb-0.jpg" :thumb-1 "thumb-1.jpg"
                           :thumb-2 "thumb-2.jpg" :thumb-3 "thumb-3.jpg"))))))

(deftest dereference-prices
  (let [prices '({:id 1 :qty 1 :price 1.00} {:id 2 :qty 2 :price 2.00} {:id 3 :qty 2 :price 3.00})
        unmarshalled (sdb/dereference-price {:price-ids #{2 3}}prices)]
    (is (= 2 (count (:prices unmarshalled))))
    (is (= (rest prices) (:prices unmarshalled)))))

(deftest dereference-sizes
  (let [sizes (list {:id (long 1) :nom "Small-ish"} {:id (long 2) :nom "One Size Fits Most"} {:id (long 3) :nom "Large"})
        unmarshalled (sdb/dereference-sizes {:sizes #{"3:-1"} :tags #{:item-type-cap}} sizes)]
    (is (= 1 (count (:sizes unmarshalled))))
    (is (= (take-last 1 sizes) (:sizes unmarshalled)))))

