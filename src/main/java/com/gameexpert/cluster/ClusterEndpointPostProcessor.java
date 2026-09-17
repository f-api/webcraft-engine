package com.gameexpert.cluster;

import com.gameexpert.ws.ConnectionEndpoint;
import java.lang.reflect.Modifier;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

/**
 * An expected failover rejection must not reach Spring's decorator, which logs every handler
 * exception as ERROR with a stack trace. Other connection failures still propagate unchanged.
 */
@Component
public final class ClusterEndpointPostProcessor implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(ClusterEndpointPostProcessor.class);

    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof ConnectionEndpoint) || !(bean instanceof WebSocketHandler)
                || Modifier.isFinal(bean.getClass().getModifiers())) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            if (!invocation.getMethod().getName().equals("afterConnectionEstablished")
                    || invocation.getArguments().length != 1
                    || !(invocation.getArguments()[0] instanceof WebSocketSession session)) {
                return invocation.proceed();
            }
            try {
                return invocation.proceed();
            } catch (WorldAuthorityUnavailableException unavailable) {
                log.warn("World owner unavailable; client will reconnect after takeover: root={} owner={} reason={}",
                        unavailable.root(), unavailable.owner(), unavailable.reason());
                if (session.isOpen()) session.close(new CloseStatus(1012, unavailable.reason()));
                return null;
            }
        });
        return proxy.getProxy();
    }
}
