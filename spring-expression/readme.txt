List of outstanding things to think about - turn into tickets once distilled to a core set of issues
待考虑的未完成事项列表——在提炼出一组核心问题后再整理成工单

High Importance
高优先级

- In the resolver/executor model we cache executors.  They are currently recorded in the AST and so if the user chooses to evaluate an expression
  在 resolver/executor 模型中，我们会缓存 executor。它们目前记录在 AST 中，因此如果用户选择在不同的上下文中求值一个表达式，
  in a different context then the stored executor may be incorrect.  It may harmless 'fail' which would cause us to retrieve a new one, but
  在不同的上下文中，那么已存储的 executor 可能就不正确。它也许会无害地“失败”，从而让我们获取一个新的 executor，但
  can it do anything malicious? In which case we either need to forget them when the context changes or store them elsewhere.  Should caching be
  它会不会做出任何恶意行为？如果是这样，我们要么在上下文变化时忘记它们，要么把它们存到别处。缓存机制是否应该
  something that can be switched on/off by the context? (shouldCacheExecutors() on the interface?)
  可以由上下文来开启/关闭？（接口上的 `shouldCacheExecutors()`？）

- Expression serialization needs supporting
  需要支持表达式序列化

- expression basic interface and common package.  Should LiteralExpression be settable? should getExpressionString return quoted value?
  表达式基础接口和 common 包。`LiteralExpression` 应该可设置吗？`getExpressionString` 是否应该返回带引号的值？

Low Importance
低优先级

- For the ternary operator, should isWritable() return true/false depending on evaluating the condition and check isWritable() of whichever branch it
  对于三元运算符，`isWritable()` 是否应该根据条件求值结果返回 true/false，并检查它本来会走到的那个分支的 `isWritable()`？
  would have taken?  At the moment ternary expressions are just considered NOT writable.
  目前三元表达式只是被认为是不可写的。

- Enhance type locator interface with direct support for register/unregister imports and ability to set class loader?
  增强 type locator 接口，使其直接支持注册/取消注册导入，以及设置 class loader 的能力？

- Should some of the common errors (like SpelMessages.TYPE_NOT_FOUND) be promoted to top level exceptions?
  是否应该把一些常见错误（例如 `SpelMessages.TYPE_NOT_FOUND`）提升为顶层异常？

- Expression comparison - is it necessary?
  表达式比较——有必要吗？

Syntax
语法

- should the 'is' operator change to 'instanceof' ?
  `'is'` 运算符是否应该改为 `'instanceof'`？

- in this expression we hit the problem of not being able to write chars, since '' always means string:
  在这个表达式里，我们遇到了无法写入 char 的问题，因为 `''` 总是表示字符串：
  evaluate("new java.lang.String('hello').charAt(2).equals('l'.charAt(0))", true, Boolean.class);
  `evaluate("new java.lang.String('hello').charAt(2).equals('l'.charAt(0))", true, Boolean.class);`
  So 'l'.charAt(0) was required - wonder if we can build in a converter for a single length string to char?
  所以才需要 `'l'.charAt(0)`——不知我们是否可以内置一个把单字符字符串转换为 char 的转换器？

- Can't do that as equals take Object and so we don't know to do a cast in order to pass a char into equals
  不能这么做，因为 `equals` 接受的是 `Object`，因此我们不知道是否应该进行强制转换，才能把 char 传给 `equals`。

- We certainly cannot do a cast (unless casts are added to the syntax).  See MethodInvocationTest.testStringClass()
  我们当然不能做强制转换（除非语法中加入 cast）。参见 `MethodInvocationTest.testStringClass()`。

- MATCHES is now the thing that takes a java regex.  What does 'like' do? right now it is the SQL LIKE that supports
  `MATCHES` 现在是用于 Java 正则表达式的运算符。那么 `'like'` 是做什么的？目前它是支持
  wildcards % and _.  It has a poor implementation but I need to know whether to keep it in the language before
  通配符 `%` 和 `_` 的 SQL `LIKE`。它的实现很差，但在修复之前，我需要知道是否要把它保留在语言中。
  fixing that.

- Need to agree on a standard date format for 'default' processing of dates.  Currently it is:
  需要就日期“默认”处理的标准日期格式达成一致。目前它是：
  formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.UK);
  // this is something of this format: "Wed, 4 Jul 2001 12:08:56 GMT"
  // 这类格式类似于：“Wed, 4 Jul 2001 12:08:56 GMT”
  // https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
  // https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html

- See LiteralTests for Date (4,5,6) - should date take an expression rather than be hardcoded in the grammar
  参见 `LiteralTests` 中关于 Date 的（4、5、6）——日期是否应该接收一个表达式，而不是在语法中硬编码
  to take 2 strings only?
  为只接受两个字符串？

- when doing arithmetic, eg. 8.4 / 4  and the user asks for an Integer return type - do we silently coerce or
  当进行算术运算时，例如 `8.4 / 4`，而用户要求返回 `Integer` 类型——我们是静默强制转换，还是
  say we cannot as it won't fit into an int? (see OperatorTests.testMathOperatorDivide04)
  直接说无法转换，因为它放不进 int？（见 `OperatorTests.testMathOperatorDivide04`）

- Is $index within projection/selection useful or just cute?
  projection/selection 中的 `$index` 有用，还是只是花哨？

- All reals are represented as Doubles (so 1.25f is held internally as a double, can be converted to float when required though) - is that ok?
  所有实数都表示为 `Double`（因此 `1.25f` 在内部按 double 保存，不过在需要时可以转换为 float）——这样可以吗？
