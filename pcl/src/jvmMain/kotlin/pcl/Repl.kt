package pcl

import com.github.ajalt.mordant.rendering.TextStyles.dim
import java.util.regex.Pattern
import java.util.logging.LogManager
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.lastIndex
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


internal object PCLCompleter : Completer {
    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        for (builtin in builtinsMap.values) {
            for (overload in builtin.overloads) {
                candidates.add(Candidate(
                    builtin.name,
                    buildString {
                        append(identifierStyle(builtin.name))
                        append("(")
                        append(overload.argTypes.map { it.simpleName }.joinToString(", "))
                        append(")")
                    },
                    "builtins",
                    overload.doc ?: dim("<no documentation>"),
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
        return AttributedString.fromAnsi(highlightSource(buffer))
    }
}

fun repl(terminal: Terminal) {
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
    }.println(terminal)

    val interpreter = Interpreter(JlineTerminalEnvironment(terminal))

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
            interpreter.run(Parser.parse(tokens))
        } catch (e: PclException) {
            e.diagnostic(line).let(AttributedString::fromAnsi).println(terminal)
            continue
        }
        if (stack.isEmpty()) {
            AttributedString("stack is empty", AttributedStyle.DEFAULT.faint()).println(terminal)
        } else {
            for (value in stack) {
                value.highlight().let(AttributedString::fromAnsi).println(terminal)
            }
        }
    }
}