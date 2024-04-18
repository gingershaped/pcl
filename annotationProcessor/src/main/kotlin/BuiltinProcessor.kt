import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.KspExperimental

private const val CALL_CONTEXT = "pcl.CallContext"
private const val STACK_VALUE = "pcl.StackValue"

@OptIn(KspExperimental::class)
class BuiltinProcessor(val codeGenerator: CodeGenerator, val logger: KSPLogger) : SymbolProcessor {
    var hasRun = false

    override fun process(resolver: Resolver) : List<KSAnnotated> {
        if (hasRun) return listOf()
        val nodeListType = resolver.getClassDeclarationByName("kotlin.collections.List")!!.asType(
            listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(resolver.getClassDeclarationByName("pcl.Node")!!.asType(listOf())), Variance.INVARIANT))
        )
        val stackValueType = resolver.getClassDeclarationByName("pcl.StackValue")!!.asStarProjectedType()
        val builtinReturnType = resolver.getClassDeclarationByName("kotlin.collections.List")!!.asType(
            listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(stackValueType), Variance.COVARIANT))
        )

        val builtinsObject = resolver.getClassDeclarationByName("pcl.Builtins")!!

        val file = buildString {
            appendLine("package pcl\n")
            appendLine("""@Suppress("UNUSED_ANONYMOUS_PARAMETER")""")
            appendLine("internal val builtinsMap: Map<String, BuiltinFunction> = mapOf(")
            for ((name, overloads) in builtinsObject.getDeclaredFunctions().filter { builtinReturnType.isAssignableFrom(it.returnType!!.resolve()) }.groupBy { it.simpleName }) {
                val takesContext = overloads.first().parameters.firstOrNull()?.let { it.name!!.asString() == "ctx" } ?: false
                val arity = overloads.first().parameters.size
                if (takesContext) {
                    check(overloads.all { it.parameters.first().let { it.name!!.asString() == "ctx" && it.type.resolve().declaration.qualifiedName!!.asString() == CALL_CONTEXT } }) {
                        "All overloads for builtin function ${name.asString()} must take a ctx parameter if any one does!"
                    }
                }
                check(overloads.all { it.parameters.size == arity }) {
                    "All overloads for builtin function ${name.asString()} must have the same number of parameters!"
                }
                val realArity = arity - if (takesContext) 1 else 0
                val realName = name.getShortName()

                appendLine("""
                |"$realName" to BuiltinFunction(
                |    name = "$realName",
                |    arity = $realArity,
                |    takesContext = $takesContext,
                |    overloads = listOf(
                """.trimMargin())

                for (overload in overloads) {
                    val argParameters = overload.parameters.drop(if (takesContext) 1 else 0)
                    appendLine("        BuiltinFunction.Overload(")
                    append    ("            argTypes = listOf(")
                    append(argParameters.map {
                        buildString {
                            append("$STACK_VALUE")
                            append(
                                when (it.type.resolve()) {
                                    resolver.builtIns.doubleType -> ".Number"
                                    resolver.builtIns.stringType -> ".Str"
                                    nodeListType -> ".Function"
                                    stackValueType -> ""
                                    else -> error(
                                        "Builtin function $realName has an invalid type ${it.type.resolve().declaration.qualifiedName?.asString()} for parameter ${it.name!!.asString()}!"
                                    )
                                }
                            )
                            append("::class")
                        }
                    }.joinToString(", "))
                    appendLine("),")
                    appendLine("            impl = { ctx: CallContext, arguments: List<StackValue<*>> ->")
                    append("                ")
                    append("${builtinsObject.qualifiedName!!.asString()}.$realName(")
                    if (takesContext) {
                        append("ctx, ")
                    }
                    for ((index, parameter) in argParameters.withIndex()) {
                        val type = parameter.type.resolve()
                        if (type == stackValueType) {
                            append("arguments[$index]")
                        } else {
                            append("(arguments[$index] as $STACK_VALUE.")
                            append(
                                when (parameter.type.resolve()) {
                                    resolver.builtIns.doubleType -> "Number"
                                    resolver.builtIns.stringType -> "Str"
                                    nodeListType -> "Function"
                                    else -> throw IllegalStateException()
                                }
                            )
                            append(").value")
                        }                        
                        append(", ")
                    }
                    appendLine(")")
                    appendLine("            },")
                    append    ("            doc = ")
                    if (overload.docString != null) {
                        append(""""${overload.docString}"""")
                    } else {
                        append("null")
                    }
                    append("\n")
                    appendLine("        ),")
                }
                appendLine(")")
                appendLine("),")
            }
            append(")")
        }
        codeGenerator.createNewFile(Dependencies(true, builtinsObject.containingFile!!), "pcl", "GeneratedBuiltins").apply {
            writer().use {
                it.write(file)
            }
        }.close()

        hasRun = true
        return listOf()
    }
}

class BuiltinProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return BuiltinProcessor(environment.codeGenerator, environment.logger)
    }
    
}