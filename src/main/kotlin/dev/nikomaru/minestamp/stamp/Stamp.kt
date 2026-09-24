/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package dev.nikomaru.minestamp.stamp

import dev.nikomaru.minestamp.MineStamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.image.BufferedImage

abstract class Stamp(
    var shortCode: String
) : KoinComponent {
    val plugin: MineStamp by inject()
    lateinit var image: BufferedImage

    fun getStamp(): BufferedImage = image
}