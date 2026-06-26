package org.springframework.context.annotation;

import java.util.Arrays;
import java.util.function.Supplier;

import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 可独立运行的应用上下文，接受 <em>组件类</em> 作为输入——尤其是带有
 * {@link Configuration @Configuration} 注解的类，同时也支持普通的
 * {@link org.springframework.stereotype.Component @Component} 类型及使用
 * {@code javax.inject} 注解的 JSR-330 兼容类。
 *
 * <p>既可以使用 {@link #register(Class...)} 逐个注册类，也可以使用
 * {@link #scan(String...)} 进行类路径扫描。
 *
 * <p>若存在多个 {@code @Configuration} 类，则后加载类中定义的 {@link Bean @Bean}
 * 方法将覆盖早先类中定义的方法。这可以通过额外的 {@code @Configuration}
 * 类有意地覆盖某些 bean 定义。
 *
 * <p>See {@link Configuration @Configuration}'s javadoc for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see #register
 * @see #scan
 * @see AnnotatedBeanDefinitionReader
 * @see ClassPathBeanDefinitionScanner
 * @see org.springframework.context.support.GenericXmlApplicationContext
 * @since 3.0
 */
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

    private final AnnotatedBeanDefinitionReader reader;

    private final ClassPathBeanDefinitionScanner scanner;


    /**
     * 创建一个新的 AnnotationConfigApplicationContext，需要通过调用 {@link #register}
     * 进行填充，然后手动 {@linkplain #refresh 刷新}。
     */
    public AnnotationConfigApplicationContext() {
        StartupStep createAnnotatedBeanDefReader = getApplicationStartup().start("spring.context.annotated-bean-reader.create");
        this.reader = new AnnotatedBeanDefinitionReader(this);
        createAnnotatedBeanDefReader.end();
        this.scanner = new ClassPathBeanDefinitionScanner(this);
    }

    /**
     * 使用给定的 DefaultListableBeanFactory 创建一个新的 AnnotationConfigApplicationContext。
     *
     * @param beanFactory 要为此上下文使用的 DefaultListableBeanFactory 实例
     */
    public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
        super(beanFactory);
        this.reader = new AnnotatedBeanDefinitionReader(this);
        this.scanner = new ClassPathBeanDefinitionScanner(this);
    }

    /**
     * 创建一个新的 AnnotationConfigApplicationContext，从给定的组件类派生 bean 定义并
     * 自动刷新上下文。
     *
     * @param componentClasses 一个或多个组件类，例如 {@link Configuration @Configuration} 类
     */
    public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
        this();
        register(componentClasses);
        refresh();
    }

    /**
     * 创建一个新的 AnnotationConfigApplicationContext，在给定的包中扫描组件，
     * 为这些组件注册 bean 定义，并自动刷新上下文。
     *
     * @param basePackages 要扫描组件类的包
     */
    public AnnotationConfigApplicationContext(String... basePackages) {
        this();
        scan(basePackages);
        refresh();
    }


    /**
     * 将给定的自定义 {@code Environment} 传播到底层的
     * {@link AnnotatedBeanDefinitionReader} 和 {@link ClassPathBeanDefinitionScanner}。
     */
    @Override
    public void setEnvironment(ConfigurableEnvironment environment) {
        super.setEnvironment(environment);
        this.reader.setEnvironment(environment);
        this.scanner.setEnvironment(environment);
    }

    /**
     * 为 {@link AnnotatedBeanDefinitionReader} 和/或
     * {@link ClassPathBeanDefinitionScanner} 提供自定义的
     * {@link BeanNameGenerator}（如果有）。
     * <p>默认是 {@link AnnotationBeanNameGenerator}。
     * <p>必须在调用 {@link #register(Class...)} 和/或 {@link #scan(String...)} 之前调用此方法。
     *
     * @see AnnotatedBeanDefinitionReader#setBeanNameGenerator
     * @see ClassPathBeanDefinitionScanner#setBeanNameGenerator
     * @see AnnotationBeanNameGenerator
     * @see FullyQualifiedAnnotationBeanNameGenerator
     */
    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.reader.setBeanNameGenerator(beanNameGenerator);
        this.scanner.setBeanNameGenerator(beanNameGenerator);
        getBeanFactory().registerSingleton(
                AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
    }

    /**
     * 设置用于已注册组件类的 {@link ScopeMetadataResolver}。
     * <p>默认是 {@link AnnotationScopeMetadataResolver}。
     * <p>必须在调用 {@link #register(Class...)} 和/或 {@link #scan(String...)} 之前调用此方法。
     */
    public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
        this.reader.setScopeMetadataResolver(scopeMetadataResolver);
        this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
    }


    //---------------------------------------------------------------------
    // Implementation of AnnotationConfigRegistry
    //---------------------------------------------------------------------

    /**
     * 注册一个或多个要处理的组件类。
     * <p>注意，必须调用 {@link #refresh()}，上下文才能完整处理这些新类。
     *
     * @param componentClasses 一个或多个组件类，例如 {@link Configuration @Configuration} 类
     * @see #scan(String...)
     * @see #refresh()
     */
    @Override
    public void register(Class<?>... componentClasses) {
        Assert.notEmpty(componentClasses, "At least one component class must be specified");
        StartupStep registerComponentClass = getApplicationStartup().start("spring.context.component-classes.register")
                .tag("classes", () -> Arrays.toString(componentClasses));
        this.reader.register(componentClasses);
        registerComponentClass.end();
    }

    /**
     * 在指定的基础包内执行扫描。
     * <p>注意，必须调用 {@link #refresh()}，上下文才能完整处理这些新类。
     *
     * @param basePackages 要扫描组件类的包
     * @see #register(Class...)
     * @see #refresh()
     */
    @Override
    public void scan(String... basePackages) {
        Assert.notEmpty(basePackages, "At least one base package must be specified");
        StartupStep scanPackages = getApplicationStartup().start("spring.context.base-packages.scan")
                .tag("packages", () -> Arrays.toString(basePackages));
        this.scanner.scan(basePackages);
        scanPackages.end();
    }


    //---------------------------------------------------------------------
    // Adapt superclass registerBean calls to AnnotatedBeanDefinitionReader
    //---------------------------------------------------------------------

    @Override
    public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
                                 @Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

        this.reader.registerBean(beanClass, beanName, supplier, customizers);
    }

}
