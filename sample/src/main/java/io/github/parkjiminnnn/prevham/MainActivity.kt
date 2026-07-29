package io.github.parkjiminnnn.prevham

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.parkjiminnnn.prevham.showcase.ActionCard
import io.github.parkjiminnnn.prevham.showcase.Address
import io.github.parkjiminnnn.prevham.showcase.AnalyticsTracker
import io.github.parkjiminnnn.prevham.showcase.Box
import io.github.parkjiminnnn.prevham.showcase.BoxCard
import io.github.parkjiminnnn.prevham.showcase.Greeting
import io.github.parkjiminnnn.prevham.showcase.IconButtonCard
import io.github.parkjiminnnn.prevham.showcase.ImageLoader
import io.github.parkjiminnnn.prevham.showcase.Logger
import io.github.parkjiminnnn.prevham.showcase.LoggerCard
import io.github.parkjiminnnn.prevham.showcase.MatrixCard
import io.github.parkjiminnnn.prevham.showcase.NullableCard
import io.github.parkjiminnnn.prevham.showcase.Order
import io.github.parkjiminnnn.prevham.showcase.OrderCard
import io.github.parkjiminnnn.prevham.showcase.PreviewVariantCard
import io.github.parkjiminnnn.prevham.showcase.RepoCard
import io.github.parkjiminnnn.prevham.showcase.Repository
import io.github.parkjiminnnn.prevham.showcase.ScoreBoard
import io.github.parkjiminnnn.prevham.showcase.StatsRow
import io.github.parkjiminnnn.prevham.showcase.Status
import io.github.parkjiminnnn.prevham.showcase.StatusBadge
import io.github.parkjiminnnn.prevham.showcase.StatusList
import io.github.parkjiminnnn.prevham.showcase.TagList
import io.github.parkjiminnnn.prevham.showcase.Task
import io.github.parkjiminnnn.prevham.showcase.TaskCard
import io.github.parkjiminnnn.prevham.showcase.TrackedCard
import io.github.parkjiminnnn.prevham.showcase.User
import io.github.parkjiminnnn.prevham.showcase.UserCard
import io.github.parkjiminnnn.prevham.showcase.UserList
import io.github.parkjiminnnn.prevham.showcase.ValidatorCard
import io.github.parkjiminnnn.prevham.ui.theme.PrevHamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrevHamTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ShowcaseList(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * One mock-generation category and the sample composables demonstrating it, rendered here with
 * real arguments.
 *
 * Every sample is also annotated with `@Prev`, so the same composables show up in the IDE's Preview
 * pane with *generated* mock arguments. Comparing the two is the point of this app: the Preview
 * pane shows what PrevHam produces, this screen shows what hand-written usage looks like.
 */
private class ShowcaseSection(
    val title: String,
    val samples: List<Pair<String, @Composable () -> Unit>>,
)

private val sampleUser = User(id = 1, name = "Jimin", age = 20)
private val sampleOrder = Order(user = sampleUser, address = Address(city = "Seoul", zip = "04524"))

private object NoOpImageLoader : ImageLoader {
    override fun load(url: String) = Unit
}

private object StringRepository : Repository<String> {
    override fun get(): String = "repo"
}

private val showcaseSections =
    listOf(
        ShowcaseSection(
            title = "Primitives & String",
            samples =
                listOf(
                    "Greeting" to { Greeting(name = "Android") },
                    "StatsRow" to { StatsRow(count = 3, ratio = 0.75f, enabled = true) },
                ),
        ),
        ShowcaseSection(
            title = "Data classes",
            samples =
                listOf(
                    "UserCard" to { UserCard(user = sampleUser) },
                    "OrderCard" to { OrderCard(order = sampleOrder) },
                ),
        ),
        ShowcaseSection(
            title = "Collections",
            samples =
                listOf(
                    "TagList" to { TagList(tags = listOf("compose", "ksp")) },
                    "UserList" to { UserList(users = listOf(sampleUser)) },
                    "StatusList" to { StatusList(statuses = Status.entries) },
                    "ScoreBoard" to { ScoreBoard(scores = mapOf("kotlin" to 10)) },
                    "MatrixCard" to { MatrixCard(rows = listOf(listOf(1, 2), listOf(3, 4))) },
                ),
        ),
        ShowcaseSection(
            title = "Enums",
            samples =
                listOf(
                    "StatusBadge" to { StatusBadge(status = Status.ACTIVE) },
                    "TaskCard" to { TaskCard(task = Task(title = "Ship v1.0", status = Status.PENDING)) },
                ),
        ),
        ShowcaseSection(
            title = "Nullable types",
            samples =
                listOf(
                    "NullableCard" to { NullableCard(name = null, onClick = null) },
                ),
        ),
        ShowcaseSection(
            title = "Interfaces & non-data classes",
            samples =
                listOf(
                    "IconButtonCard" to { IconButtonCard(modifier = Modifier, loader = NoOpImageLoader) },
                    "TrackedCard" to { TrackedCard(tracker = AnalyticsTracker()) },
                ),
        ),
        ShowcaseSection(
            title = "Function types",
            samples =
                listOf(
                    "ActionCard" to { ActionCard(onClick = {}) },
                    "ValidatorCard" to { ValidatorCard(validate = { it.isNotEmpty() }) },
                ),
        ),
        ShowcaseSection(
            title = "Generic types",
            samples =
                listOf(
                    "BoxCard" to { BoxCard(box = Box(value = "boxed")) },
                    "RepoCard" to { RepoCard(repository = StringRepository) },
                    "LoggerCard" to { LoggerCard(logger = Logger<String>()) },
                ),
        ),
        ShowcaseSection(
            title = "Preview variants",
            samples =
                listOf(
                    "PreviewVariantCard" to { PreviewVariantCard(text = "dark mode, locales, font scales") },
                ),
        ),
    )

@Composable
private fun ShowcaseList(modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(showcaseSections) { section ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = section.title, style = MaterialTheme.typography.titleMedium)
                section.samples.forEach { (name, content) ->
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(text = name, style = MaterialTheme.typography.labelSmall)
                        content()
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
