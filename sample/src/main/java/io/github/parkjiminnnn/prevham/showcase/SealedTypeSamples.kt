package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// A sealed type is built as its real subtypes rather than mocked, so `is`/`when` checks against it
// behave the way the composable expects.
//
// A sealed parameter of the composable itself gets one Preview per subtype -
// ScreenStateCardLoadingPreview, ScreenStateCardErrorPreview and ScreenStateCardSuccessPreview below.
// Object subtypes come first, then the rest in name order, so the order and the names are the same on
// every build whatever order the subtypes are declared in.

sealed interface ScreenUiState {
    data object Loading : ScreenUiState

    data class Success(
        val title: String,
    ) : ScreenUiState

    data class Error(
        val message: String,
    ) : ScreenUiState
}

// Subtypes with fields are constructed, referenced through their parent as PaymentResult.Approved
// and PaymentResult.Declined.
sealed interface PaymentResult {
    data class Approved(
        val receiptId: Long,
    ) : PaymentResult

    data class Declined(
        val reason: String,
    ) : PaymentResult
}

@Prev
@Composable
fun ScreenStateCard(state: ScreenUiState) {
    Text(text = state.toString())
}

@Prev
@Composable
fun PaymentResultCard(result: PaymentResult) {
    Text(text = result.toString())
}

// Reached through a field, a sealed type is part of a larger value and gets one subtype rather than a
// Preview each - the first in the same order, PaymentResult.Approved here.
data class Checkout(
    val orderId: Long,
    val result: PaymentResult,
)

@Prev
@Composable
fun CheckoutCard(checkout: Checkout) {
    Text(text = checkout.toString())
}
