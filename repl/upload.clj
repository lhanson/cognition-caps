(ns upload
  (:require [cognition-caps.data :as data]
            [cognition-caps.data [s3 :as s3] [simpledb :as sdb]]
            [cemerick.rummage :as rummage])
  (:use [clojure.contrib.trace] [clojure.pprint]))

(defn next-id []
  "Returns the next available item ID"
  (let [items     (data/get-items sdb/simpledb (atom 0))
        disabled  (data/get-disabled-items sdb/simpledb (atom 0))
        max-id    (reduce max (map :id (concat items disabled)))]
    (inc max-id)))

(defn upload-directory [path]
  "Uploads a directory of images named according to the convention specified in
   cognition-caps.migrations.images-to-s3, setting appropriate metadata for
   use as item images."
  (let [next-id (next-id)]
    (doseq [f (file-seq (clojure.java.io/file path))]
      (when (and (.isFile f) (not= ".DS_Store" (.getName f)))
        (println "Next item ID is" next-id)
        (println "Uploading file: " (.getName f))
        (s3/upload-image f next-id (.getName f))))))

; To create an item:
(defn upload-cap-data []
  (let [cap (data/make-Item {
              :id 312
              :nom "Rothko's Untitled"
              :url-title "rothkos-untitled"
              :description (str "<p>Mark Rothko (1903-1970) was a Latvian-American abstract expressionist painter who notably compared the work of modern painters to that of children. One of his \"multiform\" paintings from 1948, <i>Untitled</i>, foreshadows the color palette of this cap.</p><div style=\"text-align: center;\"><img src=\"http://upload.wikimedia.org/wikipedia/en/0/08/RothkoBlackGray.jpg\" width=\"258\" height=\"300\"/></div>"
                "<blockquote><p>In the spring of 1968, Rothko was diagnosed with a mild aortic aneurysm (defect in the arterial wall, that gradually leads to outpouching of the vessel and at times frank rupture). Ignoring doctor's orders, Rothko continued to drink and smoke heavily, avoided exercise, and maintained an unhealthy diet. However, he did follow the medical advice given not to paint pictures larger than a yard in height, and turned his attention to smaller, less physically strenuous formats, including acrylics on paper. Meanwhile, Rothko's marriage had become increasingly troubled, and his poor health and impotence resulting from the aneurysm compounded his feeling of estrangement in the relationship. Rothko and his wife Mell separated on New Year's Day 1969, and he moved into his studio.</p>"
                "<p>On February 25, 1970, Oliver Steindecker, Rothko's assistant, found the artist in his kitchen, lying dead on the floor in front of the sink, covered in blood. He had sliced his arms with a razor found lying at his side. During autopsy it was discovered he had also overdosed on anti-depressants. He was 66 years old.</p><cite><a href=\"http://en.wikipedia.org/wiki/Mark_Rothko\">Wikipedia</a></cite></blockquote>"
                "<p>We are somewhat confident that wearing this cap will bring on none of these tragedies.</p>"
                "<p>4-panel, 100% high-quality wool with earflaps. Hand wash cold, air dry. Eat well, ride safe, and take care of yourself. When you're gone, you're gone.</p>")
              ; We can cheat and use the nicer, nested representation here and it'll get marshalled to the flat version
              :image-urls { :main-0  "http://cognition-caps.s3.amazonaws.com/images/3ec163592b8832e6b356d8b71706a77e-0-main.jpg"
                            :front-0 "http://cognition-caps.s3.amazonaws.com/images/3ec163592b8832e6b356d8b71706a77e-0-front.jpg"
                            :thumb-0 "http://cognition-caps.s3.amazonaws.com/images/3ec163592b8832e6b356d8b71706a77e-0-thumb.jpg"
                            :cart-0  "http://cognition-caps.s3.amazonaws.com/images/3ec163592b8832e6b356d8b71706a77e-0-cart.jpg"
                            :main-1  "http://cognition-caps.s3.amazonaws.com/images/6edd3509b9136a632a52769ccdc79894-1-main.jpg"
                            :thumb-1 "http://cognition-caps.s3.amazonaws.com/images/6edd3509b9136a632a52769ccdc79894-1-thumb.jpg"
                            ;:main-2  "http://cognition-caps.s3.amazonaws.com/images/90442a69ebeaae1b1845d1c5c429775d-2-main.jpg"
                            ;:thumb-2 "http://cognition-caps.s3.amazonaws.com/images/90442a69ebeaae1b1845d1c5c429775d-2-thumb.jpg"
                          }
              :price-ids 5
              :sizes #{"1:-1" "2:-1" "3:-1"}
              :tags #{:item-type-cap}
              :user-id 1
              ; Update display-order only once you've verified the cap at /caps/NAME since it will then
              ; be visible to customers
              ;:display-order "0000"
            })
      sizes (data/get-sizes sdb/simpledb (atom 0))
      prices (data/get-prices sdb/simpledb (atom 0))
      cap (-> cap
              (sdb/dereference-sizes sizes)
              (sdb/dereference-price prices))]
    (pprint cap)
    (data/put-items sdb/simpledb (atom 0) [cap])))

(defn clobber [id]
  (println "Deleting" id)
  (rummage/delete-attrs sdb/sdb-conf "items" id))

