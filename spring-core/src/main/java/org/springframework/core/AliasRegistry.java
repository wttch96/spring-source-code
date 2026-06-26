
package org.springframework.core;

/**
 * 别名管理的通用接口，也是
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry} 的上层接口。
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 */
public interface AliasRegistry {

	/**
	 * 根据给定名称注册一个别名。
	 * @param name 规范名称
	 * @param alias 要注册的别名
	 * @throws IllegalStateException 如果该别名已被占用且不能覆盖
	 */
	void registerAlias(String name, String alias);

	/**
	 * 从此注册表中移除指定别名。
	 * @param alias 要移除的别名
	 * @throws IllegalStateException 如果未找到该别名
	 */
	void removeAlias(String alias);

	/**
	 * 判断给定名称是否被定义为别名
	 * （而不是某个已注册组件的名称）。
	 * @param name 要检查的名称
	 * @return 给定名称是否为别名
	 */
	boolean isAlias(String name);

	/**
	 * 返回给定名称对应的别名（如果已定义）。
	 * @param name 要查询别名的名称
	 * @return 别名数组；如果没有则返回空数组
	 */
	String[] getAliases(String name);

}
