# Scheduling readiness and core-usage regression

## Pre-rerun audit (2026-10-04)

See [PRE_RERUN_AUDIT_20261004.md](PRE_RERUN_AUDIT_20261004.md) before launching
another full experiment batch. The diagnostic audit found additional image
readiness and bandwidth-boundary defects, and records model/activation-policy
questions separately. The report now contains their implemented fixes and the
24-scenario, three-seed preflight. `PreRerunAudit` retains the before/after
diagnostic fixtures; the automated passing suites run through `ant regression_tests`.

The 2026-10-04 state fixes supersede the earlier tail-only/virtual-node behavior:
all execution gaps are searched; virtual START/END bookkeeping does not activate
resources; cached image times stay earliest; dynamic transfers conserve bytes
and commit atomically. Rank, tolerance, gate rules and pair-link network modeling
are unchanged. Historical data and paper assets are untouched.

## Fix scope (2026-10-03)

`BaseVNFSchedulingAlgorithm.calcEST()` used to test an execution gap using
data-ready time (DRT), then shift the returned start to image-ready time (IRT).
The shifted execution interval could overlap an already scheduled task.

The fix computes `ready_time = max(DRT, IRT)` before testing either a front
gap, an internal gap, or a tail interval. Core-usage checks now receive that
same interval. Core-tail fallback also preserves both readiness constraints.

Example: existing tasks occupy `[0,10)` and `[20,30)`. A new task has DRT=10,
IRT=18 and computation time=5. Previously it was returned as `[18,23)`;
after the fix, the rejected gap leads to `[30,35)`.

No changes were made to image-source selection, dynamic bandwidth simulation,
rank/task ordering, tolerance, gate logic, or mode selection. In particular,
the unconstrained multi-task tail-only behavior was preserved in that first fix.
It is corrected by the later full insertion search described above.

## Run

From the repository root, with a JDK matching the existing `classes/`:

```sh
ant regression_tests
```

The execution-slot suite now runs 6,416 assertions, including 968 combinations of queue length,
constraint mode, DRT and IRT. Named tests cover exact gap boundaries, delayed
images, delayed data, core-check intervals, core-tail fallback and read-only
candidate evaluation. The same tests fail on the pre-fix classes with
`IRT invalidates internal gap: execution overlaps task 2`.

The core-usage suite additionally runs 4,134 assertions. See the 2026-10-04
fix below for its scope and failure reproduction.

The new scheduling-state suite adds 44,402 assertions for interval bandwidth,
more than 4,096 transfer segments, close event boundaries, reservation rollback,
cache reuse, preloaded images, virtual activation, schedule validation, and all
384 Comp/DRT/IRT ALL/ANY/EFT gate combinations. Total: 54,952 assertions.

The boundary suite adds 30,142 assertions for missing image sources, failed
commit plans, infeasible target paths, cache-only operation, repository DC
validation, bounded floating-point sampling and unchanged RNG consumption.
The first four suites run 85,094 assertions through `ant regression_tests`.
See the final section of the audit report for the same-seed before/after checks.

The numerical-transfer suite adds 11,014 assertions, bringing the five-suite
total to 96,108. It reproduces a sub-ULP remaining-byte residual at an occupancy
boundary, with and without a later event, and checks 2,000 randomized transfer
traces. The final segment may be omitted only when its end rounds to its start
and previously recorded positive-duration segments already satisfy the existing
byte-conservation tolerance. A genuinely untransmitted image still fails.
See the audit report's numerical repair section for failing-seed replay evidence.

The six runtime/analysis safeguards have separate temporary-fixture tests:

```sh
experiments/.venv/bin/python regression/six_runner_regression.py
```

The entire simulator and regression suite were also compiled and tested in a
fresh temporary directory with Java 8. Existing repository classes were built
with Java 21, so incrementally compiling those classes with Java 8 is not
supported; use a fresh output directory for a Java-8-only build.

To audit one full generated workflow after building:

```sh
java -Xmx1000m -cp 'test/regression:classes:lib/*' \
  net.gripps.cloud.nfv.regression.ExecutionSlotRegressionTest /path/to/config.properties
```

The optional configuration runs DHEFT, baseline NHEFT and IRT-only G-NHEFT on
deep copies of the same generated workflow/platform. It checks same-vCPU
execution overlap, image readiness, predecessor-data readiness, execution
duration, complete task scheduling, and actual concurrent core usage using a
half-open event sweep. Generation/reseed order matches
`NFVSchedulingTest`. Baseline tolerance and all gates are explicitly disabled;
only IRT is then enabled for G-NHEFT. This is not a sweep of all gate policies.
The audit never modifies the configuration or existing experiment results.

## Observed reproduction results

The following configurations were copied to temporary files and assigned the
listed seeds; no experiment CSVs, logs or snapshots were changed.

| Scenario/configuration | Seed | DHEFT overlap pairs | NHEFT overlap pairs | G-NHEFT overlap pairs |
| --- | --- | --- | --- | --- |
| five/e04y, before fix | 664527 | 65 | 5 | 7 |
| five/e04y, after fix | 664527 | 0 | 0 | 0 |
| five/e01x, after fix | 741896 | 0 | 0 | 0 |
| five/e08z, after fix | 741896 | 0 | 0 | 0 |

For e04y/664527, makespans changed from 55.7727 / 50.70690230179029 /
56.93898654970761 to 58.7032 / 50.70690230179029 / 56.93898654970761;
used-vCPU counts changed from 59 / 58 / 46 to 63 / 57 / 51 (DHEFT / NHEFT /
G-NHEFT). An unchanged makespan does not imply an unchanged schedule.

## Limits and follow-up

These are correctness regressions and three workflow audits, not a rerun of
the 24-scenario study. Existing paper tables and performance claims need
reevaluation using freshly generated results; a shared bug cannot be assumed
to cancel between algorithms.

The 2026-10-03 audit also identified a separate issue in `isAssignedInDuration()`:
while iterating sibling vCPUs, it reads the candidate vCPU's queue rather than
the sibling's queue. It was not changed in the execution-gap fix; the separate
2026-10-04 correction below now addresses it. Passing regression tests is not
a certification of every inherited simulator behavior.

## Sibling queue and interval fix (2026-10-04)

`BaseVNFSchedulingAlgorithm.isAssignedInDuration()` now reads each sibling
vCPU's own queue (`v.getVnfQueue()`), not the candidate queue
(`vcpu.getVnfQueue()`). An empty candidate queue must not hide a busy sibling.

Core checks use positive-duration intersections of half-open execution
intervals `[start,end)`. A sibling task finishing exactly at the candidate's
start or starting exactly at its finish is not concurrent. Zero-duration
tasks contribute no interval load; a zero-duration candidate is feasible
for this usage check.

The pre-existing policy is unchanged: add the incoming task's usage to the
maximum overlapping usage of each sibling, divide by the total number of
vCPUs on the core, round as before, and reject only if this average exceeds
the configured limit. This is a conservative per-sibling-maximum policy,
not a new instantaneous-utilization optimization. The limit remains 75 in
the experiment configurations; no experiment setting, ranking, image model,
gate, or execution-gap policy was changed.

### Regression evidence

- Before the source fix, `CoreUsageRegressionTest` fails with
  `must read busy sibling queue: (80+80)/2 > 75`.
- After the fix, all 4,134 core-usage assertions pass, along with all 5,440
  existing execution-slot assertions (9,574 total).
- Named checks cover two/three vCPUs, distinct sibling queues, threshold
  equality, multiple tasks in a sibling queue, idle siblings, endpoint-only
  contact, zero-duration tasks, repeated/read-only checks, and actual
  `calcEST` rejection of a core-overloaded execution gap.
- The parameter sweep covers one to three vCPUs, seven usage levels, four
  thresholds, and seven relative execution intervals.
- Both the normal Java 21 build and a fresh Java 8 Ant build pass. The Java 8
  output lives in a temporary directory, not the normal `classes/`.

### Post-fix workflow spot checks

Using temporary copies of `six` configurations, the following Java 8 audits
passed readiness, duration, scheduling completeness, same-vCPU overlap and
event-swept core-usage checks. In each cell, values are DHEFT / NHEFT /
G-NHEFT. Configurations and existing results were only read, not modified.

| Scenario | Seed | Same-vCPU overlap pairs | Overloaded cores | Peak core average |
| --- | --- | --- | --- | --- |
| six/e01x | 741896 | 0 / 0 / 0 | 0 / 0 / 0 | 65.5 / 69.0 / 69.5 |
| six/e04y | 664527 | 0 / 0 / 0 | 0 / 0 / 0 | 64.5 / 67.0 / 67.5 |
| six/e08z | 741896 | 0 / 0 / 0 | 0 / 0 / 0 | 59.0 / 64.5 / 64.0 |

Their makespans and used-vCPU counts match the corresponding pre-queue-fix
`six` CSV records. This is evidence for these three instances only, not a
claim that the fix leaves all 24 scenarios unchanged.

The `six` results generated before this correction are still pre-queue-fix
results. Existing CSVs, figures, tables and paper files were not overwritten.
A common bug across algorithms cannot be assumed to cancel; reassess data
before updating final empirical claims. Small workflow audits are not a
replacement for rerunning the complete study.
