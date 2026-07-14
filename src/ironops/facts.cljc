(ns ironops.facts
  "Iron ore mining domain facts and verification.")

(def jurisdictions
  "Registered mine-safety jurisdictions with their audit checklists."
  {
   :jp {:name "Japan (METI Industrial Safety Group)"
        :required-evidence [:site-record :ore-grade-survey :equipment-safety :personnel-certification]}
   :us {:name "United States (MSHA)"
        :required-evidence [:site-record :ore-grade-survey :equipment-safety :permit-valid]}
   :au {:name "Australia (DMIRS)"
        :required-evidence [:site-record :ore-grade-survey :equipment-safety :environmental-assessment]}
   :br {:name "Brazil (ANM)"
        :required-evidence [:site-record :ore-grade-survey :equipment-safety :environmental-assessment]}
   })

(defn known-jurisdiction?
  "Is this a registered jurisdiction?"
  [jurisdiction]
  (boolean (jurisdictions jurisdiction)))

(defn required-evidence-for
  "Get the required evidence checklist for a jurisdiction."
  [jurisdiction]
  (:required-evidence (jurisdictions jurisdiction) []))

(defn required-evidence-satisfied?
  "Are all required evidence items present for a jurisdiction?
  Evidence is a set of flags/records from the assessment."
  [jurisdiction evidence-set]
  (let [required (required-evidence-for jurisdiction)]
    (every? (fn [ev] (contains? evidence-set ev)) required)))

(defn production-grade-valid?
  "Verify ore grade is within recorded bounds."
  [grade-actual grade-min grade-max]
  (and (>= grade-actual grade-min)
       (<= grade-actual grade-max)))
