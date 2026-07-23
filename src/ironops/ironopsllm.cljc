(ns ironops.ironopsllm
  "IronOps-LLM advisor for iron ore operations coordination.
  The LLM can propose coordination asks (logging, scheduling, flagging,
  shipment coordination) but CANNOT propose extraction, blasting, or
  safety-authority decisions -- those are forbidden by the governor.

  FIX (this commit): this namespace previously had NO `defprotocol
  Advisor` at all -- just plain functions (`mock-advisor`/`llm-advisor`/
  `advisor`) called directly by `ironops.sim`, bypassing any advisor
  seam. Every other actor in this fleet (`transportops.advisor/Advisor`,
  `luggage.advisor/Advisor`, etc.) has a real protocol so a genuine
  LLM-backed advisor is a swap, not a rewrite. This now matches that
  convention: a real `Advisor` protocol + `MockAdvisor`/`LlmAdvisor`
  records whose `advise` calls the SAME proposal-building logic below,
  UNCHANGED (the former top-level `mock-advisor`/`llm-advisor` fns are
  renamed `mock-proposal`/`llm-proposal` -- byte-identical bodies, only
  the name changed -- to make room for the protocol's own
  `mock-advisor`/`llm-advisor` CONSTRUCTOR functions, matching this
  fleet's naming convention where `(advisor/mock-advisor)` returns an
  `Advisor` instance, not a resolved proposal)."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; ----------------------------- proposal builders (UNCHANGED domain logic, renamed for a protocol-based home) -----------------------------

(defn mock-proposal
  "Deterministic mock proposal for demo/testing. Returns a safe proposal.
  UNCHANGED from the former top-level `mock-advisor` fn -- only the name
  changed, to make room for the `Advisor` protocol's own `mock-advisor`
  CONSTRUCTOR below."
  [_context request]
  (let [op (:op request)
        subject (:subject request)]
    {:op op
     :subject subject
     :effect :propose
     :value {:spec-basis "internal-mock"}
     :cites ["mock-advisor"]
     :confidence 0.8}))

(defn parse-edn-proposal
  "Parse EDN proposal from LLM response, via `clojure.edn/read-string`
  (not core `read-string`, which evaluates reader macros like `#=` and is
  unsafe against untrusted LLM output, and is also JVM-only in behavior
  unlike `clojure.edn`'s cross-platform reader).
  Defends against malformed LLM output by returning a low-confidence noop."
  [response-str]
  (try
    (edn/read-string response-str)
    (catch #?(:clj Exception :cljs :default) _e
      {:op :noop
       :confidence 0.0
       :error "Failed to parse proposal EDN"})))

(defn llm-proposal
  "LLM-backed proposal for iron ore operations.
  In real deployment, this would call an LLM to generate proposals.
  For now, delegates to `mock-proposal` as a placeholder. UNCHANGED from
  the former top-level `llm-advisor` fn -- only the name changed."
  [context request]
  (mock-proposal context request))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (advise [advisor request store]
    "Given a request map ({:op .. :subject ..}) and the store (currently
    unused by the mock/LLM-placeholder proposal builders but part of the
    seam so a real LLM-backed advisor can read site/production state
    before drafting), return a proposal map ready for
    `ironops.governor/check`. `:op` should be one of
    `ironops.operation/operations`; an unrecognized op is not specially
    handled here -- the governor's own checks independently reject
    whatever the advisor proposes regardless."))

(defrecord MockAdvisor []
  Advisor
  (advise [_ request _store]
    (mock-proposal {} request)))

(defrecord LlmAdvisor []
  Advisor
  (advise [_ request _store]
    (llm-proposal {} request)))

(defn mock-advisor
  "Create the deterministic mock `Advisor` for demo/testing/offline runs
  -- the actor's default. This record IS the injection point for a real
  LLM-backed advisor."
  []
  (->MockAdvisor))

(defn llm-advisor
  "Create the LLM-backed `Advisor` placeholder for deployment (delegates
  to the mock proposal builder today -- a real implementation would call
  an LLM here, e.g. via `langchain.model/ChatModel`, and defend its
  response through `parse-edn-proposal`)."
  []
  (->LlmAdvisor))

(defn advisor
  "Select the appropriate `Advisor` instance (mock or LLM) by strategy
  keyword. UNCHANGED selection logic (`:mock`/`:llm`/default all
  previously dispatched to the same fns this now constructs), now
  returning a protocol-based `Advisor` instance instead of a resolved
  proposal -- callers pass the result to `ironops.operation/build`'s
  `:advisor` option (or call `advise` on it directly with a request and
  a store)."
  [strategy]
  (case strategy
    :mock (mock-advisor)
    :llm (llm-advisor)
    (mock-advisor)))
