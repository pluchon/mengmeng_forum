from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from runtime.contracts import ModuleRequest, ModuleResult
from runtime.module_registry import ModuleRegistry


def _usage(model: str) -> dict[str, object]:
    return {
        "model_code": model,
        "input_tokens": 10,
        "output_tokens": 5,
        "latency_ms": 8,
    }


class MusicTasteGraphTest(unittest.TestCase):
    def test_brief_select_picks_preferred_candidate(self) -> None:
        from modules.recommendation.music_taste_graph import run_music_taste_graph

        def fake_completion(model, messages, **kwargs):
            return json.dumps({
                "queries": ["流行轻快"],
                "preferGenres": ["流行"],
                "preferMoods": ["轻快"],
                "avoidKeywords": ["重金属"],
                "summary": "brief摘要",
            }, ensure_ascii=False), _usage(model)

        with patch(
            "modules.recommendation.music_taste_graph.json_chat_completion",
            side_effect=fake_completion,
        ):
            result = run_music_taste_graph(
                favorites=[{"musicKey": "a", "genre": "流行", "mood": "轻快"}],
                recent_plays=[],
                extras=[],
                candidates=[
                    {
                        "musicKey": "hit",
                        "title": "晴天",
                        "artist": "周杰伦",
                        "genre": "流行",
                        "moodTags": ["轻快"],
                        "aiProfile": "",
                    },
                    {
                        "musicKey": "miss",
                        "title": "怒吼",
                        "artist": "x",
                        "genre": "重金属",
                        "moodTags": ["激烈"],
                        "aiProfile": "",
                    },
                ],
            )

        self.assertEqual(result["musicKeys"][0], "hit")
        self.assertEqual(result["rationale"], "brief摘要")
        self.assertTrue(result["usage"])

    def test_brief_fallback_still_selects(self) -> None:
        from modules.recommendation.music_taste_graph import run_music_taste_graph

        with patch(
            "modules.recommendation.music_taste_graph.json_chat_completion",
            side_effect=RuntimeError("boom"),
        ):
            result = run_music_taste_graph(
                favorites=[{"musicKey": "a", "genre": "流行", "mood": "轻快"}],
                recent_plays=[],
                extras=[],
                candidates=[
                    {
                        "musicKey": "hit",
                        "title": "晴天",
                        "genre": "流行",
                        "moodTags": ["轻快"],
                    },
                ],
            )
        self.assertIn("hit", result["musicKeys"])


class ModuleRegistryStreamFallbackTest(unittest.TestCase):
    def test_stream_fallback_runs_async_module_without_name_error(self) -> None:
        class DummyModule:
            async def run(self, request: ModuleRequest) -> ModuleResult:
                return ModuleResult(success=True, data={"ok": True})

        registry = ModuleRegistry()
        registry.register("GAME", "GOBANG_MOVE", "v1", DummyModule())
        request = ModuleRequest(
            request_id="r1",
            trace_id="t1",
            task_type="GAME",
            intent="GOBANG_MOVE",
            version="v1",
            user_context={},
            payload={},
        )
        events = list(registry.stream(request))
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].event_type, "final")
        self.assertTrue(events[0].data.get("success"))


class RagScanLimitWarningTest(unittest.TestCase):
    def test_take_scan_ids_warns_when_over_limit(self) -> None:
        from rag import store as rag_store

        with patch.object(rag_store, "VECTOR_SCAN_LIMIT", 2):
            with self.assertLogs(rag_store.logger, level="WARNING") as captured:
                ids = rag_store._take_scan_ids([b"1", b"2", b"3"], index_name="article")
        self.assertEqual(ids, [b"1", b"2"])
        self.assertTrue(any("vector_scan_limit" in line for line in captured.output))


class SharedHelpersTest(unittest.TestCase):
    def test_flash_deep_model_names(self) -> None:
        from clients.llm import deep_model_name, flash_model_name

        self.assertTrue(flash_model_name())
        self.assertTrue(deep_model_name())

    def test_filter_ranked_and_unique_clip(self) -> None:
        from modules.creation.ranking import filter_ranked, unique_clip

        class Item:
            def __init__(self, key: str, score: float) -> None:
                self.key = key
                self.score = score

        ranked = [Item("a", 0.9), Item("b", 0.4), Item("a", 0.95)]
        out = filter_ranked(
            ranked,
            {"a", "b"},
            key_fn=lambda x: x.key,
            score_fn=lambda x: x.score,
            threshold=0.5,
            limit=5,
        )
        self.assertEqual([x.key for x in out], ["a"])
        self.assertEqual(unique_clip(["  x ", "x", "y"], limit=2, max_len=10), ["x", "y"])

    def test_parse_json_object(self) -> None:
        from utils.json_parse import parse_json_object, safe_validate_json
        from pydantic import BaseModel

        class M(BaseModel):
            a: int

        self.assertEqual(parse_json_object('```json\n{"a":1}\n```'), {"a": 1})
        self.assertEqual(safe_validate_json(M, '{"a":2}').a, 2)
        self.assertIsNone(safe_validate_json(M, "not-json"))


if __name__ == "__main__":
    unittest.main()
