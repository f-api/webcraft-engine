package com.gameexpert.cluster;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import com.gameexpert.world.service.WorldDeletionCoordinator;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/** Route deletion before the service opens its transaction or takes a world-row lock. */
@Component
public final class ClusterWorldServicePostProcessor implements BeanPostProcessor {
    private final ObjectProvider<WorldDeletionCoordinator> deletions;

    public ClusterWorldServicePostProcessor(ObjectProvider<WorldDeletionCoordinator> deletions) {
        this.deletions = deletions;
    }
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!name.equals("worldService")) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            String method = invocation.getMethod().getName();
            if (!method.equals("deleteWorld") && !method.equals("deleteWorldIfMatches")) {
                return invocation.proceed();
            }
            ClusterRuntime cluster = ClusterRuntime.current();
            if (cluster != null && cluster.forwardWorldService(method, invocation.getArguments())) return null;
            long root = ((Number) invocation.getArguments()[0]).longValue();
            return deletions.getObject().delete(root,
                    () -> ((ProxyMethodInvocation) invocation).invocableClone().proceed());
        });
        return proxy.getProxy();
    }
}
