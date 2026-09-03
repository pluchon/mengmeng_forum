from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from modules.creation.cover_graph import _cover_prompt_system, run_cover_graph
from modules.creation.article_understanding import _CACHE, analyze_article
from modules.creation.format_guard import protect_content
from modules.creation.polish_graph import run_polish_graph
from modules.creation.tag_graph import find_high_similarity, run_tag_recommend_graph


def _usage(model: str) -> dict[str, object]:
    return {
        "model_code": model,
        "input_tokens": 10,
        "output_tokens": 5,
        "latency_ms": 8,
    }


class PolishGraphTest(unittest.TestCase):
    def test_complex_content_dispatches_at_most_four_workers_and_preserves_markdown(self) -> None:
        worker_calls = 0
        models: list[str] = []

        def completion(model: str, messages: list[dict[str, str]], **kwargs: object):
            nonlocal worker_calls
            models.append(model)
            prompt = messages[-1]["content"]
            if "语义复杂度" in prompt:
                return json.dumps({
                    "complexity": "complex",
                    "worker_count": 4,
                    "strategies": ["自然表达", "清晰结构", "保持语气", "简洁得体"],
                    "needs_deep": True,
                    "confidence": 0.55,
                    "reason": "事实和逻辑层次较多",
                }, ensure_ascii=False), _usage(model)
            if "评估润色候选" in prompt:
                return json.dumps({
                    "selected_index": 0,
                    "selected_score": 88,
                    "acceptable": True,
                    "needs_refine": False,
                    "feedback": "表达自然",
                }, ensure_ascii=False), _usage(model)
            worker_calls += 1
            protected_text = prompt.split("\n正文：\n", 1)[1]
            return protected_text.replace("有点", "稍微"), _usage(model)

        source = "# 标题\n\n这段话有点绕。\n\n[参考](https://example.com/a)\n\n```python\nprint('ok')\n```"
        with (
            patch("modules.creation.polish_graph.json_chat_completion", side_effect=completion),
            patch("modules.creation.polish_graph.dashscope_chat_completion", side_effect=completion),
            patch("modules.creation.polish_graph._flash_model", return_value="flash-model"),
            patch("modules.creation.polish_graph._deep_model", return_value="deep-model"),
        ):
            # allow_deep 由 Java 按 vipTier 填入，不传等于免费档。
            # 这一条测的是 PRO/MAX 路径，必须显式打开
            result = run_polish_graph("标题", source, "markdown", allow_deep=True)

        self.assertEqual(4, worker_calls)
        self.assertEqual(4, result["candidateCount"])
        self.assertTrue(result["deepUsed"])
        self.assertIn("https://example.com/a", result["content"])
        self.assertIn("print('ok')", result["content"])
        self.assertIn("deep-model", models)

    def test_free_tier_never_gets_deep_model(self) -> None:
        """深度模型是计费门：同样的复杂内容，免费档必须被卡在 flash。

        这个限制只能靠代码卡死——提示词里写「你是免费用户别用深度模型」拦不住，
        因为选模型的是代码，不是模型自己。
        """
        models: list[str] = []

        def completion(model: str, messages: list[dict[str, str]], **kwargs: object):
            models.append(model)
            prompt = messages[-1]["content"]
            if "语义复杂度" in prompt:
                return json.dumps({
                    "complexity": "complex",
                    "worker_count": 4,
                    "strategies": ["自然表达", "清晰结构", "保持语气", "简洁得体"],
                    "needs_deep": True,
                    "confidence": 0.55,
                    "reason": "事实和逻辑层次较多",
                }, ensure_ascii=False), _usage(model)
            if "评估润色候选" in prompt:
                return json.dumps({
                    "selected_index": 0,
                    "selected_score": 88,
                    "acceptable": True,
                    "needs_refine": False,
                    "feedback": "表达自然",
                }, ensure_ascii=False), _usage(model)
            return prompt.split("\n正文：\n", 1)[1], _usage(model)

        source = "# 标题\n\n这段话有点绕。\n\n[参考](https://example.com/a)"
        with (
            patch("modules.creation.polish_graph.json_chat_completion", side_effect=completion),
            patch("modules.creation.polish_graph.dashscope_chat_completion", side_effect=completion),
            patch("modules.creation.polish_graph._flash_model", return_value="flash-model"),
            patch("modules.creation.polish_graph._deep_model", return_value="deep-model"),
        ):
            result = run_polish_graph("标题", source, "markdown", allow_deep=False)

        # 模型自己报了 needs_deep=True，也照样得被挡回去
        self.assertFalse(result["deepUsed"])
        self.assertNotIn("deep-model", models)

    def test_failed_analysis_degrades_to_one_flash_worker(self) -> None:
        worker_calls = 0

        def completion(model: str, messages: list[dict[str, str]], **kwargs: object):
            nonlocal worker_calls
            prompt = messages[-1]["content"]
            if "语义复杂度" in prompt:
                raise RuntimeError("analysis unavailable")
            if "评估润色候选" in prompt:
                return json.dumps({
                    "selected_index": 0,
                    "selected_score": 90,
                    "acceptable": True,
                    "needs_refine": False,
                    "feedback": "",
                }), _usage(model)
            worker_calls += 1
            return prompt.split("\n正文：\n", 1)[1], _usage(model)

        with (
            patch("modules.creation.polish_graph.json_chat_completion", side_effect=completion),
            patch("modules.creation.polish_graph.dashscope_chat_completion", side_effect=completion),
        ):
            result = run_polish_graph("", "普通的一段正文。", "markdown")

        self.assertEqual(1, worker_calls)
        self.assertEqual("simple", result["route"])

    def test_rich_text_media_and_link_urls_are_restored_exactly(self) -> None:
        source = (
            '<p>正文<a href="https://example.com/a">链接</a></p>'
            '<span contenteditable="false" data-id="7">不可编辑节点</span>'
            '<img src="https://img/a.png">'
        )
        protected = protect_content(source, "rich")
        restored = protected.restore(protected.text.replace("正文", "自然正文"))
        self.assertIsNotNone(restored)
        self.assertIn('href="https://example.com/a"', restored)
        self.assertIn('contenteditable="false" data-id="7">不可编辑节点', restored)
        self.assertIn('<img src="https://img/a.png">', restored)


class CoverGraphTest(unittest.TestCase):
    def setUp(self) -> None:
        _CACHE.clear()

    def test_confident_analysis_skips_tavily_and_disables_second_enrichment(self) -> None:
        understanding = json.dumps({
                "summary": "白猫在窗边阅读",
                "topics": ["猫娘", "阅读"],
                "key_entities": ["白色猫娘"],
                "visual_subject": "白色猫娘",
                "visual_scene": "午后窗边",
                "tone": "温暖安静",
                "confidence": 0.95,
                "needs_deep": False,
                "needs_search": False,
                "unknown_terms": [],
                "search_query": "",
            }, ensure_ascii=False)
        cover_prompt = json.dumps({"prompt": "白色猫娘在午后窗边阅读，现代日系清新插画，柔和自然光，无文字，无水印"}, ensure_ascii=False)

        with (
            patch("modules.creation.article_understanding.json_chat_completion", return_value=(understanding, _usage("flash-model"))),
            patch("modules.creation.cover_graph._json_completion", return_value=(cover_prompt, _usage("flash-model"))),
            patch("modules.creation.cover_graph.invoke_tool") as search,
            patch("modules.creation.cover_graph.generate_image", return_value=("https://img/cover.png", _usage("image-model"), "")) as generate,
        ):
            result = run_cover_graph("标题", "<p>正文</p>", "rich", "", "normal")

        search.assert_not_called()
        generate.assert_called_once()
        self.assertFalse(generate.call_args.kwargs["enrich"])
        self.assertFalse(result["mcpUsed"])
        self.assertEqual("https://img/cover.png", result["url"])

    def test_cover_prompt_rules_are_model_specific(self) -> None:
        wan_prompt = _cover_prompt_system("wan")
        self.assertIn("主体及外观动作", wan_prompt)
        self.assertIn("景别和视角", wan_prompt)
        self.assertIn("万相", wan_prompt)


class ArticleUnderstandingTest(unittest.TestCase):
    def setUp(self) -> None:
        _CACHE.clear()

    def test_same_article_reuses_shared_understanding(self) -> None:
        understanding = json.dumps({
            "summary": "同一篇文章的摘要",
            "topics": ["测试"],
            "key_entities": [],
            "visual_subject": "测试主体",
            "visual_scene": "测试场景",
            "tone": "自然",
            "confidence": 0.9,
            "needs_deep": False,
            "needs_search": False,
            "unknown_terms": [],
            "search_query": "",
        }, ensure_ascii=False)
        with patch(
            "modules.creation.article_understanding.json_chat_completion",
            return_value=(understanding, _usage("flash-model")),
        ) as completion:
            first = analyze_article("标题", "相同正文", "markdown")
            second = analyze_article("标题", "相同正文", "markdown")

        self.assertEqual(1, completion.call_count)
        self.assertEqual(first[0].summary, second[0].summary)
        self.assertEqual([], second[1])

    def test_unknown_term_uses_tavily_once_and_search_failure_does_not_block(self) -> None:
        understanding = json.dumps({
                "summary": "介绍陌生装置",
                "topics": ["科技装置"],
                "key_entities": ["陌生装置"],
                "visual_subject": "陌生装置",
                "visual_scene": "实验室",
                "tone": "清晰理性",
                "confidence": 0.4,
                "needs_deep": False,
                "needs_search": True,
                "unknown_terms": ["陌生装置"],
                "search_query": "陌生装置 外观",
            }, ensure_ascii=False)
        cover_prompt = json.dumps({"prompt": "陌生装置置于实验室中央，科技插画，主体清晰，无文字，无水印"}, ensure_ascii=False)

        with (
            patch("modules.creation.article_understanding.json_chat_completion", return_value=(understanding, _usage("flash-model"))),
            patch("modules.creation.cover_graph._json_completion", return_value=(cover_prompt, _usage("flash-model"))),
            patch("modules.creation.cover_graph.invoke_tool", side_effect=RuntimeError("network unavailable")) as search,
            patch("modules.creation.cover_graph.generate_image", return_value=("https://img/cover.png", _usage("image-model"), "")),
        ):
            result = run_cover_graph("标题", "正文", "markdown", "", "normal")

        search.assert_called_once()
        self.assertFalse(result["mcpUsed"])
        self.assertEqual("https://img/cover.png", result["url"])


class TagGraphTest(unittest.TestCase):
    def setUp(self) -> None:
        _CACHE.clear()

    def test_recommendation_does_not_fill_irrelevant_tags_and_caps_at_five(self) -> None:
        understanding = json.dumps({
            "summary": "分享白猫的日常照护经验",
            "topics": ["猫咪", "宠物养护"],
            "key_entities": ["白猫"],
            "visual_subject": "白猫",
            "visual_scene": "居家环境",
            "tone": "温暖",
            "confidence": 0.95,
            "needs_deep": False,
            "needs_search": False,
            "unknown_terms": [],
            "search_query": "",
        }, ensure_ascii=False)
        selected = json.dumps({
            "tags": [
                {"id": 1, "score": 0.96, "reason": "核心主题"},
                {"id": 2, "score": 0.86, "reason": "直接相关"},
                {"id": 3, "score": 0.4, "reason": "弱关联"},
            ],
        }, ensure_ascii=False)
        with (
            patch("modules.creation.article_understanding.json_chat_completion", return_value=(understanding, _usage("flash-model"))),
            patch("modules.creation.tag_graph._structured_completion", return_value=(selected, _usage("flash-model"))),
        ):
            result = run_tag_recommend_graph("养猫记录", "正文", "markdown", [
                {"id": 1, "name": "猫咪"},
                {"id": 2, "name": "宠物养护"},
                {"id": 3, "name": "旅行"},
            ])

        self.assertEqual([1, 2], result["tagIds"])

    def test_similarity_requires_high_confidence(self) -> None:
        response = json.dumps({
            "highly_similar": True,
            "tag_id": 8,
            "confidence": 0.93,
            "reason": "简称与全称",
        }, ensure_ascii=False)
        with patch("modules.creation.tag_graph._structured_completion", return_value=(response, _usage("flash-model"))):
            result = find_high_similarity("人工智能", [{"id": 8, "name": "AI"}])
        self.assertEqual(8, result["similarTagId"])


if __name__ == "__main__":
    unittest.main()
