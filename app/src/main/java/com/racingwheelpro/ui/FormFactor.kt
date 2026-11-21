package com.racingwheelpro.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A 6 by 4.5 inch landscape panel is 960 by 720 dp: 4:3, about 7.5 inches on the diagonal.
 * Phone landscape is much shorter and wider, so only that squarer window uses the tablet chrome.
 * Shifter zones keep the phone defaults and the editor resize. See [com.racingwheelpro.input.ShifterZones].
 */
fun isTabletCanvas(width: Dp, height: Dp): Boolean {
    if (height < 560.dp || width < 720.dp) return false
    val aspect = width.value / height.value
    return aspect in 1.15f..1.55f
}

data class ScreenChrome(
    val tablet: Boolean,
    val ledHeight: Dp,
    val topBar: Dp,
    val bottomBar: Dp,
    val titleSp: TextUnit,
    val navSp: TextUnit,
    val navPadH: Dp,
    val navPadV: Dp,
    val pedalMin: Dp,
    val pedalMax: Dp,
    val pedalValueSp: TextUnit,
    val pedalTitleSp: TextUnit,
    val clutchHeight: Dp,
    val statusSp: TextUnit,
    val zoneLabelScale: Float,
    val editorPanel: Dp,
    val setupNav: Dp,
    val garageCard: Dp,
    val miniPadH: Dp,
    val miniPadV: Dp,
)

fun phoneChrome() = ScreenChrome(
    tablet = false,
    ledHeight = 12.dp,
    topBar = 46.dp,
    bottomBar = 28.dp,
    titleSp = 13.sp,
    navSp = 11.sp,
    navPadH = 8.dp,
    navPadV = 8.dp,
    pedalMin = 72.dp,
    pedalMax = 190.dp,
    pedalValueSp = 16.sp,
    pedalTitleSp = 10.sp,
    clutchHeight = 54.dp,
    statusSp = 11.sp,
    zoneLabelScale = 1f,
    editorPanel = 300.dp,
    setupNav = 150.dp,
    garageCard = 280.dp,
    miniPadH = 8.dp,
    miniPadV = 4.dp,
)

/** Larger strips and type for a 4:3 tablet held in landscape. */
fun tabletChrome() = phoneChrome().copy(
    tablet = true,
    ledHeight = 18.dp,
    topBar = 64.dp,
    bottomBar = 52.dp,
    titleSp = 16.sp,
    navSp = 14.sp,
    navPadH = 16.dp,
    navPadV = 14.dp,
    pedalMin = 136.dp,
    pedalMax = 220.dp,
    pedalValueSp = 28.sp,
    pedalTitleSp = 13.sp,
    clutchHeight = 88.dp,
    statusSp = 15.sp,
    zoneLabelScale = 1.5f,
    editorPanel = 400.dp,
    setupNav = 210.dp,
    garageCard = 360.dp,
    miniPadH = 16.dp,
    miniPadV = 12.dp,
)

val LocalChrome = staticCompositionLocalOf { phoneChrome() }
