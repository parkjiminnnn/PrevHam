package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class EnumMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.firstEnumEntryName() != null

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock {
        val declaration = type.declaration as KSClassDeclaration
        return CodeBlock.of("%T.%L", declaration.toClassName(), type.firstEnumEntryName())
    }

    private fun KSType.firstEnumEntryName(): String? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        if (declaration.classKind != ClassKind.ENUM_CLASS) return null
        return declaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.classKind == ClassKind.ENUM_ENTRY }
            ?.simpleName
            ?.asString()
    }
}
