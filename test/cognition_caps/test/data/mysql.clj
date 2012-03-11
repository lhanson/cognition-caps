(ns cognition-caps.test.data.mysql
  (:use clojure.test cognition-caps.data.mysql))

; A map containing the minimum elements required for the MySQL cap mapping
(def minimal-itemmap {:nom "Cap Name"
                      :description "Awesome cap"
                      :year 1 :month 1 :day 1})

(deftest cap-name-mapping
  ; Test that extraneous " Cap" suffixes are removed from cap names
  (are [nom] (= "Portland" (:nom (mapitem (atom 0) (assoc minimal-itemmap :nom nom :url-title "portland"))))
       "Portland Cap"
       "Portland cap"
       "Portland cap   "))
