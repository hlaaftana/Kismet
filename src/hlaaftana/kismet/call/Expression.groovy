package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.KismetEvaluationException
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.scope.AssignmentType
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetString
import hlaaftana.kismet.vm.WrapperKismetObject

@CompileStatic
abstract class Expression implements IKismetObject<Expression> {
	int ln, cl
	abstract IKismetObject evaluate(Context c)

	List<Expression> getMembers() { [] }

	int size() { members.size() }

	Expression getAt(int i) { members[i] }

	Expression join(List<Expression> exprs) {
		throw new UnsupportedOperationException("Cannot join exprs $exprs on class ${this.class}")
	}

	Expression percentize(Parser p) {
		new StaticExpression(this, p.context)
	}

	Expression inner() { this }

	String repr() { "expr(${this.class})" }

	String toString() { repr() }
}

@CompileStatic
class PathExpression extends Expression {
	Expression root
	List<Step> steps

	PathExpression(Expression root, List<Step> steps) {
		this.root = root
		if (steps.empty) throw new UnexpectedSyntaxException("Path without steps? Root is $root")
		this.steps = steps
	}

	IKismetObject evaluate(Context c) {
		if (null == root || root instanceof NoExpression) {
			new PathFunction(c, steps)
		} else {
			applySteps(c, root.evaluate(c), steps)
		}
	}

	static class PathFunction extends Function {
		Context context
		List<Step> steps

		PathFunction(Context context, List<Step> steps) {
			this.context = context
			this.steps = steps
		}

		IKismetObject call(IKismetObject... args) {
			applySteps(context, args[0], steps)
		}
	}

	static IKismetObject applySteps(Context c, IKismetObject object, List<Step> steps) {
		for (step in steps) object = step.get(c, object)
		object
	}

	String repr() { root.repr() + steps.join('') }

	List<Expression> getMembers() {
		def result = new ArrayList<Expression>(steps.size() + 1)
		result.add(root)
		for (def s: steps) result.add(s.asExpr())
		result
	}

	Expression join(List<Expression> m) {
		if (m.size() == 1) return m.get(0)
		def result = new ArrayList<Step>(m.size() - 1)
		final s = steps
		assert s.size() == m.size(), "Members must be same size as joined expressions"
		for (int i = 1; i < m.size(); ++i) {
			result.add(s.get(i - 1).borrow(m.get(i)))
		}
		new PathExpression(m[0], result)
	}

	interface Step {
		IKismetObject get(Context c, IKismetObject object)
		IKismetObject set(Context c, IKismetObject object, IKismetObject value)
		Expression asExpr()
		Step borrow(Expression expr)
	}

	static class PropertyStep implements Step {
		String name

		PropertyStep(String name) {
			this.name = name
		}

		IKismetObject get(Context c, IKismetObject object) {
			final n = Function.callOrNull(c, getterName(name), object)
			if (null != n) return n
			Function.tryCall(c, '.property', object, new KismetString(name))
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			final n = Function.callOrNull(c, setterName(name), object, value)
			if (null != n) return n
			Function.tryCall(c, '.property=', object, new KismetString(name), value)
		}

		static String getterName(String prop) {
			def res = new char[prop.length() + 1]
			res[0] = (char) '.'
			prop.getChars(0, prop.length(), res, 1)
			String.valueOf(res)
		}

		static String setterName(String prop) {
			def res = new char[prop.length() + 2]
			res[0] = (char) '.'
			prop.getChars(0, prop.length(), res, 1)
			res[res.length - 1] = (char) '='
			String.valueOf(res)
		}

		String toString() { getterName(name) }
		Expression asExpr() { new NameExpression(name) }
		PropertyStep borrow(Expression expr) { new PropertyStep(expr.toString()) }


	}

	static class SubscriptStep implements Step {
		Expression expression

		SubscriptStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject get(Context c, IKismetObject object) {
			Function.tryCall(c, '.[]', object, expression.evaluate(c))
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			Function.tryCall(c, '.[]=', object, expression.evaluate(c), value)
		}

		String toString() { ".[$expression]" }
		Expression asExpr() { expression }
		SubscriptStep borrow(Expression expr) { new SubscriptStep(expr) }


	}

	static class EnterStep implements Step {
		Expression expression

		EnterStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject get(Context c, IKismetObject object) {
			def ec = new EnterContext(c)
			ec.set('it', ec.object = object)
			expression.evaluate(ec)
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			throw new UnsupportedOperationException('unsupported')
		}

		String toString() { ".{$expression}" }

		@InheritConstructors
		static class EnterContext extends Context {
			IKismetObject object

			IKismetObject get(String name) {
				try {
					super.get(name)
				} catch (UndefinedVariableException ignored) {
					Function.tryCall(this, '.property', object, new KismetString(name))
				}
			}
		}
		Expression asExpr() { expression }
		EnterStep borrow(Expression expr) { new EnterStep(expr) }


	}

	int size() { 1 + steps.size() }

}

@CompileStatic
class OnceExpression extends Expression {
	Expression inner
	IKismetObject value

	OnceExpression(Expression inner) {
		this.inner = inner
	}

	IKismetObject evaluate(Context c) {
		if (null == value) value = inner.evaluate(c)
		value
	}


	List<Expression> getMembers() { [inner] }
	int size() { 1 }
	Expression getAt(int i) { i == 0 ? inner : null }

	Expression join(List<Expression> exprs) {
		new OnceExpression(exprs.get(0))
	}

	String repr() { "once($inner)" }
}

@CompileStatic
class NameExpression extends Expression {
	String text

	NameExpression(String text) { this.text = text }

	IKismetObject evaluate(Context c) {
		c.get(text)
	}

	String repr() { text }

}

@CompileStatic
class DiveExpression extends Expression {
	Expression inner

	DiveExpression(Expression inner) {
		this.inner = inner
	}

	String repr() { "dive[$inner]" }

	IKismetObject evaluate(Context c) {
		c = c.child()
		inner.evaluate(c)
	}

	List<Expression> getMembers() { [inner] }
	int size() { 1 }
	@Override DiveExpression join(List<Expression> a) { new DiveExpression(a[0]) }

}

@CompileStatic
class VariableModifyExpression extends Expression {
	String name
	Expression expression
	AssignmentType type

	VariableModifyExpression(AssignmentType type, String name, Expression expression) {
		this.type = type
		this.name = name
		this.expression = expression
	}


	IKismetObject evaluate(Context c) {
		def v = expression.evaluate(c)
		type.set(c, name, v)
		v
	}
}

@CompileStatic
class BlockExpression extends Expression {
	List<Expression> members

	String repr() {
		'{\n' + members.join('\r\n').readLines().collect('  '.&concat).join('\r\n') + '\r\n}'
	}

	BlockExpression(List<Expression> exprs) { members = exprs }

	IKismetObject evaluate(Context c) {
		IKismetObject a = Kismet.NULL
		for (e in members) a = e.evaluate(c)
		a
	}

	BlockExpression join(List<Expression> exprs) {
		new BlockExpression(exprs)
	}

}

@CompileStatic
class CallExpression extends Expression {
	Expression callValue
	List<Expression> arguments = []

	CallExpression(List<Expression> expressions) {
		if (null == expressions || expressions.empty) return
		setCallValue(expressions[0])
		arguments = expressions.tail()
	}

	CallExpression(Expression... exprs) {
		if (null == exprs || exprs.length == 0) return
		callValue = exprs[0]
		arguments = exprs.tail().toList()
	}

	CallExpression() {}

	String repr() { "call(${members.join(', ')})" }

	CallExpression plus(List<Expression> mem) {
		new CallExpression(members + mem)
	}

	CallExpression plus(CallExpression mem) {
		new CallExpression(members + mem.members)
	}

	Expression getAt(int i) {
		i < 0 ? this[arguments.size() + i + 1] : i == 0 ? callValue : arguments[i - 1]
	}

	IKismetObject evaluate(Context c) {
		if (null == callValue) return Kismet.NULL
		IKismetObject obj = callValue.evaluate(c)
		if (obj.inner() instanceof KismetCallable) {
			((KismetCallable) obj.inner()).call(c, arguments.toArray(new Expression[0]))
		} else {
			final arr = new IKismetObject[arguments.size() + 1]
			arr[0] = obj
			for (int i = 0; i < arr.length; ++i) arr[i+1] = arguments[i].evaluate(c)
			((Function) c.get('call')).call(arr)
		}
	}

	List<Expression> getMembers() {
		def r = new ArrayList<Expression>(1 + arguments.size())
		if (callValue != null) r.add(callValue)
		r.addAll(arguments)
		r
	}

	int size() { arguments.size() + 1 }

	CallExpression join(List<Expression> exprs) {
		new CallExpression(exprs)
	}

}

@CompileStatic
class ListExpression extends Expression {
	List<Expression> members

	ListExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "[${members.join(', ')}]" }

	IKismetObject evaluate(Context c) {
		new WrapperKismetObject(members*.evaluate(c))
	}
}

@CompileStatic
class TupleExpression extends Expression {
	List<Expression> members

	TupleExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "(${members.join(', ')})" }

	IKismetObject evaluate(Context c) {
		def arr = new IKismetObject[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).evaluate(c)
		new WrapperKismetObject(new Tuple<IKismetObject>(arr))
	}
}

@CompileStatic
class SetExpression extends Expression {
	List<Expression> members

	SetExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "{${members.join(', ')}}" }

	IKismetObject evaluate(Context c) {
		def arr = new HashSet<IKismetObject>(members.size())
		for (m in members) arr.add(m.evaluate(c))
		Kismet.model(arr)
	}
}

@CompileStatic
class MapExpression extends Expression {
	List<EqualsAssignExpression> members

	MapExpression(List<EqualsAssignExpression> members) {
		this.members = members
	}

	String repr() { "{#${members.join(', ')}}" }

	IKismetObject evaluate(Context c) {
		def arr = new HashMap<Object, IKismetObject>(members.size())
		for (m in members) arr.put(m.left.evaluate(c).inner(), m.right.evaluate(c))
		Kismet.model(arr)
	}
}

@CompileStatic
class EqualsAssignExpression extends Expression {
	Expression left, right

	EqualsAssignExpression(Expression left, Expression right) {
		this.left = left
		this.right = right
	}

	IKismetObject evaluate(Context c) {
		def value = right.evaluate(c)
		if (left instanceof StringExpression)
			AssignmentType.ASSIGN.set(c, ((StringExpression) left).value.inner(), value)
		else if (left instanceof NameExpression)
			AssignmentType.ASSIGN.set(c, ((NameExpression) left).text, value)
		else if (left instanceof PathExpression) {
			def steps = ((PathExpression) left).steps
			def toApply = steps.init()
			def toSet = steps.last()
			IKismetObject val = PathExpression.applySteps(c, ((PathExpression) left).root.evaluate(c), toApply)
			toSet.set(c, val, value)
		} else throw new UnexpectedSyntaxException("Left hand side of colon $left")
		value
	}


	List<Expression> getMembers() { [left, right] }

	Expression join(List<Expression> exprs) {
		new EqualsAssignExpression(exprs[0], exprs[1])
	}

	Expression getAt(int i) { i == 0 ? left : i == 1 ? right : null }

	int size() { 2 }
	String toString() { "$left = $right" }
}

@CompileStatic
class ConstantExpression<T> extends Expression {
	IKismetObject<T> value

	String repr() { "const($value)" }

	void setValue(T obj) {
		value = Kismet.model(obj)
	}

	IKismetObject<T> evaluate(Context c) {
		value
		//Kismet.model(value.inner())
	}
}

@CompileStatic
class NumberExpression extends ConstantExpression<Number> {
	String repr() { value.toString() }

	NumberExpression() {}

	NumberExpression(boolean type, StringBuilder[] arr) {
		StringBuilder x = arr[0]
		boolean t = false
		if (null != arr[4] && arr[4].length() % 2 == 1) {
			x.insert(0, (char) '-')
		}
		if (null != arr[1]) {
			x.append((char) '.').append(arr[1]); t = true
		}
		if (null != arr[2]) {
			x.append((char) 'e').append(arr[2]); t = true
		}
		String r = x.toString()
		Number v
		if (null == arr[3])
			v = t ? new BigDecimal(r) : new BigInteger(r)
		else if (type) {
			if (arr[3].length() == 0) v = new BigDecimal(r)
			else {
				int b = Integer.valueOf(arr[3].toString())
				if (b == 32) v = new Float(r)
				else if (b == 64) v = new Double(r)
				else throw new NumberFormatException("Invalid number of bits $b for explicit float")
			}
		} else if (t) {
			v = new BigDecimal(r)
			if (arr[3].length() == 0) v = v.toBigInteger()
			else {
				int b = Integer.valueOf(arr[3].toString())
				if (b == 8) v = v.byteValue()
				else if (b == 16) v = v.shortValue()
				else if (b == 32) v = v.intValue()
				else if (b == 64) v = v.longValue()
				else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
			}
		} else if (arr[3].length() == 0) v = new BigInteger(r)
		else {
			int b = Integer.valueOf(arr[3].toString())
			if (b == 8) v = new Byte(r)
			else if (b == 16) v = new Short(r)
			else if (b == 32) v = new Integer(r)
			else if (b == 64) v = new Long(r)
			else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
		}
		setValue(v)
	}

	NumberExpression(Number v) { setValue(v) }

	NumberExpression(String x) {
		Parser.NumberBuilder b = new Parser.NumberBuilder(null)
		char[] a = x.toCharArray()
		for (int i = 0; i < a.length; ++i) b.doPush((int) a[i])
		setValue b.doFinish().value.inner()
	}


	VariableIndexExpression percentize(Parser p) {
		new VariableIndexExpression(value.inner().intValue())
	}

	static class VariableIndexExpression extends Expression {
		int index

		VariableIndexExpression(int index) {
			this.index = index
		}

		@Override
		IKismetObject evaluate(Context c) {
			final x = c.@variables[index]
			if (x) x.value
			else throw new KismetEvaluationException(this, "No variable at index $index")
		}
	}
}

@CompileStatic
class StringExpression extends ConstantExpression<String> {
	String raw
	Exception exception

	String toString() { "\"${StringEscaper.escape(raw)}\"" }

	StringExpression(String v) {
		try {
			setValue(StringEscaper.unescape(raw = v))
		} catch (ex) {
			exception = ex
		}
	}

	NameExpression percentize(Parser p) {
		new NameExpression(raw)
	}

	IKismetObject<String> evaluate(Context c) {
		if (null == exception) value
		else throw exception
	}

}

@CompileStatic
class StaticExpression<T extends Expression> extends ConstantExpression<Object> {
	T expression

	String repr() { expression ? "static[$expression]($value)" : "static($value)" }

	StaticExpression(T ex = null, IKismetObject val) {
		expression = ex
		value = val
	}

	StaticExpression(T ex = null, val) {
		expression = ex
		setValue(val)
	}

	StaticExpression(T ex = null, Context c) {
		this(ex, ex.evaluate(c))
	}

}

@CompileStatic
@Singleton(property = 'INSTANCE')
class NoExpression extends Expression {
	String repr() { "noexpr" }

	IKismetObject evaluate(Context c) {
		Kismet.NULL
	}

}