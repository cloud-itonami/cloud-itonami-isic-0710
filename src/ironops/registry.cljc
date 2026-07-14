(ns ironops.registry
  "Iron ore operations registry and calculations.")

(defn ore-grade-matches-claim?
  "Verify that claimed ore grade matches the independently computed grade.
  This is the same ground-truth-recompute discipline every prior actor's
  own cost/total-matching checks establish."
  [production-record]
  (let [claimed (:claimed-grade production-record)
        actual (:grade-actual production-record)
        min-bound (:grade-min production-record)
        max-bound (:grade-max production-record)]
    (and claimed actual
         (>= actual min-bound)
         (<= actual max-bound)
         (= claimed actual))))

(defn compute-production-value
  "Independently compute the value of produced ore based on:
  quantity (tonnes) x grade (%) x price-per-unit."
  [production-record]
  (let [qty (:quantity-tonnes production-record 0)
        grade (:grade-actual production-record 0)
        price (:price-per-unit production-record 0)]
    (* qty grade price)))

(defn shipment-record-valid?
  "Verify a shipment record has all required fields."
  [shipment]
  (and (:shipment-id shipment)
       (:site-id shipment)
       (:quantity-tonnes shipment)
       (:destination shipment)
       (true? (:verified? shipment))))
