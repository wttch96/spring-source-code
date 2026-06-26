package org.springframework.beans.factory.config;

import java.util.Iterator;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.lang.Nullable;

/**
 * 供大多数可列举 bean 工厂实现的配置接口。
 * 除了 {@link ConfigurableBeanFactory} 之外，它还提供分析和修改 bean 定义，
 * 以及预实例化单例的能力。
 *
 * <p>{@link org.springframework.beans.factory.BeanFactory} 的这个子接口并不打算
 * 在普通应用代码中使用：典型场景请坚持使用
 * {@link org.springframework.beans.factory.BeanFactory} 或
 * {@link org.springframework.beans.factory.ListableBeanFactory}。这个接口仅用于让框架内部
 * 在需要访问 bean 工厂配置方法时，也能实现即插即用。
 *
 * @author Juergen Hoeller
 * @see org.springframework.context.support.AbstractApplicationContext#getBeanFactory()
 * @since 03.11.2003
 */
public interface ConfigurableListableBeanFactory
        extends ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {

    /**
     * 在自动装配时忽略给定的依赖类型：
     * 例如 String。默认不忽略任何类型。
     *
     * @param type 要忽略的依赖类型
     */
    void ignoreDependencyType(Class<?> type);

    /**
     * 在自动装配时忽略给定的依赖接口。
     * <p>这通常由应用上下文用来注册那些通过其他方式解析的依赖，
     * 比如通过 {@code BeanFactoryAware} 注入的 BeanFactory，
     * 或通过 {@code ApplicationContextAware} 注入的 ApplicationContext。
     * <p>默认只忽略 {@code BeanFactoryAware} 接口。
     * 若还要忽略其他类型，请对每个类型分别调用此方法。
     *
     * @param ifc 要忽略的依赖接口
     * @see org.springframework.beans.factory.BeanFactoryAware
     * @see org.springframework.context.ApplicationContextAware
     */
    void ignoreDependencyInterface(Class<?> ifc);

    /**
     * 注册一个特殊的依赖类型及其对应的自动装配值。
     * <p>这适用于那些应该可自动装配、但并未在工厂中定义为 bean 的工厂/上下文引用：
     * 例如，将 ApplicationContext 类型的依赖解析为该 bean 所在的
     * ApplicationContext 实例。
     * <p>注意：在普通 BeanFactory 中不会注册这类默认类型，
     * 甚至连 BeanFactory 接口本身也不会。
     *
     * @param dependencyType 要注册的依赖类型。通常是 BeanFactory 这样的基础接口；
     *                       如果将其扩展接口声明为自动装配依赖（例如 ListableBeanFactory），
     *                       只要给定值确实实现了该扩展接口，也会一并解析。
     * @param autowiredValue 对应的自动装配值。它也可以是
     *                       {@link org.springframework.beans.factory.ObjectFactory} 接口的实现，
     *                       这样可以对实际目标值进行延迟解析。
     */
    void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue);

    /**
     * 判断指定 bean 是否符合自动装配候选条件，
     * 可被注入到声明了匹配类型依赖的其他 bean 中。
     * <p>该方法也会检查祖先工厂。
     *
     * @param beanName   要检查的 bean 名称
     * @param descriptor 要解析的依赖描述符
     * @return 该 bean 是否应被视为自动装配候选者
     * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
     */
    boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
            throws NoSuchBeanDefinitionException;

    /**
     * 返回指定 bean 的已注册 BeanDefinition，便于访问其属性值和构造器参数值
     * （这些值可在 bean 工厂后处理阶段被修改）。
     * <p>返回的 BeanDefinition 对象不应是副本，而应是工厂中注册的原始定义对象。
     * 这意味着在必要时，它应当可以转换为更具体的实现类型。
     * <p><b>注意：</b> 此方法<i>不</i>考虑祖先工厂。
     * 它仅用于访问当前工厂的本地 bean 定义。
     *
     * @param beanName bean 的名称
     * @return 已注册的 BeanDefinition
     * @throws NoSuchBeanDefinitionException 如果此工厂中未定义给定名称的 bean
     */
    BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

    /**
     * 返回该工厂所管理的所有 bean 名称的统一视图。
     * <p>包括 bean 定义名称以及手动注册的单例实例名称，并且 bean 定义名称始终排在前面，
     * 类似于按类型/注解检索 bean 名称时的工作方式。
     *
     * @return bean 名称视图的复合迭代器
     * @see #containsBeanDefinition
     * @see #registerSingleton
     * @see #getBeanNamesForType
     * @see #getBeanNamesForAnnotation
     * @since 4.1.2
     */
    Iterator<String> getBeanNamesIterator();

    /**
     * 清除合并后的 bean 定义缓存，移除那些当前还不适合进行完整元数据缓存的 bean 条目。
     * <p>通常在原始 bean 定义发生变化后触发，例如应用了
     * {@link BeanFactoryPostProcessor} 之后。注意，到此时已经创建过的 bean 的元数据
     * 会被保留下来。
     *
     * @see #getBeanDefinition
     * @see #getMergedBeanDefinition
     * @since 4.2
     */
    void clearMetadataCache();

    /**
     * 冻结所有 bean 定义，表示已注册的 bean 定义将不再被修改或进一步后处理。
     * <p>这使工厂可以更积极地缓存 bean 定义元数据。
     */
    void freezeConfiguration();

    /**
     * 返回该工厂的 bean 定义是否已冻结，
     * 即不应再被修改或进一步后处理。
     *
     * @return 如果工厂配置被视为已冻结，则返回 {@code true}
     */
    boolean isConfigurationFrozen();

    /**
     * 确保所有非懒加载的单例都已实例化，同时也会考虑
     * {@link org.springframework.beans.factory.FactoryBean FactoryBean}。
     * 通常在工厂设置结束时按需调用。
     *
     * @throws BeansException 如果某个单例 bean 无法创建
     *                        注意：这可能会使工厂中已有部分 bean 已经初始化！
     *                        在这种情况下，请调用 {@link #destroySingletons()} 进行完全清理。
     * @see #destroySingletons()
     */
    void preInstantiateSingletons() throws BeansException;

}
