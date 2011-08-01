(ns cognition-caps.data
  (:use [clj-time.core :only (now)]))

(defprotocol DataAccess
  "A protocol abstracting access to product data"
  (get-caps [this] "Provides a sequence of caps currently stored"))

; Things to think about incorporating:
;   - where do we track inventory/availability? when we display a cap, we need
;     to show which sizes of it are in stock
;   - price? (YES, probably should go here)
;   - material-id and store material definitions in another entity? (NO, maybe just use the tag system i.e., material-cotton-poly, material-wool)
(defrecord Cap [id nom image-urls description tags date-added hide])

(defn make-Cap [& {:keys [id nom image-urls description tags date-added hide] 
                   :or {date-added (now) hide false}}]
  (Cap. id nom image-urls description tags date-added hide))
