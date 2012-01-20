(ns cognition-caps.migrations.images-to-s3
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
;  |  487x487 | m-n-main.jpg  | main item page (all images)              |
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

(def *old-prefix* "http://wearcognition.com/images/uploads/")

(defn- download! [uri file]
  (println "Downloading" uri "to" file)
  (with-open [in (io/input-stream uri)
              out (io/output-stream file)]
    (io/copy in out)))

(defn process-image! [orig-file resize-geometry extent-geometry suffix]
  "Takes an image file and resizes it with ImageMagick according to the given
   size and extent geometry and uploads it to Amazon S3 using its MD5 hash
   and the provided suffix. Returns the URL of the uploaded file."
  (println "Processing image, file" orig-file ", suffix" suffix)
  (let [orig-path (.getPath orig-file)
        outfile (doto (java.io.File/createTempFile "processed" ".jpg") (.deleteOnExit))]
    (shell/with-sh-dir (System/getProperty "java.io.tmpdir")
      (shell/sh "convert" orig-path "-resize" resize-geometry "-extent" extent-geometry "-gravity" "center" (.getPath outfile))
      (shell/sh "jpegoptim" "--strip-all" (.getPath outfile))
      (s3/upload-image outfile suffix))))

(defn migrate-images!
  "Copies images from old ExpressionEngine site to Amazon S3 and updates links"
  ([]
    (println "Migrating images from ExpressionEngine to Amazon S3")
    (let [simpledb-data simpledb/simpledb
          sdb-count (atom 0)
          ; TODO: debugging with only two
          caps (take 2 (data/get-caps simpledb-data sdb-count))]
      (println "Loaded" (count caps) "caps from SimpleDB with" @sdb-count "queries")
      (loop [caps caps]
        (when-not (empty? caps)
          (migrate-images! (first caps))
          (recur (rest caps))))))

  ([cap]
    "Takes a cap, transforms the images to the quantity and dimensions we need
     for the new site, uploads them to S3, and returns the new cap.
     If the images in the cap aren't hosted on the old site, does nothing."
    (let [simpledb-data simpledb/simpledb
          url-map (:image-urls cap)]
      (println "Doing urls:" url-map)
      (loop [new-images {}
             idx 0]
        (if (nil? (get url-map (keyword (str "main-" idx))))
          ; (assoc cap :image-urls url-map))
          (do
            (println "Returning cap with images" new-images)
            (assoc cap :image-urls url-map))
          (let [main-url (get url-map (keyword (str "main-" idx)))
                noo (println "Main-url" main-url)
                ;orig-file (-> (doto (java.io.File/createTempFile "orig" ".jpg") (.deleteOnExit))
               ; orig-file (->> (java.io.File/createTempFile "orig" ".jpg")
               ;                (download main-url))
                ]
            ; If we haven't already converted the image to S3
            (if (= *old-prefix* (subs main-url 0 (count *old-prefix*)))
              (let [orig-file (doto (java.io.File/createTempFile "orig" ".jpg") (.deleteOnExit))]
                (download! main-url orig-file)
                (println "Downloaded" orig-file ", idx" idx)
                (if (= idx 0)
                  (recur (-> new-images
                             (assoc (keyword (str "main-"  idx)) (process-image! orig-file "487x487^" "487x487" (str "-" idx "-main.jpg")))
                             (assoc (keyword (str "thumb-" idx)) (process-image! orig-file "73x73^"   "73x73"   (str "-" idx "-thumb.jpg")))
                             (assoc (keyword (str "front-" idx)) (process-image! orig-file "210x210^" "210x210" (str "-" idx "-front.jpg")))
                             (assoc (keyword (str "cart-"  idx)) (process-image! orig-file "102x102^" "102x102" (str "-" idx "-cart.jpg"))))
                         (inc idx))
                  (recur (-> new-images
                             (assoc (keyword (str "main-"  idx)) (process-image! orig-file "487x487^" "487x487" (str "-" idx "-main.jpg")))
                             (assoc (keyword (str "thumb-" idx)) (process-image! orig-file "73x73^"   "73x73"   (str "-" idx "-thumb.jpg"))))
                         (inc idx))))))))))
  )
