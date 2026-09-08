package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// ConstructorMockGenerator asks whether the constructor can be called, not whether the class is a
// `data` class (issue #78). The `data` keyword changes nothing about whether a value can be built,
// so these are constructed exactly as DataClassSamples' types are:
//
//     ticket = Ticket(holder = "mock", seat = "mock", price = 1)
//     id     = UserId(raw = "mock")
//
// A mock would have compiled too. What it would not have done is carry values: relaxed mode invents
// its answers from the return type, so `ticket.holder` would read "" and `ticket.price` 0, right
// beside a data class in the same Preview reading "mock" and 1.
//
// A value class is the clearest case of all - it is one value in a wrapper, so mocking it buys
// nothing whatsoever. It also used to put its compiler-generated `hashCode` and `toString` in the
// slot manifest, since they are declared on the class and return types a value can be configured
// for. Constructing it drops both.

class Ticket(
    val holder: String,
    val seat: String,
    val price: Int,
)

@JvmInline
value class UserId(
    val raw: String,
)

@Prev
@Composable
fun TicketCard(ticket: Ticket) {
    Text("${ticket.holder} — ${ticket.seat} (${ticket.price})")
}

@Prev
@Composable
fun UserIdCard(id: UserId) {
    Text(id.raw)
}

// Not constructed: with no constructor parameters there is nothing to put in, so building one
// carries exactly what a mock does while additionally running whatever the class's init block does.
// On Android that is the ViewModel shape, which is why the line is drawn here - see
// StateHolderSamples for the case this protects.

class Clock

@Prev
@Composable
fun ClockCard(clock: Clock) {
    Text(clock.toString())
}
