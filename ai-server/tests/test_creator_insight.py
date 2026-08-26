from __future__ import annotations

import unittest

from modules.creator_insight.module import _highlights, _needs_deep, _normalize_payload
from runtime.contracts import ModuleRequestError


class CreatorInsightPayloadTest(unittest.TestCase):

    def test_normalizes_metrics(self) -> None:
        result = _normalize_payload({
            "periodLabel": "近四周",
            "startDate": "2026-07-20",
            "endDate": "2026-08-13",
            "readCount": 12,
            "previousReadCount": -2,
            "likeCount": "3",
        })
        self.assertEqual(result["readCount"], 12)
        self.assertEqual(result["previousReadCount"], 0)
        self.assertEqual(result["likeCount"], 3)
        self.assertEqual(result["workCount"], 0)

    def test_rejects_missing_period(self) -> None:
        with self.assertRaises(ModuleRequestError):
            _normalize_payload({"periodLabel": "近四周"})

    def test_limits_highlights_to_three_short_items(self) -> None:
        result = _highlights(["阅读稳稳增长", "喜欢也有回应", "新作品正在积累", "不应返回第四条"])
        self.assertEqual(result, ["阅读稳稳增长", "喜欢也有回应", "新作品正在积累"])

    def test_routes_conflicting_multi_metric_changes_to_deep_model(self) -> None:
        self.assertTrue(_needs_deep({
            "readCount": 20, "previousReadCount": 10,
            "likeCount": 2, "previousLikeCount": 6,
            "workCount": 3, "previousWorkCount": 1,
            "newFollowerCount": 1, "previousNewFollowerCount": 1,
        }))


if __name__ == "__main__":
    unittest.main()
