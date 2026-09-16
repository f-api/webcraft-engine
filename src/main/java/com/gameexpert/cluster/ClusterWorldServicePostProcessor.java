package com.gameexpert.cluster;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/** Route deletion before the service opens its transaction or takes a world-row lock. */
@Component
public final class ClusterWorldServicePostProcessor implements BeanPostProcessor {
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!name.equals("worldService")) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            String method = invocation.getMethod().getName();
            ClusterRuntime cluster = ClusterRuntime.current();
            if (cluster != null && (method.equals("deleteWorld") || method.equals("deleteWorldIfMatches"))
                    && cluster.forwardWorldService(method, invocation.getArguments())) return null;
            return invocation.proceed();
        });
        return proxy.getProxy();
    }
}
