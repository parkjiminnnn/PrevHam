package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// Types declared inside the screen that uses them - an ordinary Android shape, and one that used to
// fail the whole build (issue #118). Three generators wrote a declaration's name from its package
// and simple name, which drops every enclosing class:
//
//     tab = Tab.LINEUP                                    // Tab does not resolve
//     listener = mockk<Listener>(relaxed = true)          // nor does Listener
//
// A generated file is compiled with the app, so one of these stopped the compilation outright, with
// an error pointing into build/generated rather than at anything written by hand. Every generator
// now names a declaration through its enclosing classes:
//
//     tab = LineupScreen.Tab.LINEUP
//     listener = mockk<LineupScreen.Listener>(relaxed = true)

class LineupScreen {
    enum class Tab {
        LINEUP,
        MAP,
    }

    interface Listener {
        fun onTabSelected(tab: Tab)
    }
}

@Prev
@Composable
fun LineupTabBar(
    tab: LineupScreen.Tab,
    listener: LineupScreen.Listener,
) {
    Text(tab.name)
}
