package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

internal class EnumMockGenerator : MockGenerator {
    override fun supports(type: KSType): Boolean = type.firstEnumEntryName() != null

    override fun generate(type: KSType): CodeBlock {
        val declaration = type.declaration as KSClassDeclaration
        val className = ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
        return CodeBlock.of("%T.%L", className, type.firstEnumEntryName())
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
