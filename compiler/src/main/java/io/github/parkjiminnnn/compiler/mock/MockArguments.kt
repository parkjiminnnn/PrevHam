package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock

// A parameter to mock, decoupled from KSValueParameter so callers can supply a type-parameter
// -substituted KSType (e.g. resolving Box<T>'s `value: T` to `value: String` for Box<String>)
// instead of the raw declared type.
internal data class MockParameter(
    val name: String,
    val type: KSType,
    val hasDefault: Boolean,
)

internal fun KSValueParameter.toMockParameter(type: KSType = this.type.resolve()): MockParameter? {
    val name = name?.asString() ?: return null
    return MockParameter(name, type, hasDefault)
}

internal fun firstUnsupportedParameter(
    parameters: List<MockParameter>,
    registry: MockGeneratorRegistry,
): MockParameter? = parameters.firstOrNull { parameter -> !registry.supports(parameter.type) && !parameter.hasDefault }

internal fun buildMockArguments(
    parameters: List<MockParameter>,
    registry: MockGeneratorRegistry,
): Map<String, CodeBlock> {
    val arguments = LinkedHashMap<String, CodeBlock>()
    for (parameter in parameters) {
        if (registry.supports(parameter.type)) {
            arguments[parameter.name] = registry.generate(parameter.type)
        }
    }
    return arguments
}
