package pcl


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
                    val builtin = Builtins.builtins[node.name]
                        ?: throw BuiltinException(node.range, "Cannot call unknown builtin ${node.name}!")
                    if (function.stack.size < builtin.arity) {
                        throw BuiltinException(node.range, "Cannot call builtin ${node.name} with ${function.stack.size} ${if (function.stack.size == 1) "item" else "items"} on the stack! (needs at least ${builtin.arity})")
                    }

                    val args = function.stack.pop(builtin.arity)
                    val argTypes = args.map { it::class }
                    val impl = builtin.overloads[argTypes]?.impl
                        ?: throw BuiltinException(node.range, "Builtin ${builtin.name} has no overload for ${if (argTypes.size == 1) "argument" else "arguments"} of type (${argTypes.map { it.simpleName }.joinToString(", ")})!")
                    val argArray = (listOf(function.takeIf { builtin.takesCallingFunction }) + args.map { it.value }).filterNotNull().toTypedArray()

                    impl.runCatching { call(Builtins, *argArray) }.onFailure {
                        if (it is PclException) {
                            throw it
                        } else {
                            throw BuiltinInvokeException(node.range, "An internal exception occured in builtin ${node.name}!", it as? Exception)
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