package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

/**
 * References an `object` declaration, which is its own value.
 *
 * There is nothing to build here: no constructor to run, no fields to invent, and only one instance
 * that can ever exist. `toClassName()` carries the enclosing names, so a nested object and a
 * companion are written the way they are referenced - `Outer.Inner`, `Host.Companion`.
 *
 * Registered ahead of [DataClassMockGenerator] because a `data object` carries `Modifier.DATA` too.
 * That generator used to claim one and emit `Loading()`, which does not compile: an object has a
 * synthesised zero-parameter constructor, so its check that every constructor parameter can be
 * mocked was vacuously true (issue #77).
 */
internal class ObjectMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.objectDeclaration() != null

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock = CodeBlock.of("%T", type.objectDeclaration()!!.toClassName())

    // Nullability is deliberately ignored: the object is a better answer for `Tracker?` than the
    // null NullableFallbackMockGenerator would otherwise reach.
    private fun KSType.objectDeclaration(): KSClassDeclaration? =
        (declaration as? KSClassDeclaration)?.takeIf { it.classKind == ClassKind.OBJECT }
}
