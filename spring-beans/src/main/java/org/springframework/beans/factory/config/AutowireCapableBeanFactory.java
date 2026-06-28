package org.springframework.beans.factory.config;

import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.lang.Nullable;

/**
 * {@link org.springframework.beans.factory.BeanFactory} 接口的扩展， 由具备自动装配能力且希望将该功能暴露给现有 bean 实例使用的
 * bean 工厂实现。
 *
 * <p>BeanFactory 的这个子接口并不打算在普通应用代码中使用： 对于典型用例，请继续使用 {@link
 * org.springframework.beans.factory.BeanFactory} 或 {@link
 * org.springframework.beans.factory.ListableBeanFactory}。
 *
 * <p>其他框架的集成代码可以利用此接口，对不由 Spring 控制生命周期的现有 bean 实例 进行装配和属性填充。例如，这对 WebWork Actions 和 Tapestry Page
 * 对象特别有用。
 *
 * <p>请注意，此接口未由 {@link org.springframework.context.ApplicationContext} 门面实现，
 * 因为应用代码几乎不会使用它。尽管如此，也可以从应用上下文中获取它， 可通过 ApplicationContext 的 {@link
 * org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()} 方法访问。
 *
 * <p>也可以实现 {@link org.springframework.beans.factory.BeanFactoryAware} 接口来获取
 * AutowireCapableBeanFactory：即使在 ApplicationContext 中运行，该接口也会暴露内部的 BeanFactory；只需将传入的 BeanFactory
 * 强制转换为 AutowireCapableBeanFactory。
 *
 * @author Juergen Hoeller
 * @since 04.12.2003
 * @see org.springframework.beans.factory.BeanFactoryAware
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory
 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
 */
public interface AutowireCapableBeanFactory extends BeanFactory {

  /**
   * 表示没有外部定义的自动装配的常量。请注意，仍会应用 BeanFactoryAware 等回调以及注解驱动的注入。
   *
   * @see #createBean
   * @see #autowire
   * @see #autowireBeanProperties
   */
  int AUTOWIRE_NO = 0;

  /**
   * 表示按名称自动装配 bean 属性的常量 （应用于所有 bean 属性 setter）。
   *
   * @see #createBean
   * @see #autowire
   * @see #autowireBeanProperties
   */
  int AUTOWIRE_BY_NAME = 1;

  /**
   * 表示按类型自动装配 bean 属性的常量 （应用于所有 bean 属性 setter）。
   *
   * @see #createBean
   * @see #autowire
   * @see #autowireBeanProperties
   */
  int AUTOWIRE_BY_TYPE = 2;

  /**
   * 表示自动装配可满足的最贪婪构造函数的常量 （涉及解析合适的构造函数）。
   *
   * @see #createBean
   * @see #autowire
   */
  int AUTOWIRE_CONSTRUCTOR = 3;

  /**
   * 表示通过内省 bean 类来确定合适自动装配策略的常量。
   *
   * @see #createBean
   * @see #autowire
   * @deprecated 自 Spring 3.0 起：如果使用混合自动装配策略， 请优先使用基于注解的自动装配，以便更清晰地界定自动装配需求。
   */
  @Deprecated int AUTOWIRE_AUTODETECT = 4;

  /**
   * 初始化现有 bean 实例时用于“原始实例”约定的后缀：追加到 bean 类的全限定名之后， 例如 "com.mypackage.MyClass.ORIGINAL"，用于强制返回给定实例，
   * 即不返回代理等包装对象。
   *
   * @since 5.1
   * @see #initializeBean(Object, String)
   * @see #applyBeanPostProcessorsBeforeInitialization(Object, String)
   * @see #applyBeanPostProcessorsAfterInitialization(Object, String)
   */
  String ORIGINAL_INSTANCE_SUFFIX = ".ORIGINAL";

  // -------------------------------------------------------------------------
  // 用于创建和填充外部 bean 实例的典型方法
  // -------------------------------------------------------------------------

  /**
   * 完整创建给定类的一个新 bean 实例。
   *
   * <p>执行 bean 的完整初始化，包括所有适用的 {@link BeanPostProcessor BeanPostProcessors}。
   *
   * <p>注意：此方法用于创建全新实例，填充带注解的字段和方法， 并应用所有标准 bean 初始化回调。它<i>并不</i>表示按名称或按类型 对属性进行传统自动装配；如有此类需求，请使用
   * {@link #createBean(Class, int, boolean)}。
   *
   * @param beanClass 要创建的 bean 的类
   * @return 新的 bean 实例
   * @throws BeansException 如果实例化或装配失败
   */
  <T> T createBean(Class<T> beanClass) throws BeansException;

  /**
   * 通过应用实例化后回调和 bean 属性后处理来填充给定的 bean 实例 （例如用于注解驱动的注入）。
   *
   * <p>注意：此方法本质上用于为新实例或反序列化实例（重新）填充带注解的字段和方法。 它<i>并不</i>表示按名称或按类型对属性进行传统自动装配； 如有此类需求，请使用 {@link
   * #autowireBeanProperties}。
   *
   * @param existingBean 现有的 bean 实例
   * @throws BeansException 如果装配失败
   */
  void autowireBean(Object existingBean) throws BeansException;

  /**
   * 配置给定的原始 bean：自动装配 bean 属性、应用 bean 属性值， 应用 {@code setBeanName} 和 {@code setBeanFactory} 等工厂回调，
   * 并应用所有 bean 后处理器（包括可能包装给定原始 bean 的后处理器）。
   *
   * <p>这实际上是 {@link #initializeBean} 所提供功能的超集， 会完整应用对应 bean 定义指定的配置。 <b>注意：此方法要求给定名称存在 bean 定义！</b>
   *
   * @param existingBean 现有的 bean 实例
   * @param beanName bean 的名称；必要时会传递给该 bean （必须存在该名称对应的 bean 定义）
   * @return 要使用的 bean 实例，可能是原始实例，也可能是包装后的实例
   * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException 如果不存在给定名称的 bean 定义
   * @throws BeansException 如果初始化失败
   * @see #initializeBean
   */
  Object configureBean(Object existingBean, String beanName) throws BeansException;

  // -------------------------------------------------------------------------
  // 用于精细控制 bean 生命周期的专用方法
  // -------------------------------------------------------------------------

  /**
   * 使用指定的自动装配策略，完整创建给定类的一个新 bean 实例。 这里支持本接口中定义的所有常量。
   *
   * <p>执行 bean 的完整初始化，包括所有适用的 {@link BeanPostProcessor BeanPostProcessors}。这实际上是 {@link #autowire}
   * 所提供功能的超集，额外增加了 {@link #initializeBean} 行为。
   *
   * @param beanClass 要创建的 bean 的类
   * @param autowireMode 按名称或按类型，使用本接口中的常量
   * @param dependencyCheck 是否对对象执行依赖检查 （不适用于构造函数自动装配，因此在该场景下会被忽略）
   * @return 新的 bean 实例
   * @throws BeansException 如果实例化或装配失败
   * @see #AUTOWIRE_NO
   * @see #AUTOWIRE_BY_NAME
   * @see #AUTOWIRE_BY_TYPE
   * @see #AUTOWIRE_CONSTRUCTOR
   */
  Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck)
      throws BeansException;

  /**
   * 使用指定的自动装配策略，实例化给定类的一个新 bean 实例。这里支持本接口中定义的所有常量。 也可以使用 {@code AUTOWIRE_NO}
   * 调用，以便只应用实例化前回调（例如用于注解驱动的注入）。
   *
   * <p><i>不会</i>应用标准 {@link BeanPostProcessor BeanPostProcessors} 回调， 也不会对 bean
   * 执行任何进一步初始化。本接口为这些目的提供了独立的细粒度操作， 例如 {@link #initializeBean}。不过，如果适用于实例构造， 会应用 {@link
   * InstantiationAwareBeanPostProcessor} 回调。
   *
   * @param beanClass 要实例化的 bean 的类
   * @param autowireMode 按名称或按类型，使用本接口中的常量
   * @param dependencyCheck 是否对 bean 实例中的对象引用执行依赖检查 （不适用于构造函数自动装配，因此在该场景下会被忽略）
   * @return 新的 bean 实例
   * @throws BeansException 如果实例化或装配失败
   * @see #AUTOWIRE_NO
   * @see #AUTOWIRE_BY_NAME
   * @see #AUTOWIRE_BY_TYPE
   * @see #AUTOWIRE_CONSTRUCTOR
   * @see #AUTOWIRE_AUTODETECT
   * @see #initializeBean
   * @see #applyBeanPostProcessorsBeforeInitialization
   * @see #applyBeanPostProcessorsAfterInitialization
   */
  Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck)
      throws BeansException;

  /**
   * 按名称或按类型自动装配给定 bean 实例的 bean 属性。也可以使用 {@code AUTOWIRE_NO} 调用， 以便只应用实例化后回调（例如用于注解驱动的注入）。
   *
   * <p><i>不会</i>应用标准 {@link BeanPostProcessor BeanPostProcessors} 回调， 也不会对 bean
   * 执行任何进一步初始化。本接口为这些目的提供了独立的细粒度操作， 例如 {@link #initializeBean}。不过，如果适用于实例配置， 会应用 {@link
   * InstantiationAwareBeanPostProcessor} 回调。
   *
   * @param existingBean 现有的 bean 实例
   * @param autowireMode 按名称或按类型，使用本接口中的常量
   * @param dependencyCheck 是否对 bean 实例中的对象引用执行依赖检查
   * @throws BeansException 如果装配失败
   * @see #AUTOWIRE_BY_NAME
   * @see #AUTOWIRE_BY_TYPE
   * @see #AUTOWIRE_NO
   */
  void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
      throws BeansException;

  /**
   * 将给定名称的 bean 定义中的属性值应用到给定的 bean 实例。该 bean 定义可以定义一个完整自包含的 bean 并复用其属性值，也可以只定义用于现有 bean 实例的属性值。
   *
   * <p>此方法<i>不会</i>自动装配 bean 属性；它只会应用显式定义的属性值。 如需自动装配现有 bean 实例，请使用 {@link #autowireBeanProperties}
   * 方法。 <b>注意：此方法要求给定名称存在 bean 定义！</b>
   *
   * <p><i>不会</i>应用标准 {@link BeanPostProcessor BeanPostProcessors} 回调， 也不会对 bean
   * 执行任何进一步初始化。本接口为这些目的提供了独立的细粒度操作， 例如 {@link #initializeBean}。不过，如果适用于实例配置， 会应用 {@link
   * InstantiationAwareBeanPostProcessor} 回调。
   *
   * @param existingBean 现有的 bean 实例
   * @param beanName bean 工厂中 bean 定义的名称（必须存在该名称对应的 bean 定义）
   * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException 如果不存在给定名称的 bean 定义
   * @throws BeansException 如果应用属性值失败
   * @see #autowireBeanProperties
   */
  void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException;

  /**
   * 初始化给定的原始 bean，应用 {@code setBeanName} 和 {@code setBeanFactory} 等工厂回调，并应用所有 bean 后处理器（包括可能包装给定原始
   * bean 的后处理器）。
   *
   * <p>请注意，bean 工厂中不必存在给定名称的 bean 定义。 传入的 bean 名称只会用于回调，不会与已注册的 bean 定义进行校验。
   *
   * @param existingBean 现有的 bean 实例
   * @param beanName bean 的名称；必要时会传递给该 bean（只传递给 {@link BeanPostProcessor BeanPostProcessors}； 可以遵循
   *     {@link #ORIGINAL_INSTANCE_SUFFIX} 约定，以强制返回给定实例，即不返回代理等包装对象）
   * @return 要使用的 bean 实例，可能是原始实例，也可能是包装后的实例
   * @throws BeansException 如果初始化失败
   * @see #ORIGINAL_INSTANCE_SUFFIX
   */
  Object initializeBean(Object existingBean, String beanName) throws BeansException;

  /**
   * 将 {@link BeanPostProcessor BeanPostProcessors} 应用于给定的现有 bean 实例， 调用它们的 {@code
   * postProcessBeforeInitialization} 方法。返回的 bean 实例可能是原始实例的包装对象。
   *
   * @param existingBean 现有的 bean 实例
   * @param beanName bean 的名称；必要时会传递给该 bean（只传递给 {@link BeanPostProcessor BeanPostProcessors}； 可以遵循
   *     {@link #ORIGINAL_INSTANCE_SUFFIX} 约定，以强制返回给定实例，即不返回代理等包装对象）
   * @return 要使用的 bean 实例，可能是原始实例，也可能是包装后的实例
   * @throws BeansException 如果任何后处理失败
   * @see BeanPostProcessor#postProcessBeforeInitialization
   * @see #ORIGINAL_INSTANCE_SUFFIX
   */
  Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
      throws BeansException;

  /**
   * 将 {@link BeanPostProcessor BeanPostProcessors} 应用于给定的现有 bean 实例， 调用它们的 {@code
   * postProcessAfterInitialization} 方法。返回的 bean 实例可能是原始实例的包装对象。
   *
   * @param existingBean 现有的 bean 实例
   * @param beanName bean 的名称；必要时会传递给该 bean（只传递给 {@link BeanPostProcessor BeanPostProcessors}； 可以遵循
   *     {@link #ORIGINAL_INSTANCE_SUFFIX} 约定，以强制返回给定实例，即不返回代理等包装对象）
   * @return 要使用的 bean 实例，可能是原始实例，也可能是包装后的实例
   * @throws BeansException 如果任何后处理失败
   * @see BeanPostProcessor#postProcessAfterInitialization
   * @see #ORIGINAL_INSTANCE_SUFFIX
   */
  Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
      throws BeansException;

  /**
   * 销毁给定的 bean 实例（通常来自 {@link #createBean}），应用 {@link
   * org.springframework.beans.factory.DisposableBean} 契约以及已注册的 {@link
   * DestructionAwareBeanPostProcessor DestructionAwareBeanPostProcessors}。
   *
   * <p>销毁期间发生的任何异常都应被捕获并记录日志，而不是传播给此方法的调用者。
   *
   * @param existingBean 要销毁的 bean 实例
   */
  void destroyBean(Object existingBean);

  // -------------------------------------------------------------------------
  // 用于解析注入点的委托方法
  // -------------------------------------------------------------------------

  /**
   * 解析唯一匹配给定对象类型的 bean 实例（如果存在），包括其 bean 名称。
   *
   * <p>这实际上是 {@link #getBean(Class)} 的一个变体，会保留匹配实例的 bean 名称。
   *
   * @param requiredType bean 必须匹配的类型；可以是接口或父类
   * @return bean 名称加 bean 实例
   * @throws NoSuchBeanDefinitionException 如果没有找到匹配的 bean
   * @throws NoUniqueBeanDefinitionException 如果找到多个匹配的 bean
   * @throws BeansException 如果无法创建 bean
   * @since 4.3.3
   * @see #getBean(Class)
   */
  <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException;

  /**
   * 解析给定 bean 名称对应的 bean 实例，并提供依赖描述符以暴露给目标工厂方法。
   *
   * <p>这实际上是 {@link #getBean(String, Class)} 的一个变体， 支持带有 {@link
   * org.springframework.beans.factory.InjectionPoint} 参数的工厂方法。
   *
   * @param name 要查找的 bean 名称
   * @param descriptor 请求注入点的依赖描述符
   * @return 对应的 bean 实例
   * @throws NoSuchBeanDefinitionException 如果不存在指定名称的 bean
   * @throws BeansException 如果无法创建 bean
   * @since 5.1.5
   * @see #getBean(String, Class)
   */
  Object resolveBeanByName(String name, DependencyDescriptor descriptor) throws BeansException;

  /**
   * 针对此工厂中定义的 bean 解析指定依赖。
   *
   * @param descriptor 依赖描述符（字段/方法/构造函数）
   * @param requestingBeanName 声明给定依赖的 bean 的名称
   * @return 解析得到的对象；如果未找到，则返回 {@code null}
   * @throws NoSuchBeanDefinitionException 如果没有找到匹配的 bean
   * @throws NoUniqueBeanDefinitionException 如果找到多个匹配的 bean
   * @throws BeansException 如果依赖解析因其他原因失败
   * @since 2.5
   * @see #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)
   */
  @Nullable
  Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName)
      throws BeansException;

  /**
   * 针对此工厂中定义的 bean 解析指定依赖。
   *
   * @param descriptor 依赖描述符（字段/方法/构造函数）
   * @param requestingBeanName 声明给定依赖的 bean 的名称
   * @param autowiredBeanNames 一个 Set，应该向其中添加用于解析给定依赖的所有自动装配 bean 的名称
   * @param typeConverter 用于填充数组和集合的 TypeConverter
   * @return 解析得到的对象；如果未找到，则返回 {@code null}
   * @throws NoSuchBeanDefinitionException 如果没有找到匹配的 bean
   * @throws NoUniqueBeanDefinitionException 如果找到多个匹配的 bean
   * @throws BeansException 如果依赖解析因其他原因失败
   * @since 2.5
   * @see DependencyDescriptor
   */
  @Nullable
  Object resolveDependency(
      DependencyDescriptor descriptor,
      @Nullable String requestingBeanName,
      @Nullable Set<String> autowiredBeanNames,
      @Nullable TypeConverter typeConverter)
      throws BeansException;
}
