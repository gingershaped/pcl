package pcl

import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle

open class PclException(val range: IntRange, message: String, cause: Exception? = null) : Exception(message, cause) {
    override fun toString() = "${this::class.simpleName}: $message"

    fun highlight(source: String) = AttributedStringBuilder().apply {
        style { it.foreground(AttributedStyle.RED) }
        appendLine("At position ${range.start + 1}:")
        style { AttributedStyle.DEFAULT }
        appendLine(pcl.highlight(source))
        style { AttributedStyle.DEFAULT.bold() }
        appendLine(" ".repeat(range.start) + "^".repeat((range.endInclusive - range.start) + 1))
    }
}

open class PclRuntimeException(range: IntRange, val function: Function, message: String, cause: Exception? = null) : PclException(range, message, cause)

class ParseException(range: IntRange, message: String) : PclException(range, message)
class BuiltinException(range: IntRange, function: Function, message: String) : PclRuntimeException(range, function, message)
class BuiltinBorkedException(range: IntRange, function: Function, message: String, cause: Exception?) : PclRuntimeException(range, function, message, cause)
class ValueException(range: IntRange, message: String) : PclException(range, message)
class TypeException(range: IntRange, message: String) : PclException(range, message)