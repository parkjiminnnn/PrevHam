package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

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
fun OrderCard(order: Order) {
    Text(text = order.address.city)
}

@Prev
@Composable
fun MatrixCard(rows: List<List<Int>>) {
    Text(text = rows.toString())
}
