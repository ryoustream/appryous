package com.ryou.appryous.player.mpv

/**
 * Alias/typealias ke is.xyz.mpv.MPVLib agar kode app tidak berubah.
 *
 * libmpv.so dari mpv-android mencari JNI functions di package is.xyz.mpv.
 * Semua fungsi sebenarnya ada di is/xyz/mpv/MPVLib.kt.
 * File ini hanya typealias agar import di PlayerActivity tetap bekerja.
 */
typealias MPVLib = `is`.xyz.mpv.MPVLib
