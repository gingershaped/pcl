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
    fun call(callingFunction: Function, function: List<Node>) = Function(function, mutableListOf(), callingFunction).let {
        Interpreter.run(it)
        it.stack
    }


    // Stack manipulation
    @Doc("Pop a value from the parent stack and push it to this stack")
    fun take(callingFunction: Function) = callingFunction.parent?.stack?.pop(1)
        ?: throw BuiltinRuntimeError("Cannot call take without a parent function")

    @Doc("Duplicate the top value")
    fun dup(callingFunction: Function) = listOf(callingFunction.stack.last())
    
    @Doc("Drop the top value")
    fun drop(callingFunction: Function) = listOf<StackValue<*>>().also {
        callingFunction.stack.pop(1)
    }

    @Doc("Drop every value except for the top")
    fun keeplast(callingFunction: Function) = listOf(callingFunction.stack.last()).also {
        callingFunction.stack.clear()
    }

    fun truthy(value: StackValue<*>) = when(value) {
        is StackValue.Number -> value.value != 0.0
        is StackValue.Str -> value.value.isNotEmpty()
        is StackValue.Function -> true
    }

    @Suppress("UNCHECKED_CAST")
    val builtins = Builtins::class.memberFunctions.filter {
        it.returnType.isSubtypeOf(typeOf<List<StackValue<*>>>())
    }.groupBy { it.name }.mapValues { (name, overloads) ->
        val takesCallingFunction = overloads.first().valueParameters.first().name!! == "callingFunction"
        val arity = overloads.first().valueParameters.size
        if (takesCallingFunction) {
            check(overloads.all { it.valueParameters.first().let { it.name!! == "callingFunction" && it.type == typeOf<Function>() } }) {
                "All overloads for builtin function $name must take a callingFunction parameter if any one does!"
            }
        }
        check(overloads.all { it.valueParameters.size == arity }) {
            "All overloads for builtin function $name must have the same number of parameters!"
        }
        BuiltinFunction(
            name, arity - if (takesCallingFunction) 1 else 0, takesCallingFunction,
            overloads.map { overload ->
                BuiltinFunction.Overload(
                    overload.valueParameters.drop(if (takesCallingFunction) 1 else 0).map { param ->
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
data class BuiltinFunction(val name: String, val arity: Int, val takesCallingFunction: Boolean, val overloads: List<Overload>) {
    data class Overload(val argTypes: List<KClass<*>>, val impl: KFunction<List<StackValue<*>>>, val doc: String?)
}