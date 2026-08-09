package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.CodeBlock

/**
 * Builds a real instance of one of a sealed type's concrete subtypes, instead of handing the sealed
 * type itself to MockK.
 *
 * MockK can produce a value for a sealed type - it instantiates a subtype through Objenesis, so
 * `is`/`when` checks against it do pass. What it can't do is produce a *useful* one. Objenesis skips
 * the constructor, so the instance's fields stay unset; which subtype it picks is its own choice,
 * not something the generated file records or can rely on staying the same; and every member read
 * off it is answered by relaxed mode, which is where the erased-generic problem in
 * [InterfaceMockGenerator]'s docs starts.
 *
 * A plain `UiState.Loading` or `PaymentResult.Approved(receiptId = 1L)` has none of those
 * properties: the subtype is chosen here and written into the generated file, and its fields carry
 * the same mock values every other generator produces.
 *
 * Must be registered ahead of [InterfaceMockGenerator], which would otherwise claim sealed
 * interfaces and sealed classes as ordinary mockable types.
 */
internal class SealedTypeMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.firstMockableSubtype(context) != null

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock {
        val subtype = type.firstMockableSubtype(context)!!
        if (subtype.classKind == ClassKind.OBJECT) return CodeBlock.of("%T", subtype.toClassName())
        return context.mock(subtype.asStarProjectedType())
    }

    private fun KSType.firstMockableSubtype(context: MockContext): KSClassDeclaration? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        if (Modifier.SEALED !in declaration.modifiers) return null
        // A generic sealed type's subtypes would need their type arguments substituted, which
        // asStarProjectedType() can't do. Leave those to InterfaceMockGenerator.
        if (declaration.typeParameters.isNotEmpty()) return null
        return declaration
            .getSealedSubclasses()
            // getSealedSubclasses() promises no particular order - not even declaration order - so
            // sort to keep generated code reproducible. Object subtypes sort first: they need
            // nothing constructed, so they can't be turned away by a recursion bound, and they
            // introduce no invented field values into the preview.
            .sortedWith(compareBy({ it.classKind != ClassKind.OBJECT }, { it.simpleName.asString() }))
            .firstOrNull { it.isMockable(context) }
    }

    private fun KSClassDeclaration.isMockable(context: MockContext): Boolean {
        if (typeParameters.isNotEmpty()) return false
        // An object subtype is just a reference, so it needs no further expansion at all.
        if (classKind == ClassKind.OBJECT) return true
        return context.canMock(asStarProjectedType())
    }
}
