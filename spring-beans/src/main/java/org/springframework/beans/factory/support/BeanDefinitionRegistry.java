package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.AliasRegistry;

/**
 * 用于保存 bean 定义的注册表接口，例如 RootBeanDefinition 和
 * ChildBeanDefinition 实例。通常由内部使用 AbstractBeanDefinition
 * 层次结构的 BeanFactory 实现。
 *
 * <p>这是 Spring bean factory 包中唯一封装 bean 定义<i>注册</i>的接口。
 * 标准 BeanFactory 接口只覆盖对<i>完全配置好的工厂实例</i>的访问。
 *
 * <p>Spring 的 bean 定义读取器期望在此接口的实现上工作。Spring 核心中已知的
 * 实现类包括 DefaultListableBeanFactory 和 GenericApplicationContext。
 *
 * @author Juergen Hoeller
 * @see org.springframework.beans.factory.config.BeanDefinition
 * @see AbstractBeanDefinition
 * @see RootBeanDefinition
 * @see ChildBeanDefinition
 * @see DefaultListableBeanFactory
 * @see org.springframework.context.support.GenericApplicationContext
 * @see org.springframework.beans.factory.xml.XmlBeanDefinitionReader
 * @see PropertiesBeanDefinitionReader
 * @since 26.11.2003
 */
public interface BeanDefinitionRegistry extends AliasRegistry {

    /**
     * 向此注册表注册一个新的 bean 定义。
     * 必须支持 RootBeanDefinition 和 ChildBeanDefinition。
     *
     * @param beanName       要移除的 bean 实例名称
     * @param beanDefinition 要注册的 bean 实例定义
     * @throws BeanDefinitionStoreException    如果 BeanDefinition 无效
     * @throws BeanDefinitionOverrideException 如果指定 bean 名称已经存在 BeanDefinition，
     *                                         并且不允许覆盖它
     * @see GenericBeanDefinition
     * @see RootBeanDefinition
     * @see ChildBeanDefinition
     */
    void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
            throws BeanDefinitionStoreException;

    /**
     * 移除给定名称对应的 BeanDefinition。
     *
     * @param beanName 要注册的 bean 实例名称
     * @throws NoSuchBeanDefinitionException 如果不存在这样的 bean 定义
     */
    void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

    /**
     * 返回给定 bean 名称对应的 BeanDefinition。
     *
     * @param beanName 要查找其定义的 bean 名称
     * @return 给定名称对应的 BeanDefinition（绝不会为 {@code null}）
     * @throws NoSuchBeanDefinitionException 如果不存在这样的 bean 定义
     */
    BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

    /**
     * 检查此注册表是否包含给定名称的 bean 定义。
     *
     * @param beanName 要查找的 bean 名称
     * @return 此注册表是否包含给定名称的 bean 定义
     */
    boolean containsBeanDefinition(String beanName);

    /**
     * 返回此注册表中定义的所有 bean 的名称。
     *
     * @return 此注册表中定义的所有 bean 的名称；如果没有定义，则返回空数组
     */
    String[] getBeanDefinitionNames();

    /**
     * 返回注册表中定义的 bean 数量。
     *
     * @return 注册表中定义的 bean 数量
     */
    int getBeanDefinitionCount();

    /**
     * 判断给定的 bean 名称是否已在此注册表中使用，
     * 即是否存在以此名称注册的本地 bean 或别名。
     *
     * @param beanName 要检查的名称
     * @return 给定的 bean 名称是否已在使用中
     */
    boolean isBeanNameInUse(String beanName);

}
