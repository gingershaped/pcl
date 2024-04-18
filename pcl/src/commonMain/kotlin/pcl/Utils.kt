package pcl

import kotlin.math.absoluteValue

fun String.escaped() = "\"" + replace("\"", "\\\"") + "\""

fun truthy(value: StackValue<*>) = when(value) {
    is StackValue.Number -> value.value != 0.0
    is StackValue.Str -> value.value.isNotEmpty()
    is StackValue.Function -> true
}

// Copied from https://stackoverflow.com/a/52898902/14743122
// Modified to work on Lists instead of Arrays
fun <T> List<T>.rotate(amount: Int): List<T> =
    if (size < 2 || amount % size == 0) this.toList() else {
        val shiftPoint = if (amount > 0) {
            size - (amount % size)
        } else {
            amount.absoluteValue % size
        }
        slice(shiftPoint until size) + slice(0 until shiftPoint)
    }