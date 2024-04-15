package pcl

internal val DIGIT_CHARS = '0'..'9'
internal val WHITESPACE = "\n\t ".toCharArray()

internal fun <T> ListIterator<T>.peekNext() = next().also { previous() } 
internal fun <T> ListIterator<T>.peekPrevious() = previous().also { next() }
internal fun <T> Iterator<T>.nextOrNull() =
    runCatching { next() }.getOrElse {
        if (it is NoSuchElementException) {
            null
        } else {
            throw it
        }
    }

object Parser {
    fun tokenize(program: String) = buildList {
        val tokens = program.withIndex().toList().listIterator()
        for ((position, token) in tokens) {
            when (token) {
                '\'', '"' -> {
                    val endPos: Int
                    buildString {
                        while (true) {
                            try {
                                val (charPos, char) = tokens.next()
                                when (char) {
                                    token -> {
                                        endPos = charPos
                                        break
                                    }
                                    '\\' -> append(tokens.nextOrNull()?.value
                                        ?: throw ParseException((position..program.length - 1), "Unterminated string"))
                                    else -> append(char)
                                }
                            } catch (e: NoSuchElementException) {
                                throw ParseException(position..program.length - 1, "Unterminated string")
                            }
                        }
                    }.let { Token.Str(position..endPos, it) }
                }
                in DIGIT_CHARS, '-' -> {
                    if (token == '-' && tokens.peekNext().value !in DIGIT_CHARS) {
                        Token.Sub(position..position)
                    } else {
                        val endPos: Int
                        buildString {
                            append(token)
                            while (true) {
                                val digit = tokens.nextOrNull()
                                if (digit == null) {
                                    endPos = program.length - 1
                                    break
                                }
                                when (digit.value) {
                                    in DIGIT_CHARS, '.' -> append(digit.value)
                                    else -> {
                                        tokens.previous()
                                        endPos = digit.index - 1
                                        break
                                    }
                                }
                            }
                        }.let {
                            try {
                                Token.Number(position..endPos, it.toDouble())
                            } catch (e: NumberFormatException) {
                                throw ParseException(position..endPos, "Malformed number: " + e.message)
                            }
                        }
                    }
                }
                '+' -> Token.Add(position..position)
                '/' -> Token.Div(position..position)
                '*' -> Token.Mul(position..position)
                '%' -> Token.Mod(position..position)
                '{' -> Token.OpenFunction(position..position)
                '}' -> Token.CloseFunction(position..position)
                in WHITESPACE -> null
                else -> {
                    val endPos: Int
                    buildString {
                        append(token)
                        while (true) {
                            val char = tokens.nextOrNull()
                            if (char == null) {
                                endPos = program.length
                                break
                            }
                            if (char.value in WHITESPACE) {
                                endPos = char.index
                                break
                            }
                            append(char.value)
                        }
                    }.let { Token.Identifier(position..<endPos, it) }
                }
            }?.let { add(it) }
        }
    }

    fun parse(tokens: List<Token>): List<Node> {
        if (tokens.isEmpty()) return listOf()
        val tokenIter = tokens.iterator()
        val functionStack = mutableListOf(FunctionStackEntry(mutableListOf(), 0))
        for (token in tokenIter) {
            when (token) {
                is Token.Number -> Node.Number(token.range, token.value)
                is Token.Str -> Node.Str(token.range, token.value)
                is Token.Identifier -> Node.Identifier(token.range, token.name)

                is Token.OpenFunction -> {
                    functionStack.add(FunctionStackEntry(mutableListOf(), token.range.start))
                    null
                }
                is Token.CloseFunction -> {
                    functionStack.removeLast().let {
                        functionStack.lastOrNull()?.body?.add(Node.Function((it.start)..(token.range.endInclusive), it.body))
                            ?: throw ParseException(token.range, "Unmatched closing bracket")
                    }
                    null
                }

                is Token.Add -> Node.Identifier(token.range, "add")
                is Token.Sub -> Node.Identifier(token.range, "sub")
                is Token.Div -> Node.Identifier(token.range, "div")
                is Token.Mul -> Node.Identifier(token.range, "mul")
                is Token.Mod -> Node.Identifier(token.range, "mod")
            }?.let { functionStack.last().body.add(it) }
        }
        return functionStack.singleOrNull()?.body
            ?: throw ParseException(functionStack.last().start..tokens.last().range.endInclusive, "Unclosed bracket")
    }
}

internal data class FunctionStackEntry(val body: MutableList<Node>, val start: Int)

sealed class Token {
    abstract val range: IntRange

    data class Number(override val range: IntRange, val value: Double) : Token()
    data class Str(override val range: IntRange, val value: String) : Token()
    data class Identifier(override val range: IntRange, val name: String) : Token()


    data class Add(override val range: IntRange) : Token()
    data class Sub(override val range: IntRange) : Token()
    data class Div(override val range: IntRange) : Token()
    data class Mul(override val range: IntRange) : Token()
    data class Mod(override val range: IntRange) : Token()
    data class OpenFunction(override val range: IntRange) : Token()
    data class CloseFunction(override val range: IntRange) : Token()

    override open fun toString() = this::class.simpleName!!
}

sealed class Node {
    abstract val range: IntRange
    
    data class Number(override val range: IntRange, val value: Double) : Node()
    data class Str(override val range: IntRange, val value: String) : Node()
    data class Identifier(override val range: IntRange, val name: String) : Node()
    data class Function(override val range: IntRange, val body: List<Node>) : Node()
}