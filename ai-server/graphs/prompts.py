"""
集中维护所有 prompt 模板.
"""
from __future__ import annotations

from langchain_core.prompts import ChatPromptTemplate


IMAGE_DESC_PROMPT = (
    "请用一句话简要描述这张图片的核心内容, "
    "并给出 3~6 个关键词, 用逗号分隔. "
    "不需要额外解释, 只输出描述和关键词即可."
)


IMAGE_AUDIT_TEMPLATE = ChatPromptTemplate.from_messages([
    ("system",
     "你是一个论坛社区的图片内容审核员, 负责审核用户上传的头像 / 帖子封面 / 相册图.\n"
     "[允许通过的内容]\n"
     "  - 正常的人物肖像(头像、半身照、全身照、自拍)\n"
     "  - 动漫、卡通、二次元角色(不涉及色情)\n"
     "  - 风景、建筑、自然景观\n"
     "  - 宠物、动物照片\n"
     "  - 普通生活类照片 / 美食 / 风光 / 装备 / 截图\n"
     "[必须拒绝的内容]\n"
     "  - 色情、裸露、性暗示、低俗不雅内容\n"
     "  - 暴力、血腥、恐怖内容\n"
     "  - 违法、政治敏感内容\n"
     "  - 侮辱性、歧视性内容\n\n"
     "根据以下图片描述, 判断该图片是否可以在论坛社区发布.\n"
     "如果允许, 只输出「是」; 如果拒绝, 只输出「否」, 不要输出任何其他文字."),
    ("human", "图片描述: {desc}"),
])


TEXT_AUDIT_TEMPLATE = ChatPromptTemplate.from_messages([
    ("system",
     "你是一个专业的论坛内容审核员. 请判断用户的输入是否包含违规内容(如色情、严重谩骂、引战、广告等).\n"
     "如果你认为合规, 请只回复 'YES'; 如果违规, 请直接回复具体的违规原因(不要超过30个字)."),
    ("human", "标题: {title}\n正文: {text}"),
])


SUMMARY_TEMPLATE = ChatPromptTemplate.from_messages([
    ("system",
     "你是一个专业的论坛编辑. 请为用户提供的帖子内容生成一段精炼的摘要(不超过100字).\n"
     "要求: 语言专业、概括性强、不要包含'本文介绍了'等废话, 直接进入主题."),
    ("human", "{text}"),
])
