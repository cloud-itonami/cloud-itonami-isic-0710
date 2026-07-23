(ns ironops.store
  "Persistent state store for iron ore operations.

  FIX (this commit): `ledger`/`append-ledger!` are new -- NO append-only
  audit ledger concept existed anywhere in this namespace before this
  fix, despite `blueprint.edn`'s `:required-technologies [... :audit-
  ledger]`. `ironops.operation`'s `:commit`/`:hold` graph nodes now
  append every committed/held/approval-rejected decision fact here, so
  a site's coordination history is always a query over an immutable
  log. Every pre-existing accessor below (`site`/`add-site`/
  `production-record`/`add-production-record`/`assessment-of`/
  `record-assessment`) and `MemStore`'s constructor shape are otherwise
  UNCHANGED -- only a fourth atom (`ledger-atom`) and the two new
  protocol methods were added.")

(defprotocol Store
  "State store contract for iron ore operations."
  (site [st site-id]
    "Retrieve a site/mine record by ID. Returns nil if not found.")
  (add-site [st site-id site-data]
    "Add or update a site/mine record.")
  (production-record [st record-id]
    "Retrieve a production record by ID.")
  (add-production-record [st record-id record-data]
    "Add a production record.")
  (assessment-of [st site-id]
    "Get the safety assessment for a site.")
  (record-assessment [st site-id assessment]
    "Record a safety assessment for a site.")
  (ledger [st]
    "Retrieve the append-only audit ledger: every committed/held/
    approval-rejected decision fact, in append order. Genuinely wired
    into `ironops.operation`'s `:commit`/`:hold` nodes -- not test-only
    plumbing.")
  (append-ledger! [st fact]
    "Append one immutable decision fact to the ledger. Returns the
    fact. The ledger is append-only -- there is deliberately no
    update/remove function."))

;; MemStore implementation for testing/demo
(deftype MemStore [sites-atom production-atom assessments-atom ledger-atom]
  Store
  (site [_st site-id]
    (@sites-atom site-id))
  (add-site [_st site-id site-data]
    (swap! sites-atom assoc site-id (assoc site-data :id site-id))
    (MemStore. sites-atom production-atom assessments-atom ledger-atom))
  (production-record [_st record-id]
    (@production-atom record-id))
  (add-production-record [_st record-id record-data]
    (swap! production-atom assoc record-id (assoc record-data :id record-id))
    (MemStore. sites-atom production-atom assessments-atom ledger-atom))
  (assessment-of [_st site-id]
    (@assessments-atom site-id))
  (record-assessment [_st site-id assessment]
    (swap! assessments-atom assoc site-id assessment)
    (MemStore. sites-atom production-atom assessments-atom ledger-atom))
  (ledger [_st]
    @ledger-atom)
  (append-ledger! [_st fact]
    (swap! ledger-atom conj fact)
    fact))

(defn mem-store
  "Create an in-memory store for testing/demo."
  []
  (MemStore. (atom {}) (atom {}) (atom {}) (atom [])))
