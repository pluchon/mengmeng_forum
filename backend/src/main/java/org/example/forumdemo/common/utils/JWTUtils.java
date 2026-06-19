package org.example.forumdemo.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.example.forumdemo.common.exception.ApplicationException;
import org.springframework.util.StringUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

//令牌，存放在前端的暂存区
@Slf4j
@Component
public class JWTUtils {
    //设置我们令牌的过期时间，默认是十四天
    private static final long TIME = 1000*60*60*24*14;

    //密钥的原始字符串，直接使用UTF-8生成我们的密钥，包含了+与-
    private static String SECRET_STRING;

    //使用我们的原始字符串生成我们的正式密钥，采用UTF-8编码
    private static SecretKey SECRET_KEY;

    @Value("${jwt.secret:local_dev_jwt_secret_min_32_chars_change_me}")
    public void setSecretString(String secretString) {
        JWTUtils.SECRET_STRING = secretString;
        JWTUtils.SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));
    }

    //加密我们的信息，我们前面的接口传入我们的载荷信息，最后返回一个字符串
    public static String genJwt(Map<String,Object> claim){
        return Jwts.builder().setClaims(claim)//我们的载荷内容
                .setIssuedAt(new Date())//设置现在的时间，以便后期核对有效期
                .setExpiration(new Date(System.currentTimeMillis()+TIME))//设置过期时间
                .signWith(SECRET_KEY)//我们的安全密钥
                .compact();//用于获取令牌
    }

    //验证我们的JWT密钥
    public static Claims parseJWT(String jwt){
        //如果我们的密钥是空的，直接返回
        if(!StringUtils.hasLength(jwt)){
            return null;
        }
        //预校验格式：合法JWT必须恰好含2个'.'
        //拦截"null"等非JWT字符串
        if (jwt.chars().filter(c -> c == '.').count() != 2) {
            log.warn("令牌格式非法，不是 JWT：{}", jwt);
            return null;
        }
        //创建我们的令牌解析器，并把我们的安全密钥加入
        JwtParserBuilder jwtParserBuilder = Jwts.parserBuilder().setSigningKey(SECRET_KEY);
        Claims claims;
        // 可能解析失败
        try {
            //parseClaimsJws用于解析有签名的token(parseClaimsJwt只处理未签名的，会报错)
            claims = jwtParserBuilder.build().parseClaimsJws(jwt).getBody();
        } catch (Exception e) {
            //说明我们的令牌解析失败了
            log.error("令牌解析失败：{}", jwt);
            throw new ApplicationException(e.getMessage());
        }
        return claims;
    }
}
