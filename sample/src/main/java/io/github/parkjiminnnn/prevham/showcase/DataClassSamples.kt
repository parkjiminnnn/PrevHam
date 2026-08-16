package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// DataClassMockGenerator builds a named-argument constructor call, recursing through the context
// for each constructor parameter. `Order` nests two levels deep (Order -> User/Address -> String).
// Nesting isn't capped at a depth - recursion only stops where a type would expand into itself, so
// a model like this is built out in full however many levels it has.

data class User(
    val id: Int,
    val name: String,
    val age: Int,
)

data class Address(
    val city: String,
    val zip: String,
)

data class Order(
    val user: User,
    val address: Address,
)

@Prev
@Composable
fun UserCard(
    user: User,
    onClick: () -> Unit = {},
) {
    Text(text = "${user.name} (${user.age})")
}

@Prev
@Composable
fun OrderCard(order: Order) {
    Text(text = "${order.user.name} -> ${order.address.city}")
}
