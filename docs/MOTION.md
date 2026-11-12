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
| Moving Up or Down between those rows | `rememberRowGlide(listState, bringIntoView).to(focus, index, pin)`. `pin = true` only for the top of the page. It cancels the previous glide, so a held key does not queue animations, and it still finishes at scroll 0. |
| A poster grid | `TvLazyGrid`. It installs the same page ease and refuses the platform's 30% focus pivot. |
| A poster, thumb, or backdrop | `FadeInImage` (or `HomeImage`, which calls it). Prefetch with `ArtworkPrefetch.enqueue` at `ArtworkPrefetch.pixels(role)`. The sizes must match or the cache misses. |
| Opening another screen | `NavHost` already uses `ScreenTransition`. Do not add a second transition. |
| A tab, season, or result set whose focus stays on a control outside the content | `ContentCrossfade`. Do not crossfade the node that currently holds focus. |
| A dialog, empty state, or spinner | `MotionFade`. |
| The player bar, side sheet, episode strip, or next-episode card | `ScreenTransition.barEnter`, `sheetEnter`, `stripEnter`, `cardEnter` and the matching exit. |
| Back should return to the item that opened this screen | `rememberFocusRestore()`. Call `mark(row, id)` before navigating. On the way back, pass `initialIndex` to `TvLeadingRow` and the lazy list's `initialFirstVisibleItemIndex` so the item is already in place. Then `RestoreFocus` requests focus. Do not animate that scroll. |

`TvFocusHighlight` is the breathing stroke. It draws in the draw phase. Do not read its alpha in the row.

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
