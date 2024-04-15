package pcl

open class PclException(val range: IntRange, message: String, cause: Exception? = null) : Exception(message, cause) {
    override fun toString() = "${this::class.simpleName}: $message"
    fun highlight(program: String) =
        program + "\n" + " ".repeat(range.start) + "^" + "~".repeat(range.endInclusive - range.start)
}

class ParseException(range: IntRange, message: String) : PclException(range, message)
class BuiltinException(range: IntRange, message: String) : PclException(range, message)
class BuiltinInvokeException(range: IntRange, message: String, cause: Exception?) : PclException(range, message, cause)
class TypeException(range: IntRange, message: String) : PclException(range, message)