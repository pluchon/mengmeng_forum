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

// 按 forum.domain 裁剪 service.impl 业务 Bean，使各进程只装载本域实现
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

        // points：economy 保留本地实现，其它域保留 Feign 适配（若已装配）
        if ("org.example.forumdemo.service.impl.points".equals(packageName)) {
            if ("PointsFeignService".equals(simpleName)) {
                return !ForumDomainNames.ECONOMY.equalsIgnoreCase(domain);
            }
            if ("PointsServiceImpl".equals(simpleName)) {
                return ForumDomainNames.ECONOMY.equalsIgnoreCase(domain);
            }
        }

        if (DomainServicePackages.isSharedUserSecurityClass(simpleName)) {
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
