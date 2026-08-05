import importlib.util
from pathlib import Path
import unittest

import numpy as np


SCRIPT = Path(__file__).with_name("nsidc_evaluation.py")
SPEC = importlib.util.spec_from_file_location("nsidc_evaluation", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class NsidcEvaluationTests(unittest.TestCase):
    def test_normalizes_percent_and_fraction_sic(self):
        np.testing.assert_allclose(MODULE.normalize_sic(np.array([0.0, 0.5, 1.0])), [0.0, 0.5, 1.0])
        np.testing.assert_allclose(MODULE.normalize_sic(np.array([0.0, 50.0, 100.0])), [0.0, 0.5, 1.0])

    def test_area_weighted_sic_metrics(self):
        prediction = np.array([[0.8, 0.0], [0.1, 0.7]])
        observation = np.array([[1.0, 0.0], [0.9, 0.8]])
        area = np.ones((2, 2))
        result = MODULE.weighted_sic_metrics(prediction, observation, area)
        self.assertAlmostEqual(result["rmsePercent"], 41.533119, places=6)
        self.assertAlmostEqual(result["baccPercent"], 83.333333, places=6)
        self.assertEqual(result["validCellCount"], 4)

    def test_sie_metrics_are_grouped_by_lead_month(self):
        rows = [
            {"year": "2022", "month": "1", "data": list(range(1, 13))},
            {"year": "2022", "month": "2", "data": list(range(2, 14))},
        ]
        observations = {}
        for month_index in range(2022 * 12, 2023 * 12 + 2):
            year, zero_month = divmod(month_index, 12)
            observations[(year, zero_month + 1)] = float(zero_month + 1)
        metrics, diagnostics = MODULE.compute_sie_lead_metrics(rows, observations)
        self.assertEqual(len(metrics["RMSD"]), 12)
        self.assertEqual(len(diagnostics), 12)
        self.assertEqual(diagnostics[0]["sampleCount"], 2)
        self.assertAlmostEqual(metrics["RMSD"][0], 0.0)


if __name__ == "__main__":
    unittest.main()
