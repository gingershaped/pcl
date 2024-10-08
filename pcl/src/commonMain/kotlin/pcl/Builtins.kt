package pcl

import kotlin.reflect.KClass

internal class BuiltinRuntimeError(message: String) : Exception(message)

data class CallContext(val interpreter: Interpreter, val function: Function, val currentNode: Node.Identifier)

data class BuiltinFunction(val name: String, val arity: Int, val takesContext: Boolean, val overloads: List<Overload>) {
    data class Overload(val argTypes: List<KClass<*>>, val impl: (ctx: CallContext, arguments: List<StackValue<*>>) -> List<StackValue<*>>, val doc: String?)
}


object Builtins {
    // Math
    fun add(a: Double, b: Double) = listOf(StackValue.Number(a + b))
    fun sub(a: Double, b: Double) = listOf(StackValue.Number(a - b))
    fun div(a: Double, b: Double) = listOf(StackValue.Number(a / b))
    fun mul(a: Double, b: Double) = listOf(StackValue.Number(a * b))
    fun mod(a: Double, b: Double) = listOf(StackValue.Number(a.mod(b)))

    /** Push every number in [from, to] (inclusive) */
    fun range(from: Double, to: Double) = (from.toInt()..to.toInt()).toList().map { StackValue.Number(it.toDouble()) }


    // Logic
    fun not(value: StackValue<*>) = listOf(StackValue.Number(if (!truthy(value)) 1.0 else 0.0))
    fun min(a: Double, b: Double) = listOf(StackValue.Number(listOf(a, b).min()))
    fun max(a: Double, b: Double) = listOf(StackValue.Number(listOf(a, b).max()))

    /** Push 1 if <value> is truthy, else 0 */
    fun truthify(value: StackValue<*>) = listOf(StackValue.Number(if (truthy(value)) 1.0 else 0.0))

    /** If <which> is truthy, push <a>; else, push <b> */
    fun switchpush(which: StackValue<*>, a: StackValue<*>, b: StackValue<*>) = listOf(
        if (truthy(which)) {
            a
        } else {
            b
        }
    )


    // String manipulation
    /** Concatenate two strings */
    fun add(a: String, b: String) = listOf(StackValue.Str(a + b))

    /** Repeat a string */
    fun mul(string: String, amount: Double) = listOf(StackValue.Str(string.repeat(amount.toInt())))

    /** Push the length of a string */
    fun length(string: String) = listOf(StackValue.Number(string.length.toDouble()))


    // Functions
    /** Call a function */
    fun call(ctx: CallContext, function: List<Node>) = Function(function, mutableListOf(), ctx.function, ctx.currentNode).let {
        ctx.interpreter.run(it)
        it.stack
    }

    // Control flow
    /** Apply a transformation to every value on the stack */
    fun map(ctx: CallContext, function: List<Node>) = ctx.function.stack.map {
        Function(function, mutableListOf(it), ctx.function, ctx.currentNode).also {
            ctx.interpreter.run(it)
        }.stack.let {
            it.singleOrNull() ?: if (it.isEmpty()) {
                throw BuiltinRuntimeError("No values returned from map transform")
            } else {
                throw BuiltinRuntimeError("Multiple values returned from map transform: ${it}")
            }
        }
    }.also {
        ctx.function.stack.clear()
    }
    

    // Stack manipulation
    /** Pop a value from the parent stack and push it to this stack */
    fun take(ctx: CallContext) = ctx.function.parent?.function?.stack?.let {
        if (it.isEmpty()) {
            throw BuiltinRuntimeError("Parent stack is empty")
        } else {
            it.pop(1)
        }
    }
        ?: throw BuiltinRuntimeError("Cannot call take without a parent function")

    /** Duplicate the top value */
    fun dup(value: StackValue<*>) = listOf(value, value)
    
    /** Drop the top value */
    @Suppress("UNUSED_PARAMETER")
    fun drop(value: StackValue<*>) = listOf<StackValue<*>>()

    /** Rotate the stack. Positive <n> rotates left, negative rotates right. */
    fun rot(ctx: CallContext, n: Double) = listOf<StackValue<*>>().also {
        val rotated = ctx.function.stack.rotate(n.toInt())
        ctx.function.stack.clear()
        ctx.function.stack.addAll(rotated)
    }

    /** Swap the top two values on the stack */
    fun swap(a: StackValue<*>, b: StackValue<*>) = listOf(b, a)

    /** Drop every value except for the top */
    fun keeplast(ctx: CallContext) = listOf(ctx.function.stack.lastOrNull()).filterNotNull().also {
        ctx.function.stack.clear()
    }

    /** Duplicate the value below the top of the stack */
    fun over(a: StackValue<*>, b: StackValue<*>) = listOf(a, b, a)


    // @Suppress("UNCHECKED_CAST") */
    // val builtins = Builtins::class.memberFunctions.filter {
    //     it.returnType.isSubtypeOf(typeOf<List<StackValue<*>>>())
    // }.groupBy { it.name }.mapValues { (name, overloads) ->
    //     val takesContext = overloads.first().valueParameters.first().name!! == "ctx"
    //     val arity = overloads.first().valueParameters.size
    //     if (takesContext) {
    //         check(overloads.all { it.valueParameters.first().let { it.name!! == "ctx" && it.type == typeOf<CallContext>() } }) {
    //             "All overloads for builtin function $name must take a ctx parameter if any one does!"
    //         }
    //     }
    //     check(overloads.all { it.valueParameters.size == arity }) {
    //         "All overloads for builtin function $name must have the same number of parameters!"
    //     }
    //     BuiltinFunction(
    //         name, arity - if (takesContext) 1 else 0, takesContext,
    //         overloads.map { overload ->
    //             BuiltinFunction.Overload(
    //                 overload.valueParameters.drop(if (takesContext) 1 else 0).map { param ->
    //                     val paramType = param.type
    //                     when (paramType) {
    //                         typeOf<Double>() -> StackValue.Number::class
    //                         typeOf<String>() -> StackValue.Str::class
    //                         typeOf<List<Node>>() -> StackValue.Function::class
    //                         typeOf<StackValue<*>>() -> Any::class
    //                         else -> error("Builtin function ${overload.name} has invalid type ${paramType} for parameter ${param.name}! */
    //                     }
    //                 },
    //                 overload as KFunction<List<StackValue<*>>>,
    //                 (overload.annotations.singleOrNull { it is Doc } as Doc?)?.doc
    //             )
    //         }
    //     )
    // }
}