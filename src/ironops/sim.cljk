(ns ironops.sim
  "Demo driver -- `clojure -M:dev:run`. Drives the REAL compiled
  `langgraph-clj` `StateGraph` (`ironops.operation/build`) end-to-end
  through a clean auto-commit, a HARD-blocked safety-concern flag, the
  other three HARD-block scenarios (forbidden operation, missing site,
  unverified site), and an escalate-then-approve / escalate-then-reject
  pair for a low-confidence proposal -- then prints the resulting audit
  ledger.

  FIX (this commit): the former `run-proposal` hand-called
  `ironops.ironopsllm/mock-advisor` then `ironops.governor/check`
  directly -- advisor -> governor, nothing else, never touching any
  graph. This now drives `ironops.operation/build`'s real compiled
  `langgraph.graph` via `langgraph.graph/run*`, the same as every other
  fixed actor in this fleet."
  (:require [langgraph.graph :as g]
            [ironops.store :as store]
            [ironops.operation :as operation]
            [ironops.ironopsllm :as advisor]))

(defrecord LowConfidenceAdvisor []
  advisor/Advisor
  (advise [_ request _store]
    (assoc (advisor/mock-proposal {} request) :confidence 0.3)))

(defn scenario [title]
  (println "\n===" title "==="))

(defn- exec-op [actor tid request]
  (g/run* actor {:request request} {:thread-id tid}))

(defn- approve! [actor tid by]
  (g/run* actor {:approval {:status :approved :by by}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid by]
  (g/run* actor {:approval {:status :rejected :by by}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a clean auto-commit path, a
  HARD-blocked (always-hold) safety-concern flag, three other HARD-block
  scenarios, and an escalate-then-approve / escalate-then-reject pair
  for a low-confidence proposal; print each result and the final audit
  ledger."
  []
  (println "Iron Ore Mining Operations Coordinator - Demo")

  (scenario "Clean production logging on a verified site (auto-commit)")
  (let [st (store/mem-store)
        st (store/add-site st "iron-site-001"
                           {:name "Taconite Iron Mine"
                            :jurisdiction :us
                            :verified? true})
        actor (operation/build st)
        result (exec-op actor "t1" {:op :propose/log-production :subject "iron-site-001"})]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger st)))

  (scenario "HARD-block: Flag safety concern (ALWAYS a permanent hold, never interactive)")
  (let [st (store/mem-store)
        st (store/add-site st "iron-site-001"
                           {:name "Taconite Iron Mine" :jurisdiction :us :verified? true})
        actor (operation/build st)
        result (exec-op actor "t2" {:op :propose/flag-safety-concern :subject "iron-site-001"})]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger st)))

  (scenario "HARD-block: Forbidden operation (extraction is out of scope)")
  (let [st (store/mem-store)
        actor (operation/build st)
        result (exec-op actor "t3" {:op :extraction/extract :subject "iron-site-001"})]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger st)))

  (scenario "HARD-block: Missing site record")
  (let [st (store/mem-store)
        actor (operation/build st)
        result (exec-op actor "t4" {:op :propose/coordinate-shipment :subject "unknown-site"})]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger st)))

  (scenario "HARD-block: Unverified site record")
  (let [st (store/mem-store)
        st (store/add-site st "iron-site-002"
                           {:name "Pending Verification Mine" :jurisdiction :br :verified? false})
        actor (operation/build st)
        result (exec-op actor "t5" {:op :propose/schedule-maintenance :subject "iron-site-002"})]
    (println "Decision:" (:decision (:state result)))
    (println "Ledger:" (store/ledger st)))

  (scenario "Escalate (low confidence, otherwise clean) -- compliance officer approves")
  (let [st (store/mem-store)
        st (store/add-site st "iron-site-001"
                           {:name "Taconite Iron Mine" :jurisdiction :us :verified? true})
        actor (operation/build st {:advisor (->LowConfidenceAdvisor)})
        held (exec-op actor "t6" {:op :propose/coordinate-shipment :subject "iron-site-001"})]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- compliance officer approves --")
    (let [approved (approve! actor "t6" "compliance-officer-01")]
      (println "Decision:" (:decision (:state approved)))
      (println "Ledger:" (store/ledger st))))

  (scenario "Escalate (low confidence, otherwise clean) -- compliance officer rejects")
  (let [st (store/mem-store)
        st (store/add-site st "iron-site-001"
                           {:name "Taconite Iron Mine" :jurisdiction :us :verified? true})
        actor (operation/build st {:advisor (->LowConfidenceAdvisor)})
        held (exec-op actor "t7" {:op :propose/coordinate-shipment :subject "iron-site-001"})]
    (println "Status:" (:status held) "Frontier:" (:frontier held))
    (println "-- compliance officer rejects --")
    (let [rejected (reject! actor "t7" "compliance-officer-01")]
      (println "Decision:" (:decision (:state rejected)))
      (println "Ledger:" (store/ledger st))))

  (println "\nDemo complete."))

;; Entry point for `clojure -M:dev:run`
(defn -main [& _args]
  (demo))

(comment
  (demo))
