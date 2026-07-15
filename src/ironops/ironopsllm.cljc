(ns ironops.ironopsllm
  "IronOps-LLM advisor for iron ore operations coordination.
  The LLM can propose coordination asks (logging, scheduling, flagging,
  shipment coordination) but CANNOT propose extraction, blasting, or
  safety-authority decisions -- those are forbidden by the governor."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn mock-advisor
  "Deterministic mock advisor for demo/testing. Returns a safe proposal."
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

(defn llm-advisor
  "LLM-backed advisor for iron ore operations.
  In real deployment, this would call an LLM to generate proposals.
  For now, delegates to mock-advisor as a placeholder."
  [context request]
  (mock-advisor context request))

(defn advisor
  "Select the appropriate advisor (mock or LLM)."
  [strategy context request]
  (case strategy
    :mock (mock-advisor context request)
    :llm (llm-advisor context request)
    (mock-advisor context request)))
