(ns ironops.sim
  "Iron ore operations simulation and demo driver."
  (:require [ironops.store :as store]
            [ironops.governor :as governor]
            [ironops.ironopsllm :as advisor]
            [ironops.phase :as phase]))

(defn run-proposal
  "Execute a single proposal through the governor."
  [st proposal-request]
  (let [context {:actor-id "ironops-0710"}
        proposal (advisor/mock-advisor context proposal-request)
        verdict (governor/check proposal-request context proposal st)]
    {:request proposal-request
     :proposal proposal
     :verdict verdict
     :phase-advanced (phase/advance-phase :intake verdict)}))

(defn demo
  "Run a simple demonstration of iron ore operations."
  []
  (let [st (store/mem-store)
        ;; Add a verified site
        st (store/add-site st "iron-site-001"
                           {:name "Taconite Iron Mine"
                            :jurisdiction :us
                            :verified? true})
        ;; Demo 1: Log production record (should pass)
        req1 {:op :propose/log-production
              :subject "iron-site-001"
              :value {:spec-basis "MSHA mining regulations"}}
        result1 (run-proposal st req1)
        ;; Demo 2: Flag safety concern (should escalate)
        req2 {:op :propose/flag-safety-concern
              :subject "iron-site-001"
              :value {:concern "Equipment vibration anomaly detected"
                      :spec-basis "mine safety protocol"}}
        result2 (run-proposal st req2)
        ;; Demo 3: Coordinate shipment (should pass)
        req3 {:op :propose/coordinate-shipment
              :subject "iron-site-001"
              :value {:spec-basis "logistics protocol"}}
        result3 (run-proposal st req3)]
    (println "=== Iron Ore Operations Demo ===\n")
    (println "1. Log production record:")
    (println "  Verdict OK:" (:ok? (:verdict result1)))
    (println "  Escalate:" (:escalate? (:verdict result1)))
    (println "\n2. Flag safety concern (always escalates):")
    (println "  Verdict OK:" (:ok? (:verdict result2)))
    (println "  Escalate:" (:escalate? (:verdict result2)))
    (println "\n3. Coordinate shipment:")
    (println "  Verdict OK:" (:ok? (:verdict result3)))
    (println "  Escalate:" (:escalate? (:verdict result3)))
    (println "\nDemo complete.")))

;; Entry point for `clojure -M:dev:run`
(defn -main [& _args]
  (demo))
