(ns ironops.export-test
  "`ironops.export/pedigree-for-production-record`'s cross-actor
  supply-chain-linkage export (ADR-2607999970) -- the SAME contract
  test shape `steelworks.export-test`/`autoparts.export-test` already
  establish for their own `pedigree-for-heat`/`pedigree-for-part-lot`,
  applied to this actor's operational-record-derived (not physics-
  simulation-derived) evidentiary basis."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [ironops.export :as export]
            [ironops.store :as store]))

(defn- seed-production-record
  "Adds a real production record to a real store and returns it back
  out via the store's own read path -- never a hand-typed map merely
  passed straight to `export/pedigree-for-production-record` without
  actually having gone through `ironops.store/add-production-record`
  first."
  [st record-id data]
  (let [st' (store/add-production-record st record-id data)]
    (store/production-record st' record-id)))

(deftest pedigree-for-production-record-builds-a-valid-pedigree-from-a-real-record
  (testing "a production record actually on file via ironops.store yields a shape-valid pedigree"
    (let [st (store/mem-store)
          rec (seed-production-record st "prod-pedigree-1"
                                       {:site-id "iron-site-001"
                                        :claimed-grade 62.5
                                        :grade-actual 62.5
                                        :grade-min 58.0
                                        :grade-max 68.0
                                        :quantity-tonnes 5200.0
                                        :price-per-unit 95.5})
          p (export/pedigree-for-production-record rec "2026-07-16")]
      (is (some? p))
      (is (true? (pedigree/valid? p)))
      (is (= "prod-pedigree-1" (:pedigree/subject-lot-id p)))
      (is (= "cloud-itonami-isic-0710" (:pedigree/issuing-actor p)))
      (is (= "2026-07-16" (:pedigree/issued-at p)))
      (is (not (contains? p :pedigree/upstream))
          "this actor is the origin of the chain -- never embeds an :upstream of its own")
      (testing "the claims are the record's OWN real recorded fields, not invented"
        (is (= 62.5 (pedigree/claim-value p :grade-actual)))
        (is (= 5200.0 (pedigree/claim-value p :quantity-tonnes))))
      (testing "the evidence-basis honestly discloses this is NOT a physics-simulation reading"
        (is (some #(re-find #"NOT a.*physics-2d simulation" %) (:pedigree/evidence-basis p)))))))

(deftest pedigree-for-production-record-tracks-the-real-recorded-values
  (testing "a different, independently-added production record yields a proportionally different claim -- proves the claim tracks the real stored data, not a fixed number"
    (let [st (store/mem-store)
          lean (seed-production-record st "prod-lean"
                                        {:site-id "iron-site-002"
                                         :grade-actual 45.0 :grade-min 40.0 :grade-max 68.0
                                         :quantity-tonnes 1000.0})
          rich (seed-production-record st "prod-rich"
                                        {:site-id "iron-site-002"
                                         :grade-actual 65.0 :grade-min 40.0 :grade-max 68.0
                                         :quantity-tonnes 8000.0})
          lean-p (export/pedigree-for-production-record lean "2026-07-16")
          rich-p (export/pedigree-for-production-record rich "2026-07-16")]
      (is (< (pedigree/claim-value lean-p :grade-actual)
             (pedigree/claim-value rich-p :grade-actual)))
      (is (< (pedigree/claim-value lean-p :quantity-tonnes)
             (pedigree/claim-value rich-p :quantity-tonnes))))))

(deftest pedigree-for-production-record-never-fabricates-missing-data
  (testing "a production record with no real :grade-actual/:quantity-tonnes on file yields nil, never an invented pedigree"
    (is (nil? (export/pedigree-for-production-record {:id "prod-x"} "2026-07-16")))
    (is (nil? (export/pedigree-for-production-record {:id "prod-x" :grade-actual 62.5} "2026-07-16"))
        "grade-actual alone, with no quantity-tonnes, is not a complete record")
    (is (nil? (export/pedigree-for-production-record {:id "prod-x" :quantity-tonnes 5200.0} "2026-07-16"))
        "quantity-tonnes alone, with no grade-actual, is not a complete record")
    (is (nil? (export/pedigree-for-production-record
               {:id "prod-x" :grade-actual "high" :quantity-tonnes 5200.0} "2026-07-16"))
        "a self-reported string grade (never a real numeric survey reading) is never packaged")
    (is (nil? (export/pedigree-for-production-record
               {:grade-actual 62.5 :quantity-tonnes 5200.0} "2026-07-16"))
        "no :id at all -> nil, never a pedigree with a blank subject-lot-id")))

(deftest pedigree-for-production-record-does-not-gate-on-its-own-grade-bounds
  (testing "this export fn packages the record's OWN claim unconditionally, even when the record's own grade-actual falls OUTSIDE its own recorded min/max -- gating on acceptance is the DOWNSTREAM governor's job, never this fn's"
    (let [st (store/mem-store)
          rec (seed-production-record st "prod-out-of-spec"
                                       {:site-id "iron-site-003"
                                        :grade-actual 30.0 :grade-min 58.0 :grade-max 68.0
                                        :quantity-tonnes 500.0})
          p (export/pedigree-for-production-record rec "2026-07-16")]
      (is (some? p))
      (is (true? (pedigree/valid? p)))
      (is (= 30.0 (pedigree/claim-value p :grade-actual))))))
