(ns cognition-caps.migrations.images-to-s3
  (:require [cognition-caps.data :as data]
            [cognition-caps.data [simpledb :as simpledb] [s3 :as s3]]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.contrib.shell-out :as shell])
  (:import [org.apache.commons.codec.digest.DigestUtils]))

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
;  On the old site we have:
;   /images/uploads/34a2d74f832c5e652099b4a58723ec32.JPG  (753 x 800),  lightbox image
;   /images/uploads/cache/34a2d74f832c5e652099b4a58723ec32-400x425.JPG  product page main image
;   /images/uploads/cache/34a2d74f832c5e652099b4a58723ec32-80x60.JPG    front page thumbnail
;   /images/uploads/cache/34a2d74f832c5e652099b4a58723ec32-100x109.JPG  items page thumbnail
;  Subsequent images (side, top...)
;   /images/uploads/25f5c7f0aeeeebac2e096bf0a9e6ef26.JPG (800x795)
;   /images/uploads/cache/25f5c7f0aeeeebac2e096bf0a9e6ef26-100x100.JPG  product page thumbnail

(def *old-prefix* "http://wearcognition.com/images/uploads/")

(defn- download! [uri file]
  (with-open [in (io/input-stream uri)
              out (io/output-stream file)]
    (io/copy in out)))

; TODO: these next two functions differ only in the call to upload. Refactor.
(defn process-main-image! [orig-file resize-geometry extent-geometry item-id suffix]
  "Takes an image file and resizes it with ImageMagick according to the given
   size and extent geometry and uploads it to Amazon S3, with the filename
   comprised of its MD5 and the provided suffix.
   Returns the URL of the uploaded file."
  (let [orig-path (.getPath orig-file)
        outfile (doto (java.io.File/createTempFile "processed" ".jpg") (.deleteOnExit))]
    (shell/with-sh-dir (System/getProperty "java.io.tmpdir")
      (shell/sh "convert" orig-path "-resize" resize-geometry "-extent" extent-geometry "-gravity" "center" (.getPath outfile))
      (shell/sh "jpegoptim" "--strip-all" (.getPath outfile))
      (s3/upload-image outfile
                       item-id
                       (str (org.apache.commons.codec.digest.DigestUtils/md5Hex (java.io.FileInputStream. outfile))
                            suffix)))))

(defn process-image! [orig-file resize-geometry extent-geometry item-id filename]
  "Takes an image file and resizes it with ImageMagick according to the given
   size and extent geometry and uploads it to Amazon S3 using the given
   filename. Returns the URL of the uploaded file."
  (let [orig-path (.getPath orig-file)
        outfile (doto (java.io.File/createTempFile "processed" ".jpg") (.deleteOnExit))]
    (shell/with-sh-dir (System/getProperty "java.io.tmpdir")
      (shell/sh "convert" orig-path "-resize" resize-geometry "-extent" extent-geometry "-gravity" "center" (.getPath outfile))
      (shell/sh "jpegoptim" "--strip-all" (.getPath outfile))
      (s3/upload-image outfile item-id filename))))

(defn migrate-images!
  "Copies images from old ExpressionEngine site to Amazon S3 and updates links"
  ([]
    (println "Migrating images from ExpressionEngine to Amazon S3")
    (let [simpledb-data simpledb/simpledb
          sdb-count (atom 0)
          items (data/get-items simpledb-data sdb-count)]
      (println "Loaded" (count items) "items from SimpleDB with" @sdb-count "queries")
      (loop [items items]
        (when-not (empty? items)
          (migrate-images! (first items))
          (recur (rest items))))))

  ([item]
    "Takes an item, transforms the images to the quantity and dimensions we need
     for the new site, uploads them to S3, and returns the new item.
     If the images in the item aren't hosted on the old site, does nothing."
    (println "Migrating images for item ID" (:id item))
    (let [simpledb-data simpledb/simpledb
          url-map (:image-urls item)]
      (if (:item-type-merch (:tags item))
        (println "---- Images: " (:image-urls item)))
      (loop [new-images {}
             idx 0]
        (if (nil? (get url-map (keyword (str "main-" idx))))
          (assoc item :image-urls new-images)
          (let [main-url (get url-map (keyword (str "main-" idx)))]
            ; If we haven't already converted the image to S3
            (if (= *old-prefix* (subs main-url 0 (count *old-prefix*)))
              (let [orig-file (doto (java.io.File/createTempFile "orig" ".jpg") (.deleteOnExit))]
                (download! main-url orig-file)
                (println (str "Downloaded " (.getPath orig-file) ", item image number " idx))
                (let [main-img-url (process-main-image! orig-file "487x487^" "487x487" (:id item) (str "-" idx "-main.jpg"))
                      main-md5     (second (re-find (re-pattern (str "([^/]*?)-" idx "-main.jpg"))
                                                    main-img-url))
                      thumb-url    (process-image! orig-file "73x73^" "73x73" (:id item) (str main-md5 "-" idx "-thumb.jpg"))]
                  (if (= idx 0)
                    (recur (-> new-images
                               (assoc (keyword (str "main-"  idx)) main-img-url)
                               (assoc (keyword (str "thumb-" idx)) thumb-url)
                               (assoc (keyword (str "front-" idx)) (process-image! orig-file "210x210^" "210x210" (:id item) (str main-md5 "-" idx "-front.jpg")))
                               (assoc (keyword (str "cart-"  idx)) (process-image! orig-file "102x102^" "102x102" (:id item) (str main-md5 "-" idx "-cart.jpg"))))
                           (inc idx))
                    (recur (-> new-images
                               (assoc (keyword (str "main-"  idx)) main-img-url)
                               (assoc (keyword (str "thumb-" idx)) thumb-url))
                           (inc idx))))))))))))

(defn migrate-blog-body-image!
  "Takes a placeholder URL (like {filedir}image.jpg), expands out the URL, and
   uploads it to S3. Returns the new S3 URL."
  [placeholder_url item-id]
  (let [image-file (doto (java.io.File/createTempFile "blogBodyImage" ".jpg") (.deleteOnExit))
        old-url (s/replace placeholder_url #"\{filedir_1\}" *old-prefix*)
        suffix (subs old-url (.lastIndexOf old-url "."))]
    (println "Downloading embedded body image" old-url)
    (download! old-url image-file)
    (shell/with-sh-dir (System/getProperty "java.io.tmpdir") (shell/sh "jpegoptim" "--strip-all" (.getPath image-file)))
    (s3/upload-image image-file
                     item-id
                     (str (org.apache.commons.codec.digest.DigestUtils/md5Hex (java.io.FileInputStream. image-file)) suffix))))

(defn migrate-blog-image! [blog-entry]
  (let [image-file (doto (java.io.File/createTempFile "blog" ".jpg") (.deleteOnExit))
        old-url (s/trim (str *old-prefix* (:image-url blog-entry)))
        suffix (subs old-url (.lastIndexOf old-url "."))]
    (println "Migrating blog image" old-url)
    (download! old-url image-file)
    (let [new-url (s3/upload-image image-file (:id blog-entry) (str (org.apache.commons.codec.digest.DigestUtils/md5Hex (java.io.FileInputStream. image-file)) suffix))]
      ; Now migrate any images we host that are embedded in the body of the post
      (loop [entry (assoc blog-entry :image-url new-url)
             filedir-images (rest (re-find #".*\"(\{filedir_1\}.+?)\".*" (:body blog-entry)))]
        (if (empty? filedir-images)
          entry
          (let [placeholder-url (first filedir-images)]
            (let [new-url (migrate-blog-body-image! placeholder-url (:id blog-entry))]
              (println "Replacing" placeholder-url "with" new-url)
              (recur (assoc entry :body (s/replace (:body blog-entry) placeholder-url new-url))
                     (rest filedir-images)))))))))

