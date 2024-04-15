package pcl

class Interpreter {
    fun run(body: List<Node>) {
        run(Function(body, mutableListOf(), null))
    }
    fun run(function: Function) {
        
    }
}

data class Function(val body: List<Node>, val stack: MutableList<StackValue<*>>, val parent: Function?)

sealed class StackValue<out T> {
    abstract val value: T
    data class Number(override val value: Double) : StackValue<Double>()
    data class String(override val value: String) : StackValue<String>()
    data class Function(override val value: pcl.Function): StackValue<pcl.Function>()
}