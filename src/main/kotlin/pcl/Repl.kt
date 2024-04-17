package pcl

import org.jline.reader.LineReaderBuilder
import org.jline.reader.LineReader
import org.jline.reader.Highlighter
import org.jline.reader.UserInterruptException
import org.jline.reader.EndOfFileException
import org.jline.reader.Completer
import org.jline.reader.impl.CompletionMatcherImpl
import org.jline.reader.impl.DefaultParser
import org.jline.reader.ParsedLine
import org.jline.reader.Candidate
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import java.util.regex.Pattern
import java.util.logging.LogManager
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.lastIndex


internal object PCLCompleter : Completer {
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        for (builtin in Builtins.builtins.values) {
            for (overload in builtin.overloads) {
                candidates.add(Candidate(
                    builtin.name,
                    AttributedStringBuilder().apply {
                        style(identifierStyle)
                        append(builtin.name)
                        style(AttributedStyle.DEFAULT)
                        append("(")
                        append(overload.argTypes.map { it.simpleName }.joinToString(", "))
                        append(")")
                    }.toAnsi(),
                    "builtins",
                    AttributedStringBuilder().apply {
                        if (overload.doc != null) {
                            append(overload.doc)
                        } else {
                            style(AttributedStyle.DEFAULT.faint())
                            append("<no documentation>")
                        }
                    }.toAnsi(),
                    null, null, true
                ))
            }
        }
    }
}

internal val numberStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE)
internal val stringStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)
internal val identifierStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.CYAN)
internal val functionStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold()

internal object PCLHighlighter : Highlighter {
    private lateinit var errorPattern: Pattern
    private var errorIndex = -1

    override fun setErrorPattern(pattern: Pattern) {
        errorPattern = pattern
    }

    override fun setErrorIndex(index: Int) {
        errorIndex = index
    }

    override fun highlight(reader: LineReader, buffer: String): AttributedString {
        if (buffer.startsWith(':')) {
            return AttributedString(buffer, AttributedStyle.DEFAULT.faint())
        }
        return highlight(buffer)
    }
}

fun repl() {
    Logger.getLogger("org.jline").level = Level.FINEST
    val terminal = TerminalBuilder.builder()
        .build()
    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .highlighter(PCLHighlighter)
        .completer(PCLCompleter)
        .parser(DefaultParser().escapeChars(charArrayOf()))
        .build()

    AttributedStringBuilder().apply {
        style { it.foreground(AttributedStyle.MAGENTA).bold() }
        append("PCL ")
        style { AttributedStyle.DEFAULT }
        append(BuildConfig.VERSION)
        appendLine(".")
        appendLine("Type :help for help.")
    }.println(terminal)

    while (true) {
        val line = try {
            reader.readLine("PCL? ")
        } catch (e: UserInterruptException) {
            continue
        } catch (e: EndOfFileException) {
            terminal.writer().println("\nExiting.")
            terminal.flush()
            break
        }
        val stack = try {
            Interpreter.run(Parser.parse(Parser.tokenize(line)))
        } catch (e: PclException) {
            AttributedStringBuilder().apply {
                appendLine(e.highlight(line))
                style { AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold() }
                if (e is BuiltinBorkedException) {
                    appendLine(e.stackTraceToString())
                } else {
                    appendLine(e.toString())
                }

                if (e is PclRuntimeException) {
                    style { it.boldOff() }
                    appendLine("Traceback:")
                    fun unwind(function: Function) {
                        style { AttributedStyle.DEFAULT.foreground(AttributedStyle.RED) }
                        appendLine("=== Stack:")
                        appendLine(highlightStack(function.stack))
                        style { AttributedStyle.DEFAULT.foreground(AttributedStyle.RED) }
                        if (function.parent != null) {
                            appendLine("=== Called by:")
                            appendLine(highlight(line))
                            style { AttributedStyle.DEFAULT.bold() }
                            val range = function.parent.childInvokedAt.range
                            appendLine(" ".repeat(range.start) + "^".repeat((range.endInclusive - range.start) + 1))
                            unwind(function.parent.function)
                        }
                    }
                    unwind(e.function)
                }

            }.println(terminal)
            continue
        }
        highlightStack(stack).println(terminal)
    }
}