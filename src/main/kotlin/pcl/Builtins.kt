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
    }.map { member ->
        BuiltinFunction(
            member.name,
            member.parameters.associateBy { it.name!! }.mapValues { (_, param) ->
                val paramType = param.type.classifier!! as? KClass<*>
                    ?: throw IllegalStateException()
                when (paramType) {
                    typeOf<Double>() -> StackValue.Number::class
                    typeOf<String>() -> StackValue.String::class
                    typeOf<Function>() -> StackValue.Function::class
                    else -> error("Builtin function ${member.name} has invalid type ${paramType.qualifiedName} for parameter ${param.name}!")
                }
            },
            member as KFunction<List<StackValue<*>>>
        )
    }.groupBy { it.name }
}

data class BuiltinFunction(val name: String, val argTypes: Map<String, KClass<out StackValue<*>>>, val body: KFunction<List<StackValue<*>>>)