package org.springframework.context.annotation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.event.DefaultEventListenerFactory;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * 用于便捷注册基于注解配置的常见
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} 和
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor}
 * 定义的工具类。还会注册一个通用的
 * {@link org.springframework.beans.factory.support.AutowireCandidateResolver}。
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @see ContextAnnotationAutowireCandidateResolver
 * @see ConfigurationClassPostProcessor
 * @see CommonAnnotationBeanPostProcessor
 * @see org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
 * @see org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor
 * @since 2.5
 */
public abstract class AnnotationConfigUtils {

    /**
     * 内部管理的 Configuration 注解处理器的 bean 名称。
     */
    public static final String CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME =
            "org.springframework.context.annotation.internalConfigurationAnnotationProcessor";

    /**
     * 内部管理的 BeanNameGenerator 的 bean 名称，用于处理
     * {@link Configuration} 类。由 {@link AnnotationConfigApplicationContext}
     * 和 {@code AnnotationConfigWebApplicationContext} 在启动阶段设置，
     * 以便让任何自定义命名生成策略对底层的
     * {@link ConfigurationClassPostProcessor} 可见。
     *
     * @since 3.1.1
     */
    public static final String CONFIGURATION_BEAN_NAME_GENERATOR =
            "org.springframework.context.annotation.internalConfigurationBeanNameGenerator";

    /**
     * 内部管理的 Autowired 注解处理器的 bean 名称。
     */
    public static final String AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME =
            "org.springframework.context.annotation.internalAutowiredAnnotationProcessor";

    /**
     * 内部管理的 Required 注解处理器的 bean 名称。
     *
     * @deprecated 自 5.1 起已弃用，因为默认不再注册 Required 处理器。
     */
    @Deprecated
    public static final String REQUIRED_ANNOTATION_PROCESSOR_BEAN_NAME =
            "org.springframework.context.annotation.internalRequiredAnnotationProcessor";

    /**
     * 内部管理的 JSR-250 注解处理器的 bean 名称。
     */
    public static final String COMMON_ANNOTATION_PROCESSOR_BEAN_NAME =
            "org.springframework.context.annotation.internalCommonAnnotationProcessor";

    /**
     * 内部管理的 JPA 注解处理器的 bean 名称。
     */
    public static final String PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME =
            "org.springframework.context.annotation.internalPersistenceAnnotationProcessor";

    private static final String PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME =
            "org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor";

    /**
     * 内部管理的 @EventListener 注解处理器的 bean 名称。
     */
    public static final String EVENT_LISTENER_PROCESSOR_BEAN_NAME =
            "org.springframework.context.event.internalEventListenerProcessor";

    /**
     * 内部管理的 EventListenerFactory 的 bean 名称。
     */
    public static final String EVENT_LISTENER_FACTORY_BEAN_NAME =
            "org.springframework.context.event.internalEventListenerFactory";

    /// jsr250
    private static final boolean jsr250Present;
    /// jpa
    private static final boolean jpaPresent;

    static {
        ClassLoader classLoader = AnnotationConfigUtils.class.getClassLoader();
        jsr250Present = ClassUtils.isPresent("javax.annotation.Resource", classLoader);
        jpaPresent = ClassUtils.isPresent("javax.persistence.EntityManagerFactory", classLoader) &&
                ClassUtils.isPresent(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, classLoader);
    }


    /**
     * 在给定注册表中注册所有相关的注解后置处理器。
     *
     * @param registry 要操作的注册表
     */
    public static void registerAnnotationConfigProcessors(BeanDefinitionRegistry registry) {
        registerAnnotationConfigProcessors(registry, null);
    }

    /**
     * 在给定注册表中注册所有相关的注解后置处理器。
     *
     * @param registry 要操作的注册表
     * @param source   触发此次注册的配置源元素（已提取）。
     *                 可能为 {@code null}。
     * @return BeanDefinitionHolder 集合，包含此调用实际注册的所有 bean 定义
     */
    public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(
            BeanDefinitionRegistry registry, @Nullable Object source) {
        // 尝试从传入的 BeanDefinitionRegistry 中拿到底层 DefaultListableBeanFactory。
        // 只有拿到真正的 BeanFactory 后，才能设置依赖比较器和自动装配候选解析器。
        DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
        if (beanFactory != null) {
            // 注解驱动配置依赖 @Order、@Priority、Ordered 等排序语义。
            // 如果用户还没有设置支持注解排序的比较器，这里安装 Spring 默认的注解感知比较器。
            if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
                beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
            }
            // ContextAnnotationAutowireCandidateResolver 支持 @Lazy、@Qualifier、@Value 等
            // 上下文注解语义，是注解驱动自动装配能够正确工作的关键组件。
            if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
                beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
            }
        }

        // 使用 LinkedHashSet 保留注册顺序，便于调用方按稳定顺序观察本次实际新增的定义。
        Set<BeanDefinitionHolder> beanDefs = new LinkedHashSet<>(8);

        // 注册 ConfigurationClassPostProcessor。
        // 它负责解析 @Configuration、@ComponentScan、@Import、@Bean 等配置类相关注解。
        // 如果容器中已经存在同名基础设施 Bean，则尊重已有定义，避免重复注册或覆盖用户定制。
        if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
            // 使用 RootBeanDefinition 表示一个完整的顶层 Bean 定义。
            RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
            // 保存触发注册的源对象，便于后续诊断、工具展示或错误定位。
            def.setSource(source);
            // registerPostProcessor 会统一标记 ROLE_INFRASTRUCTURE 并写入注册表。
            beanDefs.add(registerPostProcessor(registry, def, CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
        }

        // 注册 AutowiredAnnotationBeanPostProcessor。
        // 它负责处理 @Autowired、@Value、@Inject 等依赖注入注解。
        if (!registry.containsBeanDefinition(AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
            RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
            // 将 source 传递给 BeanDefinition，保持和其他内部处理器一致的元数据来源。
            def.setSource(source);
            // 返回值只收集本次新注册的定义，已存在的同名定义不会加入 beanDefs。
            beanDefs.add(registerPostProcessor(registry, def, AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME));
        }

        // 注册 CommonAnnotationBeanPostProcessor。
        // 该处理器依赖 JSR-250 API，用于支持 @Resource、@PostConstruct、@PreDestroy 等注解。
        // 只有运行时 classpath 中存在 javax.annotation.Resource 时才注册。
        if (jsr250Present && !registry.containsBeanDefinition(COMMON_ANNOTATION_PROCESSOR_BEAN_NAME)) {
            RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
            // source 通常来自 XML 元素、注解元数据或调用方传入的配置来源。
            def.setSource(source);
            beanDefs.add(registerPostProcessor(registry, def, COMMON_ANNOTATION_PROCESSOR_BEAN_NAME));
        }

        // 注册 PersistenceAnnotationBeanPostProcessor。
        // 该处理器用于支持 JPA 的 @PersistenceContext、@PersistenceUnit 等注解。
        // 只有 JPA API 和 spring-orm 中的处理器类都存在时才注册，避免强制依赖 ORM 模块。
        if (jpaPresent && !registry.containsBeanDefinition(PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME)) {
            // 这里先创建空的 RootBeanDefinition，再通过反射设置 beanClass。
            // 这样 AnnotationConfigUtils 本身不需要在编译期直接引用 spring-orm 类型。
            RootBeanDefinition def = new RootBeanDefinition();
            try {
                // 反射加载可选框架类；jpaPresent 已经做过存在性检查，这里通常不会失败。
                def.setBeanClass(ClassUtils.forName(PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME,
                        AnnotationConfigUtils.class.getClassLoader()));
            } catch (ClassNotFoundException ex) {
                // 理论上只有 classpath 在检查后发生变化或类加载器异常时才会到达这里。
                throw new IllegalStateException(
                        "Cannot load optional framework class: " + PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME, ex);
            }
            // 保留注册来源，和其他内部处理器定义保持一致。
            def.setSource(source);
            beanDefs.add(registerPostProcessor(registry, def, PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME));
        }

        // 注册 EventListenerMethodProcessor。
        // 它会扫描 Bean 方法上的 @EventListener，并将这些方法适配为应用事件监听器。
        if (!registry.containsBeanDefinition(EVENT_LISTENER_PROCESSOR_BEAN_NAME)) {
            RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
            // 为事件监听器处理器记录来源，便于问题排查。
            def.setSource(source);
            beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_PROCESSOR_BEAN_NAME));
        }

        // 注册默认的 EventListenerFactory。
        // EventListenerMethodProcessor 会借助工厂把 @EventListener 方法包装成监听器实例。
        if (!registry.containsBeanDefinition(EVENT_LISTENER_FACTORY_BEAN_NAME)) {
            RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
            // 默认工厂同样作为 Spring 内部基础设施 Bean 注册。
            def.setSource(source);
            beanDefs.add(registerPostProcessor(registry, def, EVENT_LISTENER_FACTORY_BEAN_NAME));
        }

        // 返回本次调用实际新增的所有内部处理器定义；已存在的定义不会出现在结果中。
        return beanDefs;
    }

    private static BeanDefinitionHolder registerPostProcessor(
            BeanDefinitionRegistry registry, RootBeanDefinition definition, String beanName) {

        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(beanName, definition);
        return new BeanDefinitionHolder(definition, beanName);
    }

    /**
     * 尝试从给定的 BeanDefinitionRegistry 中解包 DefaultListableBeanFactory。
     * 如果无法解包，则返回 {@code null}。
     */
    @Nullable
    private static DefaultListableBeanFactory unwrapDefaultListableBeanFactory(BeanDefinitionRegistry registry) {
        if (registry instanceof DefaultListableBeanFactory) {
            return (DefaultListableBeanFactory) registry;
        } else if (registry instanceof GenericApplicationContext) {
            return ((GenericApplicationContext) registry).getDefaultListableBeanFactory();
        } else {
            return null;
        }
    }

    public static void processCommonDefinitionAnnotations(AnnotatedBeanDefinition abd) {
        processCommonDefinitionAnnotations(abd, abd.getMetadata());
    }

    static void processCommonDefinitionAnnotations(AnnotatedBeanDefinition abd, AnnotatedTypeMetadata metadata) {
        AnnotationAttributes lazy = attributesFor(metadata, Lazy.class);
        if (lazy != null) {
            abd.setLazyInit(lazy.getBoolean("value"));
        } else if (abd.getMetadata() != metadata) {
            lazy = attributesFor(abd.getMetadata(), Lazy.class);
            if (lazy != null) {
                abd.setLazyInit(lazy.getBoolean("value"));
            }
        }

        if (metadata.isAnnotated(Primary.class.getName())) {
            abd.setPrimary(true);
        }
        AnnotationAttributes dependsOn = attributesFor(metadata, DependsOn.class);
        if (dependsOn != null) {
            abd.setDependsOn(dependsOn.getStringArray("value"));
        }

        AnnotationAttributes role = attributesFor(metadata, Role.class);
        if (role != null) {
            abd.setRole(role.getNumber("value").intValue());
        }
        AnnotationAttributes description = attributesFor(metadata, Description.class);
        if (description != null) {
            abd.setDescription(description.getString("value"));
        }
    }

    static BeanDefinitionHolder applyScopedProxyMode(
            ScopeMetadata metadata, BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {

        ScopedProxyMode scopedProxyMode = metadata.getScopedProxyMode();
        if (scopedProxyMode.equals(ScopedProxyMode.NO)) {
            return definition;
        }
        boolean proxyTargetClass = scopedProxyMode.equals(ScopedProxyMode.TARGET_CLASS);
        return ScopedProxyCreator.createScopedProxy(definition, registry, proxyTargetClass);
    }

    @Nullable
    static AnnotationAttributes attributesFor(AnnotatedTypeMetadata metadata, Class<?> annotationClass) {
        return attributesFor(metadata, annotationClass.getName());
    }

    @Nullable
    static AnnotationAttributes attributesFor(AnnotatedTypeMetadata metadata, String annotationClassName) {
        return AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(annotationClassName));
    }

    static Set<AnnotationAttributes> attributesForRepeatable(AnnotationMetadata metadata,
                                                             Class<?> containerClass, Class<?> annotationClass) {

        return attributesForRepeatable(metadata, containerClass.getName(), annotationClass.getName());
    }

    @SuppressWarnings("unchecked")
    static Set<AnnotationAttributes> attributesForRepeatable(
            AnnotationMetadata metadata, String containerClassName, String annotationClassName) {

        Set<AnnotationAttributes> result = new LinkedHashSet<>();

        // 是否存在直接注解？
        addAttributesIfNotNull(result, metadata.getAnnotationAttributes(annotationClassName));

        // 是否存在容器注解？
        Map<String, Object> container = metadata.getAnnotationAttributes(containerClassName);
        if (container != null && container.containsKey("value")) {
            for (Map<String, Object> containedAttributes : (Map<String, Object>[]) container.get("value")) {
                addAttributesIfNotNull(result, containedAttributes);
            }
        }

        // 返回合并后的结果
        return Collections.unmodifiableSet(result);
    }

    private static void addAttributesIfNotNull(
            Set<AnnotationAttributes> result, @Nullable Map<String, Object> attributes) {

        if (attributes != null) {
            result.add(AnnotationAttributes.fromMap(attributes));
        }
    }

}
