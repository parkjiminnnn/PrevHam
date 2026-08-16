package io.github.parkjiminnnn.runtime

/**
 * Devices a Preview can be rendered on, for [Prev.devices].
 *
 * Names and values match `androidx.compose.ui.tooling.preview.Devices` exactly, so `@Prev` reads the
 * same as the `@Preview` it generates. They are declared here rather than reused from Compose
 * because `runtime` deliberately carries no dependencies - see `docs/architecture.md`.
 *
 * [Prev.devices] takes plain strings, so these are a convenience rather than a restriction: a device
 * spec Compose has no constant for still works.
 *
 * ```kotlin
 * @Prev(devices = [Devices.PIXEL_5, "spec:width=900dp,height=1200dp"])
 * ```
 *
 * `AUTOMOTIVE_1024p`, `TV_720p` and `TV_1080p` aren't screaming snake case, but they are the names
 * Compose uses; renaming them would defeat the point, hence the suppression.
 */
@Suppress("ktlint:standard:property-naming")
object Devices {
    /** The Preview's default device. */
    const val DEFAULT: String = ""

    const val NEXUS_7: String = "id:Nexus 7"

    const val NEXUS_7_2013: String = "id:Nexus 7 2013"

    const val NEXUS_5: String = "id:Nexus 5"

    const val NEXUS_6: String = "id:Nexus 6"

    const val NEXUS_9: String = "id:Nexus 9"

    const val NEXUS_10: String = "name:Nexus 10"

    const val NEXUS_5X: String = "id:Nexus 5X"

    const val NEXUS_6P: String = "id:Nexus 6P"

    const val PIXEL_C: String = "id:pixel_c"

    const val PIXEL: String = "id:pixel"

    const val PIXEL_XL: String = "id:pixel_xl"

    const val PIXEL_2: String = "id:pixel_2"

    const val PIXEL_2_XL: String = "id:pixel_2_xl"

    const val PIXEL_3: String = "id:pixel_3"

    const val PIXEL_3_XL: String = "id:pixel_3_xl"

    const val PIXEL_3A: String = "id:pixel_3a"

    const val PIXEL_3A_XL: String = "id:pixel_3a_xl"

    const val PIXEL_4: String = "id:pixel_4"

    const val PIXEL_4_XL: String = "id:pixel_4_xl"

    const val PIXEL_4A: String = "id:pixel_4a"

    const val PIXEL_5: String = "id:pixel_5"

    const val PIXEL_6: String = "id:pixel_6"

    const val PIXEL_6_PRO: String = "id:pixel_6_pro"

    const val PIXEL_6A: String = "id:pixel_6a"

    const val PIXEL_7: String = "id:pixel_7"

    const val PIXEL_7_PRO: String = "id:pixel_7_pro"

    const val PIXEL_7A: String = "id:pixel_7a"

    const val PIXEL_8: String = "id:pixel_8"

    const val PIXEL_8_PRO: String = "id:pixel_8_pro"

    const val PIXEL_8A: String = "id:pixel_8a"

    const val PIXEL_9: String = "id:pixel_9"

    const val PIXEL_9_PRO: String = "id:pixel_9_pro"

    const val PIXEL_9_PRO_FOLD: String = "id:pixel_9_pro_fold"

    const val PIXEL_9_PRO_XL: String = "id:pixel_9_pro_xl"

    const val PIXEL_FOLD: String = "id:pixel_fold"

    const val PIXEL_TABLET: String = "id:pixel_tablet"

    const val AUTOMOTIVE_1024p: String = "id:automotive_1024p_landscape"

    const val WEAR_OS_LARGE_ROUND: String = "id:wearos_large_round"

    const val WEAR_OS_SMALL_ROUND: String = "id:wearos_small_round"

    const val WEAR_OS_SQUARE: String = "id:wearos_square"

    const val WEAR_OS_RECT: String = "id:wearos_rect"

    const val PHONE: String = "spec:width=411dp,height=891dp"

    const val FOLDABLE: String = "spec:width=673dp,height=841dp"

    const val TABLET: String = "spec:width=1280dp,height=800dp,dpi=240"

    const val DESKTOP: String = "spec:width=1920dp,height=1080dp,dpi=160"

    const val TV_720p: String = "spec:width=1280dp,height=720dp"

    const val TV_1080p: String = "spec:width=1920dp,height=1080dp"
}
