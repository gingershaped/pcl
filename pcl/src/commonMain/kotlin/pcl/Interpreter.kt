package pcl


interface Environment {
    fun print(text: String)
    fun input(): String
}

class Interpreter(val environment: Environment) {
    fun run(body: List<Node>, stack: List<StackValue<*>>? = null): List<StackValue<*>> {
        return (stack?.toMutableList() ?: mutableListOf<StackValue<*>>()).also {
            run(Function(body, it, null))
        }
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

                    val builtin: BuiltinFunction = builtinsMap[builtinName]
                        ?: throw BuiltinException(node.range, function, "Cannot call unknown builtin ${builtinName}!")

                    if (function.stack.size < if (conditional) builtin.arity + 1 else builtin.arity) {
                        throw BuiltinException(node.range, function,
                            "Cannot call"
                            + if (conditional) "(conditional) builtin" else "builtin"
                            + builtin.name
                            + "with ${function.stack.size}"
                            + if (function.stack.size == 1) "argument" else "arguments"
                            + "on the stack!"
                            + if (conditional) "(needs at least ${builtin.arity}, plus a value to check for truthiness)" else "(needs at least ${builtin.arity})"
                        )
                    }

                    if (conditional && !truthy(function.stack.pop(1).single())) {
                        continue
                    }
                    val argValues = function.stack.pop(builtin.arity)
                    try {
                        val argTypes = argValues.map { it::class }
                        val overload = builtin.overloads.singleOrNull {
                            it.argTypes.zip(argTypes).all { (expected, actual) ->
                                expected == StackValue::class || expected == actual
                            }
                        } ?: throw BuiltinException(node.range, function, "Builtin ${builtin.name} has no overload for ${if (argTypes.size == 1) "argument" else "arguments"} of type (${argTypes.map { it.simpleName }.joinToString(", ")})!")

                        overload.runCatching { impl(CallContext(this@Interpreter, function, node), argValues) }.onFailure {
                            when (it) {
                                is BuiltinRuntimeError -> throw BuiltinException(node.range, function, it.message!!)
                                is PclException -> throw it
                                else -> throw BuiltinBorkedException(node.range, function, "An internal exception occured in builtin ${node.name}!", it as? Exception)
                            }
                        }.onSuccess {
                            function.stack.addAll(it)
                        }
                    } catch (e: PclException) {
                        function.stack.addAll(argValues)
                        throw e
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

data class Function(val body: List<Node>, val stack: MutableList<StackValue<*>>, val parent: Parent?) {
    constructor(body: List<Node>, stack: MutableList<StackValue<*>>, parentFunction: Function, invokedAt: Node)
        : this(body, stack, Parent(parentFunction, invokedAt))
    data class Parent(val function: Function, val childInvokedAt: Node)

    fun unwind(): List<Function> = if (parent != null) parent.function.unwind() + this else listOf(this)
}

sealed class StackValue<out T> {
    abstract val value: T
    data class Number(override val value: Double) : StackValue<Double>()
    data class Str(override val value: String) : StackValue<String>()
    data class Function(override val value: List<Node>): StackValue<List<Node>>()
}