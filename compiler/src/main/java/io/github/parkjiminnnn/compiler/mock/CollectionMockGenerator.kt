package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class CollectionMockGenerator(
    private val elementRegistry: MockGeneratorRegistry,
) : MockGenerator {
    override fun supports(type: KSType): Boolean {
        val elements = type.resolveTypeArguments() ?: return false
        return elements.all { elementRegistry.supports(it) }
    }

    override fun generate(type: KSType): CodeBlock {
        val elements = type.resolveTypeArguments().orEmpty()
        val mocks = elements.map { elementRegistry.generate(it) }
        val factoryName = FACTORY_NAMES.getValue(type.qualifiedName()!!)
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
                "kotlin.collections.List" to "listOf",
                "kotlin.collections.Set" to "setOf",
                "kotlin.collections.Map" to "mapOf",
            )
    }
}
