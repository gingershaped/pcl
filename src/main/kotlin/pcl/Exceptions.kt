package pcl

open class PclException(val range: IntRange, message: String) : Exception(message) {
    override fun toString() = "${this::class.simpleName}: $message"
    fun highlight(program: String) =
        program + "\n" + " ".repeat(range.start) + "^" + "~".repeat(range.endInclusive - range.start)
}

class ParseException(range: IntRange, message: String) : PclException(range, message)
class TypeException(range: IntRange, message: String) : PclException(range, message)
