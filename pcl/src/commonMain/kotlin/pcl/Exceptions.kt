package pcl

open class PclException(val range: IntRange, message: String, cause: Exception? = null) : Exception(message, cause) {
    override fun toString() = "${this::class.simpleName}: $message"
}

open class PclRuntimeException(range: IntRange, val function: Function, message: String, cause: Exception? = null) : PclException(range, message, cause)

class ParseException(range: IntRange, message: String) : PclException(range, message)
class BuiltinException(range: IntRange, function: Function, message: String) : PclRuntimeException(range, function, message)
class BuiltinBorkedException(range: IntRange, function: Function, message: String, cause: Exception?) : PclRuntimeException(range, function, message, cause)
class ValueException(range: IntRange, message: String) : PclException(range, message)
class TypeException(range: IntRange, message: String) : PclException(range, message)