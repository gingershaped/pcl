package pcl

import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle

fun highlight(source: String) = AttributedStringBuilder().apply {
    val tokens = Parser.tokenize(source)
    for (token in tokens) {
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
                style { it.foreground(AttributedStyle.BRIGHT + AttributedStyle.RED) }
            }
            else -> {
                style { AttributedStyle.DEFAULT }
            }
        }
        append(source.substring(token.range))
    }
}.toAttributedString()

fun highlightStack(stack: List<StackValue<*>>) = AttributedStringBuilder().apply {
    if (stack.isEmpty()) {
        style { AttributedStyle.DEFAULT.faint() }
        append("stack is empty")
    } else {
        for ((index, item) in stack.withIndex()) {
            when (item) {
                is StackValue.Number -> {
                    style(numberStyle)
                    append(item.value.toString())
                }
                is StackValue.Str -> {
                    style(stringStyle)
                    append('"')
                    append(item.value)
                    append('"')
                }
                is StackValue.Function -> {
                    style(functionStyle)
                    append("{ ")
                    style { AttributedStyle.DEFAULT }
                    append(highlight(item.value.sourceify()))
                    style(functionStyle)
                    append(" }")
                }
            }
            if (index != stack.lastIndex) {
                append("\n")
            }
        }
    }
}.toAttributedString()