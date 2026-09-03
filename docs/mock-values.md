# Mock Values

By default a generated Preview shows `"mock"` for every string and `1` for every number:

```kotlin
Festival(
    festivalName = "mock",
    universityName = "mock",
    expectedVisitors = 1,
)
```

That renders, but it is neither the screen nor the data. Four characters hide every overflow, wrap and
ellipsis a real string would expose, and a screen showing `1` in every numeric field says as little as
one showing `"mock"`.

The information needed to do better is already read: PrevHam walks every declaration to build these
calls, so it knows the property is called `festivalName`, that it is a `String`, and that it sits
beside `universityName` in a package called `festabook`. A **mock value** is a value supplied for one
such place, written to a file that PrevHam reads while generating:

```kotlin
Festival(
    festivalName = "제 1회 대학 음악제",
    universityName = "서울대학교",
    expectedVisitors = 12000,
)
```

The file can be written by hand. A Gradle task can also fill it in by asking a language model, which
is where the property names and types earn their keep.

---

## The shape of it

```
[occasionally]  ./gradlew prevhamGenerateMockValues
                     │ reads the slots the compiler recorded
                     ├─ HTTPS ──→ a chat-completions endpoint
                     └─ merges into prevham/mock-values.json   ← committed

[every build]   KSP reads that file and injects the values
                     no network, no credentials, no model
```

Two files move between the two halves, in opposite directions:

| | Written by | Read by | Committed |
|---|---|---|---|
| `build/generated/prevham/mock-value-slots.json` | the compiler, every build | the task | no — a build output |
| `src/main/prevham/mock-values.json` | the task, or a person | the compiler, every build | **yes** |

### Why the build does not call the model

Calling the API from inside KSP was considered and rejected. It would make every build depend on the
network, need an API key on every machine that compiles and in CI, produce different Previews from
the same source, and make generation impossible to test deterministically.

Generating once and committing gives the opposite of each:

| | Called during the build | Generated once, committed |
|---|---|---|
| Reproducibility | different Previews from the same source | the file is the source of truth |
| Offline builds | broken | fine |
| API key | every teammate, and CI | one person, once |
| Cost | every build | one run |
| Reviewing a value | not possible | a diff, and editable by hand |
| Testing generation | non-deterministic | ordinary tests |

The premise is that **mock data is static content**. There is no reason to produce it again on every
build, and producing it once leaves it somewhere a person can read and correct — which matters,
because a model's answer is a guess and some of them are wrong.

---

## Using it

### Without a model

The task is not required. Write the file yourself:

```json
{
  "com.example.app.Festival.festivalName": "제 1회 대학 음악제",
  "com.example.app.Festival.expectedVisitors": "12000"
}
```

The keys are **slot paths**: the fully qualified declaring type, then the property. Getting one wrong
is silent, so read them from the manifest the compiler writes rather than typing them out:

```
app/build/generated/prevham/mock-value-slots.json
```

A blank value means "not decided yet" and falls back to the default, so a scaffolded file changes
nothing until it is filled in.

### With a model

```kotlin
// build.gradle.kts
prevham {
    language = "ko"
    baseUrl = "https://…/v1"
    model = "…"
}
```

```properties
# local.properties — gitignored by default
prevham.apiKey=…
```

```bash
./gradlew build                      # the compiler records the slots
./gradlew prevhamGenerateMockValues  # the model fills them in
```

```
[PrevHam] 27 slot(s), 27 without a value
[PrevHam] 27 new value(s); 27 in '…/mock-values.json'
```

Commit `mock-values.json`. Everyone else builds as usual — no key, no task, no network.

### The endpoint

Any endpoint speaking the shape most providers call **OpenAI-compatible**, which is a POST to
`{baseUrl}/chat/completions`:

| | `baseUrl` | Key |
|---|---|---|
| NVIDIA NIM | `https://integrate.api.nvidia.com/v1` | yes |
| OpenAI | `https://api.openai.com/v1` | yes |
| Ollama, on this machine | `http://localhost:11434/v1` | no |

`baseUrl` has **no default**. A default would endorse a provider, and would be half a convenience
anyway when a key and a model are needed regardless — so no build calls a service nobody named.
Unconfigured, the task writes the slot paths with empty values and leaves them to be filled in by
hand.

PrevHam depends on no vendor SDK. The request goes through the JDK's own HTTP client, because
anything this plugin depends on lands on every consumer's buildscript classpath.

### Where the key goes

Read in this order, and never from the build script:

1. `local.properties` — gitignored by default, and the Android convention
2. the `PREVHAM_API_KEY` environment variable — nothing on disk, for CI
3. the `prevham.apiKey` Gradle property, from `~/.gradle/gradle.properties`

Setting it in the **project's** `gradle.properties` is refused rather than warned about:

```
[PrevHam] 'prevham.apiKey' is set in /…/gradle.properties.

That file is normally committed, so the key would be published with the project. Move it to one of:
  local.properties            prevham.apiKey=…   (gitignored by default)
  ~/.gradle/gradle.properties prevham.apiKey=…   (outside the project)
  PREVHAM_API_KEY=…                              (an environment variable, nothing on disk)

Treat the key as compromised if this file has already been pushed.
```

Putting it there works perfectly, which is what makes it dangerous — there is nothing to notice until
the key is public. And by the time anyone reads a warning, the commit has usually happened.

A **missing** key warns only when the endpoint is not local: Ollama and anything else on this machine
needs none, and `answered 401` is a worse thing to read than the truth.

---

## Editing and re-running

A generated value is a guess, and correcting one is expected:

```json
"com.example.app.Poster.imageUrl": "https://cdn.example.com/posters/2026-main.jpg"
```

**A decided value is never overwritten.** Re-running asks only about slots with nothing in them, so a
hand-written answer survives every later run — which is the whole reason values live in a file rather
than in generated code.

```bash
./gradlew prevhamGenerateMockValues          # fills what is empty
./gradlew prevhamGenerateMockValues --force  # replaces everything
```

Adding a property therefore costs one slot rather than a whole regeneration:

```
[PrevHam] 28 slot(s), 1 without a value
[PrevHam] 1 new value(s); 28 in '…/mock-values.json'
```

---

## What can take a value

| | | |
|---|---|---|
| `String` | ✅ | The value is the literal |
| `Int` `Long` `Short` `Byte` `Double` `Float` | ✅ | Parsed, not trusted — see below |
| `Boolean` `Char` | ❌ | `true` is no better an answer than `false`, and one character carries nothing worth generating |
| dates, enums, user-defined types | ❌ | Turning `"2026-05-20"` into `LocalDate.of(2026, 5, 20)` needs a design per type |
| a member of a mocked type | ❌ | Reached by mocking rather than construction — see [#103](https://github.com/parkjiminnnn/PrevHam/issues/103) |

Numeric values are parsed before being emitted, because a value file is hand-edited and a generated
one is a model's guess:

| Configured | Emitted |
|---|---|
| `"12000"` | `12000` |
| `"about 400"` | `1` |
| `"45,000"` | `1L` |
| `9999` for a `Byte` | `1` |
| `1e400` for a `Double` | `1.0` |

Strings are escaped by KotlinPoet, so quotes and backslashes in a hand-edited file cannot produce a
literal that does not compile.

---

## Nothing here can fail a build

| | |
|---|---|
| No value file | Identical to not using the feature |
| File missing, unreadable, or malformed JSON | Warning, defaults used |
| A slot absent from the file | That one slot falls back |
| A blank value | Falls back — a blank means "not decided yet" |
| A number that is not a number, or does not fit | Falls back |
| The endpoint refuses, times out, or answers prose | Warning; those slots stay undecided |
| The model answers a key nobody asked about | That value is dropped, and counted |

An empty result reads exactly like never having run the task. The compiler treats a value file as a
hand-editable file rather than as a trusted source of Kotlin, and the task treats a reply as a guess
rather than as an answer.

---

## How a request is built

Slots travel **grouped by their declaring type**, because the siblings are the context:

```
type: Festival
  Festival.festivalName : String
  Festival.universityName : String
  Festival.expectedVisitors : Int
```

`festivalName` alone says little. Beside `universityName`, under a type called `Festival`, it says
what kind of app this is — and grouping also keeps a type's values agreeing with each other rather
than answered in isolation. Chunking never splits a type for that reason, and never puts two types
with the same simple name in one request, since the reply is keyed by that name.

Keys are short on purpose. Asking a model to echo
`com.daedan.festabook.domain.model.Festival.festivalName` back exactly is asking it to transcribe
rather than answer, and measured against a real project it failed: two of three values came back
altered enough to be dropped, and the one that survived had the shortest key. The full path is
restored from the request instead.

`response_format: json_object` is requested but not relied on — support varies by provider and by
model, and a reply that ignores it is handled like any other unusable one.

---

## Limits worth knowing

- **A model does not know your domain.** It sees names and types. `imageUrl` tends to come back as a
  placeholder URL, which costs nothing in practice — a Preview never loads one — and is exactly the
  kind of value the file exists to let someone correct, permanently.
- **A slot has to be reachable.** The manifest lists what generation actually filled, which is
  narrower than what the source appears to contain: cycle detection stops some paths, and PrevHam
  does not look inside compiled dependencies. `data class Node(val title: String, val next: Node?)`
  contributes one slot, not two.
- **Members of mocked types are out of reach**, so a composable taking a ViewModel gets no values
  today — [#103](https://github.com/parkjiminnnn/PrevHam/issues/103).
- **Nothing tells you a new property has no value yet** — [#104](https://github.com/parkjiminnnn/PrevHam/issues/104).
