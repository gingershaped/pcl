package pcl


internal val DIGIT_CHARS = '0'..'9'
internal val WHITESPACE = "\n\t ".toCharArray()
internal val IDENTIFIER_CHARS = ('a'..'z') + ('A'..'Z') + DIGIT_CHARS + '?'

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
                    runCatching {
                        val endPos: Int
                        buildString {
                            while (true) {
                                val char = tokens.nextOrNull()
                                if (char == null) {
                                    throw ParseException(position..program.length - 1, "Unterminated string")
                                }
                                when (char.value) {
                                    token -> {
                                        endPos = char.index
                                        break
                                    }
                                    '\\' -> append(tokens.nextOrNull()?.value
                                        ?: throw ParseException((position..program.length - 1), "Unterminated string"))
                                    else -> append(char.value)
                                }
                            }
                        } to endPos
                    }.map { (string, endPos) ->
                        Token.Str(position..endPos, string)
                    }.getOrElse {
                        if (it is ParseException) {
                            Token.Error(it.range, it.message!!)
                        } else {
                            throw it
                        }
                    }
                }
                in DIGIT_CHARS, '-' -> {
                    if (token == '-' && tokens.nextOrNull()?.also { tokens.previous() }?.value !in DIGIT_CHARS) {
                        Token.Symbol(position..position, Token.Symbol.Type.SUB)
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
                                Token.Error(position..endPos, "Malformed number: " + e.message)
                            }
                        }
                    }
                }
                '+' -> Token.Symbol(position..position, Token.Symbol.Type.ADD)
                '/' -> Token.Symbol(position..position, Token.Symbol.Type.DIV)
                '*' -> Token.Symbol(position..position, Token.Symbol.Type.MUL)
                '%' -> Token.Symbol(position..position, Token.Symbol.Type.MOD)
                '{' -> Token.OpenFunction(position..position)
                '}' -> Token.CloseFunction(position..position)
                in WHITESPACE -> {
                    val endPos: Int
                    while (true) {
                        when (val char = tokens.nextOrNull()) {
                            null -> {
                                endPos = program.length - 1
                                break
                            }
                            else -> {
                                if (char.value !in WHITESPACE) {
                                    tokens.previous()
                                    endPos = char.index - 1
                                    break
                                }
                            }
                        }
                    }
                    Token.Whitespace(position..endPos)
                }
                in IDENTIFIER_CHARS -> {
                    val endPos: Int
                    buildString {
                        append(token)
                        while (true) {
                            val char = tokens.nextOrNull()
                            if (char == null) {
                                endPos = program.length - 1
                                break
                            }
                            if (char.value !in IDENTIFIER_CHARS) {
                                tokens.previous()
                                endPos = char.index - 1
                                break
                            }
                            append(char.value)
                        }
                    }.let { Token.Identifier(position..endPos, it) }
                }
                else -> Token.Error(position..position, "Unknown token ${token}")
            }.let { add(it) }
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

                is Token.Symbol -> Node.Identifier(token.range, token.type.name.lowercase())

                is Token.Whitespace -> null
                is Token.Error -> throw ParseException(token.range, token.message)
            }?.let { functionStack.last().body.add(it) }
        }
        return functionStack.singleOrNull()?.body
            ?: throw ParseException(functionStack.last().start..tokens.last().range.endInclusive, "Unclosed bracket")
    }
}

private data class FunctionStackEntry(val body: MutableList<Node>, val start: Int)

sealed class Token {
    abstract val range: IntRange

    data class Number(override val range: IntRange, val value: Double) : Token()
    data class Str(override val range: IntRange, val value: String) : Token()
    data class Identifier(override val range: IntRange, val name: String) : Token()

    data class Symbol(override val range: IntRange, val type: Type) : Token() {
        enum class Type {
            ADD, SUB, DIV, MUL, MOD
        }
    }
    data class OpenFunction(override val range: IntRange) : Token()
    data class CloseFunction(override val range: IntRange) : Token()

    data class Whitespace(override val range: IntRange) : Token()
    data class Error(override val range: IntRange, val message: String) : Token()

    override open fun toString() = this::class.simpleName!!
}

sealed class Node {
    abstract val range: IntRange
    
    data class Number(override val range: IntRange, val value: Double) : Node()
    data class Str(override val range: IntRange, val value: String) : Node()
    data class Identifier(override val range: IntRange, val name: String) : Node()
    data class Function(override val range: IntRange, val body: List<Node>) : Node()
}