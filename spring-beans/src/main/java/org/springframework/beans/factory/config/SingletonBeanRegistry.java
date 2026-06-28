package org.springframework.beans.factory.config;

import org.springframework.lang.Nullable;

/**
 * 定义共享 bean 实例注册表的接口。 {@link org.springframework.beans.factory.BeanFactory} 的实现可以实现该接口，
 * 以统一的方式暴露它们的单例管理能力。
 *
 * <p>{@link ConfigurableBeanFactory} 接口继承了该接口。
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see ConfigurableBeanFactory
 * @see org.springframework.beans.factory.support.DefaultSingletonBeanRegistry
 * @see org.springframework.beans.factory.support.AbstractBeanFactory
 */
public interface SingletonBeanRegistry {

  /**
   * 将给定的现有对象以指定 bean 名称作为单例注册到 bean 注册表中。
   *
   * <p>给定的实例应当已经完全初始化；注册表不会执行任何初始化回调 （尤其不会调用 InitializingBean 的 {@code afterPropertiesSet} 方法）。
   * 给定的实例也不会收到任何销毁回调 （例如 DisposableBean 的 {@code destroy} 方法）。
   *
   * <p>在完整 BeanFactory 中运行时：<b>如果你的 bean 需要接收初始化和/或销毁回调， 请注册 bean 定义，而不是注册一个现有实例。</b>
   *
   * <p>通常在注册表配置阶段调用，但也可以用于在运行时注册单例。因此，注册表实现 应当同步单例访问；如果它支持 BeanFactory 对单例的延迟初始化，无论如何也必须这样做。
   *
   * @param beanName bean 的名称
   * @param singletonObject 现有的单例对象
   * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
   * @see org.springframework.beans.factory.DisposableBean#destroy
   * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition
   */
  void registerSingleton(String beanName, Object singletonObject);

  /**
   * 返回以给定名称注册的（原始）单例对象。
   *
   * <p>只检查已经实例化的单例；不会为尚未实例化的单例 bean 定义返回对象。
   *
   * <p>该方法的主要用途是访问手动注册的单例（见 {@link #registerSingleton}）。 也可以用于以原始形式访问由 bean 定义声明且已经创建的单例。
   *
   * <p><b>注意：</b>该查找方法不了解 FactoryBean 前缀或别名。 在获取单例实例之前，需要先解析规范的 bean 名称。
   *
   * @param beanName 要查找的 bean 名称
   * @return 已注册的单例对象；如果未找到则返回 {@code null}
   * @see ConfigurableListableBeanFactory#getBeanDefinition
   */
  @Nullable
  Object getSingleton(String beanName);

  /**
   * 检查该注册表是否包含给定名称的单例实例。
   *
   * <p>只检查已经实例化的单例；对于尚未实例化的单例 bean 定义，不会返回 {@code true}。
   *
   * <p>该方法的主要用途是检查手动注册的单例（见 {@link #registerSingleton}）。 也可以用于检查由 bean 定义声明的单例是否已经创建。
   *
   * <p>如果要检查 bean 工厂是否包含给定名称的 bean 定义， 请使用 ListableBeanFactory 的 {@code containsBeanDefinition}。
   * 同时调用 {@code containsBeanDefinition} 和 {@code containsSingleton}， 可以判断某个特定 bean 工厂是否包含给定名称的本地
   * bean 实例。
   *
   * <p>如果要进行一般性检查，判断工厂是否知道给定名称的 bean （无论是手动注册的单例实例，还是由 bean 定义创建的实例）， 请使用 BeanFactory 的 {@code
   * containsBean}，该方法也会检查祖先工厂。
   *
   * <p><b>注意：</b>该查找方法不了解 FactoryBean 前缀或别名。 在检查单例状态之前，需要先解析规范的 bean 名称。
   *
   * @param beanName 要查找的 bean 名称
   * @return 如果该 bean 工厂包含给定名称的单例实例，则返回 {@code true}
   * @see #registerSingleton
   * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
   * @see org.springframework.beans.factory.BeanFactory#containsBean
   */
  boolean containsSingleton(String beanName);

  /**
   * 返回该注册表中已注册的单例 bean 名称。
   *
   * <p>只检查已经实例化的单例；不会返回尚未实例化的单例 bean 定义的名称。
   *
   * <p>该方法的主要用途是检查手动注册的单例（见 {@link #registerSingleton}）。 也可以用于检查由 bean 定义声明的哪些单例已经创建。
   *
   * @return 名称列表，以 String 数组形式返回（永远不会为 {@code null}）
   * @see #registerSingleton
   * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionNames
   * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionNames
   */
  String[] getSingletonNames();

  /**
   * 返回该注册表中已注册的单例 bean 数量。
   *
   * <p>只检查已经实例化的单例；不会统计尚未实例化的单例 bean 定义。
   *
   * <p>该方法的主要用途是检查手动注册的单例（见 {@link #registerSingleton}）。 也可以用于统计由 bean 定义声明且已经创建的单例数量。
   *
   * @return 单例 bean 的数量
   * @see #registerSingleton
   * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionCount
   * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionCount
   */
  int getSingletonCount();

  /**
   * 返回该注册表使用的单例互斥锁（供外部协作者使用）。
   *
   * @return 互斥锁对象（永远不会为 {@code null}）
   * @since 4.2
   */
  Object getSingletonMutex();
}
