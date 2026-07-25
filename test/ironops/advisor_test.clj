(ns ironops.advisor-test
  "Tests for `ironops.ironopsllm`'s `Advisor` protocol + the SAME
  proposal-building logic it wraps.

  FIX (this commit): this namespace previously had NO `defprotocol
  Advisor` at all -- just plain functions (`mock-advisor`/`llm-advisor`/
  `advisor`) called directly, no test coverage of a protocol-based
  advisor seam existed. These tests prove `advisor/mock-advisor` /
  `advisor/llm-advisor` construct real `Advisor` instances, that
  `advise` returns the SAME proposal shape the pre-fix `mock-advisor`/
  `llm-advisor` fns (now `mock-proposal`/`llm-proposal`) always
  returned, and that `parse-edn-proposal`'s defensive EDN parsing is
  unaffected by the rename."
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [ironops.ironopsllm :as advisor]))

(deftest mock-advisor-constructs-an-advisor-instance
  (testing "mock-advisor returns something that satisfies the Advisor protocol"
    (is (satisfies? advisor/Advisor (advisor/mock-advisor)))))

(deftest llm-advisor-constructs-an-advisor-instance
  (testing "llm-advisor returns something that satisfies the Advisor protocol"
    (is (satisfies? advisor/Advisor (advisor/llm-advisor)))))

(deftest mock-advisor-advise-matches-the-preserved-proposal-shape
  (testing "MockAdvisor's advise returns the SAME shape as the preserved mock-proposal fn -- op/subject/effect/value/cites/confidence, byte-identical to the pre-fix mock-advisor fn"
    (let [request {:op :propose/log-production :subject "iron-site-001"}
          via-advise (advisor/advise (advisor/mock-advisor) request nil)
          via-direct (advisor/mock-proposal {} request)]
      (is (= via-direct via-advise))
      (is (= :propose/log-production (:op via-advise)))
      (is (= "iron-site-001" (:subject via-advise)))
      (is (= :propose (:effect via-advise)))
      (is (= "internal-mock" (get-in via-advise [:value :spec-basis])))
      (is (= ["mock-advisor"] (:cites via-advise)))
      (is (= 0.8 (:confidence via-advise))))))

(deftest mock-advisor-echoes-op-and-subject-for-every-op
  (testing "the mock proposal always echoes the request's own :op/:subject, for all four defined coordination ops"
    (doseq [op [:propose/log-production :propose/schedule-maintenance
                :propose/flag-safety-concern :propose/coordinate-shipment]]
      (let [proposal (advisor/advise (advisor/mock-advisor)
                                      {:op op :subject "iron-site-001"} nil)]
        (is (= op (:op proposal)))
        (is (= "iron-site-001" (:subject proposal)))))))

(deftest llm-advisor-delegates-to-the-same-mock-shape
  (testing "LlmAdvisor's advise (placeholder implementation) returns the SAME proposal shape as MockAdvisor today -- a real LLM call is a future swap of this one node, not a rewrite of the seam"
    (let [request {:op :propose/coordinate-shipment :subject "iron-site-001"}
          mock-result (advisor/advise (advisor/mock-advisor) request nil)
          llm-result (advisor/advise (advisor/llm-advisor) request nil)]
      (is (= mock-result llm-result)))))

(deftest advisor-selector-dispatches-by-strategy
  (testing "the advisor selector fn constructs the matching Advisor instance type"
    (is (instance? ironops.ironopsllm.MockAdvisor (advisor/advisor :mock)))
    (is (instance? ironops.ironopsllm.LlmAdvisor (advisor/advisor :llm)))
    (is (instance? ironops.ironopsllm.MockAdvisor (advisor/advisor :unknown-strategy))
        "unrecognized strategy falls back to the mock advisor, matching the pre-fix default case")))

(deftest parse-edn-proposal-parses-well-formed-edn
  (testing "parse-edn-proposal round-trips a well-formed EDN proposal string -- UNCHANGED from before this fix"
    (let [parsed (advisor/parse-edn-proposal "{:op :propose/log-production :confidence 0.9}")]
      (is (= :propose/log-production (:op parsed)))
      (is (= 0.9 (:confidence parsed))))))

(deftest parse-edn-proposal-defends-against-malformed-input
  (testing "parse-edn-proposal never throws on malformed input -- returns a safe zero-confidence noop, UNCHANGED from before this fix"
    (let [parsed (advisor/parse-edn-proposal "{not valid edn ]]]")]
      (is (= :noop (:op parsed)))
      (is (= 0.0 (:confidence parsed)))
      (is (string? (:error parsed))))))

(deftest parse-edn-proposal-never-evaluates-reader-macros
  (testing "parse-edn-proposal uses clojure.edn/read-string, not core edn/read-string -- unsafe reader macros like #= never execute against untrusted LLM output"
    (let [parsed (advisor/parse-edn-proposal "#=(+ 1 2)")]
      (is (= :noop (:op parsed))
          "a #= reader macro is rejected as malformed EDN, never evaluated"))))
