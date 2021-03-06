package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode
class TypeRelation {
	private static final int NONE = 0, SUB = 1, SUPER = 2, EQUAL = 4
	private static final TypeRelation EQ = new TypeRelation(EQUAL, 0), NO = new TypeRelation(NONE, 0)
	int kind, value

	private TypeRelation(int kind, int value) {
		this.kind = kind
		this.value = value
	}

	static TypeRelation subtype(int val) { new TypeRelation(SUB, val) }
	static TypeRelation supertype(int val) { new TypeRelation(SUPER, val) }
	static TypeRelation equal() { EQ }
	static TypeRelation none() { NO }

	static TypeRelation some(int val) {
		if (val == 0) EQ
		else if (val < 0) supertype(val)
		else subtype(val)
	}

	static TypeRelation some(boolean val) {
		val ? EQ : NO
	}

	boolean worse(TypeRelation o) {
		(none && o.kind != NONE) ||
				((isSuper() || sub) && o.kind == kind && value > o.value)
	}

	boolean better(TypeRelation o) {
		(equal && o.kind != EQUAL) ||
				((isSuper() || sub) && o.kind == kind && value < o.value)
	}

	Boolean subber() {
		if (sub) true
		else if (isSuper()) false
		else null
	}

	TypeRelation bitwiseNegate() {
		sub ? supertype(value) : isSuper() ? subtype(value) : this
	}

	int toSome() {
		isSuper() ? -value : value
	}

	boolean isSub() { kind == SUB }
	boolean isSuper() { kind == SUPER }
	boolean isEqual() { kind == EQUAL }
	boolean isNone() { kind == NONE }
	boolean isAssignableFrom() { isSuper() || equal }
	boolean isAssignableTo() { sub || equal }

	String toString() {
		if (none) 'None'
		else if (equal) 'Equal'
		else if (sub) 'Sub(' + value + ')'
		else 'Super(' + value + ')'
	}
}

