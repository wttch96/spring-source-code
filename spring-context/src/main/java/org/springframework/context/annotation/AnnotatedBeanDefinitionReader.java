package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 用于以编程方式注册 bean 类的便捷适配器。
 *
 * <p>这是 {@link ClassPathBeanDefinitionScanner} 的一种替代方案，
 * 采用相同的注解解析机制，但仅作用于显式注册的类。
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @see AnnotationConfigApplicationContext#register
 * @since 3.0
 */
public class AnnotatedBeanDefinitionReader {

    /// bean 定义注册表，用于加载 bean 定义
    private final BeanDefinitionRegistry registry;
    /// bean 名称生成器，用于已检测 bean 类
    private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;
    /// 作用域元数据解析器，用于已注册组件类
    private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();
    /// 条件评估器，用于评估带有 @Conditional 注解的组件类是否应被注册
    private ConditionEvaluator conditionEvaluator;


    /**
     * 为给定注册表创建一个新的 {@code AnnotatedBeanDefinitionReader}。
     * <p>如果注册表实现了 {@link EnvironmentCapable}（例如 {@code ApplicationContext}），
     * 则会继承其 {@link Environment}；否则会创建并使用一个新的
     * {@link StandardEnvironment}。
     *
     * @param registry 用于加载 bean 定义的 {@code BeanFactory}，
     *                 其形式为 {@code BeanDefinitionRegistry}
     * @see #AnnotatedBeanDefinitionReader(BeanDefinitionRegistry, Environment)
     * @see #setEnvironment(Environment)
     */
    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
        this(registry, getOrCreateEnvironment(registry));
    }

    /**
     * 为给定注册表创建一个新的 {@code AnnotatedBeanDefinitionReader}，
     * 并使用给定的 {@link Environment}。
     *
     * @param registry    用于加载 bean 定义的 {@code BeanFactory}，
     *                    其形式为 {@code BeanDefinitionRegistry}
     * @param environment 在评估 bean 定义 profile 时要使用的 {@code Environment}
     * @since 3.1
     */
    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        Assert.notNull(environment, "Environment must not be null");
        this.registry = registry;
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
        AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
    }


    /**
     * 获取此读取器操作的 BeanDefinitionRegistry。
     */
    public final BeanDefinitionRegistry getRegistry() {
        return this.registry;
    }

    /**
     * 设置用于评估带有 {@link Conditional @Conditional} 注解的组件类
     * 是否应被注册的 {@code Environment}。
     * <p>默认值为 {@link StandardEnvironment}。
     *
     * @see #registerBean(Class, String, Class...)
     */
    public void setEnvironment(Environment environment) {
        this.conditionEvaluator = new ConditionEvaluator(this.registry, environment, null);
    }

    /**
     * 设置用于已检测 bean 类的 {@code BeanNameGenerator}。
     * <p>默认值为 {@link AnnotationBeanNameGenerator}。
     */
    public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator =
                (beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
    }

    /**
     * 设置用于已注册组件类的 {@code ScopeMetadataResolver}。
     * <p>默认值为 {@link AnnotationScopeMetadataResolver}。
     */
    public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
        this.scopeMetadataResolver =
                (scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
    }


    /**
     * 注册一个或多个待处理的组件类。
     * <p>对 {@code register} 的调用是幂等的；重复添加同一个组件类不会产生额外效果。
     *
     * @param componentClasses 一个或多个组件类，例如 {@link Configuration @Configuration} 类
     */
    public void register(Class<?>... componentClasses) {
        for (Class<?> componentClass : componentClasses) {
            registerBean(componentClass);
        }
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解。
     *
     * @param beanClass bean 的类
     */
    public void registerBean(Class<?> beanClass) {
        doRegisterBean(beanClass, null, null, null, null);
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解。
     *
     * @param beanClass bean 的类
     * @param name      bean 的显式名称
     *                  （或传入 {@code null} 以生成默认 bean 名称）
     * @since 5.2
     */
    public void registerBean(Class<?> beanClass, @Nullable String name) {
        doRegisterBean(beanClass, name, null, null, null);
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解。
     *
     * @param beanClass  bean 的类
     * @param qualifiers 除了 bean 类级别上的 qualifier 之外，
     *                   还要考虑的特定 qualifier 注解
     */
    @SuppressWarnings("unchecked")
    public void registerBean(Class<?> beanClass, Class<? extends Annotation>... qualifiers) {
        doRegisterBean(beanClass, null, qualifiers, null, null);
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解。
     *
     * @param beanClass  bean 的类
     * @param name       bean 的显式名称
     *                   （或传入 {@code null} 以生成默认 bean 名称）
     * @param qualifiers 除了 bean 类级别上的 qualifier 之外，
     *                   还要考虑的特定 qualifier 注解
     */
    @SuppressWarnings("unchecked")
    public void registerBean(Class<?> beanClass, @Nullable String name,
                             Class<? extends Annotation>... qualifiers) {

        doRegisterBean(beanClass, name, qualifiers, null, null);
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解，
     * 并使用给定 supplier 获取新实例（可声明为 lambda 表达式或方法引用）。
     *
     * @param beanClass bean 的类
     * @param supplier  用于创建 bean 实例的回调
     *                  （可以为 {@code null}）
     * @since 5.0
     */
    public <T> void registerBean(Class<T> beanClass, @Nullable Supplier<T> supplier) {
        doRegisterBean(beanClass, null, null, supplier, null);
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解，
     * 并使用给定 supplier 获取新实例（可声明为 lambda 表达式或方法引用）。
     *
     * @param beanClass bean 的类
     * @param name      bean 的显式名称
     *                  （或传入 {@code null} 以生成默认 bean 名称）
     * @param supplier  用于创建 bean 实例的回调
     *                  （可以为 {@code null}）
     * @since 5.0
     */
    public <T> void registerBean(Class<T> beanClass, @Nullable String name, @Nullable Supplier<T> supplier) {
        doRegisterBean(beanClass, name, null, supplier, null);
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解。
     *
     * @param beanClass   bean 的类
     * @param name        bean 的显式名称
     *                    （或传入 {@code null} 以生成默认 bean 名称）
     * @param supplier    用于创建 bean 实例的回调
     *                    （可以为 {@code null}）
     * @param customizers 一个或多个用于定制工厂 {@link BeanDefinition} 的回调，
     *                    例如设置 lazy-init 或 primary 标志
     * @since 5.2
     */
    public <T> void registerBean(Class<T> beanClass, @Nullable String name, @Nullable Supplier<T> supplier,
                                 BeanDefinitionCustomizer... customizers) {

        doRegisterBean(beanClass, name, null, supplier, customizers);
    }

    /**
     * 根据给定 bean 类注册 bean，其元数据来源于类上声明的注解。
     *
     * @param beanClass   bean 的类
     * @param name        bean 的显式名称
     * @param qualifiers  若有，除了 bean 类级别上的 qualifier 之外，
     *                    还要考虑的特定 qualifier 注解
     * @param supplier    用于创建 bean 实例的回调
     *                    （可以为 {@code null}）
     * @param customizers 一个或多个用于定制工厂 {@link BeanDefinition} 的回调，
     *                    例如设置 lazy-init 或 primary 标志
     * @since 5.0
     */
    private <T> void doRegisterBean(Class<T> beanClass, @Nullable String name,
                                    @Nullable Class<? extends Annotation>[] qualifiers, @Nullable Supplier<T> supplier,
                                    @Nullable BeanDefinitionCustomizer[] customizers) {

        AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(beanClass);
        if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
            return;
        }

        abd.setInstanceSupplier(supplier);
        ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
        abd.setScope(scopeMetadata.getScopeName());
        String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

        AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
        if (qualifiers != null) {
            for (Class<? extends Annotation> qualifier : qualifiers) {
                if (Primary.class == qualifier) {
                    abd.setPrimary(true);
                } else if (Lazy.class == qualifier) {
                    abd.setLazyInit(true);
                } else {
                    abd.addQualifier(new AutowireCandidateQualifier(qualifier));
                }
            }
        }
        if (customizers != null) {
            for (BeanDefinitionCustomizer customizer : customizers) {
                customizer.customize(abd);
            }
        }

        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
        definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
        BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
    }


    /**
     * 尽可能从给定注册表中获取 Environment；否则返回新的 StandardEnvironment。
     */
    private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        if (registry instanceof EnvironmentCapable) {
            return ((EnvironmentCapable) registry).getEnvironment();
        }
        return new StandardEnvironment();
    }

}
