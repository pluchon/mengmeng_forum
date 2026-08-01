package org.pluchon.forum.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.pluchon.forum.common.constant.Constant;
import org.pluchon.forum.common.enums.ResultCode;
import org.pluchon.forum.common.exception.ApplicationException;
import org.pluchon.forum.common.result.Result;
import org.pluchon.forum.common.utils.PasswordUtils;
import org.pluchon.forum.common.utils.PiiUtils;
import org.pluchon.forum.common.utils.RegexUtil;
import org.pluchon.forum.common.utils.UUIDUtils;
import org.pluchon.forum.cloud.feign.MascotPreferenceInternalFeignClient;
import org.pluchon.forum.entity.dto.ai.RagUserIndexDTO;
import org.pluchon.forum.entity.db.User;
import org.pluchon.forum.entity.dto.user.ModifyUserRequest;
import org.pluchon.forum.entity.dto.user.UserLoginRequest;
import org.pluchon.forum.entity.dto.user.UserResigterRequest;
import org.pluchon.forum.mapper.UserMapper;
import org.pluchon.forum.service.interfaces.ai.AiHubService;
import org.pluchon.forum.service.interfaces.favorite.FavoriteFolderService;
import org.pluchon.forum.service.interfaces.growth.GrowthService;
import org.pluchon.forum.service.interfaces.points.PointsService;
import org.pluchon.forum.service.interfaces.user.UserService;
import org.pluchon.forum.service.impl.user.UserDerivedCacheInvalidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.pluchon.forum.common.constant.Constant.VALID_USERNAME_PATTERN;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MascotPreferenceInternalFeignClient mascotPreferenceInternalFeignClient;

    /**
     * 注册后立刻给用户建一个默认收藏夹.
     * auth 域走 FavoriteFolderRemoteService（Feign → content）；content 域为本地实现。
     */
    @Autowired(required = false)
    @Lazy
    private FavoriteFolderService favoriteFolderService;

    @Autowired
    @Lazy
    private PointsService pointsService;

    @Autowired
    private AiHubService aiHubService;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private UserDerivedCacheInvalidator userDerivedCacheInvalidator;

    @Autowired
    private JwtTokenVersionService jwtTokenVersionService;

    @Autowired
    private GrowthService growthService;

    // ============================================================
    // 注册，使用事务保证原子性
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
        // 直接查库校验用户名是否存在（不走 queryUserByUserName，避免抛出无法查找的异常）
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, userName).ne(User::getDeleteState, 1));
        if (existing != null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_EXISTS));
        }
        User register = new User();
        register.setUsername(userName);
        register.setNickname(nickname);
        String salt = UUIDUtils.UUID32();
        register.setSalt(salt);
        register.setPassword(PasswordUtils.encode(password));
        // 我们敏感信息都有两个字段，一个是可逆的密文，一个是哈希。
        // 可逆的密文是为了还原数据的，但是每一次生成都不确定，无法建立索引
        // 不可逆的哈希密文每一次都是固定的，因此便于建立索引
        // 这里的都是注册的时候选填的内容
        if (StringUtils.hasLength(req.getPhoneNum())) {
            register.setPhoneNum(PiiUtils.encrypt(req.getPhoneNum()));
            register.setPhoneHash(PiiUtils.hmac(req.getPhoneNum()));
        }
        if (StringUtils.hasLength(req.getEmail())) {
            register.setEmail(PiiUtils.encrypt(req.getEmail()));
            register.setEmailHash(PiiUtils.hmac(req.getEmail()));
        }
        userMapper.insert(register);
        // 跨服务 Feign（成长建档 / 注册赠分）必须在本地事务提交后再调用，
        // 否则 economy 更新同一 user 行会与本事务互相等待，触发 Lock wait timeout。
        final Long newUserId = register.getId();
        final String newUserName = register.getUsername();
        final User ragSnapshot = register;
        Runnable afterCommitSideEffects = () -> {
            try {
                growthService.createNewUserProfile(newUserId);
            } catch (Exception e) {
                log.warn("用户 {} 成长档案创建失败(可补偿): {}", newUserId, e.getMessage());
            }
            try {
                pointsService.addPoints(newUserId, Constant.POINTS_REGISTER_BONUS_AMOUNT,
                        Constant.POINTS_SOURCE_REGISTER_BONUS, newUserId, "新用户注册赠送积分",
                        "register:" + newUserId);
            } catch (Exception e) {
                log.warn("用户 {} 注册赠分失败(可补偿): {}", newUserId, e.getMessage());
            }
            storeUserNameMapping(newUserName, newUserId);
            try {
                if (favoriteFolderService != null) {
                    favoriteFolderService.ensureDefaultFolder(newUserId);
                } else {
                    log.warn("用户 {} 默认收藏夹跳过: FavoriteFolderService 未装配", newUserId);
                }
            } catch (Exception e) {
                log.warn("用户 {} 默认收藏夹创建失败, 留待懒加载补齐: {}", newUserId, e.getMessage());
            }
            indexUserRagProfile(ragSnapshot);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommitSideEffects.run();
                }
            });
        } else {
            afterCommitSideEffects.run();
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
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
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
        // 先从缓存查
        User user = getRedis(userId);
        if (user != null) {
            return user;
        }
        // 再从库里查
        user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId).ne(User::getDeleteState, 1));
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        // 把查到的最新结果放入缓存
        storeRedis(user);
        // 返回结果之前进行脱敏
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
        // 缓存中不存盐值与密码哈希，必要时回库取完整记录，获取盐值，便于查询加密后的密码
        if (user.getSalt() == null || user.getPassword() == null) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getId, user.getId()).ne(User::getDeleteState, 1));
        }
        if (!PasswordUtils.matches(password, user.getPassword(), user.getSalt())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_LOGIN));
        }
        authTokenService.assertCanAuthenticate(user);
        if (!PasswordUtils.isBcryptHash(user.getPassword())) {
            String bcrypt = PasswordUtils.encode(password);
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getPassword, bcrypt)
                    .set(User::getSalt, ""));
            user.setPassword(bcrypt);
            user.setSalt("");
        }
        user.setToken(authTokenService.issueLoginToken(user));
        log.info("用户 {} 登录校验通过", user.getUsername());
        //用户完整信息存入缓存
        storeRedis(user);
        //用户简要信息存入缓存
        storeUserNameMapping(user.getUsername(), user.getId());
        return toSafeUser(user);
    }

    private User loadUserByEmail(String email) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
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
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).setSql("article_count = article_count + 1"));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        incrementCachedArticleCount(userId, 1);
    }

    @Override
    public void deleteOneById(Long userId) {
        queryUserByUserId(userId);
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
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
        // 获取旧的用户信息
        User oldUser = queryUserByUserId(userId);
        // 获取新旧用户名
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
        // 针对特殊字段进行设置
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
        int result = userMapper.update(update, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).ne(User::getDeleteState, 1).ne(User::getState, 1));
        if (result <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        // 更新缓存中的简单的用户信息，仅限于用户名变化的时候
        if (StringUtils.hasLength(newUserName) && !newUserName.equals(oldUserName)) {
            deleteUserNameMapping(oldUserName);
            storeUserNameMapping(newUserName, userId);
            log.info("用户名变更: {} -> {}，缓存映射已更新", oldUserName, newUserName);
        }
        // 详细信息缓存失效，下一次查询会从 DB 重建
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
        User latest = queryUserByUserId(userId);
        indexUserRagProfile(latest);
        return latest;
    }

    private void indexUserRagProfile(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        RagUserIndexDTO payload = new RagUserIndexDTO();
        payload.setUserId(user.getId());
        payload.setNickname(user.getNickname());
        payload.setUsername(user.getUsername());
        payload.setRemark(user.getRemark());
        aiHubService.indexUserRag(payload);
    }

    @Override
    public void setMascotModel(Long userId, Long mascotModelId) {
        // 偏好权威在 forum-ai；auth 仅转发并失效本地用户缓存由 ai 侧完成
        mascotPreferenceInternalFeignClient.setMascotModel(userId, mascotModelId);
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
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId).ne(User::getDeleteState, 1));
        if (user == null) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        if (!PasswordUtils.matches(oldPassword, user.getPassword(), user.getSalt())) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        String newSecret = PasswordUtils.encode(newPassword);
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getSalt, "").set(User::getPassword, newSecret));
        if (updated <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED));
        }
        jwtTokenVersionService.bump(userId);
        // 盐值变更必须清缓存，下次读取时重建
        stringRedisTemplate.delete(Constant.REDIS_KEY_USER_INFO + userId);
        log.info("用户 {} 修改密码成功，缓存已清除", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApplicationException(Result.fail(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        jwtTokenVersionService.bump(userId);
    }

    // ============================================================
    // URL 落库（FileController 上传完成后调用）
    // ============================================================
    @Override
    public void updateAvatarUrl(Long userId, String url) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getAvatarUrl, url));
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
    }

    @Override
    public void updateBackgroundUrl(Long userId, String url) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId).set(User::getBackgroundUrl, url));
        userDerivedCacheInvalidator.invalidateUserCaches(userId);
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
        map.put("creatorState", user.getCreatorState() == null ? "0" : String.valueOf(user.getCreatorState().intValue()));
        map.put("ipRegion", nullToEmpty(user.getIpRegion()));
        // 敏感信息（password / salt）不入缓存
        stringRedisTemplate.opsForHash().putAll(redisKey, map);
        // 设置过期时间，防止存在内存中过久，我们一般设置为5分钟
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
        if (!map.containsKey("creatorState")) {
            stringRedisTemplate.delete(redisKey);
            return null;
        }
        // 旧版缓存结构不含 ipRegion：删除并回源 DB，保证 IP 属地能正常展示
        if (!map.containsKey("ipRegion")) {
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
            user.setVipExpireAt(new Date(Long.parseLong(vipExpireMs.trim())));
        }
        user.setPoints(Integer.valueOf(map.getOrDefault("points", "0").toString()));
        String mid = map.getOrDefault("mascotModelId", "").toString();
        if (StringUtils.hasLength(mid)) {
            user.setMascotModelId(Long.valueOf(mid.trim()));
        }
        user.setState(Byte.valueOf(map.getOrDefault("state", "0").toString()));
        user.setCreatorState(Byte.valueOf(map.getOrDefault("creatorState", "0").toString()));
        user.setIpRegion(map.getOrDefault("ipRegion", "").toString());
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

    // 解密我们的用户邮箱信息，并加密我们的手机号信息返回给前端
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
