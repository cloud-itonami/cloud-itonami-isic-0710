(ns ironops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [ironops.governor :as governor]
            [ironops.store :as store]))

(deftest forbidden-operation-violations
  (testing "Extraction operations are forbidden"
    (let [request {:op :extraction/extract :subject "site-1"}
          proposal {}]
      (let [violations (governor/forbidden-operation-violations request proposal)]
        (is (seq violations))
        (is (= :forbidden-operation (:rule (first violations)))))))

  (testing "Blasting operations are forbidden"
    (let [request {:op :extraction/blast :subject "site-1"}
          proposal {}]
      (let [violations (governor/forbidden-operation-violations request proposal)]
        (is (seq violations))
        (is (= :forbidden-operation (:rule (first violations)))))))

  (testing "Safety authority operations are forbidden"
    (let [request {:op :authority/safety-clearance :subject "site-1"}
          proposal {}]
      (let [violations (governor/forbidden-operation-violations request proposal)]
        (is (seq violations))
        (is (= :forbidden-operation (:rule (first violations))))))))

(deftest coordination-operations-allowed
  (testing "Coordination operations don't trigger forbidden check"
    (let [st (store/mem-store)
          st (store/add-site st "site-1" {:verified? true})
          request {:op :propose/log-production :subject "site-1"}
          proposal {:cites ["source"] :value {:spec-basis "rule"}}]
      (let [verdict (governor/check request {} proposal st)]
        (is (not (some #(= :forbidden-operation (:rule %)) (:violations verdict))))))))

(deftest safety-concern-escalation
  (testing "Safety concerns always escalate"
    (let [st (store/mem-store)
          st (store/add-site st "site-1" {:verified? true})
          request {:op :propose/flag-safety-concern :subject "site-1"}
          proposal {:cites ["source"] :value {:spec-basis "rule"} :confidence 0.9}]
      (let [verdict (governor/check request {} proposal st)]
        (is (some #(= :safety-concern-escalation (:rule %)) (:violations verdict)))
        (is (true? (:escalate? verdict)))))))

(deftest site-verification-required
  (testing "Unverified site blocks operations"
    (let [st (store/mem-store)
          st (store/add-site st "site-1" {:verified? false})
          request {:op :propose/log-production :subject "site-1"}
          proposal {:cites ["source"] :value {:spec-basis "rule"}}]
      (let [verdict (governor/check request {} proposal st)]
        (is (some #(= :site-not-verified (:rule %)) (:violations verdict)))))))

(deftest missing-site-record
  (testing "Missing site record blocks operations"
    (let [st (store/mem-store)
          request {:op :propose/log-production :subject "unknown"}
          proposal {:cites ["source"] :value {:spec-basis "rule"}}]
      (let [verdict (governor/check request {} proposal st)]
        (is (some #(= :site-record-missing (:rule %)) (:violations verdict)))))))

(deftest confidence-floor
  (testing "Low confidence escalates even if clean"
    (let [st (store/mem-store)
          st (store/add-site st "site-1" {:verified? true})
          request {:op :propose/log-production :subject "site-1"}
          proposal {:cites ["source"] :value {:spec-basis "rule"} :confidence 0.4}]
      (let [verdict (governor/check request {} proposal st)]
        (is (true? (:escalate? verdict)))))))
