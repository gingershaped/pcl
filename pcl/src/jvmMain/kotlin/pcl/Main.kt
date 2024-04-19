package pcl

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.inputStream
import kotlin.io.reader
import kotlin.io.path.readText
import kotlin.system.exitProcess
import kotlin.text.lowercase
import java.nio.file.Paths
import java.nio.file.Files
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.jline.reader.LineReader

internal class JlineTerminalEnvironment(val terminal: Terminal) : Environment {
    private val writer = terminal.writer()
    private val reader = terminal.reader().buffered()
    override fun print(text: String) = writer.println(text)
    override fun input(): String = reader.readLine()
}

class PCL : CliktCommand() {
    private val source: String? by argument(help = "The script to run.")
        .optional()
        .validate { path ->
            if (loadFile) {
                with (context.localization) {
                    Paths.get(path).also {
                        if (!Files.exists(it)) fail(pathDoesNotExist(pathTypeFile(), it.toString()))
                        if (Files.isDirectory(it)) fail(pathIsDirectory(pathTypeFile(), it.toString()))
                        if (!Files.isReadable(it)) fail(pathIsNotReadable(pathTypeFile(), it.toString()))
                    }
                }
            }
        }
    private val loadFile by option("-f", "--load-file", help = "Interpret <source> as a file path to read.")
        .flag(default = false)
        .validate {
            if (it && source == null) {
                fail("Source file must be supplied")
            }
        }
    private val stackFormat by option("--stack-format", help = "How to print data left on the stack.")
        .enum<StackFormat> { it.name.lowercase().replace('_', '-') }
        .default(StackFormat.FANCY)
    private val tokenize by option("--tokenize", help = "Don't run the program; instead, print the parsed tokens.")
        .flag(default = false)
        .validate {
            require(!quiet) {
                "--tokenize cannot be used with --quiet"
            }
        }
    private val quiet by option("-q", "--quiet", help = "Suppress all output, including data left on the stack and any errors.")
        .flag(default = false)

    private fun runScript(terminal: Terminal, program: String) {
        val interpreter = Interpreter(JlineTerminalEnvironment(terminal))
        val stack = try {
            interpreter.run(Parser.parse(Parser.tokenize(program)))
        } catch (e: PclException) {
            if (!quiet) {
                e.diagnostic(program).let(AttributedString::fromAnsi).println(terminal)
                terminal.flush()
            }
            throw ProgramResult(-1)
        }
        if (!quiet) {
            when (stackFormat) {
                StackFormat.HIDE -> Unit
                StackFormat.FANCY -> {
                    if (stack.isEmpty()) {
                        AttributedString("empty stack", AttributedStyle.DEFAULT.faint()).println(terminal)
                    } else {
                        for (value in stack) {
                            value.highlight().let(AttributedString::fromAnsi).println(terminal)
                        }
                    }
                }
                else -> println(stack.map { it.value.toString() }.joinToString(stackFormat.sep))
            }
        }
        terminal.flush()
    }
    
    override fun run() {
        val terminal = TerminalBuilder.builder().build()
        val program = if (loadFile) {
            Paths.get(source).readText()
        } else { source }

        if (program != null) {
            if (tokenize) {
                terminal.writer().println(Parser.tokenize(program))
            } else {
                runScript(terminal, program)
            }
        } else {
            repl(terminal)
        }
    }

    enum class StackFormat(val sep: String) {
        FANCY(""), JOIN_SPACES(" "), JOIN_NEWLINES("\n"), JOIN_NULLS("\u0000"), CONCATENATE(""), HIDE("")
    }
}

fun main(args: Array<String>) { PCL().main(args) }