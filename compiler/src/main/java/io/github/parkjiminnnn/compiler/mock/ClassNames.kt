package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.squareup.kotlinpoet.ClassName

// A nested declaration - most commonly a sealed subtype declared inside its parent, like
// FestivalUiState.Success - has to be referenced through its enclosing classes; its simple name
// alone doesn't resolve. Walking parentDeclaration collects the full nested name so KotlinPoet
// emits `FestivalUiState.Success` (and the matching import) rather than a bare `Success`.
internal fun KSClassDeclaration.toClassName(): ClassName {
    val simpleNames = mutableListOf<String>()
    var current: KSDeclaration? = this
    while (current is KSClassDeclaration) {
        simpleNames += current.simpleName.asString()
        current = current.parentDeclaration
    }
    return ClassName(packageName.asString(), simpleNames.asReversed())
}
