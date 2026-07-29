package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// FunctionTypeMockGenerator emits a lambda literal, resolving the return type through the registry
// for non-Unit types. Function types with two or more parameters get named-but-unused parameters
// ({ _, _ -> ... }), since Kotlin only infers an implicit parameter for arity 0 and 1.

@Prev
@Composable
fun ActionCard(onClick: () -> Unit) {
    Text(text = "action")
}

@Prev
@Composable
fun ValidatorCard(validate: (String) -> Boolean) {
    Text(text = "validator: ${validate("test")}")
}

@Prev
@Composable
fun RangeValidatorCard(validate: (Int, Int) -> Boolean) {
    Text(text = "range validator: ${validate(0, 10)}")
}
