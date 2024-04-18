package pcl

import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle

internal val numberStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE)
internal val stringStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
internal val identifierStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.CYAN)
internal val functionStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold()

fun highlightSource(source: String) = AttributedStringBuilder().apply {
    style { AttributedStyle.DEFAULT.foregroundOff() }
    for (token in Parser.tokenize(source)) {
        when (token) {
            is Token.Number -> {
                style(numberStyle)
            }
            is Token.Str -> {
                style(stringStyle)
            }
            is Token.OpenFunction, is Token.CloseFunction -> {
                style(functionStyle)
            }
            is Token.Identifier -> {
                if (token.name.endsWith('?')) {
                    style(identifierStyle.italic())
                } else {
                    style(identifierStyle)
                }
            }
            is Token.Error -> {
                style { AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.RED) }
            }
            else -> {
                style { AttributedStyle.DEFAULT.foregroundOff() }
            }
        }
        append(source.substring(token.range))
    }
}.toAttributedString()

fun StackValue<*>.highlight() = when (this) {
    is StackValue.Number -> AttributedString(value.toString(), numberStyle)
    is StackValue.Str -> AttributedString(value.escaped(), stringStyle)
    is StackValue.Function -> AttributedString("{ function }", functionStyle)
}

fun unwindStack(program: AttributedString, function: Function) = AttributedStringBuilder().apply {
    val callStack = function.unwind().withIndex().reversed()
    val maxDepthLength = callStack.first().index.toString().length
    style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
    appendLine("Traceback:")
    for ((depth, stackFunction) in callStack) {
        style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
        appendLine("  [" + depth.toString().padStart(maxDepthLength, ' ') + "] stack:")
        if (stackFunction.stack.isEmpty()) {
            style(AttributedStyle.DEFAULT.faint())
            appendLine("    empty stack")
        } else {
            style(AttributedStyle.DEFAULT)
            for (value in stackFunction.stack) {
                append("    ")
                appendLine(value.highlight())
            }
        }
        style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
        if (stackFunction.parent != null) {
            val range = stackFunction.parent.childInvokedAt.range
            appendLine("  Called from:")
            append("    ")
            style(AttributedStyle.DEFAULT)
            appendLine(program)
            append("    ")
            style(AttributedStyle.DEFAULT.bold())
            appendLine(" ".repeat(range.start) + "^".repeat((range.endInclusive - range.start) + 1))
        }
    }
}.toAttributedString()


fun PclException.showSourcePosition(source: AttributedString) = AttributedStringBuilder().apply {
    style { it.foreground(AttributedStyle.RED) }
    appendLine("At position ${range.start + 1}:")
    style { AttributedStyle.DEFAULT }
    appendLine(source)
    style { AttributedStyle.DEFAULT.bold() }
    appendLine(" ".repeat(range.start) + "^".repeat((range.endInclusive - range.start) + 1))
}.toAttributedString()

fun PclException.diagnostic(source: String) = AttributedStringBuilder().apply {
    val e = this@diagnostic
    val highlighted = highlightSource(source)
    if (e is PclRuntimeException) {
        appendLine(unwindStack(highlighted, e.function))
    }
    appendLine(e.showSourcePosition(highlighted))
    appendLine(AttributedString(
        if (e is BuiltinBorkedException) { e.stackTraceToString() } else { e.toString() },
        AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold()
    ))
}.toAttributedString()