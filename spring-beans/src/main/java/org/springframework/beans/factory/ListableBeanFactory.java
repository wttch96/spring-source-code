package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * {@link BeanFactory} 接口的扩展，由能够列举其所有 Bean 实例的 Bean 工厂实现，
 * 而不是按客户端请求逐个按名称尝试查找 Bean。预加载所有 Bean 定义的
 * BeanFactory 实现（例如基于 XML 的工厂）可以实现此接口。
 *
 * <p>如果这是一个 {@link HierarchicalBeanFactory}，返回值<i>不会</i>考虑任何
 * BeanFactory 层次结构，而只涉及当前工厂中定义的 Bean。可使用
 * {@link BeanFactoryUtils} 辅助类同时考虑祖先工厂中的 Bean。
 *
 * <p>此接口中的方法只会关注本工厂的 Bean 定义。它们会忽略通过其他方式注册的
 * 单例 Bean，例如
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} 的
 * {@code registerSingleton} 方法；但 {@code getBeanNamesForType} 和
 * {@code getBeansOfType} 例外，它们也会检查这类手动注册的单例。
 * 当然，BeanFactory 的 {@code getBean} 也允许透明地访问这些特殊 Bean。
 * 不过在典型场景中，所有 Bean 通常都会由外部 Bean 定义来定义，
 * 因此大多数应用不需要关心这种差异。
 *
 * <p><b>注意：</b>除 {@code getBeanDefinitionCount} 和
 * {@code containsBeanDefinition} 外，此接口中的方法并不是为频繁调用设计的。
 * 实现可能较慢。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 16 April 2001
 * @see HierarchicalBeanFactory
 * @see BeanFactoryUtils
 */
public interface ListableBeanFactory extends BeanFactory {

	/**
	 * 检查此 Bean 工厂是否包含具有给定名称的 Bean 定义。
	 * <p>不考虑此工厂可能参与的任何层次结构，并忽略通过 Bean 定义之外的
	 * 其他方式注册的任何单例 Bean。
	 * @param beanName 要查找的 Bean 名称
	 * @return 如果此 Bean 工厂包含具有给定名称的 Bean 定义则返回 true
	 * @see #containsBean
	 */
	boolean containsBeanDefinition(String beanName);

	/**
	 * 返回工厂中定义的 Bean 数量。
	 * <p>不考虑此工厂可能参与的任何层次结构，并忽略通过 Bean 定义之外的
	 * 其他方式注册的任何单例 Bean。
	 * @return 工厂中定义的 Bean 数量
	 */
	int getBeanDefinitionCount();

	/**
	 * 返回此工厂中定义的所有 Bean 的名称。
	 * <p>不考虑此工厂可能参与的任何层次结构，并忽略通过 Bean 定义之外的
	 * 其他方式注册的任何单例 Bean。
	 * @return 此工厂中定义的所有 Bean 的名称；如果没有定义则返回空数组
	 */
	String[] getBeanDefinitionNames();

	/**
	 * 返回指定 Bean 的 provider，允许按需延迟获取实例，包括可用性和唯一性选项。
	 * @param requiredType Bean 必须匹配的类型；可以是接口或超类
	 * @param allowEagerInit 基于流的访问是否可以为了类型检查而初始化
	 * <i>lazy-init 单例</i>和 <i>FactoryBean 创建的对象</i>
	 * （或由带有 "factory-bean" 引用的工厂方法创建的对象）
	 * @return 对应的 provider 句柄
	 * @since 5.3
	 * @see #getBeanProvider(ResolvableType, boolean)
	 * @see #getBeanProvider(Class)
	 * @see #getBeansOfType(Class, boolean, boolean)
	 * @see #getBeanNamesForType(Class, boolean, boolean)
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit);

	/**
	 * 返回指定 Bean 的 provider，允许按需延迟获取实例，包括可用性和唯一性选项。
	 * @param requiredType Bean 必须匹配的类型；可以是泛型类型声明。
	 * 注意，与反射式注入点不同，这里不支持集合类型。若要以编程方式获取匹配
	 * 特定类型的 Bean 列表，请在此处将实际 Bean 类型指定为参数，随后使用
	 * {@link ObjectProvider#orderedStream()} 或其延迟流式/迭代选项。
	 * @param allowEagerInit 基于流的访问是否可以为了类型检查而初始化
	 * <i>lazy-init 单例</i>和 <i>FactoryBean 创建的对象</i>
	 * （或由带有 "factory-bean" 引用的工厂方法创建的对象）
	 * @return 对应的 provider 句柄
	 * @since 5.3
	 * @see #getBeanProvider(ResolvableType)
	 * @see ObjectProvider#iterator()
	 * @see ObjectProvider#stream()
	 * @see ObjectProvider#orderedStream()
	 * @see #getBeanNamesForType(ResolvableType, boolean, boolean)
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit);

	/**
	 * 返回与给定类型（包括子类）匹配的 Bean 名称，判断依据为 Bean 定义，
	 * 或者在 FactoryBean 场景下依据 {@code getObjectType} 的值。
	 * <p><b>注意：此方法仅内省顶层 Bean。</b>它<i>不会</i>检查同样可能匹配
	 * 指定类型的嵌套 Bean。
	 * <p>会考虑 FactoryBean 创建的对象，这意味着 FactoryBean 会被初始化。
	 * 如果 FactoryBean 创建的对象不匹配，则会用原始 FactoryBean 本身与类型匹配。
	 * <p>不考虑此工厂可能参与的任何层次结构。可使用 BeanFactoryUtils 的
	 * {@code beanNamesForTypeIncludingAncestors} 同时包含祖先工厂中的 Bean。
	 * <p>注意：<i>不会</i>忽略通过 Bean 定义之外的其他方式注册的单例 Bean。
	 * <p>此版本的 {@code getBeanNamesForType} 会匹配所有类型的 Bean，
	 * 无论是单例、原型还是 FactoryBean。在大多数实现中，结果与
	 * {@code getBeanNamesForType(type, true, true)} 相同。
	 * <p>此方法返回的 Bean 名称应始终尽可能按照后端配置中的<i>定义顺序</i>返回。
	 * @param type 要匹配的泛型类或接口
	 * @return 与给定对象类型（包括子类）匹配的 Bean（或 FactoryBean 创建的对象）
	 * 的名称；如果没有则返回空数组
	 * @since 4.2
	 * @see #isTypeMatch(String, ResolvableType)
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, ResolvableType)
	 */
	String[] getBeanNamesForType(ResolvableType type);

	/**
	 * 返回与给定类型（包括子类）匹配的 Bean 名称，判断依据为 Bean 定义，
	 * 或者在 FactoryBean 场景下依据 {@code getObjectType} 的值。
	 * <p><b>注意：此方法仅内省顶层 Bean。</b>它<i>不会</i>检查同样可能匹配
	 * 指定类型的嵌套 Bean。
	 * <p>如果设置了 "allowEagerInit" 标志，则会考虑 FactoryBean 创建的对象，
	 * 这意味着 FactoryBean 会被初始化。如果 FactoryBean 创建的对象不匹配，
	 * 则会用原始 FactoryBean 本身与类型匹配。如果未设置 "allowEagerInit"，
	 * 则只检查原始 FactoryBean（这不需要初始化每个 FactoryBean）。
	 * <p>不考虑此工厂可能参与的任何层次结构。可使用 BeanFactoryUtils 的
	 * {@code beanNamesForTypeIncludingAncestors} 同时包含祖先工厂中的 Bean。
	 * <p>注意：<i>不会</i>忽略通过 Bean 定义之外的其他方式注册的单例 Bean。
	 * <p>此方法返回的 Bean 名称应始终尽可能按照后端配置中的<i>定义顺序</i>返回。
	 * @param type 要匹配的泛型类或接口
	 * @param includeNonSingletons 是否也包含原型或作用域 Bean，还是只包含单例
	 * （同样适用于 FactoryBean）
	 * @param allowEagerInit 是否为了类型检查而初始化 <i>lazy-init 单例</i>和
	 * <i>FactoryBean 创建的对象</i>（或由带有 "factory-bean" 引用的工厂方法创建的对象）。
	 * 注意，FactoryBean 需要提前初始化才能确定其类型：因此请注意，为此标志传入
	 * "true" 会初始化 FactoryBean 和 "factory-bean" 引用。
	 * @return 与给定对象类型（包括子类）匹配的 Bean（或 FactoryBean 创建的对象）
	 * 的名称；如果没有则返回空数组
	 * @since 5.2
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, ResolvableType, boolean, boolean)
	 */
	String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit);

	/**
	 * 返回与给定类型（包括子类）匹配的 Bean 名称，判断依据为 Bean 定义，
	 * 或者在 FactoryBean 场景下依据 {@code getObjectType} 的值。
	 * <p><b>注意：此方法仅内省顶层 Bean。</b>它<i>不会</i>检查同样可能匹配
	 * 指定类型的嵌套 Bean。
	 * <p>会考虑 FactoryBean 创建的对象，这意味着 FactoryBean 会被初始化。
	 * 如果 FactoryBean 创建的对象不匹配，则会用原始 FactoryBean 本身与类型匹配。
	 * <p>不考虑此工厂可能参与的任何层次结构。可使用 BeanFactoryUtils 的
	 * {@code beanNamesForTypeIncludingAncestors} 同时包含祖先工厂中的 Bean。
	 * <p>注意：<i>不会</i>忽略通过 Bean 定义之外的其他方式注册的单例 Bean。
	 * <p>此版本的 {@code getBeanNamesForType} 会匹配所有类型的 Bean，
	 * 无论是单例、原型还是 FactoryBean。在大多数实现中，结果与
	 * {@code getBeanNamesForType(type, true, true)} 相同。
	 * <p>此方法返回的 Bean 名称应始终尽可能按照后端配置中的<i>定义顺序</i>返回。
	 * @param type 要匹配的类或接口；如果为 {@code null}，则匹配所有 Bean 名称
	 * @return 与给定对象类型（包括子类）匹配的 Bean（或 FactoryBean 创建的对象）
	 * 的名称；如果没有则返回空数组
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class)
	 */
	String[] getBeanNamesForType(@Nullable Class<?> type);

	/**
	 * 返回与给定类型（包括子类）匹配的 Bean 名称，判断依据为 Bean 定义，
	 * 或者在 FactoryBean 场景下依据 {@code getObjectType} 的值。
	 * <p><b>注意：此方法仅内省顶层 Bean。</b>它<i>不会</i>检查同样可能匹配
	 * 指定类型的嵌套 Bean。
	 * <p>如果设置了 "allowEagerInit" 标志，则会考虑 FactoryBean 创建的对象，
	 * 这意味着 FactoryBean 会被初始化。如果 FactoryBean 创建的对象不匹配，
	 * 则会用原始 FactoryBean 本身与类型匹配。如果未设置 "allowEagerInit"，
	 * 则只检查原始 FactoryBean（这不需要初始化每个 FactoryBean）。
	 * <p>不考虑此工厂可能参与的任何层次结构。可使用 BeanFactoryUtils 的
	 * {@code beanNamesForTypeIncludingAncestors} 同时包含祖先工厂中的 Bean。
	 * <p>注意：<i>不会</i>忽略通过 Bean 定义之外的其他方式注册的单例 Bean。
	 * <p>此方法返回的 Bean 名称应始终尽可能按照后端配置中的<i>定义顺序</i>返回。
	 * @param type 要匹配的类或接口；如果为 {@code null}，则匹配所有 Bean 名称
	 * @param includeNonSingletons 是否也包含原型或作用域 Bean，还是只包含单例
	 * （同样适用于 FactoryBean）
	 * @param allowEagerInit 是否为了类型检查而初始化 <i>lazy-init 单例</i>和
	 * <i>FactoryBean 创建的对象</i>（或由带有 "factory-bean" 引用的工厂方法创建的对象）。
	 * 注意，FactoryBean 需要提前初始化才能确定其类型：因此请注意，为此标志传入
	 * "true" 会初始化 FactoryBean 和 "factory-bean" 引用。
	 * @return 与给定对象类型（包括子类）匹配的 Bean（或 FactoryBean 创建的对象）
	 * 的名称；如果没有则返回空数组
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
	 */
	String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit);

	/**
	 * 返回与给定对象类型（包括子类）匹配的 Bean 实例，判断依据为 Bean 定义，
	 * 或者在 FactoryBean 场景下依据 {@code getObjectType} 的值。
	 * <p><b>注意：此方法仅内省顶层 Bean。</b>它<i>不会</i>检查同样可能匹配
	 * 指定类型的嵌套 Bean。
	 * <p>会考虑 FactoryBean 创建的对象，这意味着 FactoryBean 会被初始化。
	 * 如果 FactoryBean 创建的对象不匹配，则会用原始 FactoryBean 本身与类型匹配。
	 * <p>不考虑此工厂可能参与的任何层次结构。可使用 BeanFactoryUtils 的
	 * {@code beansOfTypeIncludingAncestors} 同时包含祖先工厂中的 Bean。
	 * <p>注意：<i>不会</i>忽略通过 Bean 定义之外的其他方式注册的单例 Bean。
	 * <p>此版本的 getBeansOfType 会匹配所有类型的 Bean，无论是单例、原型还是
	 * FactoryBean。在大多数实现中，结果与 {@code getBeansOfType(type, true, true)} 相同。
	 * <p>此方法返回的 Map 应始终尽可能按照后端配置中的<i>定义顺序</i>返回
	 * Bean 名称及其对应的 Bean 实例。
	 * @param type 要匹配的类或接口；如果为 {@code null}，则匹配所有具体 Bean
	 * @return 包含匹配 Bean 的 Map，以 Bean 名称为键、对应 Bean 实例为值
	 * @throws BeansException 如果某个 Bean 无法创建
	 * @since 1.1.2
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class)
	 */
	<T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException;

	/**
	 * 返回与给定对象类型（包括子类）匹配的 Bean 实例，判断依据为 Bean 定义，
	 * 或者在 FactoryBean 场景下依据 {@code getObjectType} 的值。
	 * <p><b>注意：此方法仅内省顶层 Bean。</b>它<i>不会</i>检查同样可能匹配
	 * 指定类型的嵌套 Bean。
	 * <p>如果设置了 "allowEagerInit" 标志，则会考虑 FactoryBean 创建的对象，
	 * 这意味着 FactoryBean 会被初始化。如果 FactoryBean 创建的对象不匹配，
	 * 则会用原始 FactoryBean 本身与类型匹配。如果未设置 "allowEagerInit"，
	 * 则只检查原始 FactoryBean（这不需要初始化每个 FactoryBean）。
	 * <p>不考虑此工厂可能参与的任何层次结构。可使用 BeanFactoryUtils 的
	 * {@code beansOfTypeIncludingAncestors} 同时包含祖先工厂中的 Bean。
	 * <p>注意：<i>不会</i>忽略通过 Bean 定义之外的其他方式注册的单例 Bean。
	 * <p>此方法返回的 Map 应始终尽可能按照后端配置中的<i>定义顺序</i>返回
	 * Bean 名称及其对应的 Bean 实例。
	 * @param type 要匹配的类或接口；如果为 {@code null}，则匹配所有具体 Bean
	 * @param includeNonSingletons 是否也包含原型或作用域 Bean，还是只包含单例
	 * （同样适用于 FactoryBean）
	 * @param allowEagerInit 是否为了类型检查而初始化 <i>lazy-init 单例</i>和
	 * <i>FactoryBean 创建的对象</i>（或由带有 "factory-bean" 引用的工厂方法创建的对象）。
	 * 注意，FactoryBean 需要提前初始化才能确定其类型：因此请注意，为此标志传入
	 * "true" 会初始化 FactoryBean 和 "factory-bean" 引用。
	 * @return 包含匹配 Bean 的 Map，以 Bean 名称为键、对应 Bean 实例为值
	 * @throws BeansException 如果某个 Bean 无法创建
	 * @see FactoryBean#getObjectType
	 * @see BeanFactoryUtils#beansOfTypeIncludingAncestors(ListableBeanFactory, Class, boolean, boolean)
	 */
	<T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException;

	/**
	 * 查找所有标注了给定 {@link Annotation} 类型的 Bean 名称，
	 * 但尚不创建对应的 Bean 实例。
	 * <p>注意，此方法会考虑 FactoryBean 创建的对象，这意味着为了确定其对象类型，
	 * FactoryBean 会被初始化。
	 * @param annotationType 要查找的注解类型
	 * （位于指定 Bean 的类、接口或工厂方法级别）
	 * @return 所有匹配 Bean 的名称
	 * @since 4.0
	 * @see #findAnnotationOnBean
	 */
	String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType);

	/**
	 * 查找所有标注了给定 {@link Annotation} 类型的 Bean，
	 * 返回 Bean 名称到对应 Bean 实例的 Map。
	 * <p>注意，此方法会考虑 FactoryBean 创建的对象，这意味着为了确定其对象类型，
	 * FactoryBean 会被初始化。
	 * @param annotationType 要查找的注解类型
	 * （位于指定 Bean 的类、接口或工厂方法级别）
	 * @return 包含匹配 Bean 的 Map，以 Bean 名称为键、对应 Bean 实例为值
	 * @throws BeansException 如果某个 Bean 无法创建
	 * @since 3.0
	 * @see #findAnnotationOnBean
	 */
	Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException;

	/**
	 * 在指定 Bean 上查找 {@code annotationType} 类型的 {@link Annotation}；
	 * 如果给定类本身找不到注解，则遍历其接口和超类，并检查该 Bean 的工厂方法（如果有）。
	 * @param beanName 要在其上查找注解的 Bean 名称
	 * @param annotationType 要查找的注解类型
	 * （位于指定 Bean 的类、接口或工厂方法级别）
	 * @return 如果找到则返回给定类型的注解，否则返回 {@code null}
	 * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 Bean
	 * @since 3.0
	 * @see #getBeanNamesForAnnotation
	 * @see #getBeansWithAnnotation
	 * @see #getType(String)
	 */
	@Nullable
	<A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException;

	/**
	 * 在指定 Bean 上查找 {@code annotationType} 类型的 {@link Annotation}；
	 * 如果给定类本身找不到注解，则遍历其接口和超类，并检查该 Bean 的工厂方法（如果有）。
	 * @param beanName 要在其上查找注解的 Bean 名称
	 * @param annotationType 要查找的注解类型
	 * （位于指定 Bean 的类、接口或工厂方法级别）
	 * @param allowFactoryBeanInit 是否允许仅为了确定对象类型而初始化 {@code FactoryBean}
	 * @return 如果找到则返回给定类型的注解，否则返回 {@code null}
	 * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 Bean
	 * @since 5.3.14
	 * @see #getBeanNamesForAnnotation
	 * @see #getBeansWithAnnotation
	 * @see #getType(String, boolean)
	 */
	@Nullable
	<A extends Annotation> A findAnnotationOnBean(
			String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException;

}
