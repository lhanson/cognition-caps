(ns cognition-caps.test.data.mysql
  (:use clojure.test cognition-caps.data.mysql))

; A map containing the minimum elements required for the MySQL cap mapping
(def minimal-capmap {:nom "Cap Name"
                     :description "Awesome cap"
                     :year 1 :month 1 :day 1})

(deftest cap-name-mapping
  ; Test that extraneous " Cap" suffixes are removed from cap names
  (are [nom] (= "Portland" (:nom (mapcap (assoc minimal-capmap :nom nom :url-title "portland"))))
       "Portland Cap"
       "Portland cap"
       "Portland cap   "))
