# Motion

Every new page uses this module. Do not add a one-off duration, a zoom on focus, or a raw `LazyRow` for a poster strip.

Package: `com.xxxx.emby_tv.ui.motion`.

## Tokens

One curve, `cubic-bezier(0.2, 0, 0, 1)`, on `Motion.ease`.

| Token | Time | Use |
|---|---|---|
| `Motion.focus()` | 200 ms | Focus color, season fade |
| `Motion.row()` | 280 ms | One Left or Right press along a row |
| `Motion.repeatStep()` | 90 ms, linear | Key repeat while Left or Right is held |
| `Motion.page()` | 300 ms | Up or Down between rows, details scroll |
| `Motion.fade()` | 240 ms | Screens, tabs, dialogs, the player bar |
| `Motion.imageMs` | 180 ms | Poster and backdrop fade-in |

No scale and no zoom. Focus on a poster is a white stroke with a slow breathing pulse. Pills and action buttons use a solid white fill.

## What to call

| You are building | Call |
|---|---|
| A horizontal poster or card row | `TvLeadingRow`. It locks the focused card in the leading slot, eases one press, and glides while the key is held. Pass `imageUrls` and `backdropUrls` so the next cards and the details backdrop are already cached. |
| Moving Up or Down between homepage rows | `rememberStageScroll`. The focused row docks at one mid-upper stage. The previous row only peeks above it and the next row only peeks below. A held key replaces the running ease. Index 0 is the hero and scrolls to the true top. |
| Moving Up or Down on other pages | `rememberRowGlide(listState, bringIntoView).to(focus, index, pin)`. `pin = true` only for the top of the page. It cancels the previous glide, so a held key does not queue animations, and it still finishes at scroll 0. |
| A poster grid | `TvLazyGrid`. It installs the same page ease and refuses the platform's 30% focus pivot. |
| A poster, thumb, or backdrop | `FadeInImage` (or `HomeImage`, which calls it). Prefetch with `ArtworkPrefetch.enqueue` at `ArtworkPrefetch.pixels(role)`. The sizes must match or the cache misses. |
| Opening another screen | `NavHost` already uses `ScreenTransition`. Do not add a second transition. |
| A tab, season, or result set whose focus stays on a control outside the content | `ContentCrossfade`. Do not crossfade the node that currently holds focus. |
| A dialog, empty state, or spinner | `MotionFade`. |
| The player bar, side sheet, episode strip, or next-episode card | `ScreenTransition.barEnter`, `sheetEnter`, `stripEnter`, `cardEnter` and the matching exit. |
| Back should return to the item that opened this screen | `rememberFocusRestore()`. Call `mark(row, id)` before navigating. On the way back, pass `initialIndex` to `TvLeadingRow` and the lazy list's `initialFirstVisibleItemIndex` so the item is already in place. Then `RestoreFocus` requests focus. Do not animate that scroll. |

`TvFocusHighlight` is the breathing stroke. It draws in the draw phase. Do not read its alpha in the row.

## Homepage stage

`StageDock` is the vertical window for the homepage. Do not use it to add a hero on other pages.

- `StageDock.offset` places a row so its top sits at `stagePx`. Index 0 always returns 0, which brings the hero back.
- `StageDock.bottomSpacer` is the empty space under the last row. Without it the last row stops at the bottom of the screen and the Up that leaves it travels a different distance.
- `StageDock.titleScale` is 1 on the stage and `0.68` a row away. Apply it with `graphicsLayer` on the title only. Do not scale posters.
- `StageScroll.dock` animates with `Motion.page`. Keys closer than 160 ms use `Motion.repeatStep` and cancel the previous scroll.
- The homepage hero is the first screen. 搜索, 首页, and 收藏 sit in one dark rounded capsule on the left of the backdrop. The white pill appears only while focus is inside that capsule, and it eases onto the focused item. A selected season that does not have focus stays a gray pill (`FocusPill`). One Down still docks the first row. Up from that row returns the hero.
- `LeadingPeek` keeps the focused card at a fixed inset. When the row has moved, the previous card is clipped by the screen edge so a sliver remains. The first card has no previous item, so there is no peek. Homepage rows, the episode strip, the cast row, and 更多类似的 all use it. A poster row at 960dp with a 32dp inset, 124dp card, and 20dp gap shows about six and a half posters. Corners are 14dp.
- The library grid is six columns, not a sideways row, so a row starts at the left with no peek. `StageDock.libraryRowOffset` leaves the first row under the title and docks later rows so the previous titles still peek. `筛选` opens a right-hand panel; Back leaves a category list and then closes the panel. A selected filter toggles off with OK. `排序` is a short list. Both refresh the grid through the Emby items query.
- Movie and series details use the same stage: the hero and action row stay at scroll 0, and later blocks (episodes, cast, collections, 更多类似的) dock with a peek of the previous block. A version control is hidden for a single media source. When several exist, focus widens the diamond into the current label, and OK opens the list. Collection rows are titled `合集名中的其他作品` and omit the title you are on.
- `AmbientWash` is a low directional tint from the focused artwork. `ArtworkWash.sampleMuted` keeps mid-tone, slightly saturated pixels (a darkMuted-style swatch, not a full-screen recolor). It crossfades with `Motion.fade`. On the hero its strength is 0 so the backdrop stays in charge. If the bitmap or the swatch is missing, the wash stays transparent and the dark background shows through.

## A new page

```kotlin
val restore = rememberFocusRestore()
val listState = rememberLazyListState(
    initialFirstVisibleItemIndex = if (restore.rowKey == "posters") 1 else 0,
)
val bringIntoView = remember { TvBringIntoView() }
val glide = rememberRowGlide(listState, bringIntoView)
val rowFocus = remember { FocusRequester() }

ProvideTvBringIntoView(
    controller = bringIntoView,
    viewportScroll = { listState.viewportScroll() },
    scrolledPastStart = { listState.scrolledPastStart() },
) {
    LazyColumn(state = listState) {
        item {
            TvLeadingRow(
                items = items,
                cardWidth = HomeStyle.posterWidth,
                cardHeight = HomeStyle.posterHeight,
                corner = HomeStyle.cardCorner,
                focusRequester = rowFocus,
                up = null,
                down = null,
                initialIndex = if (restore.rowKey == "posters") restore.indexIn(items) else -1,
                imageUrls = items.map { posterUrl(it) },
                backdropUrls = items.map { backdropUrl(it) },
                onActivate = { item ->
                    item.id?.let { restore.mark("posters", it) }
                    open(item)
                },
                onMenu = {},
                caption = { item -> Text(item.name.orEmpty()) },
            ) { item ->
                HomeImage(
                    itemId = item.id,
                    fallbackArtId = null,
                    url = posterUrl(item),
                    role = ArtRole.Primary,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
```

Details backdrops blur on the GPU from API 31 (`FadeInImage` with `soften = true`). Older devices use `HeavyBlurTransformation`, which Coil caches. Prefetch that backdrop with `ArtworkPrefetch.enqueueBackdrops` when the poster is focused, not when the user presses Down.

The release APK is the build that actually feels like this. Debug builds skip baseline-profile compilation.
