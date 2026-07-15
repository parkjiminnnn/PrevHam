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
