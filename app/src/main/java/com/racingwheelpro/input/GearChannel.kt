package com.racingwheelpro.input

import com.racingwheelpro.proto.Proto

/**
 * The paddle is its own channel. Shift up sets bit 0 of the gear byte and shift down
 * sets bit 1. An optional virtual button is OR'd into the mask. Button 0 adds nothing,
 * so a paddle tap cannot light the other buttons.
 */
object GearChannel {
    fun bits(shiftUp: Boolean, shiftDown: Boolean): Int {
        var gear = 0
        if (shiftUp) gear = gear or Proto.GEAR_UP
        if (shiftDown) gear = gear or Proto.GEAR_DOWN
        return gear
    }

    fun merge(
        buttons: Long,
        shiftUp: Boolean,
        shiftDown: Boolean,
        upButton: Int,
        downButton: Int,
    ): Long {
        var mask = buttons
        // Assetto Corsa reads vJoy buttons 1 and 2. The optional profile
        // numbers are extra copies, not a replacement.
        if (shiftUp) {
            mask = mask or buttonBit(1)
            mask = mask or buttonBit(upButton)
        }
        if (shiftDown) {
            mask = mask or buttonBit(2)
            mask = mask or buttonBit(downButton)
        }
        return mask
    }

    fun buttonBit(button: Int): Long =
        if (button in 1..32) 1L shl (button - 1) else 0L
}
