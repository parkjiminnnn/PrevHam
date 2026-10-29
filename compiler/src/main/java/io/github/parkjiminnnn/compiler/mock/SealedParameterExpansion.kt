package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.CodeBlock

/**
 * One Preview function's worth of arguments.
 *
 * [label] names the case among its siblings - the subtype a sealed parameter was given - and is null
 * when the composable has only the one Preview.
 */
internal data class PreviewCase(
    val label: String?,
    val arguments: Map<String, CodeBlock>,
)

/**
 * Turns a composable that takes a sealed type into one Preview per subtype.
 *
 * A UI state is sealed because each subtype is a different screen - `Loading`, `Success` and `Error`
 * are three things to look at, not one. Picking a single subtype, which is all [SealedTypeMockGenerator]
 * can do, left the other two unreachable short of writing them by hand (issue #138).
 *
 * Only a parameter of the composable itself is expanded. Further down - a data class field, or a
 * ViewModel's `StateFlow<UiState>` - one subtype is still picked, because the value there is part of
 * a larger one and expanding it means building that larger value once per subtype.
 *
 * Only the first sealed parameter with more than one subtype to build is expanded. Expanding two
 * would produce their product, which grows faster than anyone wants to scroll through.
 *
 * A subtype is left out when it can't be built, by the same rule the generator uses to skip it, and
 * a sealed type with only one buildable subtype is not expanded - its single Preview is already that
 * subtype.
 */
internal object SealedParameterExpansion {
    /**
     * The Previews to generate for [parameters], in the order the subtypes sort.
     *
     * [newContext] is called once per case, so each Preview function is bounded by a stub budget of
     * its own - they are separate functions, and each is what the budget exists to keep compilable.
     */
    fun cases(
        parameters: List<MockParameter>,
        newContext: () -> MockContext,
    ): List<PreviewCase> {
        val deciding = newContext()
        parameters.forEachIndexed { index, parameter ->
            val subtypes =
                parameter.type
                    .resolveTypeAliases()
                    .sealedSubtypes()
                    ?.filter { it.isBuildableSubtype(deciding) }
                    .orEmpty()
            if (subtypes.size < 2) return@forEachIndexed

            val labels = labelsFor(subtypes)
            return subtypes.map { subtype ->
                // The subtype takes the parameter's place, so the generators see exactly the value
                // they would have been asked for had the parameter been declared as that subtype.
                val fixed = parameters.toMutableList().apply { set(index, parameter.copy(type = subtype.asStarProjectedType())) }
                PreviewCase(labels.getValue(subtype), buildMockArguments(fixed, newContext()))
            }
        }
        return listOf(PreviewCase(label = null, arguments = buildMockArguments(parameters, newContext())))
    }

    // Simple names, which is what reads well in the IDE and in a function name. Two direct subtypes
    // can share one when they are nested in different declarations, so those fall back to the full
    // nested name rather than producing two functions with the same name.
    private fun labelsFor(subtypes: List<KSClassDeclaration>): Map<KSClassDeclaration, String> {
        val simpleNames = subtypes.associateWith { it.simpleName.asString() }
        if (simpleNames.values.distinct().size == simpleNames.size) return simpleNames
        return subtypes.associateWith { it.toClassName().simpleNames.joinToString("") }
    }
}
