/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * 访问 Spring bean 容器的根接口。
 *
 * <p>这是 bean 容器的基本客户端视图；
 * 还可以使用 {@link ListableBeanFactory} 和
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * 等其他接口来满足特定用途。
 *
 * <p>该接口由持有多个 bean 定义的对象实现，每个定义都通过一个 String 名称唯一标识。
 * 根据 bean 定义的不同，工厂会返回容器对象的独立实例
 * （原型模式），或返回单个共享实例（这是对单例模式的更优替代，其中实例是工厂范围内的单例）。
 * 返回哪种实例取决于 bean 工厂的配置：API 是相同的。自 Spring 2.0 起，
 * 还可依据具体应用上下文使用更多作用域（例如 Web 环境中的 "request" 和 "session" 作用域）。
 *
 * <p>这种方式的关键在于 BeanFactory 作为应用组件的中心注册表，
 * 并集中管理应用组件的配置（例如，不再需要各个对象自己读取属性文件）。
 * 有关这种方式的优势，请参见 "Expert One-on-One J2EE Design and Development" 的第 4 章和第 11 章。
 *
 * <p>通常，更推荐使用依赖注入（"push" 配置）通过 setter 或构造器来配置应用对象，
 * 而不是使用 BeanFactory 查找这类 "pull" 配置。Spring 的依赖注入功能就是基于
 * 这个 BeanFactory 接口及其子接口实现的。
 *
 * <p>通常，BeanFactory 会加载存储在配置源（例如 XML 文档）中的 bean 定义，
 * 并使用 {@code org.springframework.beans} 包来配置这些 bean。不过，实现也可以直接在 Java 代码中
 * 按需返回它创建的 Java 对象。定义的存储方式没有限制：LDAP、RDBMS、XML、属性文件等都可以。
 * 鼓励实现支持 bean 之间的引用（依赖注入）。
 *
 * <p>与 {@link ListableBeanFactory} 中的方法不同，如果这是一个
 * {@link HierarchicalBeanFactory}，此接口中的所有操作也会检查父工厂。
 * 如果在当前工厂实例中找不到 bean，就会询问直接父工厂。当前工厂中的 bean 应当覆盖
 * 任意父工厂中同名的 bean。
 *
 * <p>Bean 工厂实现应尽可能支持标准的 bean 生命周期接口。完整的初始化方法及其标准顺序如下：
 * <ol>
 * <li>BeanNameAware 的 {@code setBeanName}
 * <li>BeanClassLoaderAware 的 {@code setBeanClassLoader}
 * <li>BeanFactoryAware 的 {@code setBeanFactory}
 * <li>EnvironmentAware 的 {@code setEnvironment}
 * <li>EmbeddedValueResolverAware 的 {@code setEmbeddedValueResolver}
 * <li>ResourceLoaderAware 的 {@code setResourceLoader}
 * （仅在应用上下文中运行时适用）
 * <li>ApplicationEventPublisherAware 的 {@code setApplicationEventPublisher}
 * （仅在应用上下文中运行时适用）
 * <li>MessageSourceAware 的 {@code setMessageSource}
 * （仅在应用上下文中运行时适用）
 * <li>ApplicationContextAware 的 {@code setApplicationContext}
 * （仅在应用上下文中运行时适用）
 * <li>ServletContextAware 的 {@code setServletContext}
 * （仅在 Web 应用上下文中运行时适用）
 * <li>BeanPostProcessor 的 {@code postProcessBeforeInitialization} 方法
 * <li>InitializingBean 的 {@code afterPropertiesSet}
 * <li>自定义 {@code init-method} 定义
 * <li>BeanPostProcessor 的 {@code postProcessAfterInitialization} 方法
 * </ol>
 *
 * <p>在 bean 工厂关闭时，适用以下生命周期方法：
 * <ol>
 * <li>DestructionAwareBeanPostProcessor 的 {@code postProcessBeforeDestruction} 方法
 * <li>DisposableBean 的 {@code destroy}
 * <li>自定义 {@code destroy-method} 定义
 * </ol>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see BeanNameAware#setBeanName
 * @see BeanClassLoaderAware#setBeanClassLoader
 * @see BeanFactoryAware#setBeanFactory
 * @see org.springframework.context.EnvironmentAware#setEnvironment
 * @see org.springframework.context.EmbeddedValueResolverAware#setEmbeddedValueResolver
 * @see org.springframework.context.ResourceLoaderAware#setResourceLoader
 * @see org.springframework.context.ApplicationEventPublisherAware#setApplicationEventPublisher
 * @see org.springframework.context.MessageSourceAware#setMessageSource
 * @see org.springframework.context.ApplicationContextAware#setApplicationContext
 * @see org.springframework.web.context.ServletContextAware#setServletContext
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization
 * @see InitializingBean#afterPropertiesSet
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getInitMethodName
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization
 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor#postProcessBeforeDestruction
 * @see DisposableBean#destroy
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getDestroyMethodName
 * @since 13 April 2001
 */
public interface BeanFactory {

    /**
     * 用于解引用 {@link FactoryBean} 实例，并将其与由 FactoryBean <i>创建</i> 的 bean 区分开来。
     * 例如，如果名为 {@code myJndiObject} 的 bean 是一个 FactoryBean，
     * 获取 {@code &myJndiObject} 将返回工厂本身，而不是工厂返回的实例。
     */
    String FACTORY_BEAN_PREFIX = "&";


    /**
     * 返回指定 bean 的实例，该实例可以是共享的，也可以是独立的。
     * <p>该方法允许将 Spring BeanFactory 用作单例或原型模式的替代方案。
     * 对于单例 bean，调用方可以保留返回对象的引用。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name 要获取的 bean 名称
     * @return bean 的实例。
     * 注意，返回值绝不会是 {@code null}，但如果工厂方法返回了 {@code null}，
     * 可能会返回一个占位 stub，可通过 {@code equals(null)} 进行检查。
     * 如需解析可选依赖，请考虑使用 {@link #getBeanProvider(Class)}。
     * @throws NoSuchBeanDefinitionException 如果不存在指定名称的 bean
     * @throws BeansException                如果无法获取该 bean
     */
    Object getBean(String name) throws BeansException;

    /**
     * 返回指定 bean 的实例，该实例可以是共享的，也可以是独立的。
     * <p>行为与 {@link #getBean(String)} 相同，但通过在 bean 不属于所需类型时抛出
     * BeanNotOfRequiredTypeException 来提供一定的类型安全性。
     * 这意味着正确进行结果类型转换时不会抛出 ClassCastException，
     * 而 {@link #getBean(String)} 可能会出现这种情况。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name         要获取的 bean 名称
     * @param requiredType bean 必须匹配的类型；可以是接口或父类
     * @return bean 的实例。
     * 注意，返回值绝不会是 {@code null}。如果针对请求的 bean 解析出工厂方法返回
     * {@code null} 的占位对象，则会针对 NullBean stub 抛出 {@code BeanNotOfRequiredTypeException}。
     * 如需解析可选依赖，请考虑使用 {@link #getBeanProvider(Class)}。
     * @throws NoSuchBeanDefinitionException  如果不存在这样的 bean 定义
     * @throws BeanNotOfRequiredTypeException 如果 bean 不是所需类型
     * @throws BeansException                 如果 bean 无法创建
     */
    <T> T getBean(String name, Class<T> requiredType) throws BeansException;

    /**
     * 返回指定 bean 的实例，该实例可以是共享的，也可以是独立的。
     * <p>允许显式指定构造器参数 / 工厂方法参数，
     * 从而覆盖 bean 定义中的默认参数（如果有）。
     *
     * @param name 要获取的 bean 名称
     * @param args 在使用显式参数创建 bean 实例时要用的参数
     *             （仅在创建新实例而不是获取已有实例时适用）
     * @return bean 的实例
     * @throws NoSuchBeanDefinitionException 如果不存在这样的 bean 定义
     * @throws BeanDefinitionStoreException  如果已经给出了参数，但受影响的 bean 不是原型
     * @throws BeansException                如果 bean 无法创建
     * @since 2.5
     */
    Object getBean(String name, Object... args) throws BeansException;

    /**
     * 返回与给定对象类型唯一匹配的 bean 实例（如果有）。
     * <p>此方法进入 {@link ListableBeanFactory} 的按类型查找领域，
     * 但也可能根据给定类型的名称转换为常规的按名称查找。
     * 如需在多个 bean 集合上执行更丰富的检索操作，请使用
     * {@link ListableBeanFactory} 和/或 {@link BeanFactoryUtils}。
     *
     * @param requiredType bean 必须匹配的类型；可以是接口或父类
     * @return 与所需类型匹配的单个 bean 实例
     * @throws NoSuchBeanDefinitionException   如果未找到给定类型的 bean
     * @throws NoUniqueBeanDefinitionException 如果找到多个给定类型的 bean
     * @throws BeansException                  如果 bean 无法创建
     * @see ListableBeanFactory
     * @since 3.0
     */
    <T> T getBean(Class<T> requiredType) throws BeansException;

    /**
     * 返回指定 bean 的实例，该实例可以是共享的，也可以是独立的。
     * <p>允许显式指定构造器参数 / 工厂方法参数，
     * 从而覆盖 bean 定义中的默认参数（如果有）。
     * <p>此方法进入 {@link ListableBeanFactory} 的按类型查找领域，
     * 但也可能根据给定类型的名称转换为常规的按名称查找。
     * 如需在多个 bean 集合上执行更丰富的检索操作，请使用
     * {@link ListableBeanFactory} 和/或 {@link BeanFactoryUtils}。
     *
     * @param requiredType bean 必须匹配的类型；可以是接口或父类
     * @param args         在使用显式参数创建 bean 实例时要用的参数
     *                     （仅在创建新实例而不是获取已有实例时适用）
     * @return bean 的实例
     * @throws NoSuchBeanDefinitionException 如果不存在这样的 bean 定义
     * @throws BeanDefinitionStoreException  如果已经给出了参数，但受影响的 bean 不是原型
     * @throws BeansException                如果 bean 无法创建
     * @since 4.1
     */
    <T> T getBean(Class<T> requiredType, Object... args) throws BeansException;

    /**
     * 返回指定 bean 的 provider，支持按需懒加载获取实例，
     * 包括可用性和唯一性选项。
     * <p>如需匹配泛型类型，请考虑使用 {@link #getBeanProvider(ResolvableType)}。
     *
     * @param requiredType bean 必须匹配的类型；可以是接口或父类
     * @return 对应的 provider 句柄
     * @see #getBeanProvider(ResolvableType)
     * @since 5.1
     */
    <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

    /**
     * 返回指定 bean 的 provider，支持按需懒加载获取实例，
     * 包括可用性和唯一性选项。此变体允许指定要匹配的泛型类型，
     * 类似于方法/构造器参数中带泛型声明的反射注入点。
     * <p>注意，这里不支持 bean 集合，不同于反射注入点。
     * 如果想以编程方式获取匹配某个特定类型的 bean 列表，请在此处指定实际的 bean 类型作为参数，
     * 然后再使用 {@link ObjectProvider#orderedStream()} 或其懒加载流式/迭代选项。
     * <p>另外，这里的泛型匹配是严格的，遵循 Java 赋值规则。
     * 如果该变体无法 {@link ObjectProvider#getIfAvailable() 获取到}
     * 完整泛型匹配，而你又希望采用类似 "unchecked" 的宽松回退语义，
     * 可以在第二步改用原始类型调用 {@link #getBeanProvider(Class)}。
     *
     * @param requiredType bean 必须匹配的类型；可以是泛型类型声明
     * @return 对应的 provider 句柄
     * @see ObjectProvider#iterator()
     * @see ObjectProvider#stream()
     * @see ObjectProvider#orderedStream()
     * @since 5.1
     */
    <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

    /**
     * 该 bean 工厂是否包含给定名称的 bean 定义或外部注册的单例实例？
     * <p>如果给定名称是别名，则会被转换回对应的规范 bean 名称。
     * <p>如果该工厂是层级式的，当在当前工厂实例中找不到该 bean 时，也会询问父工厂。
     * <p>如果找到与给定名称匹配的 bean 定义或单例实例，那么无论该 bean 定义是具体的还是抽象的、
     * 懒加载还是立即加载、是否在作用域内，此方法都会返回 {@code true}。
     * 因此要注意，此方法返回 {@code true} 并不一定表示 {@link #getBean}
     * 能够为同名条目获取到实例。
     *
     * @param name 要查询的 bean 名称
     * @return 是否存在给定名称的 bean
     */
    boolean containsBean(String name);

    /**
     * 该 bean 是否为共享单例？也就是说，{@link #getBean} 是否总会返回同一个实例？
     * <p>注意：此方法返回 {@code false} 并不能明确表示是独立实例。
     * 它表示非单例实例，这也可能对应一个作用域 bean。请使用 {@link #isPrototype}
     * 操作来显式检查独立实例。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name 要查询的 bean 名称
     * @return 该 bean 是否对应一个单例实例
     * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
     * @see #getBean
     * @see #isPrototype
     */
    boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

    /**
     * 该 bean 是否为原型？也就是说，{@link #getBean} 是否总会返回独立实例？
     * <p>注意：此方法返回 {@code false} 并不能明确表示是单例对象。
     * 它表示非独立实例，这也可能对应一个作用域 bean。请使用 {@link #isSingleton}
     * 操作来显式检查共享单例实例。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name 要查询的 bean 名称
     * @return 该 bean 是否总会返回独立实例
     * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
     * @see #getBean
     * @see #isSingleton
     * @since 2.0.3
     */
    boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

    /**
     * 检查给定名称的 bean 是否与指定类型匹配。
     * 更具体地说，检查对给定名称执行一次 {@link #getBean} 调用后，
     * 是否会返回一个可赋值给指定目标类型的对象。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name        要查询的 bean 名称
     * @param typeToMatch 要匹配的类型（以 {@code ResolvableType} 表示）
     * @return 如果 bean 类型匹配则为 {@code true}，
     * 如果不匹配或尚无法确定则为 {@code false}
     * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
     * @see #getBean
     * @see #getType
     * @since 4.2
     */
    boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

    /**
     * 检查给定名称的 bean 是否与指定类型匹配。
     * 更具体地说，检查对给定名称执行一次 {@link #getBean} 调用后，
     * 是否会返回一个可赋值给指定目标类型的对象。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name        要查询的 bean 名称
     * @param typeToMatch 要匹配的类型（以 {@code Class} 表示）
     * @return 如果 bean 类型匹配则为 {@code true}，
     * 如果不匹配或尚无法确定则为 {@code false}
     * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
     * @see #getBean
     * @see #getType
     * @since 2.0.1
     */
    boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

    /**
     * 确定给定名称的 bean 类型。更具体地说，
     * 确定对给定名称执行 {@link #getBean} 调用后会返回的对象类型。
     * <p>对于 {@link FactoryBean}，返回该 FactoryBean 创建的对象类型，
     * 即 {@link FactoryBean#getObjectType()} 所暴露的类型。这可能会导致先前未初始化的
     * {@code FactoryBean} 被初始化（参见 {@link #getType(String, boolean)}）。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name 要查询的 bean 名称
     * @return bean 的类型；如果无法确定则返回 {@code null}
     * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
     * @see #getBean
     * @see #isTypeMatch
     * @since 1.1.2
     */
    @Nullable
    Class<?> getType(String name) throws NoSuchBeanDefinitionException;

    /**
     * 确定给定名称的 bean 类型。更具体地说，
     * 确定对给定名称执行 {@link #getBean} 调用后会返回的对象类型。
     * <p>对于 {@link FactoryBean}，返回该 FactoryBean 创建的对象类型，
     * 即 {@link FactoryBean#getObjectType()} 所暴露的类型。根据
     * {@code allowFactoryBeanInit} 标志的不同，如果没有早期类型信息可用，这可能会导致先前未初始化的
     * {@code FactoryBean} 被初始化。
     * <p>会将别名转换回对应的规范 bean 名称。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name                 要查询的 bean 名称
     * @param allowFactoryBeanInit 是否可以为了确定对象类型而初始化 {@code FactoryBean}
     * @return bean 的类型；如果无法确定则返回 {@code null}
     * @throws NoSuchBeanDefinitionException 如果不存在给定名称的 bean
     * @see #getBean
     * @see #isTypeMatch
     * @since 5.2
     */
    @Nullable
    Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

    /**
     * 返回给定 bean 名称的别名（如果有）。
     * <p>当这些别名用于 {@link #getBean} 调用时，都会指向同一个 bean。
     * <p>如果给定名称本身就是别名，则会返回对应的原始 bean 名称以及其他别名（如果有），
     * 其中原始 bean 名称位于数组的第一个元素。
     * <p>如果在当前工厂实例中找不到该 bean，则会询问父工厂。
     *
     * @param name 要检查别名的 bean 名称
     * @return 别名数组；如果没有则返回空数组
     * @see #getBean
     */
    String[] getAliases(String name);

}
