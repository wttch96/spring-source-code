package org.springframework.beans.factory;

import org.springframework.lang.Nullable;

/**
 * 由可作为层次结构一部分的 bean 工厂实现的子接口。
 *
 * <p>对于允许以可配置方式设置父工厂的 bean 工厂， 对应的 {@code setParentBeanFactory} 方法可在 ConfigurableBeanFactory 接口中找到。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 07.07.2003
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#setParentBeanFactory
 */
public interface HierarchicalBeanFactory extends BeanFactory {

  /** 返回父 bean 工厂；如果没有父工厂，则返回 {@code null}。 */
  @Nullable
  BeanFactory getParentBeanFactory();

  /**
   * 返回本地 bean 工厂是否包含给定名称的 bean， 忽略祖先上下文中定义的 bean。
   *
   * <p>这是 {@code containsBean} 的替代方法， 会忽略祖先 bean 工厂中给定名称的 bean。
   *
   * @param name 要查询的 bean 名称
   * @return 本地工厂中是否定义了具有给定名称的 bean
   * @see BeanFactory#containsBean
   */
  boolean containsLocalBean(String name);
}
