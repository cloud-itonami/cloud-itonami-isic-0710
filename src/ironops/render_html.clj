(ns ironops.render-html
  "Build-time HTML renderer. Drives the REAL actor stack deterministically.
   Usage: clojure -M:dev:render-html [out-file]."
  (:require [clojure.string :as str]
            [ironops.store :as store]
            [ironops.operation :as op]
            [langgraph.graph :as g]))

(defn- exec! [actor tid request]
  (g/run* actor {:request request} {:thread-id tid}))
(defn- approve! [actor tid by]
  (g/run* actor {:approval {:status :approved :by by}} {:thread-id tid :resume? true}))

(defn run-demo! []
  (let [db (-> (store/mem-store)
               (store/add-site "iron-site-001"
                 {:name "Taconite Iron Mine" :jurisdiction :us :verified? true})
               (store/add-site "iron-site-002"
                 {:name "Pending Verification Mine" :jurisdiction :br :verified? false}))
        actor (op/build db)]
    (exec! actor "t1" {:op :propose/log-production :subject "iron-site-001"})
    (exec! actor "t2" {:op :propose/flag-safety-concern :subject "iron-site-001"})
    (exec! actor "t3" {:op :extraction/extract :subject "iron-site-001"})
    (exec! actor "t4" {:op :propose/schedule-maintenance :subject "iron-site-002"})
    db))

(defn- esc [v]
  (-> (str v) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- hold-rule [f] (or (some-> f :basis first) (some-> f :violations first :rule)))
(defn- last-fact-for [ledger sid] (last (filter #(= (:subject %) sid) ledger)))
(defn- status-cell [ledger sid]
  (let [f (last-fact-for ledger sid)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold: " (esc (name (or (hold-rule f) :unknown))) "</span>")
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))
(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))
(def ^:private action-gate-rows
  ["        <tr><td><code>:propose/log-production</code></td><td><span class=\"ok\">auto-commit when clean + verified</span></td></tr>"
   "        <tr><td><code>:propose/flag-safety-concern</code></td><td><span class=\"critical\">HARD always-hold (never interactive)</span></td></tr>"
   "        <tr><td><code>:extraction/extract</code></td><td><span class=\"critical\">HARD forbidden (extraction out of scope)</span></td></tr>"
   "        <tr><td><code>:propose/schedule-maintenance</code></td><td><span class=\"warn\">verified-site required; else HARD block</span></td></tr>"])
(defn render [db]
  (let [ledger (vec (store/ledger db))
        s-ids ["iron-site-001" "iron-site-002"]
        srow (fn [sid] (let [s (store/site db sid)]
                         (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
                                 (esc sid) (esc (or (:name s) "(missing)"))
                                 (esc (str (or (:jurisdiction s) :n-a))) (status-cell ledger sid))))
        site-rows (str/join "\n" (map srow s-ids))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0710</title>"
     "<style>body{font:14px/1.5 sans-serif;margin:0;color:#1a1a1a;background:#f5f5f5}"
     ".bar{background:#3a1a0a;color:#fff;padding:1.2rem 2rem}.bar h1{margin:0;font-size:1.15rem}"
     "main{max-width:980px;margin:1.5rem auto;padding:0 1rem}"
     ".card{background:#fff;border-radius:8px;padding:1.2rem 1.4rem;margin-bottom:1.2rem;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
     ".muted{color:#777;font-size:.82rem}table{border-collapse:collapse;width:100%;font-size:.85rem}"
     "th,td{text-align:left;padding:.42rem .5rem;border-bottom:1px solid #eee}th{font-weight:600;color:#555}"
     ".ok{color:#0a7d33}.warn{color:#9a6700}.critical{color:#b41010;font-weight:600}"
     "code{background:#f0f0f0;padding:.1rem .3rem;border-radius:3px;font-size:.8rem}</style></head><body>"
     "<header class=\"bar\"><h1>Iron-ore mining ops (ISIC 0710)</h1></header><main>"
     "<section class=\"card\"><h2>Sites</h2>"
     "<table><thead><tr><th>Site</th><th>Name</th><th>Jurisdiction</th><th>Status</th></tr></thead><tbody>"
     site-rows "</tbody></table></section>"
     "<section class=\"card\"><h2>Action gate</h2>"
     "<table><thead><tr><th>Op</th><th>Gate</th></tr></thead><tbody>"
     (str/join "\n" action-gate-rows) "</tbody></table></section>"
     "<section class=\"card\"><h2>Audit ledger</h2>"
     "<table><thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead><tbody>"
     ledger-rows "</tbody></table></section></main></body></html>")))
(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!) out-file (java.io.File. out)]
    (.. out-file getParentFile mkdirs)
    (spit out-file (render db))
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
