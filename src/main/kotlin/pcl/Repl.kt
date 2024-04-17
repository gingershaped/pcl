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
import org.jline.terminal.Terminal
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
        return highlightSource(buffer)
    }
}

fun repl(terminal: Terminal) {
    Logger.getLogger("org.jline").level = Level.FINEST
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
            break
        }
        val tokens = Parser.tokenize(line)
        val stack = try {
            Interpreter.run(Parser.parse(tokens))
        } catch (e: PclException) {
            e.diagnostic(line).println(terminal)
            continue
        }
        if (stack.isEmpty()) {
            AttributedString("stack is empty", AttributedStyle.DEFAULT.faint()).println(terminal)
        } else {
            for (value in stack) {
                value.highlight().println(terminal)
            }
        }
    }
}