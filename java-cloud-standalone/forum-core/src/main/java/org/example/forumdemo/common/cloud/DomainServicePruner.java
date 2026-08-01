package org.example.forumdemo.common.cloud;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 过渡裁剪器：按 forum.domain 删除非本域 service.impl Bean。
 * Phase 2 后各域实现已物理离开 forum-core classpath，本类主要兜底残留共享包；
 * 待 UserFollow/热帖/AiHub 等全部 Feign 化后删除本类与 DomainServicePrunerConfig。
 */
public class DomainServicePruner implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, PriorityOrdered {

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
        Set<String> allowedPackages = DomainServicePackages.allowedImplPackages(domain);

        for (String beanName : registry.getBeanDefinitionNames()) {
            String className = registry.getBeanDefinition(beanName).getBeanClassName();
            if (!DomainServicePackages.isServiceImplClass(className)) {
                continue;
            }
            Class<?> beanClass = loadClass(className);
            if (beanClass == null) {
                continue;
            }
            boolean isServiceBean = beanClass.isAnnotationPresent(Service.class)
                    || beanClass.isAnnotationPresent(Component.class);
            if (!isServiceBean) {
                continue;
            }
            if (shouldKeep(beanClass, allowedPackages, domain)) {
                continue;
            }
            registry.removeBeanDefinition(beanName);
        }
    }

    private boolean shouldKeep(Class<?> beanClass, Set<String> allowedPackages, String domain) {
        String packageName = beanClass.getPackageName();
        String simpleName = beanClass.getSimpleName();

        // points 本地实现仅 economy 保留（Feign 适配已在 service.impl.remote，走 SHARED 包）
        if ("org.example.forumdemo.service.impl.points".equals(packageName)
                && "PointsServiceImpl".equals(simpleName)) {
            return ForumDomainNames.ECONOMY.equalsIgnoreCase(domain);
        }

        if (DomainServicePackages.isSharedClass(simpleName)) {
            return true;
        }

        for (String allowed : allowedPackages) {
            if (packageName.equals(allowed) || packageName.startsWith(allowed + ".")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
