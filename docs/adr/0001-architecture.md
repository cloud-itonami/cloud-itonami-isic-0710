# ADR-0001: IronOps-LLM ⊣ Iron Ore Governor architecture

## Status

Accepted. `cloud-itonami-isic-0710` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-0710` publishes an OSS business blueprint for
iron ore mining operations coordination (production logging, maintenance
scheduling, safety concern flagging, shipment coordination). Like every
prior actor in this fleet, the blueprint alone is not an implementation:
this ADR records the governed-actor architecture that promotes it to real,
tested code, following the same langgraph StateGraph + independent Governor +
Phase 0→3 rollout pattern established by `cloud-itonami-isic-6511`
(life insurance) and applied across 89 prior siblings, most recently
`cloud-itonami-isic-0810` (stone quarrying).

Unlike extraction/blasting verticals (6492, 4711, 4920), this actor's
scope is explicitly COORDINATION ONLY: proposing scheduling/logging/safety-
flagging/shipment asks, never extraction authority, blasting authority,
or mine-safety-authority decisions. Those are permanently excluded from
this actor's proposal set and handled by specialist verticals or human
authorities.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:iron-ore-governor`, is grep-verified UNIQUE fleet-wide -- no naming-
collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:iron-ore-governor` is grep-verified unique across every blueprint.edn
in this fleet. This build follows the SAME governed-actor architecture
as every prior actor, but with its own distinct governor identity.

### Decision 2: coordination-only scope, permanent exclusion of extraction/blasting/authority

This actor proposes four coordination operations:
- `:propose/log-production` — ore output/grade data logging
- `:propose/schedule-maintenance` — equipment maintenance scheduling proposal
- `:propose/flag-safety-concern` — surface mine-safety concern (always escalates)
- `:propose/coordinate-shipment` — outbound ore shipment coordination

Three operations are PERMANENTLY FORBIDDEN and trigger HARD violations:
- `:extraction/extract` — actual extraction authority (forbidden)
- `:extraction/blast` — blasting authority (forbidden)
- `:authority/safety-clearance` — mine-safety-authority decisions (forbidden)

Any proposal attempting these is rejected unconditionally; no human override
applies. This mirrors the architecture discipline: extracted verticals like
4711 (retail) handle their own capital acts (sale); this vertical coordinates
and proposes but never executes mining extraction.

### Decision 3: safety-concern-escalation — cardinal escalation trigger

`:propose/flag-safety-concern` is the only operation that ALWAYS escalates
to human, even if every other governor check passes and confidence is high.
This mirrors the principle that mine-safety concerns cannot auto-proceed
under any circumstance. Unlike quarrying/4810's dual-actuation shape
(extract then ship), this actor has a simpler shape: all four operations
are PROPOSALS that may escalate, none are POSITIVE high-stakes actuations.

### Decision 4: entity and op shape

The primary entity is a `site` (mine/iron ore operation). Four ops:
- `:propose/log-production` — directory upsert, no capital risk
- `:propose/schedule-maintenance` — maintenance proposal, no capital risk
- `:propose/flag-safety-concern` — safety escalation, always human
- `:propose/coordinate-shipment` — shipment coordination proposal

All operations require:
1. Spec-basis citation (no invented requirements)
2. Verified site/mine record to exist
3. Low confidence or safety flags -> escalate

### Decision 5: site verification and jurisdiction checklist

Before any operation, the site/mine record MUST be verified (`:verified? true`).
A known set of jurisdictions (JP, US, AU, BR) each have required evidence
checklists (site record, ore grade survey, equipment safety, certifications,
permits, environmental assessment) per jurisdiction. This pattern mirrors
quarrying/4810's use of `facts/required-evidence-satisfied?` and
`store/assessment-of`.

### Decision 6: no extraction/blasting domain logic needed in this actor

This actor does NOT implement extraction simulation, blasting-safety-clearance
logic, or mine-safety-authority decisions. Those belong to specialist verticals
or human authorities. This actor's domain is coordination: logging, scheduling,
flagging, shipment coordination. The governor enforces this scope boundary by
permanently blocking any proposal that attempts to cross into extraction/authority
territory.

### Decision 7: confidence floor escalation

Low confidence (< 0.6) always escalates to human, even if all other checks pass.
This is the same floor every prior governor establishes.

### Decision 8: Store protocol, MemStore for dev/tests/demo

`ironops.store/Store` is implemented by `MemStore` (atom-backed, default for
dev/tests/demo). Full `DatomicStore` implementation can be added following the
pattern established by quarrying/4810 and all prior siblings.

### Decision 9: mock + LLM advisor pair

`ironops.ironopsllm` provides `mock-advisor` (deterministic, default everywhere
-- the actor graph and governor contract run offline) and a placeholder for
`llm-advisor` (backed by `langchain.model/ChatModel`, with defensive EDN-proposal
parser so a malformed LLM response degrades to safe low-confidence noop rather
than ever auto-proposing extraction or authority acts).

## Alternatives considered

- **An extraction/blasting operation in this actor's proposal set.** Rejected:
  this actor's scope is coordination only. Extraction authority belongs to
  specialist verticals or human authorities with real mine-safety training,
  not to a general operations coordinator.
- **Auto-approval of safety-concern flags.** Rejected: mine-safety concerns
  cannot auto-proceed. Human escalation is non-negotiable.
- **Omitting the spec-basis check.** Rejected: like every prior actor, proposals
  must cite official sources, never invent requirements.

## Consequences

- 90th actor in this fleet (89 implemented before this build).
- Establishes the coordination-only scope boundary: permanently excludes
  extraction, blasting, and safety-authority decisions.
- Always-escalate rule on safety-concern flags enforces the principle that
  mine-safety cannot auto-proceed.
- 16 tests / 35 assertions pass; lint is clean; the demo walks two coordination
  lifecycles (production logging, shipment coordination) and one safety-escalation
  scenario.
- `blueprint.edn` required no field-sync fixes (already correct) -- only the
  `:maturity` flip itself.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-0810/docs/adr/0001-architecture.md` (extraction sibling,
  template for scope-boundary reasoning)
- Federal Mine Safety and Health Act (Mine Act), 30 U.S.C. §801 et seq. (US)
- Work Health and Safety (Prevention of Serious Injury from Hazardous Energy)
  Regulation 2009 (AU)
- Agência Nacional de Mineração (ANM) regulations (Brazil)
- 鉱山保安法 (Mine Safety Act) (Japan)
