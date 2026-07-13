package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock

internal fun firstUnsupportedParameter(
    parameters: List<KSValueParameter>,
    registry: MockGeneratorRegistry,
): KSValueParameter? =
    parameters.firstOrNull { parameter ->
        !registry.supports(parameter.type.resolve()) && !parameter.hasDefault
    }

internal fun buildMockArguments(
    parameters: List<KSValueParameter>,
    registry: MockGeneratorRegistry,
): Map<String, CodeBlock> {
    val arguments = LinkedHashMap<String, CodeBlock>()
    for (parameter in parameters) {
        val name = parameter.name?.asString() ?: continue
        val type = parameter.type.resolve()
        if (registry.supports(type)) {
            arguments[name] = registry.generate(type)
        }
    }
    return arguments
}
