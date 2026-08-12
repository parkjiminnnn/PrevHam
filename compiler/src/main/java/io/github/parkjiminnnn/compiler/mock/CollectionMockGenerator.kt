package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class CollectionMockGenerator : MockGenerator {
    // An element the context can't build doesn't make the collection unmockable: unlike a data
    // class, which has no value at all without its constructor arguments, a collection has an
    // empty one. That is what keeps a recursive shape like `data class Tree(val children:
    // List<Tree>)` previewable - `Tree(children = listOf())` is exactly what such a tree's leaf
    // looks like, and rejecting it would cost the whole composable its Preview.
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.resolveTypeArguments() != null

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock {
        val elements = type.resolveTypeArguments().orEmpty()
        val factoryName = FACTORY_NAMES.getValue(type.qualifiedName()!!)
        if (elements.any { !context.canMock(it) }) return CodeBlock.of("%L()", factoryName)
        val mocks = elements.map { context.mock(it) }
        val argument = if (mocks.size == 2) CodeBlock.of("%L to %L", mocks[0], mocks[1]) else mocks[0]
        return CodeBlock.of("%L(%L)", factoryName, argument)
    }

    private fun KSType.qualifiedName(): String? = declaration.qualifiedName?.asString()

    private fun KSType.resolveTypeArguments(): List<KSType>? {
        if (qualifiedName() !in FACTORY_NAMES) return null
        val resolved = arguments.mapNotNull { it.type?.resolve() }
        return resolved.takeIf { it.size == arguments.size }
    }

    private companion object {
        val FACTORY_NAMES =
            mapOf(
                KOTLIN_LIST_QUALIFIED_NAME to "listOf",
                KOTLIN_SET_QUALIFIED_NAME to "setOf",
                KOTLIN_MAP_QUALIFIED_NAME to "mapOf",
            )
    }
}
