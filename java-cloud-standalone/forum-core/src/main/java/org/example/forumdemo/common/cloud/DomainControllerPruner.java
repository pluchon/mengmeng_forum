package org.example.forumdemo.common.cloud;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// 按 forum.domain 裁剪 RestController，使同一套 forum-core 可被多服务复用且只暴露本域接口
public class DomainControllerPruner implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final Map<String, Set<String>> DOMAIN_CONTROLLER_BEANS = new HashMap<>();

    static {
        DOMAIN_CONTROLLER_BEANS.put(ForumDomainNames.AUTH, Set.of(
                "userController",
                "captchaController",
                "mailController",
                "SMSController"
        ));
        DOMAIN_CONTROLLER_BEANS.put(ForumDomainNames.CONTENT, Set.of(
                "articleController",
                "articleReplyController",
                "articleSubReplyController",
                "articleLikeController",
                "articleReplyLikeController",
                "articleTagController",
                "articleQuestionController",
                "articleVideoDanmakuController",
                "boardController",
                "categoryController",
                "favoriteController",
                "searchController",
                "recommendationController",
                "profileInterestController",
                "fileController"
        ));
        DOMAIN_CONTROLLER_BEANS.put(ForumDomainNames.IM, Set.of(
                "messageController",
                "privateVoiceController",
                "groupChatController",
                "groupVoiceController",
                "voiceController",
                "systemMessageController",
                "forumNoticeCenterController"
        ));
        DOMAIN_CONTROLLER_BEANS.put(ForumDomainNames.GAME, Set.of(
                "gameController"
        ));
        DOMAIN_CONTROLLER_BEANS.put(ForumDomainNames.ECONOMY, Set.of(
                "pointsController",
                "checkinController",
                "vipController",
                "lotteryController",
                "emojiShopController",
                "growthController"
        ));
        DOMAIN_CONTROLLER_BEANS.put(ForumDomainNames.AI, Set.of(
                "aiController",
                "aiWorkspaceController",
                "mascotController",
                "driftBottleController"
        ));
    }

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        String domain = environment.getProperty("forum.domain", ForumDomainNames.MONOLITH);
        if (ForumDomainNames.MONOLITH.equalsIgnoreCase(domain)) {
            return;
        }
        Set<String> keep = DOMAIN_CONTROLLER_BEANS.getOrDefault(domain.toLowerCase(), Collections.emptySet());
        Set<String> keepLower = new HashSet<>();
        for (String name : keep) {
            keepLower.add(name.toLowerCase());
        }

        for (String beanName : registry.getBeanDefinitionNames()) {
            Class<?> beanClass = resolveBeanClass(registry, beanName);
            if (beanClass == null) {
                continue;
            }
            boolean isController = beanClass.isAnnotationPresent(RestController.class)
                    || beanClass.isAnnotationPresent(Controller.class);
            if (!isController) {
                continue;
            }
            if (!keepLower.contains(beanName.toLowerCase())) {
                registry.removeBeanDefinition(beanName);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Class<?> resolveBeanClass(BeanDefinitionRegistry registry, String beanName) {
        try {
            String className = registry.getBeanDefinition(beanName).getBeanClassName();
            if (className == null) {
                return null;
            }
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
