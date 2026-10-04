# Execution-slot readiness regression

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
the existing unconstrained multi-task tail-only behavior is preserved.

## Run

From the repository root, with a JDK matching the existing `classes/`:

```sh
ant regression_tests
```

This runs 5,440 assertions, including 968 combinations of queue length,
constraint mode, DRT and IRT. Named tests cover exact gap boundaries, delayed
images, delayed data, core-check intervals, core-tail fallback and read-only
candidate evaluation. The same tests fail on the pre-fix classes with
`IRT invalidates internal gap: execution overlaps task 2`.

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
duration and complete task scheduling. Generation/reseed order matches
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

There is also a separate, pre-existing issue in `isAssignedInDuration()`:
while iterating sibling vCPUs, it reads the candidate vCPU's queue rather than
the sibling's queue. That core-utilization policy was deliberately not changed
in this execution-gap fix. Passing these tests does not certify the accuracy
of core-utilization enforcement or every inherited simulator behavior.
