(ns ironops.export
  "Cross-actor supply-chain-linkage export (ADR-2607999970, the THIRD
  applied link of the ADR-2607999950 cross-actor supply-chain-linkage
  pattern: `cloud-itonami-isic-0710` iron-ore mining -> `cloud-itonami-
  isic-2410` basic iron and steel). `pedigree-for-production-record`
  is this actor's own `pedigree-for-heat`/`pedigree-for-part-lot`
  equivalent -- same shape, same `kotoba.pedigree` contract, same
  'pure data transform over data already on file, never a live
  network call and never an invented claim' discipline every prior
  export fn in this fleet already establishes.

  THIS IS THE FIRST LINK IN THE CHAIN WHOSE EVIDENTIARY BASIS IS NOT A
  PHYSICS SIMULATION -- disclosed honestly, not glossed over.
  `cloud-itonami-isic-2410`/`cloud-itonami-isic-2930` both build their
  pedigrees from a REAL `physics-2d` time-stepped rigid-body
  simulation reading (`steelworks.robotics/tensile-test-telemetry-
  for` / `autoparts.robotics/pull-test-telemetry-for`) -- fully
  reproducible, deterministic, re-runnable from the same inputs to
  the identical number. `cloud-itonami-isic-0710` runs NO physics
  simulation at all: it is a COORDINATION-ONLY actor
  (ADR-2607141920) -- `ironops.governor`'s `forbidden-ops`
  permanently excludes extraction, blasting and mine-safety-authority
  decisions from its proposal set; this actor never models the
  physical extraction/ore-processing process itself. Its evidentiary
  basis is instead a LOGGED, ON-FILE PRODUCTION RECORD: an ore-grade
  survey reading and a measured tonnage a mine operator recorded via
  `ironops.store/add-production-record`, the SAME kind of ground-
  truth operational data `ironops.facts/production-grade-valid?`
  already independently re-verifies against the record's own
  recorded `:grade-min`/`:grade-max` bounds.

  This is a genuinely WEAKER kind of evidentiary basis than a
  deterministic physics simulation, and this namespace says so
  plainly rather than letting the shared `kotoba.pedigree` shape
  imply parity it does not have: a physics-2d simulation is fully
  reproducible from its inputs alone; a production record's
  `:grade-actual`/`:quantity-tonnes` are themselves human/instrument-
  reported measurements this actor did not itself generate or
  re-derive -- it only packages what is already on file, exactly once,
  never re-measuring or re-simulating it. A downstream actor's
  governor (e.g. `cloud-itonami-isic-2410`'s new
  `upstream-ore-pedigree-claims-out-of-tolerance-violations` HARD
  check, ADR-2607999970 Part 2) independently re-verifies the shape
  and the claim value against ITS OWN disclosed acceptance floor --
  the same 'ground truth, not self-report' discipline this pilot
  applies everywhere else, but it can only re-verify the NUMBER on
  file, never re-run a measurement this actor never took itself
  either. `kotoba.pedigree` itself makes no claim about HOW a number
  was obtained (see that ns's docstring: 'this library does not know
  what any particular claim key means'); the honesty obligation is on
  THIS export fn's evidence-basis citation and docstring, not on the
  shared schema."
  (:require [kotoba.pedigree :as pedigree]))

(defn pedigree-for-production-record
  "Builds a `kotoba.pedigree` record for `production-record`, a
  production record ALREADY on file via `ironops.store/add-
  production-record` (ADR-2607999970). Unlike
  `steelworks.export/pedigree-for-heat` / `autoparts.export/
  pedigree-for-part-lot`, this fn does NOT package a physics-
  simulation reading -- see this ns's own docstring for the honest
  disclosure of that difference. It only packages the recorded ore-
  grade survey and tonnage, mirroring how every other export fn in
  this fleet only ever materializes a package body over data already
  on file, never computes new evidence.

  `:pedigree/claims` reports TWO numeric fields, both already
  treated as this actor's ground-truth production data by existing
  code, never invented for this export fn:

    - `:grade-actual` -- the ore-grade survey reading (e.g. percent
      Fe content), the same field `ironops.facts/production-grade-
      valid?` independently re-verifies against the record's own
      recorded `:grade-min`/`:grade-max`, and `ironops.registry/ore-
      grade-matches-claim?` cross-checks against `:claimed-grade`.
    - `:quantity-tonnes` -- the measured tonnage, the same field
      `ironops.registry/compute-production-value` already uses as
      ground truth.

  `:site-id` (a string, when present) is NOT included in
  `:pedigree/claims` -- `kotoba.pedigree/valid?` requires every claim
  value to be numeric, never a self-reported string/id; it is instead
  folded into `:pedigree/evidence-basis` as a citation, and
  `:pedigree/subject-lot-id` is the production record's own `:id`
  (the specific extraction/lot record this pedigree is about, mirroring
  a heat's `:id`/a part-lot's `:id` in the two existing links).

  `issued-at` (an ISO date string) is a caller-supplied argument, not
  a wall-clock read -- pure/deterministic, the same discipline
  `pedigree-for-heat`/`pedigree-for-part-lot` already establish.

  This fn does NOT gate on `ironops.facts/production-grade-valid?`
  itself (whether the grade clears its OWN recorded min/max bounds)
  -- that is a separate, ALREADY-independently-checked fact about the
  record's internal consistency, not this fn's job; and it does NOT
  gate on whether the grade would clear any DOWNSTREAM actor's
  acceptance floor either -- that acceptance-threshold judgment
  belongs entirely to the downstream actor's own governor
  (`kotoba.pedigree`'s own library-wide contract, see that ns's
  docstring), mirroring how `pedigree-for-heat` packages a heat's
  telemetry unconditionally rather than pre-judging whether it would
  clear some downstream floor.

  Returns nil (never a fabricated pedigree) when `production-record`
  carries no real `:id`, or no numeric `:grade-actual`/`:quantity-
  tonnes` on file -- the SAME disclosed 'missing data != inventable'
  discipline every sibling export fn in this fleet already
  establishes."
  [{:keys [id site-id grade-actual quantity-tonnes]} issued-at]
  (when (and id (number? grade-actual) (number? quantity-tonnes))
    (pedigree/claim
     (str "PEDIGREE-" id) id "cloud-itonami-isic-0710"
     {:grade-actual grade-actual :quantity-tonnes quantity-tonnes}
     :evidence-basis
     [(str "ironops.store/production-record"
           (when site-id (str " (site " site-id ")"))
           " -- logged, on-file ore-grade survey + tonnage record, "
           "human/instrument-verified operational data, NOT a "
           "physics-2d simulation reading; this actor is coordination-"
           "only and runs no physics simulation of its own, see ns "
           "docstring / ADR-2607141920")]
     :issued-at issued-at)))
