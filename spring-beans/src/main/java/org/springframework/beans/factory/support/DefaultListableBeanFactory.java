package org.springframework.beans.factory.support;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.inject.Provider;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.core.OrderComparator;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.CompositeIterator;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Spring 对 {@link ConfigurableListableBeanFactory} 和
 * {@link BeanDefinitionRegistry} 接口的默认实现：一个基于 Bean 定义元数据、
 * 可通过后置处理器扩展的完整 Bean 工厂。
 *
 * <p>典型用法是在访问 Bean 之前先注册所有 Bean 定义（可能从 Bean 定义文件读取）。
 * 因此，按名称查找 Bean 是在本地 Bean 定义表中进行的低成本操作，
 * 作用于已经预解析的 Bean 定义元数据对象。
 *
 * <p>注意，特定 Bean 定义格式的读取器通常会单独实现，
 * 而不是作为 Bean 工厂的子类实现：例如参见
 * {@link org.springframework.beans.factory.xml.XmlBeanDefinitionReader}.
 *
 * <p>关于 {@link org.springframework.beans.factory.ListableBeanFactory} 接口的
 * 另一个实现，请查看 {@link StaticListableBeanFactory}；它管理已有的 Bean 实例，
 * 而不是基于 Bean 定义创建新的实例。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @see #registerBeanDefinition
 * @see #addBeanPostProcessor
 * @see #getBean
 * @see #resolveDependency
 * @since 16 April 2001
 */
@SuppressWarnings("serial")
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
        implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

    @Nullable
    private static Class<?> javaxInjectProviderClass;

    static {
        try {
            javaxInjectProviderClass =
                    ClassUtils.forName("javax.inject.Provider", DefaultListableBeanFactory.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // JSR-330 API 不可用，此时不支持 Provider 接口。
            javaxInjectProviderClass = null;
        }
    }


    /**
     * 从序列化 id 到工厂实例的映射。
     */
    private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories =
            new ConcurrentHashMap<>(8);

    /**
     * 此工厂用于序列化目的的可选 id。
     */
    @Nullable
    private String serializationId;

    /**
     * 是否允许使用同名的不同定义重新注册。
     */
    private boolean allowBeanDefinitionOverriding = true;

    /**
     * 是否允许即使对 lazy-init Bean 也提前加载类。
     */
    private boolean allowEagerClassLoading = true;

    /**
     * 用于依赖 List 和数组的可选 OrderComparator。
     */
    @Nullable
    private Comparator<Object> dependencyComparator;

    /**
     * 用于检查 Bean 定义是否为自动装配候选者的解析器。
     */
    private AutowireCandidateResolver autowireCandidateResolver = SimpleAutowireCandidateResolver.INSTANCE;

    /**
     * 从依赖类型到对应自动装配值的映射。
     */
    private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);

    /**
     * Bean 定义对象的映射，以 Bean 名称为键。
     */
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

    /**
     * 从 Bean 名称到合并后 BeanDefinitionHolder 的映射。
     */
    private final Map<String, BeanDefinitionHolder> mergedBeanDefinitionHolders = new ConcurrentHashMap<>(256);

    /**
     * 单例和非单例 Bean 名称的映射，以依赖类型为键。
     */
    private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);

    /**
     * 仅单例 Bean 名称的映射，以依赖类型为键。
     */
    private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);

    /**
     * Bean 定义名称列表，按注册顺序排列。
     */
    private volatile List<String> beanDefinitionNames = new ArrayList<>(256);

    /**
     * 手动注册的单例名称列表，按注册顺序排列。
     */
    private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

    /**
     * 配置冻结时缓存的 Bean 定义名称数组。
     */
    @Nullable
    private volatile String[] frozenBeanDefinitionNames;

    /**
     * 是否可以为所有 Bean 缓存 Bean 定义元数据。
     */
    private volatile boolean configurationFrozen;


    /**
     * 创建一个新的 DefaultListableBeanFactory。
     */
    public DefaultListableBeanFactory() {
        super();
    }

    /**
     * 使用给定父工厂创建一个新的 DefaultListableBeanFactory。
     *
     * @param parentBeanFactory 父 BeanFactory
     */
    public DefaultListableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
        super(parentBeanFactory);
    }


    /**
     * 指定用于序列化目的的 id，允许在需要时通过此 id 反序列化回该 BeanFactory 对象。
     */
    public void setSerializationId(@Nullable String serializationId) {
        if (serializationId != null) {
            serializableFactories.put(serializationId, new WeakReference<>(this));
        } else if (this.serializationId != null) {
            serializableFactories.remove(this.serializationId);
        }
        this.serializationId = serializationId;
    }

    /**
     * 返回用于序列化目的的 id（如果已指定），允许在需要时通过此 id
     * 反序列化回该 BeanFactory 对象。
     *
     * @since 4.1.2
     */
    @Nullable
    public String getSerializationId() {
        return this.serializationId;
    }

    /**
     * 设置是否允许通过注册同名的不同定义来覆盖 Bean 定义，并自动替换原有定义。
     * 如果不允许，则会抛出异常。这同样适用于覆盖别名。
     * <p>默认为 "true"。
     *
     * @see #registerBeanDefinition
     */
    public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
        this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
    }

    /**
     * 返回是否允许通过注册同名的不同定义来覆盖 Bean 定义，并自动替换原有定义。
     *
     * @since 4.1.2
     */
    public boolean isAllowBeanDefinitionOverriding() {
        return this.allowBeanDefinitionOverriding;
    }

    /**
     * 设置工厂是否允许提前加载 Bean 类，即使 Bean 定义被标记为 "lazy-init"。
     * <p>默认为 "true"。关闭此标志后，除非显式请求某个 lazy-init Bean，
     * 否则会抑制其类加载。特别是，按类型查找会直接忽略没有已解析类名的 Bean 定义，
     * 而不是为了执行类型检查而按需加载 Bean 类。
     *
     * @see AbstractBeanDefinition#setLazyInit
     */
    public void setAllowEagerClassLoading(boolean allowEagerClassLoading) {
        this.allowEagerClassLoading = allowEagerClassLoading;
    }

    /**
     * 返回工厂是否允许提前加载 Bean 类，即使 Bean 定义被标记为 "lazy-init"。
     *
     * @since 4.1.2
     */
    public boolean isAllowEagerClassLoading() {
        return this.allowEagerClassLoading;
    }

    /**
     * 为依赖 List 和数组设置 {@link java.util.Comparator}。
     *
     * @see org.springframework.core.OrderComparator
     * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
     * @since 4.0
     */
    public void setDependencyComparator(@Nullable Comparator<Object> dependencyComparator) {
        this.dependencyComparator = dependencyComparator;
    }

    /**
     * 返回此 BeanFactory 的依赖比较器（可能为 {@code null}）。
     *
     * @since 4.0
     */
    @Nullable
    public Comparator<Object> getDependencyComparator() {
        return this.dependencyComparator;
    }

    /**
     * 为此 BeanFactory 设置自定义自动装配候选者解析器，用于判断某个 Bean 定义
     * 是否应被视为自动装配候选者。
     */
    public void setAutowireCandidateResolver(AutowireCandidateResolver autowireCandidateResolver) {
        Assert.notNull(autowireCandidateResolver, "AutowireCandidateResolver must not be null");
        if (autowireCandidateResolver instanceof BeanFactoryAware) {
            if (System.getSecurityManager() != null) {
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    ((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
                    return null;
                }, getAccessControlContext());
            } else {
                ((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
            }
        }
        this.autowireCandidateResolver = autowireCandidateResolver;
    }

    /**
     * 返回此 BeanFactory 的自动装配候选者解析器（永不为 {@code null}）。
     */
    public AutowireCandidateResolver getAutowireCandidateResolver() {
        return this.autowireCandidateResolver;
    }


    @Override
    public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
        super.copyConfigurationFrom(otherFactory);
        if (otherFactory instanceof DefaultListableBeanFactory) {
            DefaultListableBeanFactory otherListableFactory = (DefaultListableBeanFactory) otherFactory;
            this.allowBeanDefinitionOverriding = otherListableFactory.allowBeanDefinitionOverriding;
            this.allowEagerClassLoading = otherListableFactory.allowEagerClassLoading;
            this.dependencyComparator = otherListableFactory.dependencyComparator;
            // 克隆 AutowireCandidateResolver，因为它可能实现了 BeanFactoryAware
            setAutowireCandidateResolver(otherListableFactory.getAutowireCandidateResolver().cloneIfNecessary());
            // 让可解析依赖（例如 ResourceLoader）在这里同样可用
            this.resolvableDependencies.putAll(otherListableFactory.resolvableDependencies);
        }
    }


    //---------------------------------------------------------------------
    // 其余 BeanFactory 方法的实现
    //---------------------------------------------------------------------

    @Override
    public <T> T getBean(Class<T> requiredType) throws BeansException {
        return getBean(requiredType, (Object[]) null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
        Assert.notNull(requiredType, "Required type must not be null");
        Object resolved = resolveBean(ResolvableType.forRawClass(requiredType), args, false);
        if (resolved == null) {
            throw new NoSuchBeanDefinitionException(requiredType);
        }
        return (T) resolved;
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        Assert.notNull(requiredType, "Required type must not be null");
        return getBeanProvider(ResolvableType.forRawClass(requiredType), true);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        return getBeanProvider(requiredType, true);
    }


    //---------------------------------------------------------------------
    // ListableBeanFactory 接口的实现
    //---------------------------------------------------------------------

    @Override
    public boolean containsBeanDefinition(String beanName) {
        Assert.notNull(beanName, "Bean name must not be null");
        return this.beanDefinitionMap.containsKey(beanName);
    }

    @Override
    public int getBeanDefinitionCount() {
        return this.beanDefinitionMap.size();
    }

    @Override
    public String[] getBeanDefinitionNames() {
        String[] frozenNames = this.frozenBeanDefinitionNames;
        if (frozenNames != null) {
            return frozenNames.clone();
        } else {
            return StringUtils.toStringArray(this.beanDefinitionNames);
        }
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
        Assert.notNull(requiredType, "Required type must not be null");
        return getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
        return new BeanObjectProvider<T>() {
            @Override
            public T getObject() throws BeansException {
                T resolved = resolveBean(requiredType, null, false);
                if (resolved == null) {
                    throw new NoSuchBeanDefinitionException(requiredType);
                }
                return resolved;
            }

            @Override
            public T getObject(Object... args) throws BeansException {
                T resolved = resolveBean(requiredType, args, false);
                if (resolved == null) {
                    throw new NoSuchBeanDefinitionException(requiredType);
                }
                return resolved;
            }

            @Override
            @Nullable
            public T getIfAvailable() throws BeansException {
                try {
                    return resolveBean(requiredType, null, false);
                } catch (ScopeNotActiveException ex) {
                    // 忽略非活动作用域中已解析的 Bean
                    return null;
                }
            }

            @Override
            public void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
                T dependency = getIfAvailable();
                if (dependency != null) {
                    try {
                        dependencyConsumer.accept(dependency);
                    } catch (ScopeNotActiveException ex) {
                        // 忽略非活动作用域中已解析的 Bean，即使是在调用作用域代理时
                    }
                }
            }

            @Override
            @Nullable
            public T getIfUnique() throws BeansException {
                try {
                    return resolveBean(requiredType, null, true);
                } catch (ScopeNotActiveException ex) {
                    // 忽略非活动作用域中已解析的 Bean
                    return null;
                }
            }

            @Override
            public void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
                T dependency = getIfUnique();
                if (dependency != null) {
                    try {
                        dependencyConsumer.accept(dependency);
                    } catch (ScopeNotActiveException ex) {
                        // 忽略非活动作用域中已解析的 Bean，即使是在调用作用域代理时
                    }
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> stream() {
                return Arrays.stream(getBeanNamesForTypedStream(requiredType, allowEagerInit))
                        .map(name -> (T) getBean(name))
                        .filter(bean -> !(bean instanceof NullBean));
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> orderedStream() {
                String[] beanNames = getBeanNamesForTypedStream(requiredType, allowEagerInit);
                if (beanNames.length == 0) {
                    return Stream.empty();
                }
                Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);
                for (String beanName : beanNames) {
                    Object beanInstance = getBean(beanName);
                    if (!(beanInstance instanceof NullBean)) {
                        matchingBeans.put(beanName, (T) beanInstance);
                    }
                }
                Stream<T> stream = matchingBeans.values().stream();
                return stream.sorted(adaptOrderComparator(matchingBeans));
            }
        };
    }

    @Nullable
    private <T> T resolveBean(ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) {
        NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args, nonUniqueAsNull);
        if (namedBean != null) {
            return namedBean.getBeanInstance();
        }
        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory) {
            return ((DefaultListableBeanFactory) parent).resolveBean(requiredType, args, nonUniqueAsNull);
        } else if (parent != null) {
            ObjectProvider<T> parentProvider = parent.getBeanProvider(requiredType);
            if (args != null) {
                return parentProvider.getObject(args);
            } else {
                return (nonUniqueAsNull ? parentProvider.getIfUnique() : parentProvider.getIfAvailable());
            }
        }
        return null;
    }

    private String[] getBeanNamesForTypedStream(ResolvableType requiredType, boolean allowEagerInit) {
        return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, true, allowEagerInit);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type) {
        return getBeanNamesForType(type, true, true);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        Class<?> resolved = type.resolve();
        if (resolved != null && !type.hasGenerics()) {
            return getBeanNamesForType(resolved, includeNonSingletons, allowEagerInit);
        } else {
            return doGetBeanNamesForType(type, includeNonSingletons, allowEagerInit);
        }
    }

    @Override
    public String[] getBeanNamesForType(@Nullable Class<?> type) {
        return getBeanNamesForType(type, true, true);
    }

    @Override
    public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        if (!isConfigurationFrozen() || type == null || !allowEagerInit) {
            return doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit);
        }
        Map<Class<?>, String[]> cache =
                (includeNonSingletons ? this.allBeanNamesByType : this.singletonBeanNamesByType);
        String[] resolvedBeanNames = cache.get(type);
        if (resolvedBeanNames != null) {
            return resolvedBeanNames;
        }
        resolvedBeanNames = doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true);
        if (ClassUtils.isCacheSafe(type, getBeanClassLoader())) {
            cache.put(type, resolvedBeanNames);
        }
        return resolvedBeanNames;
    }

    private String[] doGetBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        List<String> result = new ArrayList<>();

        // 检查所有 Bean 定义。
        for (String beanName : this.beanDefinitionNames) {
            // 只有当 Bean 名称没有被定义为其他 Bean 的别名时，才认为该 Bean 符合条件。
            if (!isAlias(beanName)) {
                try {
                    RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                    // 仅在 Bean 定义完整时检查。
                    if (!mbd.isAbstract() && (allowEagerInit ||
                            (mbd.hasBeanClass() || !mbd.isLazyInit() || isAllowEagerClassLoading()) &&
                                    !requiresEagerInitForType(mbd.getFactoryBeanName()))) {
                        boolean isFactoryBean = isFactoryBean(beanName, mbd);
                        BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
                        boolean matchFound = false;
                        boolean allowFactoryBeanInit = (allowEagerInit || containsSingleton(beanName));
                        boolean isNonLazyDecorated = (dbd != null && !mbd.isLazyInit());
                        if (!isFactoryBean) {
                            if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                            }
                        } else {
                            if (includeNonSingletons || isNonLazyDecorated ||
                                    (allowFactoryBeanInit && isSingleton(beanName, mbd, dbd))) {
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                            }
                            if (!matchFound) {
                                // 对于 FactoryBean，接下来尝试匹配 FactoryBean 实例本身。
                                beanName = FACTORY_BEAN_PREFIX + beanName;
                                if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
                                    matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                                }
                            }
                        }
                        if (matchFound) {
                            result.add(beanName);
                        }
                    }
                } catch (CannotLoadBeanClassException | BeanDefinitionStoreException ex) {
                    if (allowEagerInit) {
                        throw ex;
                    }
                    // 可能是占位符：为了类型匹配先忽略它。
                    LogMessage message = (ex instanceof CannotLoadBeanClassException ?
                            LogMessage.format("Ignoring bean class loading failure for bean '%s'", beanName) :
                            LogMessage.format("Ignoring unresolvable metadata in bean definition '%s'", beanName));
                    logger.trace(message, ex);
                    // 注册异常，以防该 Bean 意外地无法解析。
                    onSuppressedException(ex);
                } catch (NoSuchBeanDefinitionException ex) {
                    // Bean 定义在迭代过程中被移除 -> 忽略。
                }
            }
        }

        // 同样检查手动注册的单例。
        for (String beanName : this.manualSingletonNames) {
            try {
                // 对于 FactoryBean，匹配由 FactoryBean 创建的对象。
                if (isFactoryBean(beanName)) {
                    if ((includeNonSingletons || isSingleton(beanName)) && isTypeMatch(beanName, type)) {
                        result.add(beanName);
                        // 已找到此 Bean 的匹配项：不再匹配 FactoryBean 本身。
                        continue;
                    }
                    // 对于 FactoryBean，接下来尝试匹配 FactoryBean 本身。
                    beanName = FACTORY_BEAN_PREFIX + beanName;
                }
                // 匹配原始 Bean 实例（可能是原始 FactoryBean）。
                if (isTypeMatch(beanName, type)) {
                    result.add(beanName);
                }
            } catch (NoSuchBeanDefinitionException ex) {
                // 不应发生，可能是循环引用解析导致的结果...
                logger.trace(LogMessage.format(
                        "Failed to check manually registered singleton with name '%s'", beanName), ex);
            }
        }

        return StringUtils.toStringArray(result);
    }

    private boolean isSingleton(String beanName, RootBeanDefinition mbd, @Nullable BeanDefinitionHolder dbd) {
        return (dbd != null ? mbd.isSingleton() : isSingleton(beanName));
    }

    /**
     * 检查指定 Bean 是否需要提前初始化才能确定其类型。
     *
     * @param factoryBeanName Bean 定义为其定义了工厂方法的 factory-bean 引用
     * @return 是否需要提前初始化
     */
    private boolean requiresEagerInitForType(@Nullable String factoryBeanName) {
        return (factoryBeanName != null && isFactoryBean(factoryBeanName) && !containsSingleton(factoryBeanName));
    }

    @Override
    public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
        return getBeansOfType(type, true, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBeansOfType(
            @Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {

        String[] beanNames = getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
        Map<String, T> result = CollectionUtils.newLinkedHashMap(beanNames.length);
        for (String beanName : beanNames) {
            try {
                Object beanInstance = getBean(beanName);
                if (!(beanInstance instanceof NullBean)) {
                    result.put(beanName, (T) beanInstance);
                }
            } catch (BeanCreationException ex) {
                Throwable rootCause = ex.getMostSpecificCause();
                if (rootCause instanceof BeanCurrentlyInCreationException) {
                    BeanCreationException bce = (BeanCreationException) rootCause;
                    String exBeanName = bce.getBeanName();
                    if (exBeanName != null && isCurrentlyInCreation(exBeanName)) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Ignoring match to currently created bean '" + exBeanName + "': " +
                                    ex.getMessage());
                        }
                        onSuppressedException(ex);
                        // 忽略：表示在自动装配构造函数时存在循环引用。
                        // 我们希望查找当前正在创建的 Bean 本身之外的匹配项。
                        continue;
                    }
                }
                throw ex;
            }
        }
        return result;
    }

    @Override
    public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        List<String> result = new ArrayList<>();
        for (String beanName : this.beanDefinitionNames) {
            BeanDefinition bd = this.beanDefinitionMap.get(beanName);
            if (bd != null && !bd.isAbstract() && findAnnotationOnBean(beanName, annotationType) != null) {
                result.add(beanName);
            }
        }
        for (String beanName : this.manualSingletonNames) {
            if (!result.contains(beanName) && findAnnotationOnBean(beanName, annotationType) != null) {
                result.add(beanName);
            }
        }
        return StringUtils.toStringArray(result);
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        String[] beanNames = getBeanNamesForAnnotation(annotationType);
        Map<String, Object> result = CollectionUtils.newLinkedHashMap(beanNames.length);
        for (String beanName : beanNames) {
            Object beanInstance = getBean(beanName);
            if (!(beanInstance instanceof NullBean)) {
                result.put(beanName, beanInstance);
            }
        }
        return result;
    }

    @Override
    @Nullable
    public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
            throws NoSuchBeanDefinitionException {

        return findAnnotationOnBean(beanName, annotationType, true);
    }

    @Override
    @Nullable
    public <A extends Annotation> A findAnnotationOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {

        return findMergedAnnotationOnBean(beanName, annotationType, allowFactoryBeanInit)
                .synthesize(MergedAnnotation::isPresent).orElse(null);
    }

    private <A extends Annotation> MergedAnnotation<A> findMergedAnnotationOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) {

        Class<?> beanType = getType(beanName, allowFactoryBeanInit);
        if (beanType != null) {
            MergedAnnotation<A> annotation =
                    MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
            if (annotation.isPresent()) {
                return annotation;
            }
        }
        if (containsBeanDefinition(beanName)) {
            RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            // 检查原始 Bean 类，例如存在代理的情况。
            if (bd.hasBeanClass()) {
                Class<?> beanClass = bd.getBeanClass();
                if (beanClass != beanType) {
                    MergedAnnotation<A> annotation =
                            MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
                    if (annotation.isPresent()) {
                        return annotation;
                    }
                }
            }
            // 检查工厂方法上声明的注解（如果有）。
            Method factoryMethod = bd.getResolvedFactoryMethod();
            if (factoryMethod != null) {
                MergedAnnotation<A> annotation =
                        MergedAnnotations.from(factoryMethod, SearchStrategy.TYPE_HIERARCHY).get(annotationType);
                if (annotation.isPresent()) {
                    return annotation;
                }
            }
        }
        return MergedAnnotation.missing();
    }


    //---------------------------------------------------------------------
    // ConfigurableListableBeanFactory 接口的实现
    //---------------------------------------------------------------------

    @Override
    public void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue) {
        Assert.notNull(dependencyType, "Dependency type must not be null");
        if (autowiredValue != null) {
            if (!(autowiredValue instanceof ObjectFactory || dependencyType.isInstance(autowiredValue))) {
                throw new IllegalArgumentException("Value [" + autowiredValue +
                        "] does not implement specified dependency type [" + dependencyType.getName() + "]");
            }
            this.resolvableDependencies.put(dependencyType, autowiredValue);
        }
    }

    @Override
    public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
            throws NoSuchBeanDefinitionException {

        return isAutowireCandidate(beanName, descriptor, getAutowireCandidateResolver());
    }

    /**
     * 判断指定 Bean 定义是否具备自动装配候选资格，可注入到声明了匹配类型依赖的其他 Bean 中。
     *
     * @param beanName   要检查的 Bean 定义名称
     * @param descriptor 要解析的依赖描述符
     * @param resolver   实际解析算法使用的 AutowireCandidateResolver
     * @return 是否应将该 Bean 视为自动装配候选者
     */
    protected boolean isAutowireCandidate(
            String beanName, DependencyDescriptor descriptor, AutowireCandidateResolver resolver)
            throws NoSuchBeanDefinitionException {

        String bdName = BeanFactoryUtils.transformedBeanName(beanName);
        if (containsBeanDefinition(bdName)) {
            return isAutowireCandidate(beanName, getMergedLocalBeanDefinition(bdName), descriptor, resolver);
        } else if (containsSingleton(beanName)) {
            return isAutowireCandidate(beanName, new RootBeanDefinition(getType(beanName)), descriptor, resolver);
        }

        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory) {
            // 当前工厂中未找到 Bean 定义 -> 委托给父工厂。
            return ((DefaultListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor, resolver);
        } else if (parent instanceof ConfigurableListableBeanFactory) {
            // 如果不是 DefaultListableBeanFactory，则无法传递解析器。
            return ((ConfigurableListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor);
        } else {
            return true;
        }
    }

    /**
     * 判断指定 Bean 定义是否具备自动装配候选资格，可注入到声明了匹配类型依赖的其他 Bean 中。
     *
     * @param beanName   要检查的 Bean 定义名称
     * @param mbd        要检查的合并后 Bean 定义
     * @param descriptor 要解析的依赖描述符
     * @param resolver   实际解析算法使用的 AutowireCandidateResolver
     * @return 是否应将该 Bean 视为自动装配候选者
     */
    protected boolean isAutowireCandidate(String beanName, RootBeanDefinition mbd,
                                          DependencyDescriptor descriptor, AutowireCandidateResolver resolver) {

        String bdName = BeanFactoryUtils.transformedBeanName(beanName);
        resolveBeanClass(mbd, bdName);
        if (mbd.isFactoryMethodUnique && mbd.factoryMethodToIntrospect == null) {
            new ConstructorResolver(this).resolveFactoryMethodIfPossible(mbd);
        }
        BeanDefinitionHolder holder = (beanName.equals(bdName) ?
                this.mergedBeanDefinitionHolders.computeIfAbsent(beanName,
                        key -> new BeanDefinitionHolder(mbd, beanName, getAliases(bdName))) :
                new BeanDefinitionHolder(mbd, beanName, getAliases(bdName)));
        return resolver.isAutowireCandidate(holder, descriptor);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        BeanDefinition bd = this.beanDefinitionMap.get(beanName);
        if (bd == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No bean named '" + beanName + "' found in " + this);
            }
            throw new NoSuchBeanDefinitionException(beanName);
        }
        return bd;
    }

    @Override
    public Iterator<String> getBeanNamesIterator() {
        CompositeIterator<String> iterator = new CompositeIterator<>();
        iterator.add(this.beanDefinitionNames.iterator());
        iterator.add(this.manualSingletonNames.iterator());
        return iterator;
    }

    @Override
    protected void clearMergedBeanDefinition(String beanName) {
        super.clearMergedBeanDefinition(beanName);
        this.mergedBeanDefinitionHolders.remove(beanName);
    }

    @Override
    public void clearMetadataCache() {
        super.clearMetadataCache();
        this.mergedBeanDefinitionHolders.clear();
        clearByTypeCache();
    }

    @Override
    public void freezeConfiguration() {
        this.configurationFrozen = true;
        this.frozenBeanDefinitionNames = StringUtils.toStringArray(this.beanDefinitionNames);
    }

    @Override
    public boolean isConfigurationFrozen() {
        return this.configurationFrozen;
    }

    /**
     * 如果工厂配置已标记为冻结，则认为所有 Bean 都符合元数据缓存条件。
     *
     * @see #freezeConfiguration()
     */
    @Override
    protected boolean isBeanEligibleForMetadataCaching(String beanName) {
        return (this.configurationFrozen || super.isBeanEligibleForMetadataCaching(beanName));
    }

    @Override
    public void preInstantiateSingletons() throws BeansException {
        if (logger.isTraceEnabled()) {
            logger.trace("Pre-instantiating singletons in " + this);
        }

        // 遍历副本，以允许初始化方法继续注册新的 Bean 定义。
        // 虽然这可能不是常规工厂引导流程的一部分，但除此之外可以正常工作。
        List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

        // 触发所有非延迟单例 Bean 的初始化...
        for (String beanName : beanNames) {
            RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                if (isFactoryBean(beanName)) {
                    Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                    if (bean instanceof FactoryBean) {
                        FactoryBean<?> factory = (FactoryBean<?>) bean;
                        boolean isEagerInit;
                        if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                            isEagerInit = AccessController.doPrivileged(
                                    (PrivilegedAction<Boolean>) ((SmartFactoryBean<?>) factory)::isEagerInit,
                                    getAccessControlContext());
                        } else {
                            isEagerInit = (factory instanceof SmartFactoryBean &&
                                    ((SmartFactoryBean<?>) factory).isEagerInit());
                        }
                        if (isEagerInit) {
                            getBean(beanName);
                        }
                    }
                } else {
                    getBean(beanName);
                }
            }
        }

        // 为所有适用的 Bean 触发初始化后回调...
        for (String beanName : beanNames) {
            Object singletonInstance = getSingleton(beanName);
            if (singletonInstance instanceof SmartInitializingSingleton) {
                StartupStep smartInitialize = getApplicationStartup().start("spring.beans.smart-initialize")
                        .tag("beanName", beanName);
                SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
                if (System.getSecurityManager() != null) {
                    AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                        smartSingleton.afterSingletonsInstantiated();
                        return null;
                    }, getAccessControlContext());
                } else {
                    smartSingleton.afterSingletonsInstantiated();
                }
                smartInitialize.end();
            }
        }
    }


    //---------------------------------------------------------------------
    // BeanDefinitionRegistry 接口的实现
    //---------------------------------------------------------------------

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
            throws BeanDefinitionStoreException {

        Assert.hasText(beanName, "Bean name must not be empty");
        Assert.notNull(beanDefinition, "BeanDefinition must not be null");

        if (beanDefinition instanceof AbstractBeanDefinition) {
            try {
                ((AbstractBeanDefinition) beanDefinition).validate();
            } catch (BeanDefinitionValidationException ex) {
                throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName,
                        "Validation of bean definition failed", ex);
            }
        }

        BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
        if (existingDefinition != null) {
            if (!isAllowBeanDefinitionOverriding()) {
                throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
            } else if (existingDefinition.getRole() < beanDefinition.getRole()) {
                // 例如原来是 ROLE_APPLICATION，现在用 ROLE_SUPPORT 或 ROLE_INFRASTRUCTURE 覆盖
                if (logger.isInfoEnabled()) {
                    logger.info("Overriding user-defined bean definition for bean '" + beanName +
                            "' with a framework-generated bean definition: replacing [" +
                            existingDefinition + "] with [" + beanDefinition + "]");
                }
            } else if (!beanDefinition.equals(existingDefinition)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Overriding bean definition for bean '" + beanName +
                            "' with a different definition: replacing [" + existingDefinition +
                            "] with [" + beanDefinition + "]");
                }
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Overriding bean definition for bean '" + beanName +
                            "' with an equivalent definition: replacing [" + existingDefinition +
                            "] with [" + beanDefinition + "]");
                }
            }
            this.beanDefinitionMap.put(beanName, beanDefinition);
        } else {
            if (hasBeanCreationStarted()) {
                // 为了稳定迭代，不能再修改启动阶段集合元素
                synchronized (this.beanDefinitionMap) {
                    this.beanDefinitionMap.put(beanName, beanDefinition);
                    List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
                    updatedDefinitions.addAll(this.beanDefinitionNames);
                    updatedDefinitions.add(beanName);
                    this.beanDefinitionNames = updatedDefinitions;
                    removeManualSingletonName(beanName);
                }
            } else {
                // 仍处于启动注册阶段
                this.beanDefinitionMap.put(beanName, beanDefinition);
                this.beanDefinitionNames.add(beanName);
                removeManualSingletonName(beanName);
            }
            this.frozenBeanDefinitionNames = null;
        }

        if (existingDefinition != null || containsSingleton(beanName)) {
            resetBeanDefinition(beanName);
        } else if (isConfigurationFrozen()) {
            clearByTypeCache();
        }
    }

    @Override
    public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        Assert.hasText(beanName, "'beanName' must not be empty");

        BeanDefinition bd = this.beanDefinitionMap.remove(beanName);
        if (bd == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No bean named '" + beanName + "' found in " + this);
            }
            throw new NoSuchBeanDefinitionException(beanName);
        }

        if (hasBeanCreationStarted()) {
            // 为了稳定迭代，不能再修改启动阶段集合元素
            synchronized (this.beanDefinitionMap) {
                List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames);
                updatedDefinitions.remove(beanName);
                this.beanDefinitionNames = updatedDefinitions;
            }
        } else {
            // 仍处于启动注册阶段
            this.beanDefinitionNames.remove(beanName);
        }
        this.frozenBeanDefinitionNames = null;

        resetBeanDefinition(beanName);
    }

    /**
     * 重置给定 Bean 的所有 Bean 定义缓存，包括从它派生的 Bean 的缓存。
     * <p>在已有 Bean 定义被替换或移除后调用，会对给定 Bean 以及
     * 所有以该 Bean 为父级的 Bean 定义触发 {@link #clearMergedBeanDefinition}、
     * {@link #destroySingleton} 和
     * {@link MergedBeanDefinitionPostProcessor#resetBeanDefinition}。
     *
     * @param beanName 要重置的 Bean 名称
     * @see #registerBeanDefinition
     * @see #removeBeanDefinition
     */
    protected void resetBeanDefinition(String beanName) {
        // 如果给定 Bean 的合并后 Bean 定义已经创建，则将其移除。
        clearMergedBeanDefinition(beanName);

        // 从单例缓存中移除对应 Bean（如果存在）。通常不应需要这样做，
        // 主要是为了覆盖上下文的默认 Bean
        //（例如 StaticApplicationContext 中默认的 StaticMessageSource）。
        destroySingleton(beanName);

        // 通知所有后置处理器指定的 Bean 定义已被重置。
        for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
            processor.resetBeanDefinition(beanName);
        }

        // 重置所有以给定 Bean 为父级的 Bean 定义（递归）。
        for (String bdName : this.beanDefinitionNames) {
            if (!beanName.equals(bdName)) {
                BeanDefinition bd = this.beanDefinitionMap.get(bdName);
                // 由于 beanDefinitionMap 可能被并发修改，确保 bd 非 null。
                if (bd != null && beanName.equals(bd.getParentName())) {
                    resetBeanDefinition(bdName);
                }
            }
        }
    }

    /**
     * 仅当允许覆盖 Bean 定义时，才允许覆盖别名。
     */
    @Override
    protected boolean allowAliasOverriding() {
        return isAllowBeanDefinitionOverriding();
    }

    /**
     * 同时检查别名是否会覆盖同名的 Bean 定义。
     */
    @Override
    protected void checkForAliasCircle(String name, String alias) {
        super.checkForAliasCircle(name, alias);
        if (!isAllowBeanDefinitionOverriding() && containsBeanDefinition(alias)) {
            throw new IllegalStateException("Cannot register alias '" + alias +
                    "' for name '" + name + "': Alias would override bean definition '" + alias + "'");
        }
    }

    @Override
    public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
        super.registerSingleton(beanName, singletonObject);
        updateManualSingletonNames(set -> set.add(beanName), set -> !this.beanDefinitionMap.containsKey(beanName));
        clearByTypeCache();
    }

    @Override
    public void destroySingletons() {
        super.destroySingletons();
        updateManualSingletonNames(Set::clear, set -> !set.isEmpty());
        clearByTypeCache();
    }

    @Override
    public void destroySingleton(String beanName) {
        super.destroySingleton(beanName);
        removeManualSingletonName(beanName);
        clearByTypeCache();
    }

    private void removeManualSingletonName(String beanName) {
        updateManualSingletonNames(set -> set.remove(beanName), set -> set.contains(beanName));
    }

    /**
     * 更新工厂内部的手动单例名称集合。
     *
     * @param action    修改操作
     * @param condition 修改操作的前置条件（如果该条件不适用，可以跳过该操作）
     */
    private void updateManualSingletonNames(Consumer<Set<String>> action, Predicate<Set<String>> condition) {
        if (hasBeanCreationStarted()) {
            // 为了稳定迭代，不能再修改启动阶段集合元素
            synchronized (this.beanDefinitionMap) {
                if (condition.test(this.manualSingletonNames)) {
                    Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
                    action.accept(updatedSingletons);
                    this.manualSingletonNames = updatedSingletons;
                }
            }
        } else {
            // 仍处于启动注册阶段
            if (condition.test(this.manualSingletonNames)) {
                action.accept(this.manualSingletonNames);
            }
        }
    }

    /**
     * 移除关于按类型映射的所有假设。
     */
    private void clearByTypeCache() {
        this.allBeanNamesByType.clear();
        this.singletonBeanNamesByType.clear();
    }


    //---------------------------------------------------------------------
    // 依赖解析功能
    //---------------------------------------------------------------------

    @Override
    public <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException {
        Assert.notNull(requiredType, "Required type must not be null");
        NamedBeanHolder<T> namedBean = resolveNamedBean(ResolvableType.forRawClass(requiredType), null, false);
        if (namedBean != null) {
            return namedBean;
        }
        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof AutowireCapableBeanFactory) {
            return ((AutowireCapableBeanFactory) parent).resolveNamedBean(requiredType);
        }
        throw new NoSuchBeanDefinitionException(requiredType);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> NamedBeanHolder<T> resolveNamedBean(
            ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) throws BeansException {

        Assert.notNull(requiredType, "Required type must not be null");
        String[] candidateNames = getBeanNamesForType(requiredType);

        if (candidateNames.length > 1) {
            List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
            for (String beanName : candidateNames) {
                if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
                    autowireCandidates.add(beanName);
                }
            }
            if (!autowireCandidates.isEmpty()) {
                candidateNames = StringUtils.toStringArray(autowireCandidates);
            }
        }

        if (candidateNames.length == 1) {
            return resolveNamedBean(candidateNames[0], requiredType, args);
        } else if (candidateNames.length > 1) {
            Map<String, Object> candidates = CollectionUtils.newLinkedHashMap(candidateNames.length);
            for (String beanName : candidateNames) {
                if (containsSingleton(beanName) && args == null) {
                    Object beanInstance = getBean(beanName);
                    candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
                } else {
                    candidates.put(beanName, getType(beanName));
                }
            }
            String candidateName = determinePrimaryCandidate(candidates, requiredType.toClass());
            if (candidateName == null) {
                candidateName = determineHighestPriorityCandidate(candidates, requiredType.toClass());
            }
            if (candidateName != null) {
                Object beanInstance = candidates.get(candidateName);
                if (beanInstance == null) {
                    return null;
                }
                if (beanInstance instanceof Class) {
                    return resolveNamedBean(candidateName, requiredType, args);
                }
                return new NamedBeanHolder<>(candidateName, (T) beanInstance);
            }
            if (!nonUniqueAsNull) {
                throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
            }
        }

        return null;
    }

    @Nullable
    private <T> NamedBeanHolder<T> resolveNamedBean(
            String beanName, ResolvableType requiredType, @Nullable Object[] args) throws BeansException {

        Object bean = getBean(beanName, null, args);
        if (bean instanceof NullBean) {
            return null;
        }
        return new NamedBeanHolder<T>(beanName, adaptBeanInstance(beanName, bean, requiredType.toClass()));
    }

    @Override
    @Nullable
    public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName,
                                    @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

        descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
        if (Optional.class == descriptor.getDependencyType()) {
            return createOptionalDependency(descriptor, requestingBeanName);
        } else if (ObjectFactory.class == descriptor.getDependencyType() ||
                ObjectProvider.class == descriptor.getDependencyType()) {
            return new DependencyObjectProvider(descriptor, requestingBeanName);
        } else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
            return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
        } else {
            Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(
                    descriptor, requestingBeanName);
            if (result == null) {
                result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
            }
            return result;
        }
    }

    @Nullable
    public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName,
                                      @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {

        InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
        try {
            Object shortcut = descriptor.resolveShortcut(this);
            if (shortcut != null) {
                return shortcut;
            }

            Class<?> type = descriptor.getDependencyType();
            Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
            if (value != null) {
                if (value instanceof String) {
                    String strVal = resolveEmbeddedValue((String) value);
                    BeanDefinition bd = (beanName != null && containsBean(beanName) ?
                            getMergedBeanDefinition(beanName) : null);
                    value = evaluateBeanDefinitionString(strVal, bd);
                }
                TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
                try {
                    return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
                } catch (UnsupportedOperationException ex) {
                    // 不支持 TypeDescriptor 解析的自定义 TypeConverter...
                    return (descriptor.getField() != null ?
                            converter.convertIfNecessary(value, type, descriptor.getField()) :
                            converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
                }
            }

            Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
            if (multipleBeans != null) {
                return multipleBeans;
            }

            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
            if (matchingBeans.isEmpty()) {
                if (isRequired(descriptor)) {
                    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
                }
                return null;
            }

            String autowiredBeanName;
            Object instanceCandidate;

            if (matchingBeans.size() > 1) {
                autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
                if (autowiredBeanName == null) {
                    if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
                        return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
                    } else {
                        // 对于可选的 Collection/Map，静默忽略非唯一的情况：
                        // 它可能本意是多个常规 Bean 组成的空集合
                        //（尤其是在 4.3 之前，我们甚至不会查找集合 Bean）。
                        return null;
                    }
                }
                instanceCandidate = matchingBeans.get(autowiredBeanName);
            } else {
                // 正好有一个匹配项。
                Map.Entry<String, Object> entry = matchingBeans.entrySet().iterator().next();
                autowiredBeanName = entry.getKey();
                instanceCandidate = entry.getValue();
            }

            if (autowiredBeanNames != null) {
                autowiredBeanNames.add(autowiredBeanName);
            }
            if (instanceCandidate instanceof Class) {
                instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
            }
            Object result = instanceCandidate;
            if (result instanceof NullBean) {
                if (isRequired(descriptor)) {
                    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
                }
                result = null;
            }
            if (!ClassUtils.isAssignableValue(type, result)) {
                throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
            }
            return result;
        } finally {
            ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
        }
    }

    @Nullable
    private Object resolveMultipleBeans(DependencyDescriptor descriptor, @Nullable String beanName,
                                        @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) {

        Class<?> type = descriptor.getDependencyType();

        if (descriptor instanceof StreamDependencyDescriptor) {
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            Stream<Object> stream = matchingBeans.keySet().stream()
                    .map(name -> descriptor.resolveCandidate(name, type, this))
                    .filter(bean -> !(bean instanceof NullBean));
            if (((StreamDependencyDescriptor) descriptor).isOrdered()) {
                stream = stream.sorted(adaptOrderComparator(matchingBeans));
            }
            return stream;
        } else if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            ResolvableType resolvableType = descriptor.getResolvableType();
            Class<?> resolvedArrayType = resolvableType.resolve(type);
            if (resolvedArrayType != type) {
                componentType = resolvableType.getComponentType().resolve();
            }
            if (componentType == null) {
                return null;
            }
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType,
                    new MultiElementDescriptor(descriptor));
            if (matchingBeans.isEmpty()) {
                return null;
            }
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
            Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
            if (result instanceof Object[]) {
                Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
                if (comparator != null) {
                    Arrays.sort((Object[]) result, comparator);
                }
            }
            return result;
        } else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
            Class<?> elementType = descriptor.getResolvableType().asCollection().resolveGeneric();
            if (elementType == null) {
                return null;
            }
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType,
                    new MultiElementDescriptor(descriptor));
            if (matchingBeans.isEmpty()) {
                return null;
            }
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
            Object result = converter.convertIfNecessary(matchingBeans.values(), type);
            if (result instanceof List) {
                if (((List<?>) result).size() > 1) {
                    Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
                    if (comparator != null) {
                        ((List<?>) result).sort(comparator);
                    }
                }
            }
            return result;
        } else if (Map.class == type) {
            ResolvableType mapType = descriptor.getResolvableType().asMap();
            Class<?> keyType = mapType.resolveGeneric(0);
            if (String.class != keyType) {
                return null;
            }
            Class<?> valueType = mapType.resolveGeneric(1);
            if (valueType == null) {
                return null;
            }
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType,
                    new MultiElementDescriptor(descriptor));
            if (matchingBeans.isEmpty()) {
                return null;
            }
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            return matchingBeans;
        } else {
            return null;
        }
    }

    private boolean isRequired(DependencyDescriptor descriptor) {
        return getAutowireCandidateResolver().isRequired(descriptor);
    }

    private boolean indicatesMultipleBeans(Class<?> type) {
        return (type.isArray() || (type.isInterface() &&
                (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))));
    }

    @Nullable
    private Comparator<Object> adaptDependencyComparator(Map<String, ?> matchingBeans) {
        Comparator<Object> comparator = getDependencyComparator();
        if (comparator instanceof OrderComparator) {
            return ((OrderComparator) comparator).withSourceProvider(
                    createFactoryAwareOrderSourceProvider(matchingBeans));
        } else {
            return comparator;
        }
    }

    private Comparator<Object> adaptOrderComparator(Map<String, ?> matchingBeans) {
        Comparator<Object> dependencyComparator = getDependencyComparator();
        OrderComparator comparator = (dependencyComparator instanceof OrderComparator ?
                (OrderComparator) dependencyComparator : OrderComparator.INSTANCE);
        return comparator.withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
    }

    private OrderComparator.OrderSourceProvider createFactoryAwareOrderSourceProvider(Map<String, ?> beans) {
        IdentityHashMap<Object, String> instancesToBeanNames = new IdentityHashMap<>();
        beans.forEach((beanName, instance) -> instancesToBeanNames.put(instance, beanName));
        return new FactoryAwareOrderSourceProvider(instancesToBeanNames);
    }

    /**
     * 查找与所需类型匹配的 Bean 实例。
     * 在为指定 Bean 执行自动装配时调用。
     *
     * @param beanName     即将被装配的 Bean 名称
     * @param requiredType 要查找的实际 Bean 类型
     *                     （可能是数组组件类型或集合元素类型）
     * @param descriptor   要解析的依赖描述符
     * @return 与所需类型匹配的候选名称和候选实例的 Map（永不为 {@code null}）
     * @throws BeansException 发生错误时抛出
     * @see #autowireByType
     * @see #autowireConstructor
     */
    protected Map<String, Object> findAutowireCandidates(
            @Nullable String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {

        String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                this, requiredType, true, descriptor.isEager());
        Map<String, Object> result = CollectionUtils.newLinkedHashMap(candidateNames.length);
        for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
            Class<?> autowiringType = classObjectEntry.getKey();
            if (autowiringType.isAssignableFrom(requiredType)) {
                Object autowiringValue = classObjectEntry.getValue();
                autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
                if (requiredType.isInstance(autowiringValue)) {
                    result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
                    break;
                }
            }
        }
        for (String candidate : candidateNames) {
            if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
                addCandidateEntry(result, candidate, descriptor, requiredType);
            }
        }
        if (result.isEmpty()) {
            boolean multiple = indicatesMultipleBeans(requiredType);
            // 如果第一轮没有找到任何内容，则考虑后备匹配...
            DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();
            for (String candidate : candidateNames) {
                if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) &&
                        (!multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
                    addCandidateEntry(result, candidate, descriptor, requiredType);
                }
            }
            if (result.isEmpty() && !multiple) {
                // 最后一轮考虑自引用...
                // 但对于依赖集合，不考虑完全相同的 Bean 本身。
                for (String candidate : candidateNames) {
                    if (isSelfReference(beanName, candidate) &&
                            (!(descriptor instanceof MultiElementDescriptor) || !beanName.equals(candidate)) &&
                            isAutowireCandidate(candidate, fallbackDescriptor)) {
                        addCandidateEntry(result, candidate, descriptor, requiredType);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 向候选者 Map 添加一个条目：如果可用则为 Bean 实例，否则仅为已解析类型，
     * 以防在选择 primary 候选者之前过早初始化 Bean。
     */
    private void addCandidateEntry(Map<String, Object> candidates, String candidateName,
                                   DependencyDescriptor descriptor, Class<?> requiredType) {

        if (descriptor instanceof MultiElementDescriptor) {
            Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
            if (!(beanInstance instanceof NullBean)) {
                candidates.put(candidateName, beanInstance);
            }
        } else if (containsSingleton(candidateName) || (descriptor instanceof StreamDependencyDescriptor &&
                ((StreamDependencyDescriptor) descriptor).isOrdered())) {
            Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
            candidates.put(candidateName, (beanInstance instanceof NullBean ? null : beanInstance));
        } else {
            candidates.put(candidateName, getType(candidateName));
        }
    }

    /**
     * 在给定 Bean 集合中确定自动装配候选者。
     * <p>按顺序查找 {@code @Primary} 和 {@code @Priority}。
     *
     * @param candidates 候选名称和候选实例的 Map，它们与所需类型匹配，
     *                   如 {@link #findAutowireCandidates} 所返回
     * @param descriptor 要匹配的目标依赖
     * @return 自动装配候选者的名称；如果没有找到则返回 {@code null}
     */
    @Nullable
    protected String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
        Class<?> requiredType = descriptor.getDependencyType();
        String primaryCandidate = determinePrimaryCandidate(candidates, requiredType);
        if (primaryCandidate != null) {
            return primaryCandidate;
        }
        String priorityCandidate = determineHighestPriorityCandidate(candidates, requiredType);
        if (priorityCandidate != null) {
            return priorityCandidate;
        }
        // 后备方案
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateName = entry.getKey();
            Object beanInstance = entry.getValue();
            if ((beanInstance != null && this.resolvableDependencies.containsValue(beanInstance)) ||
                    matchesBeanName(candidateName, descriptor.getDependencyName())) {
                return candidateName;
            }
        }
        return null;
    }

    /**
     * 在给定 Bean 集合中确定 primary 候选者。
     *
     * @param candidates   与所需类型匹配的候选名称和候选实例的 Map
     *                     （如果尚未创建，则为候选类）
     * @param requiredType 要匹配的目标依赖类型
     * @return primary 候选者的名称；如果没有找到则返回 {@code null}
     * @see #isPrimary(String, Object)
     */
    @Nullable
    protected String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
        String primaryBeanName = null;
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateBeanName = entry.getKey();
            Object beanInstance = entry.getValue();
            if (isPrimary(candidateBeanName, beanInstance)) {
                if (primaryBeanName != null) {
                    boolean candidateLocal = containsBeanDefinition(candidateBeanName);
                    boolean primaryLocal = containsBeanDefinition(primaryBeanName);
                    if (candidateLocal && primaryLocal) {
                        throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
                                "more than one 'primary' bean found among candidates: " + candidates.keySet());
                    } else if (candidateLocal) {
                        primaryBeanName = candidateBeanName;
                    }
                } else {
                    primaryBeanName = candidateBeanName;
                }
            }
        }
        return primaryBeanName;
    }

    /**
     * 在给定 Bean 集合中确定优先级最高的候选者。
     * <p>基于 {@code @javax.annotation.Priority}。按照相关
     * {@link org.springframework.core.Ordered} 接口的定义，数值越低优先级越高。
     *
     * @param candidates   与所需类型匹配的候选名称和候选实例的 Map
     *                     （如果尚未创建，则为候选类）
     * @param requiredType 要匹配的目标依赖类型
     * @return 优先级最高的候选者名称；如果没有找到则返回 {@code null}
     * @see #getPriority(Object)
     */
    @Nullable
    protected String determineHighestPriorityCandidate(Map<String, Object> candidates, Class<?> requiredType) {
        String highestPriorityBeanName = null;
        Integer highestPriority = null;
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateBeanName = entry.getKey();
            Object beanInstance = entry.getValue();
            if (beanInstance != null) {
                Integer candidatePriority = getPriority(beanInstance);
                if (candidatePriority != null) {
                    if (highestPriorityBeanName != null) {
                        if (candidatePriority.equals(highestPriority)) {
                            throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
                                    "Multiple beans found with the same priority ('" + highestPriority +
                                            "') among candidates: " + candidates.keySet());
                        } else if (candidatePriority < highestPriority) {
                            highestPriorityBeanName = candidateBeanName;
                            highestPriority = candidatePriority;
                        }
                    } else {
                        highestPriorityBeanName = candidateBeanName;
                        highestPriority = candidatePriority;
                    }
                }
            }
        }
        return highestPriorityBeanName;
    }

    /**
     * 返回给定 Bean 名称对应的 Bean 定义是否被标记为 primary Bean。
     *
     * @param beanName     Bean 名称
     * @param beanInstance 对应的 Bean 实例（可以为 null）
     * @return 给定 Bean 是否具备 primary 资格
     */
    protected boolean isPrimary(String beanName, Object beanInstance) {
        String transformedBeanName = transformedBeanName(beanName);
        if (containsBeanDefinition(transformedBeanName)) {
            return getMergedLocalBeanDefinition(transformedBeanName).isPrimary();
        }
        BeanFactory parent = getParentBeanFactory();
        return (parent instanceof DefaultListableBeanFactory &&
                ((DefaultListableBeanFactory) parent).isPrimary(transformedBeanName, beanInstance));
    }

    /**
     * 返回 {@code javax.annotation.Priority} 注解为给定 Bean 实例分配的优先级。
     * <p>默认实现委托给指定的 {@link #setDependencyComparator 依赖比较器}；
     * 如果该比较器是 Spring 通用 {@link OrderComparator} 的扩展（通常是
     * {@link org.springframework.core.annotation.AnnotationAwareOrderComparator}），
     * 则检查其 {@link OrderComparator#getPriority method}。
     * 如果不存在这样的比较器，此实现返回 {@code null}。
     *
     * @param beanInstance 要检查的 Bean 实例（可以为 {@code null}）
     * @return 分配给该 Bean 的优先级；如果未设置则返回 {@code null}
     */
    @Nullable
    protected Integer getPriority(Object beanInstance) {
        Comparator<Object> comparator = getDependencyComparator();
        if (comparator instanceof OrderComparator) {
            return ((OrderComparator) comparator).getPriority(beanInstance);
        }
        return null;
    }

    /**
     * 判断给定候选名称是否匹配此 Bean 定义中存储的 Bean 名称或别名。
     */
    protected boolean matchesBeanName(String beanName, @Nullable String candidateName) {
        return (candidateName != null &&
                (candidateName.equals(beanName) || ObjectUtils.containsElement(getAliases(beanName), candidateName)));
    }

    /**
     * 判断给定的 beanName/candidateName 对是否表示自引用，
     * 即候选者是否指回原始 Bean，或指向原始 Bean 上的工厂方法。
     */
    private boolean isSelfReference(@Nullable String beanName, @Nullable String candidateName) {
        return (beanName != null && candidateName != null &&
                (beanName.equals(candidateName) || (containsBeanDefinition(candidateName) &&
                        beanName.equals(getMergedLocalBeanDefinition(candidateName).getFactoryBeanName()))));
    }

    /**
     * 针对无法解析的依赖抛出 NoSuchBeanDefinitionException 或 BeanNotOfRequiredTypeException。
     */
    private void raiseNoMatchingBeanFound(
            Class<?> type, ResolvableType resolvableType, DependencyDescriptor descriptor) throws BeansException {

        checkBeanNotOfRequiredType(type, descriptor);

        throw new NoSuchBeanDefinitionException(resolvableType,
                "expected at least 1 bean which qualifies as autowire candidate. " +
                        "Dependency annotations: " + ObjectUtils.nullSafeToString(descriptor.getAnnotations()));
    }

    /**
     * 在适用时，针对无法解析的依赖抛出 BeanNotOfRequiredTypeException，
     * 即 Bean 的目标类型本应匹配，但暴露出来的代理不匹配。
     */
    private void checkBeanNotOfRequiredType(Class<?> type, DependencyDescriptor descriptor) {
        for (String beanName : this.beanDefinitionNames) {
            try {
                RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                Class<?> targetType = mbd.getTargetType();
                if (targetType != null && type.isAssignableFrom(targetType) &&
                        isAutowireCandidate(beanName, mbd, descriptor, getAutowireCandidateResolver())) {
                    // 可能是代理干扰了目标类型匹配 -> 抛出有意义的异常。
                    Object beanInstance = getSingleton(beanName, false);
                    Class<?> beanType = (beanInstance != null && beanInstance.getClass() != NullBean.class ?
                            beanInstance.getClass() : predictBeanType(beanName, mbd));
                    if (beanType != null && !type.isAssignableFrom(beanType)) {
                        throw new BeanNotOfRequiredTypeException(beanName, type, beanType);
                    }
                }
            } catch (NoSuchBeanDefinitionException ex) {
                // Bean 定义在迭代过程中被移除 -> 忽略。
            }
        }

        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory) {
            ((DefaultListableBeanFactory) parent).checkBeanNotOfRequiredType(type, descriptor);
        }
    }

    /**
     * 为指定依赖创建 {@link Optional} 包装器。
     */
    private Optional<?> createOptionalDependency(
            DependencyDescriptor descriptor, @Nullable String beanName, final Object... args) {

        DependencyDescriptor descriptorToUse = new NestedDependencyDescriptor(descriptor) {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
                return (!ObjectUtils.isEmpty(args) ? beanFactory.getBean(beanName, args) :
                        super.resolveCandidate(beanName, requiredType, beanFactory));
            }
        };
        Object result = doResolveDependency(descriptorToUse, beanName, null, null);
        return (result instanceof Optional ? (Optional<?>) result : Optional.ofNullable(result));
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(ObjectUtils.identityToString(this));
        sb.append(": defining beans [");
        sb.append(StringUtils.collectionToCommaDelimitedString(this.beanDefinitionNames));
        sb.append("]; ");
        BeanFactory parent = getParentBeanFactory();
        if (parent == null) {
            sb.append("root of factory hierarchy");
        } else {
            sb.append("parent: ").append(ObjectUtils.identityToString(parent));
        }
        return sb.toString();
    }


    //---------------------------------------------------------------------
    // 序列化支持
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        throw new NotSerializableException("DefaultListableBeanFactory itself is not deserializable - " +
                "just a SerializedBeanFactoryReference is");
    }

    protected Object writeReplace() throws ObjectStreamException {
        if (this.serializationId != null) {
            return new SerializedBeanFactoryReference(this.serializationId);
        } else {
            throw new NotSerializableException("DefaultListableBeanFactory has no serialization id");
        }
    }


    /**
     * 对工厂的最小 id 引用。
     * 在反序列化时解析为实际的工厂实例。
     */
    private static class SerializedBeanFactoryReference implements Serializable {

        private final String id;

        public SerializedBeanFactoryReference(String id) {
            this.id = id;
        }

        private Object readResolve() {
            Reference<?> ref = serializableFactories.get(this.id);
            if (ref != null) {
                Object result = ref.get();
                if (result != null) {
                    return result;
                }
            }
            // 宽松后备方案：在找不到原始工厂时使用虚拟工厂...
            DefaultListableBeanFactory dummyFactory = new DefaultListableBeanFactory();
            dummyFactory.serializationId = this.id;
            return dummyFactory;
        }
    }


    /**
     * 用于嵌套元素的依赖描述符标记。
     */
    private static class NestedDependencyDescriptor extends DependencyDescriptor {

        public NestedDependencyDescriptor(DependencyDescriptor original) {
            super(original);
            increaseNestingLevel();
        }
    }


    /**
     * 用于包含嵌套元素的多元素声明的依赖描述符。
     */
    private static class MultiElementDescriptor extends NestedDependencyDescriptor {

        public MultiElementDescriptor(DependencyDescriptor original) {
            super(original);
        }
    }


    /**
     * 用于以流方式访问多个元素的依赖描述符标记。
     */
    private static class StreamDependencyDescriptor extends DependencyDescriptor {

        private final boolean ordered;

        public StreamDependencyDescriptor(DependencyDescriptor original, boolean ordered) {
            super(original);
            this.ordered = ordered;
        }

        public boolean isOrdered() {
            return this.ordered;
        }
    }


    private interface BeanObjectProvider<T> extends ObjectProvider<T>, Serializable {
    }


    /**
     * 用于延迟解析依赖的可序列化 ObjectFactory/ObjectProvider。
     */
    private class DependencyObjectProvider implements BeanObjectProvider<Object> {

        private final DependencyDescriptor descriptor;

        private final boolean optional;

        @Nullable
        private final String beanName;

        public DependencyObjectProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
            this.descriptor = new NestedDependencyDescriptor(descriptor);
            this.optional = (this.descriptor.getDependencyType() == Optional.class);
            this.beanName = beanName;
        }

        @Override
        public Object getObject() throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName);
            } else {
                Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
                if (result == null) {
                    throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
                }
                return result;
            }
        }

        @Override
        public Object getObject(final Object... args) throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName, args);
            } else {
                DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                    @Override
                    public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
                        return beanFactory.getBean(beanName, args);
                    }
                };
                Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
                if (result == null) {
                    throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
                }
                return result;
            }
        }

        @Override
        @Nullable
        public Object getIfAvailable() throws BeansException {
            try {
                if (this.optional) {
                    return createOptionalDependency(this.descriptor, this.beanName);
                } else {
                    DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                        @Override
                        public boolean isRequired() {
                            return false;
                        }
                    };
                    return doResolveDependency(descriptorToUse, this.beanName, null, null);
                }
            } catch (ScopeNotActiveException ex) {
                // 忽略非活动作用域中已解析的 Bean
                return null;
            }
        }

        @Override
        public void ifAvailable(Consumer<Object> dependencyConsumer) throws BeansException {
            Object dependency = getIfAvailable();
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency);
                } catch (ScopeNotActiveException ex) {
                    // 忽略非活动作用域中已解析的 Bean，即使是在调用作用域代理时
                }
            }
        }

        @Override
        @Nullable
        public Object getIfUnique() throws BeansException {
            DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                @Override
                public boolean isRequired() {
                    return false;
                }

                @Override
                @Nullable
                public Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) {
                    return null;
                }
            };
            try {
                if (this.optional) {
                    return createOptionalDependency(descriptorToUse, this.beanName);
                } else {
                    return doResolveDependency(descriptorToUse, this.beanName, null, null);
                }
            } catch (ScopeNotActiveException ex) {
                // 忽略非活动作用域中已解析的 Bean
                return null;
            }
        }

        @Override
        public void ifUnique(Consumer<Object> dependencyConsumer) throws BeansException {
            Object dependency = getIfUnique();
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency);
                } catch (ScopeNotActiveException ex) {
                    // 忽略非活动作用域中已解析的 Bean，即使是在调用作用域代理时
                }
            }
        }

        @Nullable
        protected Object getValue() throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName);
            } else {
                return doResolveDependency(this.descriptor, this.beanName, null, null);
            }
        }

        @Override
        public Stream<Object> stream() {
            return resolveStream(false);
        }

        @Override
        public Stream<Object> orderedStream() {
            return resolveStream(true);
        }

        @SuppressWarnings("unchecked")
        private Stream<Object> resolveStream(boolean ordered) {
            DependencyDescriptor descriptorToUse = new StreamDependencyDescriptor(this.descriptor, ordered);
            Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
            return (result instanceof Stream ? (Stream<Object>) result : Stream.of(result));
        }
    }


    /**
     * 单独的内部类，用于避免对 {@code javax.inject} API 的硬依赖。
     * 实际的 {@code javax.inject.Provider} 实现在这里嵌套，
     * 以避免被 Graal 对 DefaultListableBeanFactory 嵌套类的内省发现。
     */
    private class Jsr330Factory implements Serializable {

        public Object createDependencyProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
            return new Jsr330Provider(descriptor, beanName);
        }

        private class Jsr330Provider extends DependencyObjectProvider implements Provider<Object> {

            public Jsr330Provider(DependencyDescriptor descriptor, @Nullable String beanName) {
                super(descriptor, beanName);
            }

            @Override
            @Nullable
            public Object get() throws BeansException {
                return getValue();
            }
        }
    }


    /**
     * 一个能够感知待排序实例的 Bean 元数据的
     * {@link org.springframework.core.OrderComparator.OrderSourceProvider} 实现。
     * <p>查找待排序实例的工厂方法（如果有），并让比较器获取其上定义的
     * {@link org.springframework.core.annotation.Order} 值。
     * 这本质上允许如下结构：
     */
    private class FactoryAwareOrderSourceProvider implements OrderComparator.OrderSourceProvider {

        private final Map<Object, String> instancesToBeanNames;

        public FactoryAwareOrderSourceProvider(Map<Object, String> instancesToBeanNames) {
            this.instancesToBeanNames = instancesToBeanNames;
        }

        @Override
        @Nullable
        public Object getOrderSource(Object obj) {
            String beanName = this.instancesToBeanNames.get(obj);
            if (beanName == null) {
                return null;
            }
            try {
                RootBeanDefinition beanDefinition = (RootBeanDefinition) getMergedBeanDefinition(beanName);
                List<Object> sources = new ArrayList<>(2);
                Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
                if (factoryMethod != null) {
                    sources.add(factoryMethod);
                }
                Class<?> targetType = beanDefinition.getTargetType();
                if (targetType != null && targetType != obj.getClass()) {
                    sources.add(targetType);
                }
                return sources.toArray();
            } catch (NoSuchBeanDefinitionException ex) {
                return null;
            }
        }
    }

}
