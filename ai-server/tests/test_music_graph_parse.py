from __future__ import annotations

import unittest

from modules.creation.music_graph_parse import (
    normalize_ranked_track,
    normalize_search_intent,
    parse_music_selection_payload,
    parse_search_intent_payload,
)


class MusicGraphParseTest(unittest.TestCase):
    def test_search_intent_coerces_list_fields(self) -> None:
        payload = normalize_search_intent({
            "moods": ["治愈"],
            "genre": ["DJ", "电子舞曲", "EDM"],
            "artist": [],
            "keywords": ["空灵"],
            "summary": "想要 DJ 氛围",
        })
        self.assertEqual("DJ, 电子舞曲, EDM", payload["genre"])
        self.assertEqual("", payload["artist"])

    def test_search_intent_coerces_null_artist(self) -> None:
        payload = parse_search_intent_payload(
            '{"genre":["Electronic","EDM"],"artist":null,"moods":[],"keywords":[],"summary":"test"}'
        )
        self.assertEqual("Electronic, EDM", payload["genre"])
        self.assertEqual("", payload["artist"])

    def test_music_selection_coerces_percent_score(self) -> None:
        payload = parse_music_selection_payload({
            "tracks": [{"musicKey": "k1", "score": 95, "reason": "很贴"}],
            "rationale": "ok",
        })
        self.assertAlmostEqual(0.95, payload["tracks"][0]["score"])

    def test_ranked_track_coerces_fraction_score(self) -> None:
        row = normalize_ranked_track({"musicKey": "k2", "score": 0.88, "reason": ""})
        self.assertAlmostEqual(0.88, row["score"])


if __name__ == "__main__":
    unittest.main()
