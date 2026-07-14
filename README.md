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
- **Flag Safety Concern** — surface mine-safety concerns (always escalates)
- **Coordinate Shipment** — outbound ore shipment coordination

**Scope boundary**: This actor handles COORDINATION only. Extraction authority,
blasting operations, and mine-safety-authority decisions are permanently
excluded and escalate to human specialists or dedicated verticals.

## Architecture

The governed-actor pattern (langgraph StateGraph + independent Governor +
Phase 0→3 rollout) applies three independent checks:

1. **Governor: Iron Ore Governor** — enforces domain rules
   - Forbidden operations (extraction, blasting, safety authority) -> HARD block
   - Spec-basis citation required (no invented requirements)
   - Site/mine record must be verified
   - Safety concerns always escalate to human
   - Low confidence escalates to human

2. **LLM Advisor: IronOps-LLM** — proposes coordination asks
   - Mock advisor (deterministic, offline) for demo/testing
   - LLM advisor placeholder (langchain.model/ChatModel) for deployment
   - Defensive EDN parsing (malformed responses -> safe noop)

3. **Store: MemStore** — persistent state for sites, production records, assessments
   - Protocol-based (MemStore for dev/test, DatomicStore for production)
   - Site verification, assessment checklists, production records

## Running

### Demo (mock advisor, offline)

```bash
clojure -M:dev:run
```

Walks through three scenarios:
- Production logging (proposed, clean)
- Safety concern flagging (always escalates)
- Shipment coordination (proposed, clean)

### Tests

```bash
clojure -M:test
```

16 tests / 35 assertions covering store contract, governor rules, and escalation
scenarios.

### Lint

```bash
clojure -M:lint
```

Static analysis (clj-kondo) on src/ and test/.

## Domain Model

**Sites** (iron ore mining operations) have:
- Jurisdiction (JP, US, AU, BR)
- Verification status (required before any operation)
- Associated production records and assessments

**Governance** enforces:
- Spec-basis citations (no invented requirements)
- Verified site records (no operation on unverified sites)
- Forbidden operation blocking (extraction/blasting/authority)
- Safety escalation (flag-safety-concern always escalates)
- Confidence floor (low confidence escalates)

## Architecture Decision Records

- `docs/adr/0001-architecture.md` — Full ADR explaining the governed-actor
  architecture, scope boundary, and design decisions.

## License

AGPL-3.0-or-later. See LICENSE file.

## Contributing

See CONTRIBUTING.md.

## Governance

See GOVERNANCE.md.
