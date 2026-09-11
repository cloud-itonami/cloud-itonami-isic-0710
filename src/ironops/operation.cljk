(ns ironops.operation
  "OperationActor -- one iron-ore mining coordination request = one
  supervised actor run, expressed as a REAL compiled `langgraph-clj`
  `StateGraph` (`langgraph.graph/state-graph` + `compile-graph`). The
  advisor (IronOps-LLM, `ironops.ironopsllm/Advisor`) is sealed into a
  single node (`:advise`); its proposal is ALWAYS routed through the
  independent `ironops.governor` (`:govern`) before anything commits to
  the SSoT.

  FIX (this commit): this namespace previously had NO StateGraph at all
  -- it was just a static op-registry (the `operations` map +
  `valid-operation?`/`operation-info`, PRESERVED UNCHANGED below). The
  actual 'flow' lived as a hand-called chain inside `ironops.sim`'s
  `run-proposal`, which called the advisor then the governor directly
  (advisor -> governor, nothing else -- literally a hand-called governor
  invoked directly from a sim/demo file, never through any graph).
  `ironops.ironopsllm` had no `defprotocol Advisor` either -- just plain
  functions. `ironops.store` had no append-only audit-ledger concept at
  all. `blueprint.edn` nonetheless claimed `:itonami.blueprint/maturity
  :implemented`, and the README claimed the 'governed-actor pattern
  (langgraph StateGraph + independent Governor + Phase 0->3 rollout)' --
  both false given the above.

  State machine:
  intake -> advise -> govern -> decide -+-> commit
                                         +-> request-approval -> commit
                                         +-> hold

  `ironops.governor/check` is reused UNCHANGED -- this fix only wires
  the existing mine-safety/coordination compliance policy into a real
  compiled graph and a real ledger, it does not redesign it. The
  Governor's own five checks (see its docstring) are ALL described as
  'a human approver CANNOT override them' -- but the verdict map's own
  `:hard?`/`:escalate?` flags encode a genuine two-tier signal within
  that, which this graph's `:decide` node reads directly (never
  re-deriving or second-guessing it):

    - `:hard?` true (`:violations` non-empty: forbidden-operation /
      no-spec-basis / site-record-missing / site-not-verified /
      safety-concern-escalation -- checks 1-4) is a PERMANENT block,
      routed straight to `:hold`, NEVER through `:request-approval`.
      This is where `:propose/flag-safety-concern` always lands (check
      4 fires unconditionally for that op, per the governor's own
      `safety-concern-escalation-violations`) -- unlike some sibling
      actors in this fleet, a safety-concern flag here is never offered
      an interactive human approve/reject button; it is a durable hold,
      reviewable via the ledger, matching the governor's own 'cannot
      override' framing for that check.
    - The ONLY case where `:hard?` is false but `:escalate?` is true is
      check 5 ALONE (confidence < `governor/confidence-floor`, with an
      EMPTY `:violations` vector -- no other check fired). That is this
      actor's sole human-interactive path: it routes through
      `:request-approval`, where a human dispatcher/compliance officer
      can review the otherwise-clean-but-low-confidence proposal and
      approve or reject it.
    - `:ok?` true (both flags false) commits directly.

  `ironops.phase/advance-phase` is reused UNCHANGED (the exact
  `(phase/advance-phase :intake verdict)` expression `ironops.sim`'s old
  `run-proposal` already computed) to annotate every audit fact with
  the actor's own intake/verify/hold phase-transition semantics --
  informational business-state, not itself a StateGraph routing input
  (routing comes only from the governor verdict above).

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (`ironops.store/mem-store`, or any `Store` impl)
    - the Advisor  (mock today; `ironops.ironopsllm/Advisor` is already
                     the injection point)

  One graph run = one coordination request (production logging /
  maintenance scheduling / safety-concern flag / shipment
  coordination). No unbounded inner loop -- each run is auditable and
  checkpointed. Every commit/hold/approval-rejected decision fact lands
  in `ironops.store`'s append-only ledger (`store/append-ledger!`),
  genuinely wired into both the `:commit` and `:hold` terminal nodes.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` GENUINELY pauses (checkpointed)
  the actor at the `:request-approval` node until a human
  dispatcher/compliance officer resumes the SAME compiled graph/thread
  with `{:approval {:status :approved :by ...}}` (or `:rejected`)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [ironops.ironopsllm :as advisor]
            [ironops.governor :as governor]
            [ironops.phase :as phase]
            [ironops.store :as store]))

;; ============================================================================
;; Op registry -- PRESERVED UNCHANGED from the pre-fix `ironops.operation`
;; ============================================================================

(def operations
  "Defined operations for iron ore mining coordination.
  All effects are `:propose` -- this actor does NOT execute mining or
  safety authority decisions. It proposes coordination asks that may
  require human approval."
  {
   :propose/log-production
   {:name "Log Production Record"
    :description "Record ore extraction quantity and grade data"
    :effect :propose
    :scope :coordination}

   :propose/schedule-maintenance
   {:name "Schedule Equipment Maintenance"
    :description "Propose equipment maintenance schedule"
    :effect :propose
    :scope :coordination}

   :propose/flag-safety-concern
   {:name "Flag Safety Concern"
    :description "Surface a mine-safety concern for escalation"
    :effect :propose
    :scope :coordination
    :escalates-always true}

   :propose/coordinate-shipment
   {:name "Coordinate Shipment"
    :description "Coordinate ore shipment logistics"
    :effect :propose
    :scope :coordination}
   })

(defn valid-operation?
  "Is this a known operation?"
  [op]
  (boolean (operations op)))

(defn operation-info
  "Get metadata for an operation."
  [op]
  (operations op))

;; ============================================================================
;; Audit-fact builders
;; ============================================================================

(def actor-context
  "The actor identity context `ironops.governor/hold-fact` expects --
  UNCHANGED from the `{:actor-id \"ironops-0710\"}` value
  `ironops.sim`'s old `run-proposal` already passed as its `context`
  argument."
  {:actor-id "ironops-0710"})

(defn- commit-fact
  "The audit fact written when a proposal commits. `:proposal` carries
  the full advisor proposal -- this actor has no separate stateful
  commit-record! entity for coordination proposals beyond the ledger
  itself (unlike `ironops.store/add-production-record`, which is a
  distinct, directly-called write path for actually-on-file production
  data, unrelated to this advise/govern/commit flow -- see
  `ironops.export`), so the ledger fact is the durable record of what
  happened."
  [request proposal verdict phase-advanced approval]
  (cond-> {:t              :committed
           :op             (:op request)
           :subject        (:subject request)
           :disposition    :commit
           :basis          (:cites proposal)
           :confidence     (:confidence verdict)
           :phase-advanced phase-advanced
           :proposal       proposal}
    approval (assoc :approved-by (:by approval))))

;; ============================================================================
;; Compiled StateGraph
;; ============================================================================

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- an `ironops.ironopsllm/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)

  The compiled graph's input map: `{:request ..}`, where `:request` is
  `{:op .. :subject ..}` (`:op` one of `operations`, `:subject` a
  site-id)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request        {:default nil}
         :proposal        {:default nil}
         :verdict         {:default nil}
         :phase-advanced  {:default nil}
         :decision        {:default nil}
         :approval        {:default nil}
         :audit           {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          {:proposal (advisor/advise advisor request store)}))

      (g/add-node :govern
        (fn [{:keys [request proposal]}]
          {:verdict (governor/check request actor-context proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request verdict]}]
          (let [hard?          (boolean (:hard? verdict))
                escalate?      (boolean (:escalate? verdict))
                ok?            (boolean (:ok? verdict))
                phase-advanced (phase/advance-phase :intake verdict)]
            (cond
              ;; HARD governor violations are a permanent block -- NEVER
              ;; routed through human approval, straight to :hold. This
              ;; is where :propose/flag-safety-concern always lands
              ;; (safety-concern-escalation-violations is always a hard
              ;; check, along with forbidden-operation/no-spec-basis/
              ;; site-record-missing/site-not-verified).
              hard?
              {:phase-advanced phase-advanced
               :decision :hold
               :audit [(assoc (governor/hold-fact request actor-context verdict)
                              :phase-advanced phase-advanced)]}

              ;; The ONLY way to reach here (hard? false, escalate?
              ;; true) is check 5 alone: low confidence, no other
              ;; violation. Human-approvable.
              escalate?
              {:phase-advanced phase-advanced
               :decision :escalate
               :audit [{:t              :approval-requested
                        :op             (:op request)
                        :subject        (:subject request)
                        :reason         :low-confidence
                        :phase-advanced phase-advanced
                        :confidence     (:confidence verdict)}]}

              ok?
              {:phase-advanced phase-advanced
               :decision :commit}

              ;; Defensive fallback -- unreachable under the governor's
              ;; current three-way ok?/escalate?/hard? partition
              ;; (see ns docstring), kept only so an unexpected verdict
              ;; shape fails safe to :hold rather than falling through.
              :else
              {:phase-advanced phase-advanced
               :decision :hold
               :audit [(assoc (governor/hold-fact request actor-context verdict)
                              :phase-advanced phase-advanced)]}))))

      (g/add-node :request-approval
        (fn [{:keys [request approval verdict phase-advanced]}]
          (if (= :approved (:status approval))
            {:decision :commit
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:decision :hold
             :audit [(assoc (governor/hold-fact request actor-context verdict)
                            :t :approval-rejected
                            :phase-advanced phase-advanced
                            :approver (:by approval))]})))

      (g/add-node :commit
        (fn [{:keys [request proposal verdict phase-advanced approval]}]
          (let [f (commit-fact request proposal verdict phase-advanced approval)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store hf))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [decision]}]
          (case decision
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [decision]}]
          (if (= :commit decision) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
