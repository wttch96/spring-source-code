package org.springframework.web.context.support;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigRegistry;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ScopeMetadataResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ContextLoader;

/**
 * 接受 <em>组件类</em> 作为输入的
 * {@link org.springframework.web.context.WebApplicationContext WebApplicationContext}
 * 实现——尤其是 {@link org.springframework.context.annotation.Configuration @Configuration}
 * 类，同时也支持普通的 {@link org.springframework.stereotype.Component @Component}
 * 类，以及使用 {@code javax.inject} 注解的符合 JSR-330 的类。
 *
 * <p>既支持逐个注册类（将类名指定为配置位置），也支持通过类路径扫描
 * （将基础包指定为配置位置）。
 *
 * <p>这基本上相当于 Web 环境下的
 * {@link org.springframework.context.annotation.AnnotationConfigApplicationContext
 * AnnotationConfigApplicationContext}。不过，与
 * {@code AnnotationConfigApplicationContext} 不同的是，这个类并不继承
 * {@link org.springframework.context.support.GenericApplicationContext
 * GenericApplicationContext}，因此也不提供 {@code GenericApplicationContext}
 * 中可用的某些便捷 {@code registerBean(...)} 方法。如果你希望在 Web 环境中将带注解的
 * <em>组件类</em> 注册到 {@code GenericApplicationContext}，可以将
 * {@code GenericWebApplicationContext} 与
 * {@link org.springframework.context.annotation.AnnotatedBeanDefinitionReader
 * AnnotatedBeanDefinitionReader} 一起使用。详情和示例请参见
 * {@link GenericWebApplicationContext} 的 Javadoc。
 *
 * <p>要使用这个应用上下文，必须将 ContextLoader 的
 * {@linkplain ContextLoader#CONTEXT_CLASS_PARAM "contextClass"} context-param 和/或
 * FrameworkServlet 的 "contextClass" init-param 设置为该类的完全限定名。
 *
 * <p>从 Spring 3.1 开始，在使用基于代码、替代 {@code web.xml} 的
 * {@link org.springframework.web.WebApplicationInitializer WebApplicationInitializer}
 * 方式时，也可以直接实例化该类并注入到 Spring 的 {@code DispatcherServlet}
 * 或 {@code ContextLoaderListener} 中。详情和使用示例请参见其 Javadoc。
 *
 * <p>与 {@link XmlWebApplicationContext} 不同，这里不会假定任何默认的配置类位置。
 * 相反，必须为 {@link ContextLoader} 设置
 * {@linkplain ContextLoader#CONFIG_LOCATION_PARAM "contextConfigLocation"} context-param，
 * 和/或为 FrameworkServlet 设置 "contextConfigLocation" init-param。参数值既可以包含
 * 完全限定类名，也可以包含要扫描组件的基础包。有关这些位置如何处理的详细信息，
 * 请参见 {@link #loadBeanDefinitions}。
 *
 * <p>作为设置 "contextConfigLocation" 参数的替代方案，用户也可以实现
 * {@link org.springframework.context.ApplicationContextInitializer
 * ApplicationContextInitializer}，并设置
 * {@linkplain ContextLoader#CONTEXT_INITIALIZER_CLASSES_PARAM "contextInitializerClasses"}
 * context-param / init-param。在这种情况下，应优先使用 {@link #refresh()}
 * 和 {@link #scan(String...)} 方法，而不是 {@link #setConfigLocation(String)}
 * 方法，后者主要供 {@code ContextLoader} 使用。
 *
 * <p>注意：如果存在多个 {@code @Configuration} 类，后加载文件中定义的
 * {@code @Bean} 将覆盖先加载文件中定义的同名 {@code @Bean}。这可以用来通过额外的
 * {@code @Configuration} 类有意覆盖某些 bean 定义。
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see org.springframework.context.annotation.AnnotationConfigApplicationContext
 * @see org.springframework.web.context.support.GenericWebApplicationContext
 * @since 3.0
 */
public class AnnotationConfigWebApplicationContext extends AbstractRefreshableWebApplicationContext
        implements AnnotationConfigRegistry {

    @Nullable
    private BeanNameGenerator beanNameGenerator;

    @Nullable
    private ScopeMetadataResolver scopeMetadataResolver;

    private final Set<Class<?>> componentClasses = new LinkedHashSet<>();

    private final Set<String> basePackages = new LinkedHashSet<>();


    /**
     * 设置一个自定义的 {@link BeanNameGenerator}，供 {@link AnnotatedBeanDefinitionReader}
     * 和/或 {@link ClassPathBeanDefinitionScanner} 使用。
     * <p>默认值为 {@link org.springframework.context.annotation.AnnotationBeanNameGenerator}。
     *
     * @see AnnotatedBeanDefinitionReader#setBeanNameGenerator
     * @see ClassPathBeanDefinitionScanner#setBeanNameGenerator
     */
    public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = beanNameGenerator;
    }

    /**
     * 返回自定义的 {@link BeanNameGenerator}，供 {@link AnnotatedBeanDefinitionReader}
     * 和/或 {@link ClassPathBeanDefinitionScanner} 使用（如果有）。
     */
    @Nullable
    protected BeanNameGenerator getBeanNameGenerator() {
        return this.beanNameGenerator;
    }

    /**
     * 设置一个自定义的 {@link ScopeMetadataResolver}，供 {@link AnnotatedBeanDefinitionReader}
     * 和/或 {@link ClassPathBeanDefinitionScanner} 使用。
     * <p>默认值为 {@link org.springframework.context.annotation.AnnotationScopeMetadataResolver}。
     *
     * @see AnnotatedBeanDefinitionReader#setScopeMetadataResolver
     * @see ClassPathBeanDefinitionScanner#setScopeMetadataResolver
     */
    public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
        this.scopeMetadataResolver = scopeMetadataResolver;
    }

    /**
     * 返回自定义的 {@link ScopeMetadataResolver}，供 {@link AnnotatedBeanDefinitionReader}
     * 和/或 {@link ClassPathBeanDefinitionScanner} 使用（如果有）。
     */
    @Nullable
    protected ScopeMetadataResolver getScopeMetadataResolver() {
        return this.scopeMetadataResolver;
    }


    /**
     * 注册一个或多个待处理的组件类。
     * <p>注意，必须调用 {@link #refresh()}，上下文才能完整处理这些新类。
     *
     * @param componentClasses 一个或多个组件类，
     *                         例如 {@link org.springframework.context.annotation.Configuration @Configuration} 类
     * @see #scan(String...)
     * @see #loadBeanDefinitions(DefaultListableBeanFactory)
     * @see #setConfigLocation(String)
     * @see #refresh()
     */
    @Override
    public void register(Class<?>... componentClasses) {
        Assert.notEmpty(componentClasses, "At least one component class must be specified");
        Collections.addAll(this.componentClasses, componentClasses);
    }

    /**
     * 在指定的基础包内执行扫描。
     * <p>注意，必须调用 {@link #refresh()}，上下文才能完整处理这些新类。
     *
     * @param basePackages 要检查组件类的包
     * @see #loadBeanDefinitions(DefaultListableBeanFactory)
     * @see #register(Class...)
     * @see #setConfigLocation(String)
     * @see #refresh()
     */
    @Override
    public void scan(String... basePackages) {
        Assert.notEmpty(basePackages, "At least one base package must be specified");
        Collections.addAll(this.basePackages, basePackages);
    }


    /**
     * 为通过 {@link #register(Class...)} 指定的类注册
     * {@link org.springframework.beans.factory.config.BeanDefinition}，并扫描通过
     * {@link #scan(String...)} 指定的所有包。
     * <p>对于通过 {@link #setConfigLocation(String)} 或
     * {@link #setConfigLocations(String[])} 指定的值，首先尝试将每个位置作为类加载；
     * 如果类加载成功，则注册 {@code BeanDefinition}；如果类加载失败
     * （即抛出 {@code ClassNotFoundException}），则认为该值是一个包，并尝试在其中
     * 扫描组件类。
     * <p>启用默认的注解配置后置处理器集合，因此可以使用 {@code @Autowired}、
     * {@code @Required} 及相关注解。
     * <p>除非在 stereotype 注解中提供了 {@code value} 属性，否则配置类的 bean 定义
     * 会使用生成的 bean 定义名称进行注册。
     *
     * @param beanFactory 要将 bean 定义加载到其中的 bean 工厂
     * @see #register(Class...)
     * @see #scan(String...)
     * @see #setConfigLocation(String)
     * @see #setConfigLocations(String[])
     * @see AnnotatedBeanDefinitionReader
     * @see ClassPathBeanDefinitionScanner
     */
    @Override
    protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
        AnnotatedBeanDefinitionReader reader = getAnnotatedBeanDefinitionReader(beanFactory);
        ClassPathBeanDefinitionScanner scanner = getClassPathBeanDefinitionScanner(beanFactory);

        BeanNameGenerator beanNameGenerator = getBeanNameGenerator();
        if (beanNameGenerator != null) {
            reader.setBeanNameGenerator(beanNameGenerator);
            scanner.setBeanNameGenerator(beanNameGenerator);
            beanFactory.registerSingleton(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
        }

        ScopeMetadataResolver scopeMetadataResolver = getScopeMetadataResolver();
        if (scopeMetadataResolver != null) {
            reader.setScopeMetadataResolver(scopeMetadataResolver);
            scanner.setScopeMetadataResolver(scopeMetadataResolver);
        }

        if (!this.componentClasses.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Registering component classes: [" +
                        StringUtils.collectionToCommaDelimitedString(this.componentClasses) + "]");
            }
            reader.register(ClassUtils.toClassArray(this.componentClasses));
        }

        if (!this.basePackages.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Scanning base packages: [" +
                        StringUtils.collectionToCommaDelimitedString(this.basePackages) + "]");
            }
            scanner.scan(StringUtils.toStringArray(this.basePackages));
        }

        String[] configLocations = getConfigLocations();
        if (configLocations != null) {
            for (String configLocation : configLocations) {
                try {
                    Class<?> clazz = ClassUtils.forName(configLocation, getClassLoader());
                    if (logger.isTraceEnabled()) {
                        logger.trace("Registering [" + configLocation + "]");
                    }
                    reader.register(clazz);
                } catch (ClassNotFoundException ex) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Could not load class for config location [" + configLocation +
                                "] - trying package scan. " + ex);
                    }
                    int count = scanner.scan(configLocation);
                    if (count == 0 && logger.isDebugEnabled()) {
                        logger.debug("No component classes found for specified class/package [" + configLocation + "]");
                    }
                }
            }
        }
    }


    /**
     * 为给定的 bean 工厂构建一个 {@link AnnotatedBeanDefinitionReader}。
     * <p>它应当预先配置好 {@code Environment}（如果需要），但还不要配置
     * {@code BeanNameGenerator} 或 {@code ScopeMetadataResolver}。
     *
     * @param beanFactory 要将 bean 定义加载到其中的 bean 工厂
     * @see #getEnvironment()
     * @see #getBeanNameGenerator()
     * @see #getScopeMetadataResolver()
     * @since 4.1.9
     */
    protected AnnotatedBeanDefinitionReader getAnnotatedBeanDefinitionReader(DefaultListableBeanFactory beanFactory) {
        return new AnnotatedBeanDefinitionReader(beanFactory, getEnvironment());
    }

    /**
     * 为给定的 bean 工厂构建一个 {@link ClassPathBeanDefinitionScanner}。
     * <p>它应当预先配置好 {@code Environment}（如果需要），但还不要配置
     * {@code BeanNameGenerator} 或 {@code ScopeMetadataResolver}。
     *
     * @param beanFactory 要将 bean 定义加载到其中的 bean 工厂
     * @see #getEnvironment()
     * @see #getBeanNameGenerator()
     * @see #getScopeMetadataResolver()
     * @since 4.1.9
     */
    protected ClassPathBeanDefinitionScanner getClassPathBeanDefinitionScanner(DefaultListableBeanFactory beanFactory) {
        return new ClassPathBeanDefinitionScanner(beanFactory, true, getEnvironment());
    }

}
