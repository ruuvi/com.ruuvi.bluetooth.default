package com.ruuvi.station.bluetooth.util.extensions

import java.math.RoundingMode
import kotlin.Double

fun Double.roundHalfUp(places: Int): Double =
    this.toBigDecimal().setScale(places, RoundingMode.HALF_UP).toDouble()