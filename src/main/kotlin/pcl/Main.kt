package pcl

fun main() {
    val program = """hello world"""
    try {
        println(Parser.parse(Parser.tokenize(program)))
    } catch (e: ParseException) {
        System.err.println("Parse error at position ${e.range.start + 1}: " + e.message)
        System.err.println(e.highlight(program))
        System.exit(-1)
    }
}