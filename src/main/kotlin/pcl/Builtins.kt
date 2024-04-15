package pcl

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KCallable
import kotlin.reflect.typeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.isSubclassOf

object Builtins {
    @Suppress("UNCHECKED_CAST")
    val builtins = Builtins::class.memberFunctions.filter {
        it.returnType == typeOf<List<StackValue<*>>>()
    }.groupBy { it.name }.mapValues { (name, overloads) ->
        val takesCallingFunction = overloads.first().parameters.first().name!! == "callingFunction"
        val arity = overloads.first().parameters.size
        if (takesCallingFunction) {
            check(overloads.all { it.parameters.first().let { it.name!! == "callingFunction" && it.type == typeOf<Function>() } }) {
                "All overloads for builtin function $name must take a callingFunction parameter if any one does!"
            }
        }
        check(overloads.all { it.parameters.size == arity }) {
            "All overloads for builtin function $name must have the same number of parameters!"
        }
        BuiltinFunction(
            name, arity, takesCallingFunction,
            overloads.map { overload ->
                overload.parameters.map { param ->
                    val paramType = param.type.classifier!! as? KClass<*>
                        ?: throw IllegalStateException()
                    when (paramType) {
                        typeOf<Double>() -> StackValue.Number::class
                        typeOf<String>() -> StackValue.String::class
                        typeOf<Function>() -> StackValue.Function::class
                        else -> error("Builtin function ${overload.name} has invalid type ${paramType.qualifiedName} for parameter ${param.name}!")
                    }
                } to overload as KFunction<List<StackValue<*>>>
            }.toMap()
        )
    }
}

data class BuiltinFunction(val name: String, val arity: Int, val takesCallingFunction: Boolean, val overloads: Map<List<KClass<out StackValue<Any>>>, KFunction<List<StackValue<*>>>>)