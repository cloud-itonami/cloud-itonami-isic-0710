(ns ironops.store-contract-test
  (:require [clojure.test :refer [deftest is testing]]
            [ironops.store :as store]))

(deftest mem-store-implements-contract
  (testing "MemStore site operations"
    (let [st (store/mem-store)]
      (is (nil? (store/site st "unknown")))
      (let [st' (store/add-site st "site-1" {:name "Test Mine"})]
        (let [site (store/site st' "site-1")]
          (is (= "site-1" (:id site)))
          (is (= "Test Mine" (:name site)))))))

  (testing "MemStore production records"
    (let [st (store/mem-store)]
      (is (nil? (store/production-record st "unknown")))
      (let [st' (store/add-production-record st "prod-1" {:tonnes 500})]
        (let [rec (store/production-record st' "prod-1")]
          (is (= "prod-1" (:id rec)))
          (is (= 500 (:tonnes rec)))))))

  (testing "MemStore assessments"
    (let [st (store/mem-store)]
      (is (nil? (store/assessment-of st "site-1")))
      (let [st' (store/record-assessment st "site-1" {:checklist #{:verified}})]
        (let [assessment (store/assessment-of st' "site-1")]
          (is (contains? (:checklist assessment) :verified)))))))

;; ----------------------------- append-only audit ledger (FIX, this commit) -----------------------------
;; `ledger`/`append-ledger!` did not exist anywhere in `ironops.store`
;; before this fix -- not dead code, the concept was entirely absent.
;; These tests prove the new protocol methods on the SAME `MemStore`
;; shape used above, never a parallel/alternate store implementation.

(deftest ledger-starts-empty
  (testing "a freshly created MemStore has an empty ledger"
    (let [st (store/mem-store)]
      (is (= [] (store/ledger st))))))

(deftest append-ledger-appends-in-order
  (testing "append-ledger! appends facts in append order, never reorders or dedupes"
    (let [st (store/mem-store)]
      (store/append-ledger! st {:t :committed :op :propose/log-production})
      (store/append-ledger! st {:t :governor-hold :op :propose/flag-safety-concern})
      (let [ledger (store/ledger st)]
        (is (= 2 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :governor-hold (:t (second ledger))))))))

(deftest append-ledger-returns-the-fact
  (testing "append-ledger! returns the fact it appended"
    (let [st (store/mem-store)
          fact {:t :committed :op :propose/coordinate-shipment}]
      (is (= fact (store/append-ledger! st fact))))))

(deftest ledger-is-independent-across-stores
  (testing "two separately-created MemStores have independent ledgers -- no shared mutable state leaking across instances"
    (let [st1 (store/mem-store)
          st2 (store/mem-store)]
      (store/append-ledger! st1 {:t :committed :op :propose/log-production})
      (is (= 1 (count (store/ledger st1))))
      (is (= 0 (count (store/ledger st2)))))))

(deftest ledger-survives-site-and-production-mutations
  (testing "site/production-record mutations on the same store do not disturb the ledger's independent atom"
    (let [st (store/mem-store)
          st (store/add-site st "site-1" {:name "Test Mine"})
          st (store/add-production-record st "prod-1" {:tonnes 500})]
      (store/append-ledger! st {:t :committed :op :propose/log-production})
      (is (= 1 (count (store/ledger st))))
      (is (some? (store/site st "site-1")))
      (is (some? (store/production-record st "prod-1"))))))
