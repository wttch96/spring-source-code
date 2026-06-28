package org.springframework.beans.factory.config;

import java.beans.PropertyEditor;
import java.security.AccessControlContext;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

/**
 * 大多数 bean 工厂需要实现的配置接口。除了 {@link org.springframework.beans.factory.BeanFactory} 接口中的 bean 工厂
 * 客户端方法之外，还提供用于配置 bean 工厂的功能。
 *
 * <p>这个 bean 工厂接口并非供普通应用程序代码使用：典型需求应继续使用 {@link org.springframework.beans.factory.BeanFactory} 或
 * {@link org.springframework.beans.factory.ListableBeanFactory}。这个扩展接口 仅用于支持框架内部的即插即用，以及对 bean
 * 工厂配置方法的特殊访问。
 *
 * @author Juergen Hoeller
 * @since 03.11.2003
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.ListableBeanFactory
 * @see ConfigurableListableBeanFactory
 */
public interface ConfigurableBeanFactory extends HierarchicalBeanFactory, SingletonBeanRegistry {

  /**
   * 标准单例作用域的作用域标识符：{@value}。
   *
   * <p>可以通过 {@code registerScope} 添加自定义作用域。
   *
   * @see #registerScope
   */
  String SCOPE_SINGLETON = "singleton";

  /**
   * 标准原型作用域的作用域标识符：{@value}。
   *
   * <p>可以通过 {@code registerScope} 添加自定义作用域。
   *
   * @see #registerScope
   */
  String SCOPE_PROTOTYPE = "prototype";

  /**
   * 设置此 bean 工厂的父工厂。
   *
   * <p>注意，父工厂不能更改：只有在工厂实例化时无法获得父工厂的情况下， 才应在构造函数之外设置它。
   *
   * @param parentBeanFactory 父 BeanFactory
   * @throws IllegalStateException 如果此工厂已经关联了父 BeanFactory
   * @see #getParentBeanFactory()
   */
  void setParentBeanFactory(BeanFactory parentBeanFactory) throws IllegalStateException;

  /**
   * 设置用于加载 bean 类的类加载器。 默认是线程上下文类加载器。
   *
   * <p>注意，此类加载器只会应用于尚未携带已解析 bean 类的 bean 定义。 从 Spring 2.0 开始，默认就是这种情况：bean 定义只携带 bean 类名， 等到工厂处理该
   * bean 定义时再进行解析。
   *
   * @param beanClassLoader 要使用的类加载器，或使用 {@code null} 表示建议使用默认类加载器
   */
  void setBeanClassLoader(@Nullable ClassLoader beanClassLoader);

  /**
   * 返回此工厂用于加载 bean 类的类加载器 （只有在连系统 ClassLoader 都不可访问时才为 {@code null}）。
   *
   * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
   */
  @Nullable
  ClassLoader getBeanClassLoader();

  /**
   * 指定一个用于类型匹配的临时 ClassLoader。 默认没有临时 ClassLoader，只使用标准的 bean ClassLoader。
   *
   * <p>通常只有涉及<i>加载时织入</i>时才会指定临时 ClassLoader， 以确保实际的 bean 类尽可能延迟加载。BeanFactory 完成启动阶段后， 该临时加载器会被移除。
   *
   * @since 2.5
   */
  void setTempClassLoader(@Nullable ClassLoader tempClassLoader);

  /**
   * 返回用于类型匹配的临时 ClassLoader（如果有）。
   *
   * @since 2.5
   */
  @Nullable
  ClassLoader getTempClassLoader();

  /**
   * 设置是否缓存 bean 元数据，例如给定的 bean 定义（以合并形式）和已解析的 bean 类。默认开启。
   *
   * <p>关闭此标志可以启用 bean 定义对象，尤其是 bean 类的热刷新。 如果此标志关闭，每次创建 bean 实例都会重新查询 bean 类加载器， 以获取新解析的类。
   */
  void setCacheBeanMetadata(boolean cacheBeanMetadata);

  /** 返回是否缓存 bean 元数据，例如给定的 bean 定义（以合并形式） 和已解析的 bean 类。 */
  boolean isCacheBeanMetadata();

  /**
   * 指定 bean 定义值中表达式的解析策略。
   *
   * <p>默认情况下，BeanFactory 中没有启用表达式支持。 ApplicationContext 通常会在这里设置标准表达式策略， 以兼容 Unified EL 的风格支持
   * "#{...}" 表达式。
   *
   * @since 3.0
   */
  void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver);

  /**
   * 返回 bean 定义值中表达式的解析策略。
   *
   * @since 3.0
   */
  @Nullable
  BeanExpressionResolver getBeanExpressionResolver();

  /**
   * 指定用于转换属性值的 Spring 3.0 ConversionService， 作为 JavaBeans PropertyEditors 的替代方案。
   *
   * @since 3.0
   */
  void setConversionService(@Nullable ConversionService conversionService);

  /**
   * 返回关联的 ConversionService（如果有）。
   *
   * @since 3.0
   */
  @Nullable
  ConversionService getConversionService();

  /**
   * 添加一个要应用于所有 bean 创建过程的 PropertyEditorRegistrar。
   *
   * <p>这样的 registrar 会创建新的 PropertyEditor 实例，并在给定的注册表上 注册它们；每次尝试创建 bean 时都会使用全新的实例。这样可以避免对
   * 自定义编辑器进行同步；因此，通常更推荐使用此方法，而不是 {@link #registerCustomEditor}。
   *
   * @param registrar 要注册的 PropertyEditorRegistrar
   */
  void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar);

  /**
   * 为给定类型的所有属性注册给定的自定义属性编辑器。 应在工厂配置期间调用。
   *
   * <p>注意，此方法会注册一个共享的自定义编辑器实例； 对该实例的访问会进行同步以保证线程安全。通常更推荐使用 {@link
   * #addPropertyEditorRegistrar}，而不是此方法， 以避免自定义编辑器上的同步需求。
   *
   * @param requiredType 属性的类型
   * @param propertyEditorClass 要注册的 {@link PropertyEditor} 类
   */
  void registerCustomEditor(
      Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass);

  /**
   * 使用已经注册到此 BeanFactory 的自定义编辑器初始化给定的 PropertyEditorRegistry。
   *
   * @param registry 要初始化的 PropertyEditorRegistry
   */
  void copyRegisteredEditorsTo(PropertyEditorRegistry registry);

  /**
   * 设置此 BeanFactory 在转换 bean 属性值、构造函数参数值等时应使用的 自定义类型转换器。
   *
   * <p>这会覆盖默认的 PropertyEditor 机制，因此任何自定义编辑器或 自定义编辑器 registrar 都将不再相关。
   *
   * @since 2.5
   * @see #addPropertyEditorRegistrar
   * @see #registerCustomEditor
   */
  void setTypeConverter(TypeConverter typeConverter);

  /**
   * 获取此 BeanFactory 使用的类型转换器。由于 TypeConverter 通常<i>不是</i> 线程安全的，因此每次调用都可能返回一个新的实例。
   *
   * <p>如果默认的 PropertyEditor 机制处于活动状态，返回的 TypeConverter 会感知所有已注册的自定义编辑器。
   *
   * @since 2.5
   */
  TypeConverter getTypeConverter();

  /**
   * 为注解属性等嵌入值添加 String 解析器。
   *
   * @param valueResolver 要应用于嵌入值的 String 解析器
   * @since 3.0
   */
  void addEmbeddedValueResolver(StringValueResolver valueResolver);

  /**
   * 确定此 bean 工厂是否已经注册了嵌入值解析器， 该解析器会通过 {@link #resolveEmbeddedValue(String)} 应用。
   *
   * @since 4.3
   */
  boolean hasEmbeddedValueResolver();

  /**
   * 解析给定的嵌入值，例如注解属性。
   *
   * @param value 要解析的值
   * @return 解析后的值（可能就是原始值本身）
   * @since 3.0
   */
  @Nullable
  String resolveEmbeddedValue(String value);

  /**
   * 添加一个新的 BeanPostProcessor，它会应用于此工厂创建的 bean。 应在工厂配置期间调用。
   *
   * <p>注意：这里提交的后处理器会按注册顺序应用； 任何通过实现 {@link org.springframework.core.Ordered} 接口表达的
   * 排序语义都会被忽略。另请注意，自动检测到的后处理器 （例如 ApplicationContext 中的 bean）始终会在以编程方式注册的 后处理器之后应用。
   *
   * @param beanPostProcessor 要注册的后处理器
   */
  void addBeanPostProcessor(BeanPostProcessor beanPostProcessor);

  /** 返回当前已注册的 BeanPostProcessor 数量（如果有）。 */
  int getBeanPostProcessorCount();

  /**
   * 注册给定的作用域，并由给定的 Scope 实现提供支持。
   *
   * @param scopeName 作用域标识符
   * @param scope 提供支持的 Scope 实现
   */
  void registerScope(String scopeName, Scope scope);

  /**
   * 返回当前所有已注册作用域的名称。
   *
   * <p>这只会返回显式注册的作用域名称。 不会暴露 "singleton" 和 "prototype" 等内置作用域。
   *
   * @return 作用域名称数组；如果没有，则为空数组
   * @see #registerScope
   */
  String[] getRegisteredScopeNames();

  /**
   * 返回给定作用域名称对应的 Scope 实现（如果有）。
   *
   * <p>这只会返回显式注册的作用域。 不会暴露 "singleton" 和 "prototype" 等内置作用域。
   *
   * @param scopeName 作用域的名称
   * @return 已注册的 Scope 实现；如果没有，则为 {@code null}
   * @see #registerScope
   */
  @Nullable
  Scope getRegisteredScope(String scopeName);

  /**
   * 设置此 bean 工厂的 {@code ApplicationStartup}。
   *
   * <p>这允许应用程序上下文在应用程序启动期间记录指标。
   *
   * @param applicationStartup 新的应用程序启动对象
   * @since 5.3
   */
  void setApplicationStartup(ApplicationStartup applicationStartup);

  /**
   * 返回此 bean 工厂的 {@code ApplicationStartup}。
   *
   * @since 5.3
   */
  ApplicationStartup getApplicationStartup();

  /**
   * 提供与此工厂相关的安全访问控制上下文。
   *
   * @return 适用的 AccessControlContext（永不为 {@code null}）
   * @since 3.0
   */
  AccessControlContext getAccessControlContext();

  /**
   * 从给定的另一个工厂复制所有相关配置。
   *
   * <p>应包括所有标准配置设置，以及 BeanPostProcessor、Scope 和 工厂特定的内部设置。不应包括实际 bean 定义的任何元数据， 例如 BeanDefinition
   * 对象和 bean 名称别名。
   *
   * @param otherFactory 要从中复制配置的另一个 BeanFactory
   */
  void copyConfigurationFrom(ConfigurableBeanFactory otherFactory);

  /**
   * 给定一个 bean 名称，创建一个别名。通常使用此方法来支持在 XML id （用于 bean 名称）中非法的名称。
   *
   * <p>通常在工厂配置期间调用，但也可以用于运行时注册别名。 因此，工厂实现应同步对别名的访问。
   *
   * @param beanName 目标 bean 的规范名称
   * @param alias 要为该 bean 注册的别名
   * @throws BeanDefinitionStoreException 如果该别名已经被使用
   */
  void registerAlias(String beanName, String alias) throws BeanDefinitionStoreException;

  /**
   * 解析此工厂中注册的所有别名目标名称和别名，并对它们应用给定的 StringValueResolver。
   *
   * <p>例如，该值解析器可以解析目标 bean 名称中的占位符， 甚至也可以解析别名中的占位符。
   *
   * @param valueResolver 要应用的 StringValueResolver
   * @since 2.5
   */
  void resolveAliases(StringValueResolver valueResolver);

  /**
   * 返回给定 bean 名称对应的合并 BeanDefinition， 必要时会将子 bean 定义与其父定义合并。 同时也会考虑祖先工厂中的 bean 定义。
   *
   * @param beanName 要检索合并定义的 bean 名称
   * @return 给定 bean 的 BeanDefinition（可能已经合并）
   * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean 定义
   * @since 2.5
   */
  BeanDefinition getMergedBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

  /**
   * 确定给定名称的 bean 是否为 FactoryBean。
   *
   * @param name 要检查的 bean 名称
   * @return 该 bean 是否为 FactoryBean （{@code false} 表示该 bean 存在，但不是 FactoryBean）
   * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
   * @since 2.5
   */
  boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException;

  /**
   * 显式控制指定 bean 当前是否处于创建中状态。 仅供容器内部使用。
   *
   * @param beanName bean 的名称
   * @param inCreation bean 当前是否正在创建中
   * @since 3.1
   */
  void setCurrentlyInCreation(String beanName, boolean inCreation);

  /**
   * 确定指定 bean 当前是否正在创建中。
   *
   * @param beanName bean 的名称
   * @return bean 当前是否正在创建中
   * @since 2.5
   */
  boolean isCurrentlyInCreation(String beanName);

  /**
   * 为给定 bean 注册一个依赖 bean，该依赖 bean 会在给定 bean 销毁之前销毁。
   *
   * @param beanName bean 的名称
   * @param dependentBeanName 依赖 bean 的名称
   * @since 2.5
   */
  void registerDependentBean(String beanName, String dependentBeanName);

  /**
   * 返回依赖于指定 bean 的所有 bean 的名称（如果有）。
   *
   * @param beanName bean 的名称
   * @return 依赖 bean 名称数组；如果没有，则为空数组
   * @since 2.5
   */
  String[] getDependentBeans(String beanName);

  /**
   * 返回指定 bean 所依赖的所有 bean 的名称（如果有）。
   *
   * @param beanName bean 的名称
   * @return 该 bean 所依赖的 bean 名称数组；如果没有，则为空数组
   * @since 2.5
   */
  String[] getDependenciesForBean(String beanName);

  /**
   * 根据 bean 定义销毁给定的 bean 实例（通常是从此工厂获取的原型实例）。
   *
   * <p>销毁期间出现的任何异常都应被捕获并记录，而不是传播给此方法的调用者。
   *
   * @param beanName bean 定义的名称
   * @param beanInstance 要销毁的 bean 实例
   */
  void destroyBean(String beanName, Object beanInstance);

  /**
   * 销毁当前目标作用域中的指定作用域 bean（如果有）。
   *
   * <p>销毁期间出现的任何异常都应被捕获并记录，而不是传播给此方法的调用者。
   *
   * @param beanName 作用域 bean 的名称
   */
  void destroyScopedBean(String beanName);

  /**
   * 销毁此工厂中的所有单例 bean，包括已注册为可销毁的内部 bean。 应在工厂关闭时调用。
   *
   * <p>销毁期间出现的任何异常都应被捕获并记录，而不是传播给此方法的调用者。
   */
  void destroySingletons();
}
