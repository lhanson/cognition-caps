(require '[cognition-caps.data :as data] '[cemerick.rummage :as sdb] '[cognition-caps.data.simpledb :as simpledb] '[cemerick.rummage.encoding :as enc] '[cognition-caps.config :as conf] :reload-all)
(def c simpledb/sdb-conf)

; Example to query attributes:
; (sdb/get-attrs c "items" 289)
; Hide an item
; (sdb/delete-attrs c "items" 289 :attrs #{:display-order})
