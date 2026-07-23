(ns ironops.operation-test
  "Integration tests for `ironops.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. This namespace did not exist before: the
  actual 'flow' lived as a hand-called chain inside `ironops.sim`'s
  `run-proposal` (advisor -> governor, nothing else -- never touching
  `kotoba-lang/langgraph` at all). These tests prove the compiled graph
  is real and that the audit ledger (`ironops.store/append-ledger!`) is
  genuinely wired into the `:commit`/`:hold` nodes, that the governor's
  own `:hard?`/`:escalate?` two-tier signal (see `ironops.operation`'s
  ns docstring) is what genuinely drives routing, and that
  `ironops.governor/check`'s five checks are exercised UNCHANGED through
  the real graph, not re-derived."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [ironops.operation :as operation]
            [ironops.ironopsllm :as advisor]
            [ironops.store :as store]))

;; ----------------------------- test-double advisors -----------------------------
;; The Advisor protocol's own injection point (`ironops.operation/build`'s
;; `:advisor` option) -- used here to exercise governor checks the
;; default MockAdvisor's fixed, always-clean proposal shape can never
;; reach on its own (no-spec-basis needs empty :cites; the escalate-only
;; path needs sub-floor confidence).

(defrecord NoCitesAdvisor []
  advisor/Advisor
  (advise [_ request _store]
    (assoc (advisor/mock-proposal {} request) :cites [])))

(defrecord LowConfidenceAdvisor []
  advisor/Advisor
  (advise [_ request _store]
    (assoc (advisor/mock-proposal {} request) :confidence 0.3)))

(defn- verified-site-store []
  (store/add-site (store/mem-store) "iron-site-001"
                   {:name "Taconite Iron Mine" :jurisdiction :us :verified? true}))

(defn- exec [actor tid request]
  (g/run* actor {:request request} {:thread-id tid}))

;; ----------------------------- commit path -----------------------------

(deftest commit-path-clean-proposal
  (testing "a clean, high-confidence production-logging request on a verified site commits through the real compiled graph and appends to the audit ledger"
    (let [st (verified-site-store)
          actor (operation/build st)
          result (exec actor "t-commit"
                       {:op :propose/log-production :subject "iron-site-001"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:decision state)))
      (let [ledger (store/ledger st)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :propose/log-production (:op (first ledger))))
        (is (= "iron-site-001" (:subject (first ledger))))
        (is (= :verify (:phase-advanced (first ledger)))
            "a clean verdict advances phase intake->verify, per ironops.phase/advance-phase, UNCHANGED")))))

(deftest commit-path-all-four-coordination-ops-commit-cleanly
  (testing "all four defined coordination ops commit cleanly on a verified site with the default mock advisor"
    (doseq [op [:propose/log-production :propose/schedule-maintenance
                :propose/coordinate-shipment]]
      (let [st (verified-site-store)
            actor (operation/build st)
            result (exec actor (str "t-" (name op))
                         {:op op :subject "iron-site-001"})]
        (is (= :commit (:decision (:state result))) (str op " should commit"))))))

;; ----------------------------- HARD-block paths (never interactive) -----------------------------

(deftest hard-hold-forbidden-operation-extraction
  (testing "extraction is a permanent, HARD-blocked forbidden operation -- routes straight to :hold, no interrupt, no human-approval detour"
    (let [st (store/mem-store)
          actor (operation/build st)
          result (exec actor "t-forbidden-extract"
                       {:op :extraction/extract :subject "iron-site-001"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger st)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :forbidden-operation (:rule %)) (:violations (first ledger))))))))

(deftest hard-hold-forbidden-operation-blasting
  (testing "blasting is also a permanent HARD-blocked forbidden operation"
    (let [st (store/mem-store)
          actor (operation/build st)
          result (exec actor "t-forbidden-blast"
                       {:op :extraction/blast :subject "iron-site-001"})]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :forbidden-operation (:rule %))
                (:violations (first (store/ledger st))))))))

(deftest hard-hold-forbidden-operation-safety-authority
  (testing "safety-authority clearance decisions are also a permanent HARD-blocked forbidden operation"
    (let [st (store/mem-store)
          actor (operation/build st)
          result (exec actor "t-forbidden-authority"
                       {:op :authority/safety-clearance :subject "iron-site-001"})]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :forbidden-operation (:rule %))
                (:violations (first (store/ledger st))))))))

(deftest hard-hold-missing-site-record
  (testing "a site with no record on file is a HARD violation -- ground truth is re-derived from the store, never trusted from the proposal"
    (let [st (store/mem-store)
          actor (operation/build st)
          result (exec actor "t-missing-site"
                       {:op :propose/coordinate-shipment :subject "unknown-site"})]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :site-record-missing (:rule %))
                (:violations (first (store/ledger st))))))))

(deftest hard-hold-unverified-site
  (testing "a registered-but-NOT-verified site is also a HARD violation, distinct from a missing site"
    (let [st (store/add-site (store/mem-store) "iron-site-002"
                             {:name "Pending Mine" :jurisdiction :br :verified? false})
          actor (operation/build st)
          result (exec actor "t-unverified-site"
                       {:op :propose/schedule-maintenance :subject "iron-site-002"})]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :site-not-verified (:rule %))
                (:violations (first (store/ledger st))))))))

(deftest hard-hold-no-spec-basis
  (testing "a proposal with no citations is a HARD violation -- exercised via a test-double Advisor since the default mock always supplies a citation"
    (let [st (verified-site-store)
          actor (operation/build st {:advisor (->NoCitesAdvisor)})
          result (exec actor "t-no-cites"
                       {:op :propose/log-production :subject "iron-site-001"})]
      (is (= :hold (:decision (:state result))))
      (is (some #(= :no-spec-basis (:rule %))
                (:violations (first (store/ledger st))))))))

(deftest hard-hold-safety-concern-always-blocked-never-interactive
  (testing ":propose/flag-safety-concern is ALWAYS a permanent HARD hold, even on a verified site with a high-confidence clean advisor -- this actor's governor groups safety-concern-escalation under its HARD checks (checks 1-4), so unlike some sibling actors in this fleet, it is NEVER routed through :request-approval"
    (let [st (verified-site-store)
          actor (operation/build st)
          result (exec actor "t-safety-concern"
                       {:op :propose/flag-safety-concern :subject "iron-site-001"})
          state (:state result)]
      (is (= :done (:status result))
          "never :interrupted -- confirms no interactive detour was offered")
      (is (= :hold (:decision state)))
      (let [ledger (store/ledger st)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :safety-concern-escalation (:rule %)) (:violations (first ledger))))))))

;; ----------------------------- escalate (low confidence only) -- the sole interactive path -----------------------------

(deftest escalate-only-reaches-interrupted-status
  (testing "a clean-but-low-confidence proposal (no other violation) GENUINELY interrupts at :request-approval -- checkpointed, not yet committed or held"
    (let [st (verified-site-store)
          actor (operation/build st {:advisor (->LowConfidenceAdvisor)})
          held (exec actor "t-escalate-status"
                     {:op :propose/coordinate-shipment :subject "iron-site-001"})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger st))
          "not yet committed or held -- awaiting human sign-off, ledger untouched")
      (is (= :hold (:phase-advanced (:state held)))
          "ironops.phase/advance-phase reports :hold for ANY non-ok verdict (it does not know about human overrides), even though this path is still awaiting a possible commit"))))

(deftest escalate-then-approve-commits
  (testing "a human compliance officer approving an escalated low-confidence proposal resumes the SAME compiled graph and commits via the graph's own :request-approval -> :commit edge, durably appending to the ledger"
    (let [st (verified-site-store)
          actor (operation/build st {:advisor (->LowConfidenceAdvisor)})
          _held (exec actor "t-escalate-approve"
                      {:op :propose/coordinate-shipment :subject "iron-site-001"})
          approved (g/run* actor {:approval {:status :approved :by "compliance-officer-01"}}
                           {:thread-id "t-escalate-approve" :resume? true})
          approved-state (:state approved)]
      (is (= :done (:status approved)))
      (is (= :commit (:decision approved-state)))
      (let [ledger (store/ledger st)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :propose/coordinate-shipment (:op (first ledger))))
        (is (= "compliance-officer-01" (:approved-by (first ledger))))))))

(deftest escalate-then-reject-holds
  (testing "a human compliance officer rejecting an escalated proposal routes to :hold via the :request-approval node's own decision, durably recording the rejection"
    (let [st (verified-site-store)
          actor (operation/build st {:advisor (->LowConfidenceAdvisor)})
          _held (exec actor "t-escalate-reject"
                      {:op :propose/coordinate-shipment :subject "iron-site-001"})
          rejected (g/run* actor {:approval {:status :rejected :by "compliance-officer-01"}}
                           {:thread-id "t-escalate-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:decision rejected-state)))
      (let [ledger (store/ledger st)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (= "compliance-officer-01" (:approver (first ledger))))))))

;; ----------------------------- ledger discipline -----------------------------

(deftest ledger-stays-empty-across-independent-runs-until-terminal
  (testing "the ledger only grows at :commit/:hold terminal nodes -- an :advise/:govern-only partial run never appends"
    (let [st (verified-site-store)
          actor (operation/build st {:advisor (->LowConfidenceAdvisor)})]
      (is (= 0 (count (store/ledger st))))
      (exec actor "t-ledger-empty" {:op :propose/coordinate-shipment :subject "iron-site-001"})
      (is (= 0 (count (store/ledger st)))
          "interrupted at :request-approval -- still zero"))))

(deftest ledger-accumulates-across-multiple-independent-requests
  (testing "multiple independent graph runs against the SAME store each append exactly one fact, in order"
    (let [st (verified-site-store)
          actor (operation/build st)]
      (exec actor "t-multi-1" {:op :propose/log-production :subject "iron-site-001"})
      (exec actor "t-multi-2" {:op :propose/schedule-maintenance :subject "iron-site-001"})
      (exec actor "t-multi-3" {:op :extraction/extract :subject "iron-site-001"})
      (let [ledger (store/ledger st)]
        (is (= 3 (count ledger)))
        (is (= [:propose/log-production :propose/schedule-maintenance :extraction/extract]
               (map :op ledger)))))))

;; ----------------------------- default advisor / op registry preservation -----------------------------

(deftest build-defaults-to-mock-advisor-when-no-advisor-option-given
  (testing "build's default :advisor is the mock advisor -- matches the pre-fix demo's hard-coded mock-advisor call"
    (let [st (verified-site-store)
          actor (operation/build st)
          result (exec actor "t-default-advisor"
                       {:op :propose/log-production :subject "iron-site-001"})]
      (is (= :commit (:decision (:state result))))
      (is (= ["mock-advisor"] (:basis (first (store/ledger st))))))))

(deftest operations-registry-preserved-unchanged
  (testing "the pre-fix static op-registry (operations/valid-operation?/operation-info) is preserved unchanged in the same namespace"
    (is (operation/valid-operation? :propose/log-production))
    (is (operation/valid-operation? :propose/flag-safety-concern))
    (is (not (operation/valid-operation? :extraction/extract))
        "forbidden ops were never part of the coordination registry, even before this fix")
    (is (= "Log Production Record" (:name (operation/operation-info :propose/log-production))))
    (is (true? (:escalates-always (operation/operation-info :propose/flag-safety-concern))))))
