package org.springframework.context.annotation;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
 * 在类路径上检测 bean 候选项的 bean 定义扫描器，
 * 会将对应的 bean 定义注册到给定的注册表（{@code BeanFactory}
 * 或 {@code ApplicationContext}）中。
 *
 * <p>候选类通过可配置的类型过滤器检测。默认过滤器包含带有 Spring
 * {@link org.springframework.stereotype.Component @Component}、
 * {@link org.springframework.stereotype.Repository @Repository}、
 * {@link org.springframework.stereotype.Service @Service} 或
 * {@link org.springframework.stereotype.Controller @Controller} stereotype
 * 注解的类。
 *
 * <p>若可用，也支持 Java EE 6 的 {@link javax.annotation.ManagedBean}
 * 和 JSR-330 的 {@link javax.inject.Named} 注解。
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see AnnotationConfigApplicationContext#scan
 * @see org.springframework.stereotype.Component
 * @see org.springframework.stereotype.Repository
 * @see org.springframework.stereotype.Service
 * @see org.springframework.stereotype.Controller
 * @since 2.5
 */
public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {

    private final BeanDefinitionRegistry registry;

    private BeanDefinitionDefaults beanDefinitionDefaults = new BeanDefinitionDefaults();

    @Nullable
    private String[] autowireCandidatePatterns;

    private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

    private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

    private boolean includeAnnotationConfig = true;


    /**
     * 为给定 bean 工厂创建一个新的 {@code ClassPathBeanDefinitionScanner}。
     *
     * @param registry 用于加载 bean 定义的 {@code BeanFactory}，其形式为
     *                 {@code BeanDefinitionRegistry}
     */
    public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
        this(registry, true);
    }

    /**
     * 为给定 bean 工厂创建一个新的 {@code ClassPathBeanDefinitionScanner}。
     * <p>如果传入的 bean 工厂不仅实现了 {@code BeanDefinitionRegistry} 接口，
     * 还实现了 {@code ResourceLoader} 接口，则也会将其用作默认的
     * {@code ResourceLoader}。这通常适用于
     * {@link org.springframework.context.ApplicationContext} 实现。
     * <p>如果给定的是普通的 {@code BeanDefinitionRegistry}，默认的
     * {@code ResourceLoader} 将是
     * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver}。
     * <p>如果传入的 bean 工厂还实现了 {@link EnvironmentCapable}，则会使用其环境。
     * 否则，读取器会初始化并使用
     * {@link org.springframework.core.env.StandardEnvironment}。所有
     * {@code ApplicationContext} 实现都属于 {@code EnvironmentCapable}，
     * 而普通 {@code BeanFactory} 实现则不是。
     *
     * @param registry          用于加载 bean 定义的 {@code BeanFactory}，其形式为
     *                          {@code BeanDefinitionRegistry}
     * @param useDefaultFilters 是否包含默认过滤器，用于识别带有
     *                          {@link org.springframework.stereotype.Component @Component}、
     *                          {@link org.springframework.stereotype.Repository @Repository}、
     *                          {@link org.springframework.stereotype.Service @Service} 和
     *                          {@link org.springframework.stereotype.Controller @Controller} stereotype 注解的类
     * @see #setResourceLoader
     * @see #setEnvironment
     */
    public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
        this(registry, useDefaultFilters, getOrCreateEnvironment(registry));
    }

    /**
     * 为给定 bean 工厂创建一个新的 {@code ClassPathBeanDefinitionScanner}，
     * 并在评估 bean 定义 profile 元数据时使用给定的 {@link Environment}。
     * <p>如果传入的 bean 工厂不仅实现了 {@code BeanDefinitionRegistry} 接口，
     * 还实现了 {@link ResourceLoader} 接口，则也会将其用作默认的
     * {@code ResourceLoader}。这通常适用于
     * {@link org.springframework.context.ApplicationContext} 实现。
     * <p>如果给定的是普通的 {@code BeanDefinitionRegistry}，默认的
     * {@code ResourceLoader} 将是
     * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver}。
     *
     * @param registry          用于加载 bean 定义的 {@code BeanFactory}，其形式为
     *                          {@code BeanDefinitionRegistry}
     * @param useDefaultFilters 是否包含默认过滤器，用于识别带有
     *                          {@link org.springframework.stereotype.Component @Component}、
     *                          {@link org.springframework.stereotype.Repository @Repository}、
     *                          {@link org.springframework.stereotype.Service @Service} 和
     *                          {@link org.springframework.stereotype.Controller @Controller} stereotype 注解的类
     * @param environment       在评估 bean 定义 profile 元数据时使用的 Spring {@link Environment}
     * @see #setResourceLoader
     * @since 3.1
     */
    public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
                                          Environment environment) {

        this(registry, useDefaultFilters, environment,
                (registry instanceof ResourceLoader ? (ResourceLoader) registry : null));
    }

    /**
     * 为给定 bean 工厂创建一个新的 {@code ClassPathBeanDefinitionScanner}，
     * 并在评估 bean 定义 profile 元数据时使用给定的 {@link Environment}。
     *
     * @param registry          用于加载 bean 定义的 {@code BeanFactory}，其形式为
     *                          {@code BeanDefinitionRegistry}
     * @param useDefaultFilters 是否包含默认过滤器，用于识别带有
     *                          {@link org.springframework.stereotype.Component @Component}、
     *                          {@link org.springframework.stereotype.Repository @Repository}、
     *                          {@link org.springframework.stereotype.Service @Service} 和
     *                          {@link org.springframework.stereotype.Controller @Controller} stereotype 注解的类
     * @param environment       在评估 bean 定义 profile 元数据时使用的 Spring {@link Environment}
     * @param resourceLoader    要使用的 {@link ResourceLoader}
     * @since 4.3.6
     */
    public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
                                          Environment environment, @Nullable ResourceLoader resourceLoader) {

        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        this.registry = registry;

        if (useDefaultFilters) {
            registerDefaultFilters();
        }
        setEnvironment(environment);
        setResourceLoader(resourceLoader);
    }


    /**
     * 返回此扫描器操作的 BeanDefinitionRegistry。
     */
    @Override
    public final BeanDefinitionRegistry getRegistry() {
        return this.registry;
    }

    /**
     * 设置用于已检测 bean 的默认值。
     *
     * @see BeanDefinitionDefaults
     */
    public void setBeanDefinitionDefaults(@Nullable BeanDefinitionDefaults beanDefinitionDefaults) {
        this.beanDefinitionDefaults =
                (beanDefinitionDefaults != null ? beanDefinitionDefaults : new BeanDefinitionDefaults());
    }

    /**
     * 返回用于已检测 bean 的默认值（永不为 {@code null}）。
     *
     * @since 4.1
     */
    public BeanDefinitionDefaults getBeanDefinitionDefaults() {
        return this.beanDefinitionDefaults;
    }

    /**
     * 设置用于判定自动装配候选项的名称匹配模式。
     *
     * @param autowireCandidatePatterns 要匹配的模式
     */
    public void setAutowireCandidatePatterns(@Nullable String... autowireCandidatePatterns) {
        this.autowireCandidatePatterns = autowireCandidatePatterns;
    }

    /**
     * 设置用于已检测 bean 类的 BeanNameGenerator。
     * <p>默认值为 {@link AnnotationBeanNameGenerator}。
     */
    public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator =
                (beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
    }

    /**
     * 设置用于已检测 bean 类的 ScopeMetadataResolver。
     * 注意，这会覆盖任何自定义的 "scopedProxyMode" 设置。
     * <p>默认值为 {@link AnnotationScopeMetadataResolver}。
     *
     * @see #setScopedProxyMode
     */
    public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
        this.scopeMetadataResolver =
                (scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
    }

    /**
     * 指定非单例作用域 bean 的代理行为。
     * 注意，这会覆盖任何自定义的 "scopeMetadataResolver" 设置。
     * <p>默认值为 {@link ScopedProxyMode#NO}。
     *
     * @see #setScopeMetadataResolver
     */
    public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
        this.scopeMetadataResolver = new AnnotationScopeMetadataResolver(scopedProxyMode);
    }

    /**
     * 指定是否注册注解配置后置处理器。
     * <p>默认会注册这些后置处理器。关闭后可忽略这些注解，或以其他方式处理它们。
     */
    public void setIncludeAnnotationConfig(boolean includeAnnotationConfig) {
        this.includeAnnotationConfig = includeAnnotationConfig;
    }


    /**
     * 在指定基础包内执行扫描。
     *
     * @param basePackages 要检查注解类的包
     * @return 已注册的 bean 数量
     */
    public int scan(String... basePackages) {
        int beanCountAtScanStart = this.registry.getBeanDefinitionCount();

        doScan(basePackages);

        // 如有需要，注册注解配置处理器。
        if (this.includeAnnotationConfig) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
        }

        return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
    }

    /**
     * 在指定基础包内执行扫描，并返回已注册的 bean 定义。
     * <p>此方法<i>不会</i>注册注解配置处理器，而是将该操作交由调用方处理。
     *
     * @param basePackages 要检查注解类的包
     * @return 为工具注册目的而返回的已注册 bean 集合（若有，永不为 {@code null}）
     */
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Assert.notEmpty(basePackages, "At least one base package must be specified");
        Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
            for (BeanDefinition candidate : candidates) {
                ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
                candidate.setScope(scopeMetadata.getScopeName());
                String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
                if (candidate instanceof AbstractBeanDefinition) {
                    postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
                }
                if (candidate instanceof AnnotatedBeanDefinition) {
                    AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
                }
                if (checkCandidate(beanName, candidate)) {
                    BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
                    definitionHolder =
                            AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
                    beanDefinitions.add(definitionHolder);
                    registerBeanDefinition(definitionHolder, this.registry);
                }
            }
        }
        return beanDefinitions;
    }

    /**
     * 对给定 bean 定义应用更多设置，
     * 超出从扫描组件类中获取到的内容。
     *
     * @param beanDefinition 扫描得到的 bean 定义
     * @param beanName       为给定 bean 生成的 bean 名称
     */
    protected void postProcessBeanDefinition(AbstractBeanDefinition beanDefinition, String beanName) {
        beanDefinition.applyDefaults(this.beanDefinitionDefaults);
        if (this.autowireCandidatePatterns != null) {
            beanDefinition.setAutowireCandidate(PatternMatchUtils.simpleMatch(this.autowireCandidatePatterns, beanName));
        }
    }

    /**
     * 将指定 bean 注册到给定注册表中。
     * <p>可在子类中重写，例如用于调整注册流程，
     * 或为每个扫描到的 bean 继续注册更多 bean 定义。
     *
     * @param definitionHolder 该 bean 的 bean 定义及 bean 名称
     * @param registry         用于注册该 bean 的 BeanDefinitionRegistry
     */
    protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
        BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
    }


    /**
     * 检查给定候选项的 bean 名称，判断对应 bean 定义是否需要注册，
     * 或是否与现有定义冲突。
     *
     * @param beanName       建议使用的 bean 名称
     * @param beanDefinition 对应的 bean 定义
     * @return 若 bean 可以按原样注册则返回 {@code true}；
     * 若因指定名称已存在兼容定义而应跳过则返回 {@code false}
     * @throws IllegalStateException 如果发现指定名称已有不兼容的 bean 定义
     */
    protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
        if (!this.registry.containsBeanDefinition(beanName)) {
            return true;
        }
        BeanDefinition existingDef = this.registry.getBeanDefinition(beanName);
        BeanDefinition originatingDef = existingDef.getOriginatingBeanDefinition();
        if (originatingDef != null) {
            existingDef = originatingDef;
        }
        if (isCompatible(beanDefinition, existingDef)) {
            return false;
        }
        throw new ConflictingBeanDefinitionException("Annotation-specified bean name '" + beanName +
                "' for bean class [" + beanDefinition.getBeanClassName() + "] conflicts with existing, " +
                "non-compatible bean definition of same name and class [" + existingDef.getBeanClassName() + "]");
    }

    /**
     * 判断给定的新 bean 定义是否与给定的现有 bean 定义兼容。
     * <p>默认实现中，当现有 bean 定义来自同一来源或来自非扫描来源时，
     * 会将两者视为兼容。
     *
     * @param newDef      新 bean 定义，来源于扫描
     * @param existingDef 现有 bean 定义，可能是显式定义的，
     *                    或是之前扫描生成的
     * @return 这些定义是否被视为兼容；若兼容则跳过新定义并保留现有定义
     */
    protected boolean isCompatible(BeanDefinition newDef, BeanDefinition existingDef) {
        return (!(existingDef instanceof ScannedGenericBeanDefinition) ||  // 显式注册的覆盖 bean
                (newDef.getSource() != null && newDef.getSource().equals(existingDef.getSource())) ||  // 同一文件被扫描两次
                newDef.equals(existingDef));  // 等价类被扫描两次
    }


    /**
     * 尽可能从给定注册表获取 Environment；否则返回一个新的 StandardEnvironment。
     */
    private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        if (registry instanceof EnvironmentCapable) {
            return ((EnvironmentCapable) registry).getEnvironment();
        }
        return new StandardEnvironment();
    }

}
