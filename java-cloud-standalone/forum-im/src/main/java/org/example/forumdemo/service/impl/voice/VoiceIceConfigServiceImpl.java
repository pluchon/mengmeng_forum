package org.example.forumdemo.service.impl.voice;

import org.example.forumdemo.entity.vo.voice.VoiceIceConfigVO;
import org.example.forumdemo.entity.vo.voice.VoiceIceServerVO;
import org.example.forumdemo.service.interfaces.voice.VoiceIceConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Service
// WebRTC ICE 配置服务实现
public class VoiceIceConfigServiceImpl implements VoiceIceConfigService {

    @Value("${forum.voice.ice.stun-urls:stun:stun.l.google.com:19302}")
    private String stunUrls;

    @Value("${forum.voice.ice.turn-urls:}")
    private String turnUrls;

    @Value("${forum.voice.ice.turn-username:}")
    private String turnUsername;

    @Value("${forum.voice.ice.turn-credential:}")
    private String turnCredential;

    @Value("${forum.voice.ice.turn-secret:}")
    private String turnSecret;

    @Value("${forum.voice.ice.turn-ttl-seconds:3600}")
    private Long turnTtlSeconds;

    @Override
    public VoiceIceConfigVO queryIceConfig(Long loginUserId) {
        VoiceIceConfigVO config = new VoiceIceConfigVO();
        List<VoiceIceServerVO> servers = new ArrayList<>();
        List<String> stunUrlList = splitUrls(stunUrls);
        if (!stunUrlList.isEmpty()) {
            VoiceIceServerVO stun = new VoiceIceServerVO();
            stun.setUrls(stunUrlList);
            servers.add(stun);
        }
        List<String> turnUrlList = splitUrls(turnUrls);
        if (!turnUrlList.isEmpty()) {
            VoiceIceServerVO turn = new VoiceIceServerVO();
            turn.setUrls(turnUrlList);
            applyTurnCredential(turn, loginUserId);
            servers.add(turn);
        }
        config.setIceServers(servers);
        return config;
    }

    private void applyTurnCredential(VoiceIceServerVO turn, Long loginUserId) {
        if (StringUtils.hasText(turnSecret)) {
            String username = generateTurnUsername(loginUserId);
            turn.setUsername(username);
            turn.setCredential(generateTurnCredential(username));
            return;
        }
        if (StringUtils.hasText(turnUsername) && StringUtils.hasText(turnCredential)) {
            turn.setUsername(turnUsername.trim());
            turn.setCredential(turnCredential.trim());
        }
    }

    private String generateTurnUsername(Long loginUserId) {
        long expiresAt = Instant.now().getEpochSecond() + Math.max(60L, turnTtlSeconds == null ? 3600L : turnTtlSeconds);
        return expiresAt + ":" + loginUserId;
    }

    private String generateTurnCredential(String username) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(turnSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> splitUrls(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
