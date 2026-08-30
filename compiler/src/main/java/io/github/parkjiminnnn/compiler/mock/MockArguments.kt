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
    // Where this value is declared, e.g. com.example.Festival.festivalName - null when the owner
    // has no qualified name to build one from. Type alone can't choose a value, since
    // Festival.name and User.name are both String and want different answers.
    val slot: String? = null,
)

internal fun KSValueParameter.toMockParameter(
    type: KSType = this.type.resolve(),
    owner: String? = null,
): MockParameter? {
    val name = name?.asString() ?: return null
    return MockParameter(name, type, hasDefault, slot = owner?.let { "$it.$name" })
}

// Shared by the processor, for a @Prev function's own parameters, and by DataClassMockGenerator,
// for a constructor's. Both descend through the context, so a parameter list nested inside another
// type is bounded by the same path the rest of the recursion is.
internal fun firstUnsupportedParameter(
    parameters: List<MockParameter>,
    context: MockContext,
): MockParameter? = parameters.firstOrNull { parameter -> !context.canMock(parameter.type, parameter.slot) && !parameter.hasDefault }

internal fun buildMockArguments(
    parameters: List<MockParameter>,
    context: MockContext,
): Map<String, CodeBlock> {
    val arguments = LinkedHashMap<String, CodeBlock>()
    for (parameter in parameters) {
        if (context.canMock(parameter.type, parameter.slot)) {
            arguments[parameter.name] = context.mock(parameter.type, parameter.slot)
        }
    }
    return arguments
}
