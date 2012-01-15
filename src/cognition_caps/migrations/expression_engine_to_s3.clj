(ns cognition-caps.migrations.expression-engine-to-s3
  (:require [cognition-caps.data :as data]
            [cognition-caps.data.simpledb :as simpledb]
            [cognition-caps.data.s3 :as s3]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.contrib.shell-out :as shell]))

;  Item images required:
;  +=====================================================================+
;  |   size   | name suffix*  | use                                      |
;  |==========|==========================================================+
;  |  487x487 | m-n-item.jpg  | main item page (all images)              |
;  |  73x73   | m-n-thumb.jpg | item page thumbnails (all images)        |
;  |  210x210 | m-0-front.jpg | front/products page (primary image only) |
;  |  102x102 | m-0-cart.jpg  | shopping cart page (primary image only)  |
;  +=====================================================================+
;   *m: md5 checksum of the image
;    n: image number (0=primary, 1=second, 2=third image, etc)
;
; On the old site we have:
;  /images/uploads/34a2d74f832c5e652099b4a58723ec32.JPG  (753 x 800),  lightbox image
;  /images/uploads/cache/34a2d74f832c5e652099b4a58723ec32-400x425.JPG  product page main image
;  /images/uploads/cache/34a2d74f832c5e652099b4a58723ec32-80x60.JPG    front page thumbnail
;  /images/uploads/cache/34a2d74f832c5e652099b4a58723ec32-100x109.JPG  caps page thumbnail
; Subsequent images (side, top...)
;  /images/uploads/25f5c7f0aeeeebac2e096bf0a9e6ef26.JPG (800x795)
;  /images/uploads/cache/25f5c7f0aeeeebac2e096bf0a9e6ef26-100x100.JPG  product page thumbnail

(defn- copy [uri file]
  (with-open [in (io/input-stream uri)
              out (io/output-stream file)]
    (io/copy in out)))

(defn convert-image [image-url image-index]
  "For a given image-url on the old site, does scaling and processing required
   for the new site. Uploads the resulting images to S3."
  (let [filename (subs image-url (inc (. image-url lastIndexOf "/")))
        file (doto (java.io.File/createTempFile filename ".jpg") (.deleteOnExit))
        orig-path (.getPath file)
        process-image! (fn [infile resize-geometry extent-geometry outfile]
                         (shell/sh "convert" infile "-resize" resize-geometry "-extent" extent-geometry "-gravity" "center" outfile)
                         (shell/sh "jpegoptim" "--strip-all" outfile))]
    (shell/with-sh-dir (System/getProperty "java.io.tmpdir")
      (copy image-url file)
      ; TODO: need to somehow store these in a data structure in the cap itself now
      (process-image! orig-path "487x487^" "487x487" "item.jpg")
      (process-image! orig-path "73x73^"   "73x73"   "thumb.jpg")
      (if (= 0 image-index)
        (do
          (process-image! orig-path "210x210^" "210x210" "front.jpg")
          (process-image! orig-path "102x102^" "102x102" "cart.jpg"))))))

(defn migrate-images []
  "Copies images from old ExpressionEngine site to Amazon S3 and updates links"
  (println "Migrating images from ExpressionEngine to Amazon S3")
  (let [simpledb-data simpledb/simpledb
        sdb-count (atom 0)
        caps (data/get-caps simpledb-data sdb-count)]
    (println "Loaded" (count caps) "caps from SimpleDB with" @sdb-count "queries")
    (loop [caps caps]
      (when-not (empty? caps)
        (println "Id" (:id (first caps)))
        (loop [images (:image-urls (first caps))]
          (when-not (empty? images)
            (println "\t" (first images))
            (recur (rest images))
            ))
        (recur (rest caps))
      ))))
