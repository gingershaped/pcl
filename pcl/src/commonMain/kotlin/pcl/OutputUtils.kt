package pcl

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.rendering.TextStyle

internal val numberStyle = blue
internal val stringStyle = green
internal val identifierStyle = brightCyan
internal val functionStyle = bold + yellow

fun highlightSource(source: String) = buildString {
    for (token in Parser.tokenize(source)) {
        when (token) {
            is Token.Number -> {
                numberStyle
            }
            is Token.Str -> {
                stringStyle
            }
            is Token.OpenFunction, is Token.CloseFunction -> {
                functionStyle
            }
            is Token.Identifier -> {
                if (token.name.endsWith('?')) {
                    identifierStyle + italic
                } else {
                    identifierStyle
                }
            }
            is Token.Error -> {
                brightRed
            }
            else -> {
                white
            }
        }.let { append(it(source.substring(token.range))) }
    }
}

fun StackValue<*>.highlight() = when (this) {
    is StackValue.Number -> numberStyle(value.toString())
    is StackValue.Str -> stringStyle(value.escaped())
    is StackValue.Function -> functionStyle("{ function }")
}

fun unwindStack(program: String, function: Function) = buildString {
    val callStack = function.unwind().withIndex().reversed()
    val maxDepthLength = callStack.first().index.toString().length
    appendLine(red("Traceback:"))
    for ((depth, stackFunction) in callStack) {
        appendLine(red("  [" + depth.toString().padStart(maxDepthLength, ' ') + "] stack:"))
        if (stackFunction.stack.isEmpty()) {
            appendLine(dim("    empty stack"))
        } else {
            for (value in stackFunction.stack) {
                append("    ")
                appendLine(value.highlight())
            }
        }
        if (stackFunction.parent != null) {
            val range = stackFunction.parent.childInvokedAt.range
            appendLine(red("  Called from:"))
            append("    ")
            appendLine(program)
            append("    ")
            appendLine(bold(" ".repeat(range.start) + "^".repeat((range.endInclusive - range.start) + 1)))
        }
    }
}


fun PclException.showSourcePosition(source: String) = buildString {
    appendLine(red("At position ${range.start + 1}:"))
    appendLine(source)
    appendLine(bold(" ".repeat(range.start) + "^".repeat((range.endInclusive - range.start) + 1)))
}

fun PclException.diagnostic(source: String) = buildString {
    val e = this@diagnostic
    val highlighted = highlightSource(source)
    if (e is PclRuntimeException) {
        appendLine(unwindStack(highlighted, e.function))
    }
    appendLine(e.showSourcePosition(highlighted))
    appendLine((red + bold)(if (e is BuiltinBorkedException) { e.stackTraceToString() } else { e.toString() }))
}