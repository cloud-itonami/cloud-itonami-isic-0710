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
