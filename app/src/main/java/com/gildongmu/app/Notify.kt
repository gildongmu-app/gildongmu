package com.gildongmu.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 원래 내비가 화면을 맡고 있을 때: 지나는 길의 한마디를 시스템 알림(헤드업)으로 띄운다.
 * (차에서 주행 중 표시 여부는 Pleos 정책을 따른다)
 */
object Notify {
    private const val CHANNEL = "nearby_words"

    fun nearby(ctx: Context, p: Place) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "지나는 길의 한마디", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "다른 길동무가 남긴 한마디를 지나갈 때 알려 줘요"
            })
        }
        val body = p.diary?.let { "공유 일기 · ${it.title}" } ?: p.lead?.text ?: ""
        val title = when (p.kind) {
            PlaceKind.CAUTION -> "주의 · ${p.name}"
            PlaceKind.DIARY -> "${p.name}에서 쓴 일기"
            PlaceKind.VOICE -> "${p.name} · ${p.lead?.author ?: "길동무"}님의 한마디"
        }
        val open = PendingIntent.getActivity(
            ctx, p.id.hashCode(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("place", p.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        runCatching { nm.notify(p.id.hashCode(), n) }
    }
}
