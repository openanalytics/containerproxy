/**
 * ContainerProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.containerproxy.spec.expression;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.expression.BeanExpressionContextAccessor;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Note: inspired by org.springframework.context.expression.StandardBeanExpressionResolver
 */
@Component
public class SpecExpressionResolver {

    private final ApplicationContext appContext;
    private final ExpressionParser expressionParser;
    private final Cache<SpecExpressionContext, StandardEvaluationContext> evaluationCache =  Caffeine.newBuilder()
        .scheduler(Scheduler.systemScheduler())
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build();

    private final ParserContext beanExpressionParserContext = new ParserContext() {
        @Override
        public boolean isTemplate() {
            return true;
        }

        @Override
        public String getExpressionPrefix() {
            return StandardBeanExpressionResolver.DEFAULT_EXPRESSION_PREFIX;
        }

        @Override
        public String getExpressionSuffix() {
            return StandardBeanExpressionResolver.DEFAULT_EXPRESSION_SUFFIX;
        }
    };

    public SpecExpressionResolver(ApplicationContext appContext) {
        this.appContext = appContext;
        this.expressionParser = new SpelExpressionParser();
    }

    public <T> T evaluate(String expression, SpecExpressionContext context, Class<T> resType) {
        if (expression == null) return null;
        if (expression.isEmpty()) return null;

        try {
            Expression expr = this.expressionParser.parseExpression(expression, this.beanExpressionParserContext);

            ConfigurableBeanFactory beanFactory = ((ConfigurableApplicationContext) appContext).getBeanFactory();

            StandardEvaluationContext sec = evaluationCache.get(context, k -> {
                StandardEvaluationContext result = new StandardEvaluationContext();
                result.setRootObject(context);
                result.addPropertyAccessor(new BeanExpressionContextAccessor());
                result.addPropertyAccessor(new BeanFactoryAccessor());
                result.addPropertyAccessor(new MapAccessor());
                result.addPropertyAccessor(new EnvironmentAccessor());
                result.setBeanResolver(new BeanFactoryResolver(appContext));
                result.setTypeLocator(new StandardTypeLocator(beanFactory.getBeanClassLoader()));
                ConversionService conversionService = beanFactory.getConversionService();
                if (conversionService != null) result.setTypeConverter(new StandardTypeConverter(conversionService));
                return result;
            });

            return expr.getValue(sec, resType);
        } catch (ExpressionException ex) {
            throw new SpelException(ex, expression);
        } catch (Throwable ex) {
            throw new SpelException(ex, expression);
        }
    }

    public String evaluateToString(String expression, SpecExpressionContext context) {
        // use the toString() method and not the conversionService in order to maintain behaviour of ShinyProxy 2.6.1 and earlier
        Object res = evaluate(expression, context, Object.class);
        if (res == null) {
            return "";
        }
        if (res instanceof SpelField<?, ?> spelfield) {
            return spelfield.getValueAsString();
        }
        return res.toString();
    }

    public Long evaluateToLong(String expression, SpecExpressionContext context) {
        return evaluate(expression, context, Long.class);
    }

    public Integer evaluateToInteger(String expression, SpecExpressionContext context) {
        return evaluate(expression, context, Integer.class);
    }

    public Boolean evaluateToBoolean(String expression, SpecExpressionContext context) {
        return evaluate(expression, context, Boolean.class);
    }

    public List<String> evaluateToList(List<String> expressions, SpecExpressionContext context) {
        if (expressions == null) return null;
        return expressions.stream()
            .flatMap((el) -> {
                Object result = evaluate(el, context, Object.class);
                if (result == null) {
                    result = new ArrayList<>();
                }
                if (result instanceof List) {
                    return ((List<Object>) result).stream().map(Object::toString);
                }
                if (result instanceof ArrayNode) {
                    return StreamSupport.stream(((ArrayNode) result).spliterator(), false).map(JsonNode::asText);
                }
                return Stream.of(result.toString());
            })
            .toList();
    }
}
