package com.gildongmu.app

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pleos 내비와 함께 쓰기. 길 안내는 원래 내비가 맡고, 길동무는
 *  - 지금 위치를 받아 지도에 내 차를 놓고
 *  - "내비로 돌아가기"로 내비 화면을 다시 앞으로 부르고
 *  - "들렀다 가기"로 이야기가 남겨진 곳을 경유지로 넘긴다.
 */
class Navi(context: Context) {
    private val helper: NaviHelper? = runCatching { NaviHelper(context) }.onFailure { Log.w(TAG, "NaviHelper 없음", it) }.getOrNull()
    private var ready = false

    /** 내비가 알려 준 현재 위치 (위도, 경도) */
    var location by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            Log.d(TAG, "현재 위치 ${info.latitude}, ${info.longitude} (${info.address})")
            location = info.latitude to info.longitude
        }
    }

    fun start() {
        val h = helper ?: return
        runCatching {
            h.initialize({
                ready = true
                h.addListener(listener)
                h.getCurrentLocationInfo()
            }, { e -> Log.w(TAG, "내비 연결 실패", e) })
        }.onFailure { Log.w(TAG, "내비 연결 실패", it) }
    }

    fun refreshLocation() { if (ready) runCatching { helper?.getCurrentLocationInfo() } }

    /** 원래 내비 화면을 앞으로 */
    fun open(): Boolean = ready && runCatching { helper?.requestNavigationScreen(NavigationScreenRequest.Foreground) }
        .onFailure { Log.w(TAG, "내비 화면 열기 실패", it) }.isSuccess

    /** 이야기가 남겨진 곳을 경유지로 */
    fun addStop(lat: Double, lng: Double, name: String): Boolean = ready && runCatching {
        helper?.addWaypoint(RequestWaypointInfo(lat, lng, name, WaypointIndex.FIRST, "", "", "", ""))
    }.onFailure { Log.w(TAG, "경유지 추가 실패", it) }.isSuccess

    fun release() {
        runCatching { helper?.removeListener(listener); helper?.release() }
    }

    companion object { private const val TAG = "Gildongmu" }
}
