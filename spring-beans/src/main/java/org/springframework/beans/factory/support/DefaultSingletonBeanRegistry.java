package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 共享 bean 实例的通用注册表，实现 {@link org.springframework.beans.factory.config.SingletonBeanRegistry}。
 * 允许注册应当被该注册表所有调用者共享的单例实例，并通过 bean 名称获取它们。
 *
 * <p>也支持注册 {@link org.springframework.beans.factory.DisposableBean} 实例
 * （这些实例可能对应已注册的单例，也可能不对应），以便在注册表关闭时销毁。 可以注册 bean 之间的依赖关系，以强制执行合适的关闭顺序。
 *
 * <p>该类主要作为 {@link org.springframework.beans.factory.BeanFactory} 实现的基类，抽取单例 bean 实例的通用管理逻辑。注意
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} 接口继承了 {@link
 * SingletonBeanRegistry} 接口。
 *
 * <p>注意，与继承它的 {@link AbstractBeanFactory} 和 {@link DefaultListableBeanFactory} 不同，该类既不假定存在 bean
 * 定义概念， 也不假定 bean 实例有特定的创建过程。它也可以作为嵌套辅助对象被委托使用。
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry
    implements SingletonBeanRegistry {

  /** 要保留的被抑制异常的最大数量。 */
  private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;

  /** 单例对象缓存：bean 名称到 bean 实例。 */
  private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

  /** 单例工厂缓存：bean 名称到 ObjectFactory。 */
  private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

  /** 早期单例对象缓存：bean 名称到 bean 实例。 */
  private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

  /** 已注册单例的集合，按注册顺序保存 bean 名称。 */
  private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

  /** 当前正在创建的 bean 名称。 */
  private final Set<String> singletonsCurrentlyInCreation =
      Collections.newSetFromMap(new ConcurrentHashMap<>(16));

  /** 当前从创建中检查排除的 bean 名称。 */
  private final Set<String> inCreationCheckExclusions =
      Collections.newSetFromMap(new ConcurrentHashMap<>(16));

  /** 被抑制异常的集合，可用于关联相关原因。 */
  @Nullable private Set<Exception> suppressedExceptions;

  /** 标记当前是否处于 destroySingletons 执行过程中。 */
  private boolean singletonsCurrentlyInDestruction = false;

  /** 可销毁 bean 实例：bean 名称到可销毁实例。 */
  private final Map<String, DisposableBean> disposableBeans = new LinkedHashMap<>();

  /** 包含关系映射：bean 名称到该 bean 所包含的 bean 名称集合。 */
  private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

  /** 依赖 bean 名称映射：bean 名称到依赖该 bean 的 bean 名称集合。 */
  private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

  /** 被依赖 bean 名称映射：bean 名称到该 bean 所依赖的 bean 名称集合。 */
  private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);

  @Override
  public void registerSingleton(String beanName, Object singletonObject)
      throws IllegalStateException {
    Assert.notNull(beanName, "Bean name must not be null");
    Assert.notNull(singletonObject, "Singleton object must not be null");
    synchronized (this.singletonObjects) {
      Object oldObject = this.singletonObjects.get(beanName);
      if (oldObject != null) {
        throw new IllegalStateException(
            "Could not register object ["
                + singletonObject
                + "] under bean name '"
                + beanName
                + "': there is already object ["
                + oldObject
                + "] bound");
      }
      addSingleton(beanName, singletonObject);
    }
  }

  /**
   * 将给定的单例对象添加到该工厂的单例缓存中。
   *
   * <p>用于单例的提前注册。
   *
   * @param beanName bean 的名称
   * @param singletonObject 单例对象
   */
  protected void addSingleton(String beanName, Object singletonObject) {
    synchronized (this.singletonObjects) {
      this.singletonObjects.put(beanName, singletonObject);
      this.singletonFactories.remove(beanName);
      this.earlySingletonObjects.remove(beanName);
      this.registeredSingletons.add(beanName);
    }
  }

  /**
   * 添加给定的单例工厂，以便在必要时构建指定的单例。
   *
   * <p>用于单例的提前注册，例如为了能够解析循环引用。
   *
   * @param beanName bean 的名称
   * @param singletonFactory 单例对象的工厂
   */
  protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
    Assert.notNull(singletonFactory, "Singleton factory must not be null");
    synchronized (this.singletonObjects) {
      if (!this.singletonObjects.containsKey(beanName)) {
        this.singletonFactories.put(beanName, singletonFactory);
        this.earlySingletonObjects.remove(beanName);
        this.registeredSingletons.add(beanName);
      }
    }
  }

  @Override
  @Nullable
  public Object getSingleton(String beanName) {
    return getSingleton(beanName, true);
  }

  /**
   * 返回以给定名称注册的（原始）单例对象。
   *
   * <p>检查已经实例化的单例，也允许获取当前正在创建的单例的早期引用 （用于解析循环引用）。
   *
   * @param beanName 要查找的 bean 名称
   * @param allowEarlyReference 是否应当创建早期引用
   * @return 已注册的单例对象；如果未找到则返回 {@code null}
   */
  @Nullable
  protected Object getSingleton(String beanName, boolean allowEarlyReference) {
    // 不加完整单例锁，快速检查现有实例
    Object singletonObject = this.singletonObjects.get(beanName);
    if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
      singletonObject = this.earlySingletonObjects.get(beanName);
      if (singletonObject == null && allowEarlyReference) {
        synchronized (this.singletonObjects) {
          // 在完整单例锁内一致地创建早期引用
          singletonObject = this.singletonObjects.get(beanName);
          if (singletonObject == null) {
            singletonObject = this.earlySingletonObjects.get(beanName);
            if (singletonObject == null) {
              ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
              if (singletonFactory != null) {
                singletonObject = singletonFactory.getObject();
                this.earlySingletonObjects.put(beanName, singletonObject);
                this.singletonFactories.remove(beanName);
              }
            }
          }
        }
      }
    }
    return singletonObject;
  }

  /**
   * 返回以给定名称注册的（原始）单例对象； 如果尚未注册，则创建并注册一个新的单例对象。
   *
   * @param beanName bean 的名称
   * @param singletonFactory 必要时用于延迟创建单例的 ObjectFactory
   * @return 已注册的单例对象
   */
  public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
    Assert.notNull(beanName, "Bean name must not be null");
    synchronized (this.singletonObjects) {
      Object singletonObject = this.singletonObjects.get(beanName);
      if (singletonObject == null) {
        if (this.singletonsCurrentlyInDestruction) {
          throw new BeanCreationNotAllowedException(
              beanName,
              "Singleton bean creation not allowed while singletons of this factory are in destruction "
                  + "(Do not request a bean from a BeanFactory in a destroy method implementation!)");
        }
        if (logger.isDebugEnabled()) {
          logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
        }
        beforeSingletonCreation(beanName);
        boolean newSingleton = false;
        boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
        if (recordSuppressedExceptions) {
          this.suppressedExceptions = new LinkedHashSet<>();
        }
        try {
          singletonObject = singletonFactory.getObject();
          newSingleton = true;
        } catch (IllegalStateException ex) {
          // 期间单例对象是否已经隐式出现？
          // 如果是，则继续使用它，因为该异常表明了这种状态。
          singletonObject = this.singletonObjects.get(beanName);
          if (singletonObject == null) {
            throw ex;
          }
        } catch (BeanCreationException ex) {
          if (recordSuppressedExceptions) {
            for (Exception suppressedException : this.suppressedExceptions) {
              ex.addRelatedCause(suppressedException);
            }
          }
          throw ex;
        } finally {
          if (recordSuppressedExceptions) {
            this.suppressedExceptions = null;
          }
          afterSingletonCreation(beanName);
        }
        if (newSingleton) {
          addSingleton(beanName, singletonObject);
        }
      }
      return singletonObject;
    }
  }

  /**
   * 注册在创建单例 bean 实例期间被抑制的异常， 例如临时的循环引用解析问题。
   *
   * <p>默认实现会将任何给定异常保存到该注册表的被抑制异常集合中， 最多保存 100 个异常，并将它们作为相关原因添加到最终的顶层 {@link BeanCreationException}。
   *
   * @param ex 要注册的异常
   * @see BeanCreationException#getRelatedCauses()
   */
  protected void onSuppressedException(Exception ex) {
    synchronized (this.singletonObjects) {
      if (this.suppressedExceptions != null
          && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
        this.suppressedExceptions.add(ex);
      }
    }
  }

  /**
   * 从该工厂的单例缓存中移除给定名称的 bean， 以便在创建失败时清理单例的提前注册。
   *
   * @param beanName bean 的名称
   * @see #getSingletonMutex()
   */
  protected void removeSingleton(String beanName) {
    synchronized (this.singletonObjects) {
      this.singletonObjects.remove(beanName);
      this.singletonFactories.remove(beanName);
      this.earlySingletonObjects.remove(beanName);
      this.registeredSingletons.remove(beanName);
    }
  }

  @Override
  public boolean containsSingleton(String beanName) {
    return this.singletonObjects.containsKey(beanName);
  }

  @Override
  public String[] getSingletonNames() {
    synchronized (this.singletonObjects) {
      return StringUtils.toStringArray(this.registeredSingletons);
    }
  }

  @Override
  public int getSingletonCount() {
    synchronized (this.singletonObjects) {
      return this.registeredSingletons.size();
    }
  }

  public void setCurrentlyInCreation(String beanName, boolean inCreation) {
    Assert.notNull(beanName, "Bean name must not be null");
    if (!inCreation) {
      this.inCreationCheckExclusions.add(beanName);
    } else {
      this.inCreationCheckExclusions.remove(beanName);
    }
  }

  public boolean isCurrentlyInCreation(String beanName) {
    Assert.notNull(beanName, "Bean name must not be null");
    return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
  }

  protected boolean isActuallyInCreation(String beanName) {
    return isSingletonCurrentlyInCreation(beanName);
  }

  /**
   * 返回指定的单例 bean 当前是否正在创建中（在整个工厂范围内）。
   *
   * @param beanName bean 的名称
   */
  public boolean isSingletonCurrentlyInCreation(@Nullable String beanName) {
    return this.singletonsCurrentlyInCreation.contains(beanName);
  }

  /**
   * 单例创建前的回调。
   *
   * <p>默认实现会将该单例注册为当前正在创建中。
   *
   * @param beanName 即将创建的单例名称
   * @see #isSingletonCurrentlyInCreation
   */
  protected void beforeSingletonCreation(String beanName) {
    if (!this.inCreationCheckExclusions.contains(beanName)
        && !this.singletonsCurrentlyInCreation.add(beanName)) {
      throw new BeanCurrentlyInCreationException(beanName);
    }
  }

  /**
   * 单例创建后的回调。
   *
   * <p>默认实现会将该单例标记为不再处于创建中。
   *
   * @param beanName 已经创建的单例名称
   * @see #isSingletonCurrentlyInCreation
   */
  protected void afterSingletonCreation(String beanName) {
    if (!this.inCreationCheckExclusions.contains(beanName)
        && !this.singletonsCurrentlyInCreation.remove(beanName)) {
      throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
    }
  }

  /**
   * 将给定 bean 添加到该注册表的可销毁 bean 列表中。
   *
   * <p>可销毁 bean 通常对应已注册的单例，bean 名称相同， 但可能是不同的实例（例如，不自然实现 Spring 的 DisposableBean 接口的单例所对应的
   * DisposableBean 适配器）。
   *
   * @param beanName bean 的名称
   * @param bean bean 实例
   */
  public void registerDisposableBean(String beanName, DisposableBean bean) {
    synchronized (this.disposableBeans) {
      this.disposableBeans.put(beanName, bean);
    }
  }

  /**
   * 注册两个 bean 之间的包含关系， 例如内部 bean 与包含它的外部 bean 之间的关系。
   *
   * <p>同时会在销毁顺序上，将包含方 bean 注册为依赖被包含方 bean。
   *
   * @param containedBeanName 被包含的（内部）bean 名称
   * @param containingBeanName 包含方的（外部）bean 名称
   * @see #registerDependentBean
   */
  public void registerContainedBean(String containedBeanName, String containingBeanName) {
    synchronized (this.containedBeanMap) {
      Set<String> containedBeans =
          this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
      if (!containedBeans.add(containedBeanName)) {
        return;
      }
    }
    registerDependentBean(containedBeanName, containingBeanName);
  }

  /**
   * 为给定 bean 注册一个依赖它的 bean， 该依赖 bean 会在给定 bean 销毁之前被销毁。
   *
   * @param beanName bean 的名称
   * @param dependentBeanName 依赖该 bean 的 bean 名称
   */
  public void registerDependentBean(String beanName, String dependentBeanName) {
    String canonicalName = canonicalName(beanName);

    synchronized (this.dependentBeanMap) {
      Set<String> dependentBeans =
          this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
      if (!dependentBeans.add(dependentBeanName)) {
        return;
      }
    }

    synchronized (this.dependenciesForBeanMap) {
      Set<String> dependenciesForBean =
          this.dependenciesForBeanMap.computeIfAbsent(
              dependentBeanName, k -> new LinkedHashSet<>(8));
      dependenciesForBean.add(canonicalName);
    }
  }

  /**
   * 判断指定的依赖 bean 是否已被注册为依赖给定 bean， 或依赖给定 bean 的任一传递依赖。
   *
   * @param beanName 要检查的 bean 名称
   * @param dependentBeanName 依赖 bean 的名称
   * @since 4.0
   */
  protected boolean isDependent(String beanName, String dependentBeanName) {
    synchronized (this.dependentBeanMap) {
      return isDependent(beanName, dependentBeanName, null);
    }
  }

  private boolean isDependent(
      String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
    if (alreadySeen != null && alreadySeen.contains(beanName)) {
      return false;
    }
    String canonicalName = canonicalName(beanName);
    Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
    if (dependentBeans == null || dependentBeans.isEmpty()) {
      return false;
    }
    if (dependentBeans.contains(dependentBeanName)) {
      return true;
    }
    if (alreadySeen == null) {
      alreadySeen = new HashSet<>();
    }
    alreadySeen.add(beanName);
    for (String transitiveDependency : dependentBeans) {
      if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 判断是否已为给定名称注册了依赖它的 bean。
   *
   * @param beanName 要检查的 bean 名称
   */
  protected boolean hasDependentBean(String beanName) {
    return this.dependentBeanMap.containsKey(beanName);
  }

  /**
   * 返回所有依赖指定 bean 的 bean 名称（如果有）。
   *
   * @param beanName bean 的名称
   * @return 依赖 bean 名称数组；如果没有则返回空数组
   */
  public String[] getDependentBeans(String beanName) {
    Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
    if (dependentBeans == null) {
      return new String[0];
    }
    synchronized (this.dependentBeanMap) {
      return StringUtils.toStringArray(dependentBeans);
    }
  }

  /**
   * 返回指定 bean 所依赖的所有 bean 名称（如果有）。
   *
   * @param beanName bean 的名称
   * @return 该 bean 所依赖的 bean 名称数组；如果没有则返回空数组
   */
  public String[] getDependenciesForBean(String beanName) {
    Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
    if (dependenciesForBean == null) {
      return new String[0];
    }
    synchronized (this.dependenciesForBeanMap) {
      return StringUtils.toStringArray(dependenciesForBean);
    }
  }

  public void destroySingletons() {
    if (logger.isTraceEnabled()) {
      logger.trace("Destroying singletons in " + this);
    }
    synchronized (this.singletonObjects) {
      this.singletonsCurrentlyInDestruction = true;
    }

    String[] disposableBeanNames;
    synchronized (this.disposableBeans) {
      disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
    }
    for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
      destroySingleton(disposableBeanNames[i]);
    }

    this.containedBeanMap.clear();
    this.dependentBeanMap.clear();
    this.dependenciesForBeanMap.clear();

    clearSingletonCache();
  }

  /**
   * 清除该注册表中的所有已缓存单例实例。
   *
   * @since 4.3.15
   */
  protected void clearSingletonCache() {
    synchronized (this.singletonObjects) {
      this.singletonObjects.clear();
      this.singletonFactories.clear();
      this.earlySingletonObjects.clear();
      this.registeredSingletons.clear();
      this.singletonsCurrentlyInDestruction = false;
    }
  }

  /**
   * 销毁给定的 bean。如果找到对应的可销毁 bean 实例， 则委托给 {@code destroyBean}。
   *
   * @param beanName bean 的名称
   * @see #destroyBean
   */
  public void destroySingleton(String beanName) {
    // 移除给定名称的已注册单例（如果有）。
    removeSingleton(beanName);

    // 销毁对应的 DisposableBean 实例。
    DisposableBean disposableBean;
    synchronized (this.disposableBeans) {
      disposableBean = this.disposableBeans.remove(beanName);
    }
    destroyBean(beanName, disposableBean);
  }

  /**
   * 销毁给定的 bean。必须先销毁依赖该 bean 的 bean，再销毁该 bean 本身。 不应抛出任何异常。
   *
   * @param beanName bean 的名称
   * @param bean 要销毁的 bean 实例
   */
  protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
    // 先触发依赖 bean 的销毁...
    Set<String> dependentBeanNames;
    synchronized (this.dependentBeanMap) {
      // 在完整同步内执行，以保证得到一个已断开连接的 Set
      dependentBeanNames = this.dependentBeanMap.remove(beanName);
    }
    if (dependentBeanNames != null) {
      if (logger.isTraceEnabled()) {
        logger.trace(
            "Retrieved dependent beans for bean '" + beanName + "': " + dependentBeanNames);
      }
      for (String dependentBeanName : dependentBeanNames) {
        destroySingleton(dependentBeanName);
      }
    }

    // 现在实际销毁该 bean...
    if (bean != null) {
      try {
        bean.destroy();
      } catch (Throwable ex) {
        if (logger.isWarnEnabled()) {
          logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
        }
      }
    }

    // 触发被包含 bean 的销毁...
    Set<String> containedBeans;
    synchronized (this.containedBeanMap) {
      // 在完整同步内执行，以保证得到一个已断开连接的 Set
      containedBeans = this.containedBeanMap.remove(beanName);
    }
    if (containedBeans != null) {
      for (String containedBeanName : containedBeans) {
        destroySingleton(containedBeanName);
      }
    }

    // 从其他 bean 的依赖中移除已销毁的 bean。
    synchronized (this.dependentBeanMap) {
      for (Iterator<Map.Entry<String, Set<String>>> it =
              this.dependentBeanMap.entrySet().iterator();
          it.hasNext(); ) {
        Map.Entry<String, Set<String>> entry = it.next();
        Set<String> dependenciesToClean = entry.getValue();
        dependenciesToClean.remove(beanName);
        if (dependenciesToClean.isEmpty()) {
          it.remove();
        }
      }
    }

    // 移除已销毁 bean 的预备依赖信息。
    this.dependenciesForBeanMap.remove(beanName);
  }

  /**
   * 向子类和外部协作者暴露单例互斥锁。
   *
   * <p>如果子类执行任何形式的扩展单例创建阶段，应当在给定 Object 上同步。 特别是，子类<i>不应</i>让自己的互斥锁参与单例创建， 以避免在延迟初始化场景中出现潜在死锁。
   */
  @Override
  public final Object getSingletonMutex() {
    return this.singletonObjects;
  }
}
