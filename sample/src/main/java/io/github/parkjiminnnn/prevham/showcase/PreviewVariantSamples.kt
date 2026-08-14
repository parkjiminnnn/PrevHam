package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Devices
import io.github.parkjiminnnn.runtime.Prev
import io.github.parkjiminnnn.runtime.Wallpapers

// @Prev's parameters come in two kinds.
//
// Variants - darkMode, locales, fontScales, devices - each add one @Preview on top of the default
// one. Compose's @Preview is @Repeatable, so they stack on a single generated function rather than
// needing one wrapper per variant. The composable below produces six Previews in the IDE.

@Prev(darkMode = true, locales = ["ko", "en"], fontScales = [0.85f, 1.5f])
@Composable
fun PreviewVariantCard(text: String) {
    Text(text = text)
}

@Prev(devices = [Devices.PIXEL_5, Devices.PIXEL_TABLET])
@Composable
fun DeviceVariantCard(text: String) {
    Text(text = text)
}

// Devices is a convenience, not a restriction - devices takes plain strings, so a spec Compose has
// no constant for works just as well.

@Prev(devices = [Devices.PIXEL_FOLD, "spec:width=900dp,height=1200dp"])
@Composable
fun CustomSpecCard(text: String) {
    Text(text = text)
}

// Settings - everything else - describe how to render rather than what to render, so they are
// applied to every generated @Preview, variants included. Anything left at its default is left out
// of the generated annotation entirely.

@Prev(
    showBackground = true,
    backgroundColor = 0xFFFFFBFE,
    widthDp = 320,
    heightDp = 120,
    group = "cards",
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
fun FramedCard(text: String) {
    Text(text = text)
}

// showSystemUi renders inside a device frame, and apiLevel pins the platform version the Preview
// is rendered against.

@Prev(showSystemUi = true, apiLevel = 34, group = "cards")
@Composable
fun SystemUiCard(text: String) {
    Text(text = text)
}

// A configured name becomes the variants' common prefix - "Badge", "Badge - Dark Mode" - so each
// stays distinguishable in the IDE rather than three Previews sharing one name.

@Prev(name = "Badge", darkMode = true, group = "cards")
@Composable
fun NamedVariantCard(text: String) {
    Text(text = text)
}
