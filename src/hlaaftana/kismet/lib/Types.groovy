package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.*
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetBoolean
import hlaaftana.kismet.vm.KismetNumber

import static hlaaftana.kismet.lib.Functions.*

@CompileStatic
class Types extends NativeModule {
    static final SingleType META_TYPE = new SingleType('Meta', +Type.ANY),
        TYPE_BOUND_TYPE = new SingleType('TypeBound', +META_TYPE)

    static Type inferType(IKismetObject value) {
        if (value instanceof KismetNumber) value.type
        else if (value instanceof Function) Functions.FUNCTION_TYPE//func(Type.NONE, new TupleType(new Type[0]).withVarargs(Type.ANY))
        else if (value instanceof Template) Functions.TEMPLATE_TYPE
        else if (value instanceof TypeChecker) Functions.TYPE_CHECKER_TYPE
        else if (value instanceof Instructor) Functions.INSTRUCTOR_TYPE
        else if (value instanceof TypedTemplate) Functions.TYPED_TEMPLATE_TYPE
        else if (value instanceof Type) new GenericType(Types.META_TYPE, value)
        else throw new UnsupportedOperationException("Cannot infer type for kismet object $value")
    }

    Types() {
        super("types")
        define 'Any', new GenericType(META_TYPE, Type.ANY), Type.ANY
        define 'None', new GenericType(META_TYPE, Type.NONE), Type.NONE
        define 'cast', Functions.TYPE_CHECKER_TYPE, new TypeChecker() {
            @CompileStatic
            TypedExpression transform(TypedContext context, Expression... args) {
                final typ = ((GenericType) args[0].type(context, +META_TYPE).type).arguments[0]
                final expr = args[1].type(context)
                new TypedExpression() {
                    Type getType() { typ }

                    Instruction getInstruction() { expr.instruction }
                }
            }
        }
        define 'null', Type.NONE, Kismet.NULL
        define 'null?', func(Logic.BOOLEAN_TYPE, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(null == args[0] || null == args[0].inner())
            }
        }
        negated 'null?', 'not_null?'
        define META_TYPE
        define '.[]', typedTmpl(META_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = (SingleType) args[0].instruction.evaluate(context)
                final arg = (Type) args[1].instruction.evaluate(context)
                final typ = new GenericType(base, arg)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define '.[]', typedTmpl(META_TYPE, META_TYPE, new TupleType().withVarargs(META_TYPE)), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = (SingleType) args[0].instruction.evaluate(context)
                final arg = (Type[]) args[1].instruction.evaluate(context).inner()
                final typ = new GenericType(base, arg)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'union_type', TYPED_TEMPLATE_TYPE.generic(new TupleType().withVarargs(META_TYPE), META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final types = new Type[args.length]
                for (int i = 0; i < args.length; ++i)
                    types[i] = (Type) args[i].instruction.evaluate(context)
                final typ = new UnionType(types)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'intersection_type', TYPED_TEMPLATE_TYPE.generic(new TupleType().withVarargs(META_TYPE), META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final types = new Type[args.length]
                for (int i = 0; i < args.length; ++i)
                    types[i] = (Type) args[i].instruction.evaluate(context)
                final typ = new IntersectionType(types)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'distinct', typedTmpl(META_TYPE, META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = (Type) args[0].instruction.evaluate(context)
                final typ = new DistinctType(base)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'type_of', typedTmpl(META_TYPE, Type.ANY), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, args[0].type), args[0].type)
            }
        }
        define 'as',  func { IKismetObject... a -> a[0].invokeMethod('as', [a[1].inner()] as Object[]) }
        define 'assignable_to?', typedTmpl(Logic.BOOLEAN_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<KismetBoolean>(Logic.BOOLEAN_TYPE,
                        KismetBoolean.from(unmeta(args[0].type).relation(unmeta(args[1].type)).assignableTo))
            }
        }
        define 'covariant', func(TYPE_BOUND_TYPE, META_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                TypeBound.co((Type) args[0])
            }
        }
        define 'contravariant', func(TYPE_BOUND_TYPE, META_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                TypeBound.contra((Type) args[0])
            }
        }
        define 'invariant', func(TYPE_BOUND_TYPE, META_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                TypeBound.invar((Type) args[0])
            }
        }
        define 'covariant?', func(Logic.BOOLEAN_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((TypeBound) args[0]).variance == TypeBound.Variance.COVARIANT)
            }
        }
        define 'contravariant?', func(Logic.BOOLEAN_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((TypeBound) args[0]).variance == TypeBound.Variance.CONTRAVARIANT)
            }
        }
        define 'invariant?', func(Logic.BOOLEAN_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((TypeBound) args[0]).variance == TypeBound.Variance.INVARIANT)
            }
        }
        define 'get_type', func(META_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((TypeBound) args[0]).type
            }
        }
    }

    static Type unmeta(Type t) {
        t instanceof GenericType && t.base == META_TYPE ? t.arguments[0] : t
    }
}
