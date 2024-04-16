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

object Builtins {
    fun add(a: Double, b: Double) = listOf(StackValue.Number(a + b))
    fun sub(a: Double, b: Double) = listOf(StackValue.Number(a - b))
    fun div(a: Double, b: Double) = listOf(StackValue.Number(a / b))
    fun mul(a: Double, b: Double) = listOf(StackValue.Number(a * b))
    fun mod(a: Double, b: Double) = listOf(StackValue.Number(a.mod(b)))

    @Doc("Concatenate two strings")
    fun add(a: String, b: String) = listOf(StackValue.Str(a + b))

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
            name, arity, takesCallingFunction,
            overloads.map { overload ->
                overload.valueParameters.map { param ->
                    val paramType = param.type.classifier!! as? KClass<*>
                        ?: throw IllegalStateException()
                    when (paramType) {
                        Double::class -> StackValue.Number::class
                        String::class -> StackValue.Str::class
                        Function::class -> StackValue.Function::class
                        else -> error("Builtin function ${overload.name} has invalid type ${paramType.qualifiedName} for parameter ${param.name}!")
                    }
                } to BuiltinFunction.Overload(
                    overload as KFunction<List<StackValue<*>>>,
                    (overload.annotations.singleOrNull { it is Doc } as Doc?)?.doc
                )
            }.toMap()
        )
    }
}
data class BuiltinFunction(val name: String, val arity: Int, val takesCallingFunction: Boolean, val overloads: Map<List<KClass<out StackValue<Any>>>, Overload>) {
    data class Overload(val impl: KFunction<List<StackValue<*>>>, val doc: String?)
}