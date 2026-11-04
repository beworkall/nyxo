package com.beworkall.nyxo.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.beworkall.nyxo.R
import com.beworkall.nyxo.data.Prefs

enum class Sfx(val res: Int, val gain: Float) {
    TAP(R.raw.s_tap, 0.6f),
    TICK(R.raw.s_tick, 0.55f),
    OPEN(R.raw.s_open, 0.5f),
    LAUNCH(R.raw.s_launch, 0.6f),
    UNLOCK(R.raw.s_unlock, 0.7f),
    GLASS(R.raw.s_glass, 0.6f),
    DROP(R.raw.s_drop, 0.6f),
}

/**
 * Small UI sound player.
 * Stays silent when the phone is on silent or vibrate, or when sounds are turned off.
 */
object Sound {
    private var pool: SoundPool? = null
    private val ids = HashMap<Sfx, Int>()
    private val ready = HashSet<Int>()
    private var audio: AudioManager? = null
    private var prefs: Prefs? = null
    private var lastTick = 0L

    fun init(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        prefs = Prefs.get(app)
        audio = app.getSystemService(AudioManager::class.java)
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        p.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) ready.add(sampleId) }
        for (s in Sfx.values()) ids[s] = p.load(app, s.res, 1)
        pool = p
    }

    fun play(sfx: Sfx) {
        val p = pool ?: return
        val pr = prefs ?: return
        if (!pr.sounds.value) return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = ids[sfx] ?: return
        if (id !in ready) return
        var rate = 1f
        if (sfx == Sfx.TICK) {
            // Fast scrolling: skip ticks closer than 40 ms, and vary the pitch a touch so it sounds like a real detent.
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastTick < 40) return
            lastTick = now
            rate = 0.96f + (now % 9) * 0.01f
        }
        val v = (pr.soundVolume.value * sfx.gain).coerceIn(0f, 1f)
        p.play(id, v, v, 1, 0, rate)
    }

    /** The launch sound matches the panel style: glass clinks, water drops. */
    fun launch() {
        when (prefs?.panelStyle?.value) {
            "glass", "hardglass" -> play(Sfx.GLASS)
            "water" -> play(Sfx.DROP)
            else -> play(Sfx.LAUNCH)
        }
    }
}
