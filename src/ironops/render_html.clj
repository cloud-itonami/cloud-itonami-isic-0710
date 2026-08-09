(ns ironops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave1 Lane A-hand): this namespace drives the REAL actor stack
  (`ironops.operation` -> `ironops.governor` -> `ironops.store`) through
  a scenario adapted from this repo's own `ironops.sim` demo driver
  (`clojure -M:dev:run`, confirmed BEFORE writing this file to produce a
  sensible ledger against real site ids this harness seeds itself --
  `ironops.store` has no built-in `demo-data`/`seed-db`, so sites are
  added via `store/add-site` exactly as `ironops.sim` does), trimmed to
  a representative subset (one clean phase-3 auto-commit production
  log, one low-confidence escalate-then-approve, and four distinct
  HARD-hold reasons that never reach a human) and rendered
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verify
  by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [ironops.store :as store]
            [ironops.operation :as op]
            [ironops.ironopsllm :as advisor]
            [langgraph.graph :as g]))

;; ----------------------------- harness -----------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :mining-ops-coordinator :phase 3})

(defrecord LowConfidenceAdvisor []
  advisor/Advisor
  (advise [_ request _store]
    (assoc (advisor/mock-proposal {} request) :confidence 0.3)))

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a freshly seeded store through a scenario mixing every
  disposition this actor can reach: iron-site-001 (verified) clears a
  clean `:propose/log-production` auto-commit at phase 3; the same site
  then sees a low-confidence `:propose/coordinate-shipment` escalate
  (confidence floor only -- empty violations) and get human-approved;
  iron-site-001 HARD-holds a `:propose/flag-safety-concern` (ALWAYS a
  permanent hold on this actor -- never interactive, per
  `ironops.governor` check 4 / `safety-concern-escalation`); an
  `:extraction/extract` attempt HARD-holds on `:forbidden-operation`;
  iron-site-002 (present but `:verified? false`) HARD-holds
  `:propose/schedule-maintenance` on `:site-not-verified`; a
  never-registered subject HARD-holds on `:site-record-missing`. Every
  HARD hold never reaches a human. Returns the resulting store -- every
  field read by `render` below is real governor/store output, not a
  hand-typed copy."
  []
  (let [db (-> (store/mem-store)
               (store/add-site "iron-site-001"
                               {:name "Taconite Iron Mine"
                                :jurisdiction :us
                                :verified? true})
               (store/add-site "iron-site-002"
                               {:name "Pending Verification Mine"
                                :jurisdiction :br
                                :verified? false}))
        actor (op/build db)
        low-actor (op/build db {:advisor (->LowConfidenceAdvisor)})]
    ;; clean auto-commit
    (exec! actor "t1-log" {:op :propose/log-production :subject "iron-site-001"})

    ;; escalate (confidence only) then human approve
    (exec! low-actor "t1-ship" {:op :propose/coordinate-shipment :subject "iron-site-001"})
    (approve! low-actor "t1-ship")

    ;; HARD: safety concern is a permanent hold on this actor
    (exec! actor "t1-safety" {:op :propose/flag-safety-concern :subject "iron-site-001"})

    ;; HARD: extraction permanently out of scope
    (exec! actor "t1-extract" {:op :extraction/extract :subject "iron-site-001"})

    ;; HARD: site present but not verified
    (exec! actor "t2-maint" {:op :propose/schedule-maintenance :subject "iron-site-002"})

    ;; HARD: site record missing entirely
    (exec! actor "t3-missing" {:op :propose/coordinate-shipment :subject "unknown-site"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- hold-rule [f]
  (or (some-> f :basis first)
      (some-> f :violations first :rule)))

(defn- last-fact-for [ledger sid]
  (last (filter #(= (:subject %) sid) ledger)))

(defn- status-cell [ledger sid]
  (let [f (last-fact-for ledger sid)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (hold-rule f)]
        (case rule
          :safety-concern-escalation
          "<span class=\"critical\">HARD hold &middot; safety-concern (permanent)</span>"
          :forbidden-operation
          "<span class=\"critical\">HARD hold &middot; forbidden-operation</span>"
          :site-not-verified
          "<span class=\"critical\">HARD hold &middot; site-not-verified</span>"
          :site-record-missing
          "<span class=\"critical\">HARD hold &middot; site-record-missing</span>"
          (str "<span class=\"critical\">HARD hold &middot; "
               (esc (name (or rule :unknown))) "</span>")))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- verified-cell [site]
  (cond
    (nil? site) "<span class=\"critical\">missing</span>"
    (true? (:verified? site)) "<span class=\"ok\">verified</span>"
    :else "<span class=\"warn\">unverified</span>"))

(defn- site-row [ledger db sid]
  (let [s (store/site db sid)]
    (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc sid)
            (esc (or (:name s) "(missing)"))
            (esc (str (or (:jurisdiction s) :n-a)))
            (verified-cell s)
            (status-cell ledger sid))))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t))
          (esc (name (or op :n-a)))
          (esc subject)
          (esc (or (some->> basis (map name) (str/join ", "))
                   (some-> disposition name)
                   ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README Trust Controls, `ironops.governor`/`ironops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:propose/log-production</code></td><td><span class=\"ok\">phase-3 auto-commit when clean + verified site + conf ≥ floor</span></td></tr>"
   "        <tr><td><code>:propose/schedule-maintenance</code></td><td><span class=\"ok\">phase-3 auto-commit when clean + verified site + conf ≥ floor</span></td></tr>"
   "        <tr><td><code>:propose/coordinate-shipment</code></td><td><span class=\"ok\">phase-3 auto-commit when clean + verified site + conf ≥ floor</span></td></tr>"
   "        <tr><td><code>:propose/flag-safety-concern</code></td><td><span class=\"critical\">HARD always-hold (permanent; never interactive on this actor)</span></td></tr>"
   "        <tr><td><code>:extraction/extract</code> / blast / safety-authority</td><td><span class=\"critical\">HARD forbidden (coordination only; never overridable)</span></td></tr>"
   "        <tr><td>site missing / unverified</td><td><span class=\"critical\">HARD hold &middot; site-record-missing / site-not-verified</span></td></tr>"
   "        <tr><td>confidence &lt; floor (0.6), otherwise clean</td><td><span class=\"warn\">human approval (only interactive path)</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        site-ids ["iron-site-001" "iron-site-002" "unknown-site"]
        site-rows (str/join "\n" (map (partial site-row ledger db) site-ids))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0710 &middot; iron-ore mining operations coordination</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Iron ore mining operations coordination (ISIC 0710) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · extraction/blasting/safety-authority permanently HARD-blocked · safety-concern permanent hold</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Mine sites</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>ironops.store</code> via <code>ironops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site</th><th>Name</th><th>Jurisdiction</th><th>Verification</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     site-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Iron Ore Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. This actor coordinates production logging, maintenance scheduling, safety-concern flagging and shipment coordination only — extraction, blasting and mine-safety-authority decisions are permanently out of scope. A safety-concern flag is a durable hold (reviewable via the ledger), not an interactive approve/reject button.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op / condition</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        out-file (java.io.File. out)]
    (when-let [parent (.getParentFile out-file)]
      (.mkdirs parent))
    (spit out-file html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
