package io.github.parkjiminnnn.compiler.mock

// Kotlin function types (() -> Unit, (String) -> Int, ...) are represented as
// kotlin.FunctionN<P1, ..., PN, R>. Shared by InterfaceMockGenerator (to exclude them) and
// FunctionTypeMockGenerator (to detect them), so both stay in sync if this ever changes.
internal const val KOTLIN_FUNCTION_TYPE_PREFIX = "kotlin.Function"

// Shared by CollectionMockGenerator (which owns these types) and InterfaceMockGenerator (which
// must exclude them, since List/Set/Map are also declared as `interface` in Kotlin).
internal const val KOTLIN_LIST_QUALIFIED_NAME = "kotlin.collections.List"
internal const val KOTLIN_SET_QUALIFIED_NAME = "kotlin.collections.Set"
internal const val KOTLIN_MAP_QUALIFIED_NAME = "kotlin.collections.Map"
internal val KOTLIN_COLLECTION_QUALIFIED_NAMES =
    setOf(KOTLIN_LIST_QUALIFIED_NAME, KOTLIN_SET_QUALIFIED_NAME, KOTLIN_MAP_QUALIFIED_NAME)

internal const val KOTLIN_ANY_QUALIFIED_NAME = "kotlin.Any"
internal const val KOTLIN_UNIT_QUALIFIED_NAME = "kotlin.Unit"

// Flow types a mocked member can expose. MutableStateFlow(value) satisfies every one of them
// (MutableStateFlow <: StateFlow <: SharedFlow <: Flow, and MutableStateFlow <: MutableSharedFlow),
// so InterfaceMockGenerator can stub all of these with a single factory call.
internal val KOTLINX_FLOW_QUALIFIED_NAMES =
    setOf(
        "kotlinx.coroutines.flow.Flow",
        "kotlinx.coroutines.flow.SharedFlow",
        "kotlinx.coroutines.flow.MutableSharedFlow",
        "kotlinx.coroutines.flow.StateFlow",
        "kotlinx.coroutines.flow.MutableStateFlow",
    )

// The types that become a literal rather than a mock - PrimitiveMockGenerator's and
// StringMockGenerator's territory. Shared with StubNecessity, which stops searching at them: a
// literal has no members read through a mock, so nothing below one can need stubbing.
internal val KOTLIN_LITERAL_QUALIFIED_NAMES =
    setOf(
        "kotlin.Int",
        "kotlin.Long",
        "kotlin.Short",
        "kotlin.Byte",
        "kotlin.Double",
        "kotlin.Float",
        "kotlin.Boolean",
        "kotlin.Char",
        "kotlin.String",
    )
