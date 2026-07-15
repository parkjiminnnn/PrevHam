package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName

internal class InterfaceMockGenerator : MockGenerator {
    override fun supports(type: KSType): Boolean {
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        if (declaration.isOwnedByAnotherGenerator()) return false
        return declaration.classKind == ClassKind.INTERFACE ||
            (declaration.classKind == ClassKind.CLASS && Modifier.DATA !in declaration.modifiers)
    }

    override fun generate(type: KSType): CodeBlock {
        val declaration = type.declaration as KSClassDeclaration
        val className = ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
        return declaration.selfImplementingCompanionMock(className) ?: CodeBlock.of("%M<%T>(relaxed = true)", MOCKK_FUNCTION, className)
    }

    // Some interfaces (e.g. androidx.compose.ui.Modifier) declare a companion object that
    // implements the interface itself, as a ready-made "empty" instance. Prefer that real
    // instance over a MockK mock when it's available.
    private fun KSClassDeclaration.selfImplementingCompanionMock(className: ClassName): CodeBlock? {
        val companion = declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }
        val implementsSelf =
            companion?.superTypes?.any {
                it
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() == qualifiedName?.asString()
            } == true
        return if (implementsSelf) CodeBlock.of("%T", className) else null
    }

    // List/Set/Map and function types (kotlin.FunctionN) are also declared as `interface` in
    // Kotlin, but they already have dedicated generators (CollectionMockGenerator, and a future
    // FunctionTypeMockGenerator) that produce more useful mocks than a MockK relaxed mock.
    private fun KSClassDeclaration.isOwnedByAnotherGenerator(): Boolean {
        val name = qualifiedName?.asString() ?: return false
        return name in COLLECTION_QUALIFIED_NAMES || name.startsWith("kotlin.Function")
    }

    private companion object {
        val COLLECTION_QUALIFIED_NAMES =
            setOf("kotlin.collections.List", "kotlin.collections.Set", "kotlin.collections.Map")
        val MOCKK_FUNCTION = MemberName("io.mockk", "mockk")
    }
}
