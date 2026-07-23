# cloud-itonami-isic-0710

**Iron Ore Mining Operations Coordinator** — an OSS business blueprint
and governed-actor implementation for iron ore mining operations (ISIC
Rev.5 0710).

## Overview

`cloud-itonami-isic-0710` is an independent LLM-backed operations coordinator
for iron ore mining, implementing the governed-actor pattern: an LLM advisor
(IronOps-LLM) behind an independent Governor (Iron Ore Governor) that enforces
domain compliance.

The actor proposes four coordination operations:
- **Log Production Record** — ore output/grade data logging
- **Schedule Maintenance** — equipment maintenance scheduling
- **Flag Safety Concern** — surface mine-safety concerns (always a permanent hold)
- **Coordinate Shipment** — outbound ore shipment coordination

**Scope boundary**: This actor handles COORDINATION only. Extraction authority,
blasting operations, and mine-safety-authority decisions are permanently
excluded and are HARD-blocked (never proposable, never human-overridable).

## Architecture

**As of this fix, the claims below are genuinely true.** Before this
fix they were NOT — see "Maturity / honest history" further down for
exactly what was fabricated and what this fix built. A REAL compiled
`langgraph-clj` StateGraph (`ironops.operation/build`) wires an
independent Governor in front of the LLM advisor's every proposal:

```
intake -> advise -> govern -> decide -+-> commit
                                       +-> request-approval -> commit
                                       +-> hold
```

1. **Governor: Iron Ore Governor** (`ironops.governor`, reused
   UNCHANGED by the graph's `:govern` node — this fix did not touch its
   policy, only its wiring) — five checks, in priority order:
   - Forbidden operations (extraction, blasting, safety authority) -> HARD block
   - Spec-basis citation required (no invented requirements) -> HARD block
   - Site/mine record must exist and be verified -> HARD block
   - Safety concerns always escalate -> HARD block (a **permanent hold**,
     never offered an interactive approve/reject button — see below)
   - Low confidence (< 0.6) escalates -> the actor's **sole interactive**
     `:request-approval` path, reached ONLY when no other check fired

   The governor's own verdict carries `:hard?`/`:escalate?` flags that
   the graph's `:decide` node reads directly, never re-derives: `:hard?`
   true (any of the first four checks) routes straight to `:hold`, no
   human-approval detour, ever. The ONLY way to reach `:hard?` false but
   `:escalate?` true is the confidence-floor check firing alone — that
   is what genuinely interrupts at `:request-approval` for a human
   dispatcher/compliance officer to approve or reject.

2. **LLM Advisor: IronOps-LLM** (`ironops.ironopsllm`) — proposes coordination asks
   - A real `Advisor` protocol + `MockAdvisor`/`LlmAdvisor` records
     (`(advisor/mock-advisor)` / `(advisor/llm-advisor)`), the injection
     point `ironops.operation/build`'s `:advisor` option consumes —
     swapping in a real LLM is a new record, not a rewrite of the seam
   - Mock advisor (deterministic, offline) for demo/testing
   - LLM advisor placeholder (delegates to the mock proposal builder
     today; a real LLM call is a future swap of this one node)
   - Defensive EDN parsing (malformed responses -> safe noop, via
     `clojure.edn/read-string`, never core `read-string`)

3. **Store: MemStore** (`ironops.store`) — persistent state for sites,
   production records, assessments, and a real append-only audit ledger
   - Protocol-based (`ironops.store/Store`; `MemStore` is the dev/test/
     demo implementation shipped here — a production deployment swaps
     in a different `Store` implementation behind the same protocol,
     the same injection pattern this actor already uses for the
     Advisor)
   - Site verification, assessment checklists, production records
   - `store/ledger` / `store/append-ledger!` — a real append-only audit
     ledger, genuinely wired into the compiled graph's `:commit`/`:hold`
     nodes. Every committed / held / approval-rejected decision is a
     durable, immutable fact; the ledger stays empty until a run
     actually reaches a terminal node (proven by
     `test/ironops/operation_test.clj`)

## Running

### Demo (mock advisor, offline)

```bash
clojure -M:dev:run
```

Drives the REAL compiled StateGraph through seven scenarios: a clean
auto-commit, the safety-concern flag's permanent HARD hold, the other
three HARD-block cases (forbidden operation, missing site, unverified
site), and an escalate-then-approve / escalate-then-reject pair for a
low-confidence proposal — printing the resulting audit ledger after
each.

### Tests

```bash
clojure -M:dev:test          # 41 tests / 129 assertions (test/)
clojure -M:dev:cross-repo-test  # 1 test / 5 assertions (test-cross-repo/)
```

Covers the Advisor protocol, governor rules (all five checks, exercised
through both direct `governor/check` calls and the real compiled
graph), the store contract including the append-only ledger, the
cross-actor pedigree export, and end-to-end StateGraph runs (commit,
every HARD-hold path, escalate-then-approve, escalate-then-reject,
ledger discipline).

### Lint

```bash
clojure -M:lint
```

Static analysis (clj-kondo) on src/, test/, and test-cross-repo/.

## Domain Model

**Sites** (iron ore mining operations) have:
- Jurisdiction (JP, US, AU, BR)
- Verification status (required before any operation)
- Associated production records and assessments

**Governance** enforces:
- Spec-basis citations (no invented requirements)
- Verified site records (no operation on unverified sites)
- Forbidden operation blocking (extraction/blasting/authority)
- Safety escalation (flag-safety-concern is always a permanent hold)
- Confidence floor (low confidence is the sole interactive-approval path)

## Files

| File | Role |
|---|---|
| `src/ironops/store.cljc` | `Store` protocol + `MemStore`: sites, production records, assessments, and the append-only audit ledger (`ledger`/`append-ledger!`) |
| `src/ironops/facts.cljc` | Per-jurisdiction (JP/US/AU/BR) mine-safety evidence catalog |
| `src/ironops/ironopsllm.cljc` | IronOps-LLM Advisor — a real `Advisor` protocol + `MockAdvisor`/`LlmAdvisor`; the same proposal-building logic as before, now behind the protocol |
| `src/ironops/registry.cljc` | Ore-grade/shipment-record calculation helpers |
| `src/ironops/governor.cljc` | **Iron Ore Governor** — 5 checks (forbidden-operation · no-spec-basis · site-record-missing/not-verified · safety-concern-escalation · confidence-floor), reused unchanged |
| `src/ironops/phase.cljc` | The intake/verify/propose/resolved/hold phase-transition tracker, reused unchanged to annotate audit facts |
| `src/ironops/operation.cljc` | **The real compiled `langgraph-clj` StateGraph** (`operation/build`): `intake -> advise -> govern -> decide -+-> commit / request-approval -> commit / hold`, `interrupt-before #{:request-approval}` for genuine human-in-the-loop approval. Also carries the op registry (`operations`/`valid-operation?`/`operation-info`), preserved unchanged from before this fix |
| `src/ironops/export.cljc` | Cross-actor supply-chain-linkage pedigree export (ADR-2607999970) — unrelated to the advise/govern/commit flow above, a separate direct write/read path over `store/add-production-record` |
| `src/ironops/sim.cljc` | demo driver — drives the real compiled StateGraph end-to-end |
| `test/ironops/*_test.clj` | advisor · governor · store contract/ledger · export · operation (real StateGraph, end-to-end) |
| `test-cross-repo/ironops/pedigree_integration_test.clj` | real store round-trip proof for the pedigree export chain |

## Architecture Decision Records

- `docs/adr/0001-architecture.md` — Full ADR explaining the governed-actor
  architecture, scope boundary, and design decisions.

## Maturity / honest history

`:implemented` — and, as of this fix, genuinely so. Before this fix,
this repository had **no StateGraph at all**: `ironops.operation` was
just a static op-registry (a data map + two lookup functions, no graph,
no wiring); the actual "flow" was a hand-called chain living in
`ironops.sim`'s `run-proposal` function (advisor -> governor, nothing
else — a governor invoked directly from a demo file, never through any
graph). `ironops.ironopsllm` had **no `defprotocol Advisor`** — just
plain functions (`mock-advisor`/`llm-advisor`/`advisor`). **No ledger
concept existed at all** in `ironops.store`. And this README itself
**actively made two false technical claims**: this "Architecture"
section claimed "The governed-actor pattern (langgraph StateGraph +
independent Governor...)" when no StateGraph existed anywhere in the
code, and the old "Store" bullet claimed "Protocol-based (MemStore for
dev/test, **DatomicStore for production**)" when no `DatomicStore` ever
existed. This was worse than a mere blueprint overclaim: the README's
own prose made specific, checkable, false statements about the code.
`blueprint.edn` also claimed `:itonami.blueprint/maturity :implemented`
and listed `:audit-ledger`/`:telemetry` as `required-technologies` —
also false given all of the above. Separately, the old README's "Tests"
section claimed "16 tests / 35 assertions", but `test/` only ever
contained 6 deftests (governor) + 1 deftest (store contract) = 7
deftests — that count was fabricated too.

Now: `ironops.operation/build` is a genuinely compiled `langgraph.graph`
StateGraph (`intake -> advise -> govern -> decide -+-> commit /
request-approval -> commit / hold`) with `interrupt-before
#{:request-approval}` + an in-memory checkpointer for real
human-in-the-loop resume; `ironops.ironopsllm/Advisor` is a real
protocol with `MockAdvisor`/`LlmAdvisor` records wrapping the SAME
proposal-building logic that existed before (renamed, not rewritten);
and `ironops.store/ledger`/`append-ledger!` is a real append-only audit
ledger, genuinely wired into both the `:commit` and `:hold` graph
nodes. `ironops.governor`'s five checks and `ironops.phase`'s
transition table are reused UNCHANGED — this fix only wires the
existing mine-safety/coordination compliance policy into a real
compiled graph and a real ledger, it does not redesign it. Proven
end-to-end by `test/ironops/operation_test.clj` (ledger stays empty
until a real commit/hold, all five governor checks exercised through
the real graph including a permanent hold for the safety-concern flag
that is NEVER offered an interactive approval, escalate-then-approve
and escalate-then-reject for the sole low-confidence-only interactive
path) — 41 tests / 129 assertions in `test/`, plus the pre-existing 1
test / 5 assertions in `test-cross-repo/` (unaffected by this fix).
CI (`.github/workflows/ci.yml`) was missing entirely and is added in
this fix.

## License

AGPL-3.0-or-later. See LICENSE file.

## Contributing

See CONTRIBUTING.md.

## Governance

See GOVERNANCE.md.
