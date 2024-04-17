package pcl

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KCallable
import kotlin.reflect.typeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.valueParameters

internal annotation class Doc(val doc: String)

internal class BuiltinRuntimeError(message: String) : Exception(message)

object Builtins {
    // Math
    fun add(a: Double, b: Double) = listOf(StackValue.Number(a + b))
    fun sub(a: Double, b: Double) = listOf(StackValue.Number(a - b))
    fun div(a: Double, b: Double) = listOf(StackValue.Number(a / b))
    fun mul(a: Double, b: Double) = listOf(StackValue.Number(a * b))
    fun mod(a: Double, b: Double) = listOf(StackValue.Number(a.mod(b)))

    @Doc("Push every number in [from, to] (inclusive)")
    fun range(from: Double, to: Double) = (from.toInt()..to.toInt()).toList().map { StackValue.Number(it.toDouble()) }


    // Logic
    fun min(a: Double, b: Double) = listOf(StackValue.Number(listOf(a, b).min()))
    fun max(a: Double, b: Double) = listOf(StackValue.Number(listOf(a, b).max()))

    @Doc("Push 1 if <value> is truthy, else 0")
    fun truthify(value: StackValue<*>) = listOf(StackValue.Number(if (truthy(value)) 1.0 else 0.0))

    @Doc("If <which> is truthy, push <a>; else, push <b>")
    fun switchpush(which: StackValue<*>, a: StackValue<*>, b: StackValue<*>) = listOf(
        if (truthy(which)) {
            a
        } else {
            b
        }
    )


    // String manipulation
    @Doc("Concatenate two strings")
    fun add(a: String, b: String) = listOf(StackValue.Str(a + b))

    @Doc("Push the length of a string")
    fun length(string: String) = listOf(StackValue.Number(string.length.toDouble()))


    // Functions
    @Doc("Call a function")
    fun call(ctx: CallContext, function: List<Node>) = Function(function, mutableListOf(), ctx.function).let {
        Interpreter.run(it)
        it.stack
    }

    // Control flow
    @Doc("Call a function once for every value on the stack")
    fun map(ctx: CallContext, function: List<Node>) = ctx.function.stack.toList().flatMap {
        Function(function, mutableListOf(it), ctx.function).also {
            Interpreter.run(it)
        }.stack
    }

    // Stack manipulation
    @Doc("Pop a value from the parent stack and push it to this stack")
    fun take(ctx: CallContext) = ctx.function.parent?.stack?.pop(1)
        ?: throw BuiltinRuntimeError("Cannot call take without a parent function")

    @Doc("Duplicate the top value")
    fun dup(ctx: CallContext) = listOf(ctx.function.stack.last())
    
    @Doc("Drop the top value")
    fun drop(ctx: CallContext) = listOf<StackValue<*>>().also {
        ctx.function.stack.pop(1)
    }

    @Doc("Rotate the stack. Positive <n> rotates left, negative rotates right.")
    fun rot(ctx: CallContext, n: Double) = listOf<StackValue<*>>().also {
        val rotated = ctx.function.stack.rotate(n.toInt())
        ctx.function.stack.clear()
        ctx.function.stack.addAll(rotated)
    } 

    @Doc("Drop every value except for the top")
    fun keeplast(ctx: CallContext) = listOf(ctx.function.stack.last()).also {
        ctx.function.stack.clear()
    }

    @Suppress("UNCHECKED_CAST")
    val builtins = Builtins::class.memberFunctions.filter {
        it.returnType.isSubtypeOf(typeOf<List<StackValue<*>>>())
    }.groupBy { it.name }.mapValues { (name, overloads) ->
        val takesContext = overloads.first().valueParameters.first().name!! == "ctx"
        val arity = overloads.first().valueParameters.size
        if (takesContext) {
            check(overloads.all { it.valueParameters.first().let { it.name!! == "ctx" && it.type == typeOf<CallContext>() } }) {
                "All overloads for builtin function $name must take a ctx parameter if any one does!"
            }
        }
        check(overloads.all { it.valueParameters.size == arity }) {
            "All overloads for builtin function $name must have the same number of parameters!"
        }
        BuiltinFunction(
            name, arity - if (takesContext) 1 else 0, takesContext,
            overloads.map { overload ->
                BuiltinFunction.Overload(
                    overload.valueParameters.drop(if (takesContext) 1 else 0).map { param ->
                        val paramType = param.type
                        when (paramType) {
                            typeOf<Double>() -> StackValue.Number::class
                            typeOf<String>() -> StackValue.Str::class
                            typeOf<List<Node>>() -> StackValue.Function::class
                            typeOf<StackValue<*>>() -> Any::class
                            else -> error("Builtin function ${overload.name} has invalid type ${paramType} for parameter ${param.name}!")
                        }
                    },
                    overload as KFunction<List<StackValue<*>>>,
                    (overload.annotations.singleOrNull { it is Doc } as Doc?)?.doc
                )
            }
        )
    }
}

data class CallContext(val function: Function, val node: Node.Identifier)

data class BuiltinFunction(val name: String, val arity: Int, val takesContext: Boolean, val overloads: List<Overload>) {
    data class Overload(val argTypes: List<KClass<*>>, val impl: KFunction<List<StackValue<*>>>, val doc: String?)
}