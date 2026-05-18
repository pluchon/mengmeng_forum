package org.example.forumdemo.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.constant.Constant;
import org.example.forumdemo.common.enums.ResultCode;
import org.example.forumdemo.common.exception.ApplicationException;
import org.example.forumdemo.common.result.Result;
import org.example.forumdemo.common.utils.JWTUtils;
import org.example.forumdemo.common.utils.MD5Utils;
import org.example.forumdemo.common.utils.PiiUtils;
import org.example.forumdemo.common.utils.RegexUtil;
import org.example.forumdemo.common.utils.UUIDUtils;
import org.example.forumdemo.entity.db.ForumMascotModel;
import org.example.forumdemo.entity.db.User;
import org.example.forumdemo.entity.dto.user.ModifyUserRequest;
import org.example.forumdemo.entity.dto.user.UserLoginRequest;
import org.example.forumdemo.entity.dto.user.UserResigterRequest;
import org.example.forumdemo.mapper.ForumMascotModelMapper;
import org.example.forumdemo.mapper.UserMapper;
import org.example.forumdemo.service.interfaces.favorite.FavoriteFolderService;
import org.example.forumdemo.service.interfaces.points.PointsService;
import org.example.forumdemo.service.interfaces.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.example.forumdemo.common.constant.Constant.VALID_USERNAME_PATTERN;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ForumMascotModelMapper forumMascotModelMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注册后立刻给用户建一个默认收藏夹.
     * @Lazy 打破潜在循环依赖 (FavoriteFolderService -> ArticleService -> UserService).
     */
    @Autowired
    @Lazy
    private FavoriteFolderService favoriteFolderService;

    @Autowired
    @Lazy
    private PointsService pointsService;

    // ============================================================
    // 注册
    // ============================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resigter(UserResigterRequest req) {
        String userName = req.getUserName();
        String nickname = req.getNickname();
        String password = req.getPassword();
        if (!StringUtils.hasLength(userName) || !StringUtils.hasLength(nickname) || !StringUtils.hasLength(password)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!isValidUserName(userName)) {
            log.warn("用户名包含非法字符: {}", userName);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!RegexUtil.checkPassword(password)) {
            log.warn("用户 {} 注册时密码强度不达标", userName);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 直接查库校验用户名是否存在（不走 queryUserByUserName，避免抛找不到异常）
        User existing = userMapper.selectOne(new QueryWrapper<User>().lambda()
                .eq(User::getUsername, userName).ne(User::getDeleteState, 1));
        if (existing != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_EXISTS));
        }
        User register = new User();
        register.setUsername(userName);
        register.setNickname(nickname);
        String salt = UUIDUtils.UUID32();
        register.setSalt(salt);
        register.setPassword(MD5Utils.md5SaltHigh(password, salt));
        if (StringUtils.hasLength(req.getPhoneNum())) {
            register.setPhoneNum(PiiUtils.encrypt(req.getPhoneNum()));
            register.setPhoneHash(PiiUtils.hmac(req.getPhoneNum()));
        }
        if (StringUtils.hasLength(req.getEmail())) {
            register.setEmail(PiiUtils.encrypt(req.getEmail()));
            register.setEmailHash(PiiUtils.hmac(req.getEmail()));
        }
        userMapper.insert(register);
        pointsService.addPoints(
                register.getId(),
                Constant.POINTS_REGISTER_BONUS_AMOUNT,
                Constant.POINTS_SOURCE_REGISTER_BONUS,
                register.getId(),
                "新用户注册赠送积分");
        storeUserNameMapping(register.getUsername(), register.getId());
        // 注册成功后立刻为用户创建默认收藏夹; 失败仅记日志, 不影响注册主流程,
        // 因为首次收藏时 ensureDefaultFolder 仍会兜底补创建
        try {
            favoriteFolderService.ensureDefaultFolder(register.getId());
        } catch (Exception e) {
            log.warn("用户 {} 默认收藏夹创建失败, 留待懒加载补齐: {}", register.getId(), e.getMessage());
        }
    }

    // ============================================================
    // 共通查询：先 Redis 后 DB
    // ============================================================
    @Override
    public User queryUserByUserName(String userName) {
        if (!StringUtils.hasLength(userName)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String nameKey = Constant.REDIS_KEY_USER_NAME + userName;
        String cachedUserId = stringRedisTemplate.opsForValue().get(nameKey);
        if (cachedUserId != null) {
            if (cachedUserId.equals(Constant.REDIS_EMPTY_MARK)) {
                throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
            }
            return queryUserByUserId(Long.valueOf(cachedUserId));
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().lambda()
                .eq(User::getUsername, userName).ne(User::getDeleteState, 1));
        if (user == null) {
            // 缓存穿透保护：写一个短 TTL 的空标记
            stringRedisTemplate.opsForValue().set(nameKey,
                    Constant.REDIS_EMPTY_MARK, Constant.REDIS_TTL_EMPTY_MARK, TimeUnit.SECONDS);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        storeUserNameMapping(user.getUsername(), user.getId());
        storeRedis(user);
        return toSafeUser(user);
    }

    @Override
    public User queryUserByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        User user = getRedis(userId);
        if (user != null) {
            return user;
        }
        user = userMapper.selectOne(new QueryWrapper<User>().lambda()
                .eq(User::getId, userId).ne(User::getDeleteState, 1));
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        storeRedis(user);
        return toSafeUser(user);
    }

    @Override
    public User getUserInfoById(Long userId) {
        return queryUserByUserId(userId);
    }

    // ============================================================
    // 登录：用户名 / 邮箱
    // ============================================================
    @Override
    public User login(UserLoginRequest req) {
        String account = req.getUserName();
        String password = req.getPassword();
        if (!StringUtils.hasLength(account) || !StringUtils.hasLength(password)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 区分邮箱登录 / 用户名登录：邮箱不走用户名缓存
        User user = account.contains("@") ? loadUserByEmail(account) : loadUserByUsername(account);
        // 缓存中不存盐值与密码哈希，必要时回库取完整记录
        if (user.getSalt() == null || user.getPassword() == null) {
            user = userMapper.selectOne(new QueryWrapper<User>().lambda()
                    .eq(User::getId, user.getId()).ne(User::getDeleteState, 1));
        }
        if (!MD5Utils.md5SaltHigh(password, user.getSalt()).equals(user.getPassword())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_LOGIN));
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put(Constant.JWT_USER_ID, user.getId());
        claims.put(Constant.JWT_USER_NAME, user.getUsername());
        user.setToken(JWTUtils.genJwt(claims));
        log.info("用户 {} 登录校验通过", user.getUsername());
        storeRedis(user);
        storeUserNameMapping(user.getUsername(), user.getId());
        return toSafeUser(user);
    }

    private User loadUserByEmail(String email) {
        User user = userMapper.selectOne(new QueryWrapper<User>().lambda()
                .eq(User::getEmailHash, PiiUtils.hmac(email)).ne(User::getDeleteState, 1));
        if (user == null) {
            log.warn("邮箱未绑定账号: {}", email);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
    }

    private User loadUserByUsername(String userName) {
        User user = queryUserByUserName(userName);
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
    }

    // ============================================================
    // 帖子计数维护（外部 ArticleService 调用）
    // ============================================================
    @Override
    public void addOneById(Long userId) {
        queryUserByUserId(userId);
        int updated = userMapper.update(null, new UpdateWrapper<User>().lambda()
                .eq(User::getId, userId).setSql("article_count = article_count + 1"));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        incrementCachedArticleCount(userId, 1);
    }

    @Override
    public void deleteOneById(Long userId) {
        queryUserByUserId(userId);
        int updated = userMapper.update(null, new UpdateWrapper<User>().lambda()
                .eq(User::getId, userId).setSql("article_count = article_count - 1"));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        incrementCachedArticleCount(userId, -1);
    }

    /** 用户详情 Hash 还在缓存里就同步增减；不在则跳过，避免创建残缺 Hash */
    private void incrementCachedArticleCount(Long userId, int delta) {
        String cacheKey = Constant.REDIS_KEY_USER_INFO + userId;
        if (stringRedisTemplate.hasKey(cacheKey)) {
            stringRedisTemplate.opsForHash().increment(cacheKey, "articleCount", delta);
        }
    }

    // ============================================================
    // 修改用户信息
    // ============================================================
    @Override
    public User modifyUser(ModifyUserRequest req, Long userId) {
        User oldUser = queryUserByUserId(userId);
        String oldUserName = oldUser.getUsername();
        String newUserName = req.getUserName();
        if (StringUtils.hasLength(newUserName) && !isValidUserName(newUserName)) {
            log.warn("新用户名包含非法字符: {}", newUserName);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // mybatis-plus 默认仅更新非空字段，无需手动判空
        User update = new User();
        update.setUsername(newUserName);
        update.setNickname(req.getNickName());
        if (StringUtils.hasLength(req.getEmail())) {
            update.setEmail(PiiUtils.encrypt(req.getEmail()));
            update.setEmailHash(PiiUtils.hmac(req.getEmail()));
        }
        update.setGender(req.getGender());
        if (StringUtils.hasLength(req.getPhoneNum())) {
            update.setPhoneNum(PiiUtils.encrypt(req.getPhoneNum()));
            update.setPhoneHash(PiiUtils.hmac(req.getPhoneNum()));
        }
        update.setRemark(req.getRemark());
        int result = userMapper.update(update, new UpdateWrapper<User>().lambda()
                .eq(User::getId, userId).ne(User::getDeleteState, 1).ne(User::getState, 1));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        if (StringUtils.hasLength(newUserName) && !newUserName.equals(oldUserName)) {
            deleteUserNameMapping(oldUserName);
            storeUserNameMapping(newUserName, userId);
            log.info("用户名变更: {} -> {}，缓存映射已更新", oldUserName, newUserName);
        }
        // 详细信息缓存失效，下一次查询会从 DB 重建
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
        return queryUserByUserId(userId);
    }

    @Override
    public void setMascotModel(Long userId, Long mascotModelId) {
        if (userId == null || mascotModelId == null || mascotModelId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        ForumMascotModel m = forumMascotModelMapper.selectById(mascotModelId);
        if (m == null || (m.getDeleteState() != null && m.getDeleteState() == 1)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_NOT_EXISTS));
        }
        if (m.getShelfStatus() == null || m.getShelfStatus() != 1) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE, "仅可选择已上架的看板娘"));
        }
        int n = userMapper.update(null, new UpdateWrapper<User>().lambda()
                .eq(User::getId, userId).ne(User::getDeleteState, 1)
                .set(User::getMascotModelId, mascotModelId));
        if (n <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
    }

    // ============================================================
    // 修改密码
    // ============================================================
    @Override
    public void updatePawssword(Long userId, String oldPassword, String newPassword) {
        if (!StringUtils.hasLength(oldPassword) || !StringUtils.hasLength(newPassword)) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        if (!RegexUtil.checkPassword(newPassword)) {
            log.warn("用户 {} 修改密码时新密码强度不达标", userId);
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 缓存不存 salt，必须直接查 DB 拿完整记录
        User user = userMapper.selectOne(new QueryWrapper<User>().lambda()
                .eq(User::getId, userId).ne(User::getDeleteState, 1));
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        if (!MD5Utils.md5SaltHigh(oldPassword, user.getSalt()).equals(user.getPassword())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String newSalt = UUIDUtils.UUID32();
        String newSecret = MD5Utils.md5SaltHigh(newPassword, newSalt);
        int updated = userMapper.update(null, new UpdateWrapper<User>().lambda()
                .eq(User::getId, userId).set(User::getSalt, newSalt).set(User::getPassword, newSecret));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        // 盐值变更必须清缓存，下次读取时重建
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
        log.info("用户 {} 修改密码成功，缓存已清除", userId);
    }

    // ============================================================
    // URL 落库（FileController 上传完成后调用）
    // ============================================================
    @Override
    public void updateAvatarUrl(Long userId, String url) {
        userMapper.update(null, new UpdateWrapper<User>().lambda()
                .eq(User::getId, userId).set(User::getAvatarUrl, url));
        stringRedisTemplate.opsForHash().put(Constant.REDIS_KEY_USER_INFO + userId, "avatarUrl", url);
    }

    @Override
    public void updateBackgroundUrl(Long userId, String url) {
        userMapper.update(null, new UpdateWrapper<User>().lambda()
                .eq(User::getId, userId).set(User::getBackgroundUrl, url));
        stringRedisTemplate.opsForHash().put(Constant.REDIS_KEY_USER_INFO + userId, "backgroundUrl", url);
    }

    // ============================================================
    // Redis 缓存：用户详情 Hash + 用户名->ID 映射
    // ============================================================
    private void storeRedis(User user) {
        String redisKey = Constant.REDIS_KEY_USER_INFO + user.getId();
        Map<String, String> map = new HashMap<>();
        map.put("id", String.valueOf(user.getId()));
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatarUrl", nullToEmpty(user.getAvatarUrl()));
        map.put("articleCount", String.valueOf(user.getArticleCount()));
        map.put("isAdmin", String.valueOf(user.getIsAdmin()));
        map.put("email", nullToEmpty(PiiUtils.decrypt(user.getEmail())));
        map.put("phoneNum", nullToEmpty(PiiUtils.maskPhone(user.getPhoneNum())));
        map.put("remark", nullToEmpty(user.getRemark()));
        map.put("backgroundUrl", nullToEmpty(user.getBackgroundUrl()));
        map.put("vipTier", user.getVipTier() == null ? "0" : String.valueOf(user.getVipTier().intValue()));
        map.put("vipExpireMs", user.getVipExpireAt() == null ? "" : String.valueOf(user.getVipExpireAt().getTime()));
        map.put("points", user.getPoints() == null ? "0" : String.valueOf(user.getPoints()));
        map.put("mascotModelId", user.getMascotModelId() == null ? "" : String.valueOf(user.getMascotModelId()));
        map.put("state", user.getState() == null ? "0" : String.valueOf(user.getState().intValue()));
        // 敏感信息（password / salt）不入缓存
        stringRedisTemplate.opsForHash().putAll(redisKey, map);
        stringRedisTemplate.expire(redisKey, Constant.REDIS_TTL_USER_INFO, TimeUnit.SECONDS);
    }

    private User getRedis(Long userId) {
        String redisKey = Constant.REDIS_KEY_USER_INFO + userId;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(redisKey);
        if (map.isEmpty()) {
            return null;
        }
        // 旧版缓存结构不含 vipTier：删除并回源 DB，避免前端长期拿不到 VIP 状态
        if (!map.containsKey("vipTier")) {
            stringRedisTemplate.delete(redisKey);
            return null;
        }
        if (!map.containsKey("mascotModelId")) {
            stringRedisTemplate.delete(redisKey);
            return null;
        }
        if (!map.containsKey("state")) {
            stringRedisTemplate.delete(redisKey);
            return null;
        }
        User user = new User();
        user.setId(Long.valueOf(map.get("id").toString()));
        user.setUsername(map.get("username").toString());
        user.setNickname(map.get("nickname").toString());
        user.setAvatarUrl(map.getOrDefault("avatarUrl", "").toString());
        user.setArticleCount(Integer.valueOf(map.get("articleCount").toString()));
        user.setIsAdmin(Byte.valueOf(map.get("isAdmin").toString()));
        user.setEmail(map.getOrDefault("email", "").toString());
        user.setPhoneNum(map.getOrDefault("phoneNum", "").toString());
        user.setRemark(map.getOrDefault("remark", "").toString());
        user.setBackgroundUrl(map.getOrDefault("backgroundUrl", "").toString());
        user.setVipTier(Byte.valueOf(map.getOrDefault("vipTier", "0").toString()));
        String vipExpireMs = map.getOrDefault("vipExpireMs", "").toString();
        if (StringUtils.hasLength(vipExpireMs)) {
            user.setVipExpireAt(new java.util.Date(Long.parseLong(vipExpireMs.trim())));
        }
        user.setPoints(Integer.valueOf(map.getOrDefault("points", "0").toString()));
        String mid = map.getOrDefault("mascotModelId", "").toString();
        if (StringUtils.hasLength(mid)) {
            user.setMascotModelId(Long.valueOf(mid.trim()));
        }
        user.setState(Byte.valueOf(map.getOrDefault("state", "0").toString()));
        return user;
    }

    private void storeUserNameMapping(String userName, Long userId) {
        stringRedisTemplate.opsForValue().set(Constant.REDIS_KEY_USER_NAME + userName,
                String.valueOf(userId), Constant.REDIS_TTL_USER_NAME, TimeUnit.SECONDS);
    }

    private void deleteUserNameMapping(String userName) {
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_NAME + userName);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private User toSafeUser(User user) {
        if (user == null) {
            return null;
        }
        user.setEmail(PiiUtils.decrypt(user.getEmail()));
        user.setPhoneNum(PiiUtils.maskPhone(user.getPhoneNum()));
        return user;
    }

    /** 用户名长度 4-20，且必须以中英文/数字开头结尾 */
    private boolean isValidUserName(String userName) {
        if (userName == null || userName.length() < 4 || userName.length() > 20) {
            return false;
        }
        return VALID_USERNAME_PATTERN.matcher(userName).matches();
    }
}
