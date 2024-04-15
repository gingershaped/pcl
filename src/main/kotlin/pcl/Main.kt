package pcl

fun main() {
    val program = """1 1 +"""
    try {
        println(Interpreter.run(Parser.parse(Parser.tokenize(program))))
    } catch (e: PclException) {
        System.err.println("At position ${e.range.start + 1}:")
        System.err.println(e.highlight(program))
        System.err.println(e.stackTraceToString())
        System.exit(-1)
    }
}