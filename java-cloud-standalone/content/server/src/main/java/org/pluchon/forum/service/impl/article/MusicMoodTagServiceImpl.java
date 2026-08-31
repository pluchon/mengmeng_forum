package org.pluchon.forum.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.entity.db.MusicMoodTag;
import org.pluchon.forum.entity.db.UserMusic;
import org.pluchon.forum.entity.vo.article.MusicMoodTagVO;
import org.pluchon.forum.entity.vo.common.PageResult;
import org.pluchon.forum.mapper.MusicMoodTagMapper;
import org.pluchon.forum.mapper.UserMusicMapper;
import org.pluchon.forum.service.interfaces.article.MusicMoodTagService;
import org.pluchon.forum.service.remote.ContentAiGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 音乐氛围标签池。
 *
 * <p>标签名审核走 {@code validateText}，与帖子标签用的是同一条 LangGraph 链路
 * （content → ai 域 → ai-server 的 article_audit 图）。同类需求再拉一个平行子图
 * 只会多一份要维护的提示词，收益不抵成本。
 */
@Slf4j
@Service
public class MusicMoodTagServiceImpl implements MusicMoodTagService {

    public static final String SOURCE_BUILTIN = "BUILTIN";
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_USER = "USER";

    private static final int DEFAULT_PAGE_SIZE = 18;
    private static final int MAX_LIMIT = 200;
    private static final int NAME_MIN_LEN = 2;
    private static final int NAME_MAX_LEN = Constant.MUSIC_MOOD_TAG_NAME_MAX_LEN;

    // 氛围词只允许中文、字母、数字，挡掉表情、控制字符与各类分隔符
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9]+$");

    @Autowired
    private MusicMoodTagMapper musicMoodTagMapper;

    @Autowired
    private UserMusicMapper userMusicMapper;

    @Autowired
    private ContentAiGatewayService contentAiGatewayService;

    @Override
    public PageResult<MusicMoodTagVO> page(String keyword, Integer pageNum, Integer pageSize) {
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_LIMIT);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        Page<MusicMoodTag> page = new Page<>(num, size);
        Page<MusicMoodTag> result = musicMoodTagMapper.selectPage(page, listWrapper(keyword));
        List<MusicMoodTagVO> records = result.getRecords().stream()
                .map(MusicMoodTagServiceImpl::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), (int) result.getCurrent(),
                (int) result.getSize(), result.getPages(), result.getCurrent() < result.getPages());
    }

    private LambdaQueryWrapper<MusicMoodTag> listWrapper(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        return new LambdaQueryWrapper<MusicMoodTag>()
                .eq(MusicMoodTag::getEnabled, 1)
                .eq(MusicMoodTag::getDeleteState, 0)
                .like(StringUtils.hasText(kw), MusicMoodTag::getName, kw)
                .orderByDesc(MusicMoodTag::getUseCount)
                .orderByAsc(MusicMoodTag::getId);
    }

    private static MusicMoodTagVO toVO(MusicMoodTag row) {
        MusicMoodTagVO vo = new MusicMoodTagVO();
        vo.setName(row.getName());
        vo.setSource(row.getSource());
        vo.setUseCount(row.getUseCount());
        return vo;
    }

    @Override
    public List<String> listNames() {
        // 曲库筛选栏一次全出，不分页
        return musicMoodTagMapper.selectList(listWrapper(null).last("LIMIT " + MAX_LIMIT))
                .stream().map(MusicMoodTag::getName).collect(Collectors.toList());
    }

    @Override
    public void touchAll(List<String> names, String source) {
        if (names == null || names.isEmpty()) {
            return;
        }
        String src = SOURCE_USER.equals(source) || SOURCE_BUILTIN.equals(source) ? source : SOURCE_AI;
        for (String raw : names) {
            String name = normalize(raw);
            if (name == null) {
                continue;
            }
            try {
                musicMoodTagMapper.upsertAndTouch(name, src, null);
            } catch (Exception e) {
                // 标签池是展示用的附属数据，写失败不该把歌曲审核结果一起回滚
                log.warn("氛围标签入池失败 name={}", name, e);
            }
        }
    }

    @Override
    public String createByUser(Long userId, String rawName) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        String name = normalize(rawName);
        if (name == null || name.length() < NAME_MIN_LEN) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE,
                    "标签名需 " + NAME_MIN_LEN + "～" + NAME_MAX_LEN + " 位中文、字母或数字"));
        }
        // 没发布过歌的人建标签，池子会很快被试探性词条稀释
        Long published = userMusicMapper.selectCount(new LambdaQueryWrapper<UserMusic>()
                .eq(UserMusic::getUserId, userId)
                .eq(UserMusic::getStatus, Constant.USER_MUSIC_STATUS_PUBLISHED)
                .ne(UserMusic::getDeleteState, 1));
        if (published == null || published == 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_FORBIDDEN,
                    "发布过歌曲后才能创建标签"));
        }
        MusicMoodTag existed = musicMoodTagMapper.selectOne(new LambdaQueryWrapper<MusicMoodTag>()
                .eq(MusicMoodTag::getName, name)
                .last("LIMIT 1"));
        if (existed != null) {
            if (existed.getEnabled() != null && existed.getEnabled() == 1
                    && existed.getDeleteState() != null && existed.getDeleteState() == 0) {
                return name;
            }
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "该标签不可用"));
        }
        // fail-closed：模型不可用时宁可让人重试，也不能放未审的标签名进池
        String audit = contentAiGatewayService.validateText("音乐氛围标签：" + name);
        if (audit != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_AI_CHECK_CONTENT_ERROR, audit));
        }
        musicMoodTagMapper.insertIfAbsent(name, userId);
        return name;
    }

    /** 统一的标签名清洗：去空白、限长、只留中文字母数字 */
    public static String normalize(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > NAME_MAX_LEN) {
            t = t.substring(0, NAME_MAX_LEN);
        }
        return NAME_PATTERN.matcher(t).matches() ? t : null;
    }

    /** 入库前的标签数组清洗：去重、丢非法、限量 */
    public static List<String> sanitizeTagList(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String item : raw == null ? List.<String>of() : raw) {
            String name = normalize(item);
            if (name != null && !out.contains(name)) {
                out.add(name);
                if (out.size() >= Constant.MUSIC_MOOD_TAG_MAX_COUNT) {
                    break;
                }
            }
        }
        return out;
    }
}
