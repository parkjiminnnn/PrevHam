package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.ClassName

// A nested declaration - a sealed subtype inside its parent like FestivalUiState.Success, or an
// enum or listener declared inside the screen that uses it - has to be referenced through its
// enclosing classes; its simple name alone doesn't resolve. Walking parentDeclaration collects the
// full nested name so KotlinPoet emits `FestivalUiState.Success` (and the matching import) rather
// than a bare `Success`.
//
// Every generator that names a declaration goes through this. Building ClassName(packageName,
// simpleName) directly looks equivalent and is not: it compiles only for top-level types, and the
// generated file that fails is one the consumer never wrote, so their whole build stops on an error
// pointing into build/generated (issue #118).
internal fun KSClassDeclaration.toClassName(): ClassName {
    val simpleNames = mutableListOf<String>()
    var current: KSDeclaration? = this
    while (current is KSClassDeclaration) {
        simpleNames += current.simpleName.asString()
        current = current.parentDeclaration
    }
    return ClassName(packageName.asString(), simpleNames.asReversed())
}
