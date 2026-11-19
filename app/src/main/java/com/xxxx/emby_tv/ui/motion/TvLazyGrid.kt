package com.xxxx.emby_tv.ui.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xxxx.emby_tv.ui.focus.ProvideTvBringIntoView
import com.xxxx.emby_tv.ui.focus.TvBringIntoView

/**
 * Vertical poster grid. Focus scrolling uses [Motion.page] and the TV
 * bring-into-view spec, so a row lands on the edge instead of the 30% pivot.
 * Pass the same [bringIntoView] the cells use when they pin the first row.
 */
@Composable
fun TvLazyGrid(
    columns: GridCells,
    state: LazyGridState,
    bringIntoView: TvBringIntoView,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyGridScope.() -> Unit,
) {
    ProvideTvBringIntoView(
        controller = bringIntoView,
        viewportScroll = { state.firstVisibleItemScrollOffset.toFloat() },
        scrolledPastStart = { state.firstVisibleItemIndex > 0 },
    ) {
        LazyVerticalGrid(
            columns = columns,
            state = state,
            modifier = modifier,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
