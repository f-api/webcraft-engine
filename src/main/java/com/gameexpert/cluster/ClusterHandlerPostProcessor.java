package com.gameexpert.cluster;

import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.ws.handler.EngineMessageHandler;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** The student router still executes locally; only the game handler invocation crosses the wire. */
@Component
public final class ClusterHandlerPostProcessor implements BeanPostProcessor {
    public static final Set<String> LOCAL_TYPES = Set.of("chat", "ping", "onlineUsers");
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof EngineMessageHandler handler)) return bean;
        boolean local = handler.supportedTypes().stream().anyMatch(LOCAL_TYPES::contains);
        boolean chat = handler.supportedTypes().contains("chat");
        if (local && !chat) return bean;
        ProxyFactory proxy = new ProxyFactory(bean);
        proxy.setProxyTargetClass(!Modifier.isFinal(bean.getClass().getModifiers()));
        proxy.addAdvice((MethodInterceptor) invocation -> {
            if (invocation.getMethod().getName().equals("handle") && invocation.getArguments().length == 2
                    && invocation.getArguments()[0] instanceof WsMessageContext context
                    && invocation.getArguments()[1] instanceof JsonNode message) {
                if (!local && ClusterRuntime.forwardFromEdge(context, message)) return null;
                if (chat) {
                    try { return invocation.proceed(); }
                    catch (ClusterRuntime.CommandCancelledException cancelled) {
                        // Stop before the student router converts a normal dimension cancellation
                        // into INTERNAL_ERROR; saving and relaying after the command must not run.
                        return null;
                    }
                }
            }
            return invocation.proceed();
        });
        return proxy.getProxy();
    }
}
