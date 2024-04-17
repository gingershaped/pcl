package pcl

import java.lang.reflect.InvocationTargetException
import kotlin.reflect.full.isSuperclassOf


object Interpreter {
    fun run(body: List<Node>): List<StackValue<*>> {
        val stack = mutableListOf<StackValue<*>>()
        run(Function(body, stack, null))
        return stack
    }
    fun run(function: Function) {
        for (node in function.body) {
            when (node) {
                is Node.Identifier -> {
                    val conditional = node.name.endsWith('?')
                    val builtinName = if (conditional) {
                        node.name.dropLast(1)
                    } else {
                        node.name
                    }


                    val builtin = Builtins.builtins[builtinName]
                        ?: throw BuiltinException(node.range, "Cannot call unknown builtin ${builtinName}!")
                    if (function.stack.size < if (conditional) builtin.arity + 1 else builtin.arity) {
                        throw BuiltinException(node.range,
                            "Cannot call"
                            + if (conditional) "(conditional) builtin" else "builtin"
                            + builtin.name
                            + "with ${function.stack.size}"
                            + if (function.stack.size == 1) "argument" else "arguments"
                            + "on the stack!"
                            + if (conditional) "(needs at least ${builtin.arity}, plus a value to check for truthiness)" else "(needs at least ${builtin.arity})"
                        )
                    }
                    val argValues = function.stack.pop(builtin.arity)
                    if (conditional && !Builtins.truthy(function.stack.pop(1).single())) {
                        continue
                    }
                    val argTypes = argValues.map { it::class }
                    val overload = builtin.overloads.singleOrNull {
                        it.argTypes.zip(argTypes).all { (expected, actual) ->
                            expected.isSuperclassOf(actual)
                        }
                    } ?: throw BuiltinException(node.range, "Builtin ${builtin.name} has no overload for ${if (argTypes.size == 1) "argument" else "arguments"} of type (${argTypes.map { it.simpleName }.joinToString(", ")})!")
                    val args = argValues.zip(overload.argTypes).map { (value, type) ->
                        when (type) {
                            Any::class -> value
                            else -> value.value
                        }
                    }
                    val argArray = (listOf(function.takeIf { builtin.takesCallingFunction }) + args).filterNotNull().toTypedArray()

                    overload.impl.runCatching { call(Builtins, *argArray) }.onFailure {
                        when (
                            val error = if (it is InvocationTargetException) {
                                it.cause ?: it
                            } else {
                                it
                            }
                        ) {
                            is BuiltinRuntimeError -> throw BuiltinException(node.range, error.message!!)
                            is PclException -> throw error
                            else -> throw BuiltinBorkedException(node.range, "An internal exception occured in builtin ${node.name}!", error as? Exception)
                        }
                    }.onSuccess {
                        function.stack.addAll(it)
                    }
                }
                is Node.Number -> {
                    function.stack.add(StackValue.Number(node.value))
                }
                is Node.Str -> {
                    function.stack.add(StackValue.Str(node.value))
                }
                is Node.Function -> {
                    function.stack.add(StackValue.Function(node.body))
                }
            }
        }
    }
}

fun <T> MutableList<T>.pop(amount: Int = 1) = (0..<amount).map { removeAt(size - 1) }.reversed()

data class Function(val body: List<Node>, val stack: MutableList<StackValue<*>>, val parent: Function?)

fun Collection<Node>.sourceify(): String = buildList {
    for (node in this@sourceify) {
        when (node) {
            is Node.Number -> {
                add(node.value)
            }
            is Node.Str -> {
                add("\"${node.value}\"")
            }
            is Node.Identifier -> {
                add(node.name)
            }
            is Node.Function -> {
                add("{")
                add(node.body.sourceify())
                add("}")
            }
        }
    }
}.joinToString(" ")

sealed class StackValue<out T> {
    abstract val value: T
    data class Number(override val value: Double) : StackValue<Double>()
    data class Str(override val value: String) : StackValue<String>()
    data class Function(override val value: List<Node>): StackValue<List<Node>>()
}