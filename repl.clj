(require '[cognition-caps.data :as data] '[cemerick.rummage :as sdb] '[cognition-caps.data.simpledb :as simpledb] '[cemerick.rummage.encoding :as enc] '[cognition-caps.config :as conf] :reload-all)
(def c simpledb/sdb-conf)

; Example to query attributes:
; (sdb/get-attrs c "items" 289)
; Hide an item
; (sdb/put-attrs simpledb/sdb-conf "items" {:cemerick.rummage/id 289 :display-order "-"})
; Hide a batch of items
; (sdb/batch-put-attrs simpledb/sdb-conf "items" (for [x ids] {:cemerick.rummage/id x :display-order "-"}))
; Delete an attribute
; (sdb/delete-attrs c "items" 289 :attrs #{:display-order})
