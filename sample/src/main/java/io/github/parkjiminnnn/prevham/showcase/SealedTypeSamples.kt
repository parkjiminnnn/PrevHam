package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// SealedTypeMockGenerator builds one of a sealed type's real subtypes rather than mocking the
// sealed type itself, so `is`/`when` checks against it behave the way the composable expects.
// Object subtypes are preferred, then subtypes are taken in name order, keeping the choice the same
// on every build - ScreenUiState below resolves to ScreenUiState.Loading.

sealed interface ScreenUiState {
    data object Loading : ScreenUiState

    data class Success(
        val title: String,
    ) : ScreenUiState

    data class Error(
        val message: String,
    ) : ScreenUiState
}

// With no object subtype to fall back on, the first subtype in name order is constructed instead,
// referenced through its parent as PaymentResult.Approved.
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
