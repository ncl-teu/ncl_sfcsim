#!/usr/bin/env python3
"""Read-only/temporary-fixture tests for the six experiment safeguards."""
import concurrent.futures
import importlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "experiments" / "six"))
runner = importlib.import_module("common_runner")
analysis = importlib.import_module("analyze_results")


class RunnerRegression(unittest.TestCase):
    def test_parallel_seed_publication(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "seeds.json"
            with patch.object(runner, "SEED_MANIFEST_PATH", path):
                with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
                    results = list(executor.map(lambda _: runner.load_or_create_shared_seeds(151, 20), range(24)))
                self.assertTrue(all(value == results[0] for value in results))
                self.assertEqual(json.loads(path.read_text())["seeds"], results[0])
                self.assertEqual(list(Path(directory).iterdir()), [path])
                with self.assertRaises(RuntimeError):
                    runner.load_or_create_shared_seeds(152, 20)

    def test_finite_metrics_and_validation_markers(self):
        output = f"[SIMULATOR-REVISION] {runner.DATA_REVISION}\n"
        output += "CCR_data: 0.1 / IDR_image: 0.1 / NCCR_total: 0.2\n"
        for label in runner.ALGORITHM_LABELS:
            output += f"[{label}-VALIDATION] PASS\n[{label}]makespan: 10\n"
            output += f"[{label}]SLR: 2 / # of vCPUs: 3 / # of Hosts:2/# of Ins:1\n"
        metrics = runner.parse_output(output)
        self.assertTrue(runner.has_required_metrics(metrics))
        self.assertEqual(set(metrics["validated_algorithms"]), set(runner.ALGORITHM_LABELS))
        self.assertEqual(metrics["data_revision"], runner.DATA_REVISION)
        for invalid in (float("nan"), float("inf"), 0, -1, None):
            self.assertFalse(runner.has_required_metrics({**metrics, "nheft_makespan": invalid}))

    def test_classes_are_frozen(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "src"; source.mkdir()
            (source / "A.java").write_text("class A {}")
            classes = root / "classes"
            main = classes / "net/gripps/cloud/nfv/main/NFVSchedulingTest.class"
            main.parent.mkdir(parents=True); main.write_bytes(b"compiled-before")
            (classes / "A.class").write_bytes(b"compiled-source")
            lib = root / "lib"; lib.mkdir()
            (lib / "a.jar").write_bytes(b"dependency")
            out = root / "run"; out.mkdir()
            command, metadata = runner.freeze_runtime(root, ["java", "-cp",
                f"{classes}{os.pathsep}{lib / '*'}", "Main"], out)
            main.write_bytes(b"compiled-after")
            self.assertEqual((out / "classes_snapshot" / main.relative_to(classes)).read_bytes(), b"compiled-before")
            self.assertIn(str(out / "classes_snapshot"), command[2])
            self.assertEqual(len(metadata["classes_sha256"]), 64)
            self.assertEqual((out / "libraries_snapshot_0/a.jar").read_bytes(), b"dependency")

    def test_latest_complete_not_directory_mtime(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for timestamp, complete in (("20261004_010000", True), ("20261004_020000", True),
                                        ("20261004_030000", False)):
                path = root / f"run_{timestamp}_151_500"; path.mkdir()
                (path / "e01x_results.csv").write_text("seed,status\n1,ok\n")
                manifest = {"num_seeds": 500, "completed_runs": 500 if complete else 10,
                            "status_counts": {"ok": 500 if complete else 10}, "complete": complete,
                            "validation_required": True, "classes_sha256": "same",
                            "data_revision": runner.DATA_REVISION}
                (path / "run_manifest.json").write_text(json.dumps(manifest))
            # Touch the older result: selection must still follow the run timestamp.
            os.utime(root / "run_20261004_010000_151_500", None)
            self.assertEqual(analysis.find_latest_run_dir(root, "e01x_results.csv").name,
                             "run_20261004_020000_151_500")

    def test_mixed_versions_rejected_before_analysis(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for index, spec in enumerate(analysis.scenario_specs()):
                name = spec["scenario"]
                path = root / name / "run_20261004_010000_151_2"; path.mkdir(parents=True)
                (path / f"{name}_results.csv").write_text("seed,status\n1,ok\n2,ok\n")
                manifest = {"num_seeds": 2, "completed_runs": 2, "status_counts": {"ok": 2},
                            "complete": True, "validation_required": True, "seeds_used": [1, 2],
                            "classes_sha256": "other" if index == 23 else "same",
                            "data_revision": runner.DATA_REVISION}
                (path / "run_manifest.json").write_text(json.dumps(manifest))
            with patch.object(analysis, "FIVE_DIR", root), patch.object(analysis, "TARGET_SEED_COUNT", 2), \
                    patch.object(sys, "argv", ["analyze_results.py"]):
                with self.assertRaisesRegex(RuntimeError, "mix simulator versions"):
                    analysis.main()
            self.assertFalse(list(root.glob("analysis_*")))


if __name__ == "__main__":
    unittest.main(verbosity=2)
