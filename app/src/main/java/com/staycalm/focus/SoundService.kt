package com.staycalm.focus

import android.app.*
import android.content.*
import android.media.*
import android.os.Build

data class Ambient(val id:String,val label:String,val detail:String,val glyph:String)
object Ambients {
    val all=listOf(
        Ambient("rain","Soft rain","Gentle rain on glass","rain"),
        Ambient("ocean","Ocean tide","Slow coastal waves","water"),
        Ambient("birds","Dawn chorus","Birdsong and foliage","forest"),
        Ambient("stream","Flowing stream","Freshwater current","stream"),
        Ambient("fireplace","Fireside","Warm crackling logs","fire"),
        Ambient("wind","Night wind","A steady soft breeze","wind"),
        Ambient("thunder","Distant thunder","Rainclouds and rumble","storm")
    )
}

class SoundService:Service(){
    private val channel="staycalm_ambient"
    private var player:MediaPlayer?=null
    private var current:String=""
    private var audio:AudioManager?=null
    @Suppress("DEPRECATION")
    private val focusListener=AudioManager.OnAudioFocusChangeListener { state->
        if(state==AudioManager.AUDIOFOCUS_LOSS){stopSelf()}
        else if(state==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){player?.pause()}
        else if(state==AudioManager.AUDIOFOCUS_GAIN){player?.start()}
    }
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel,"Ambient audio",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action){
            "stop"->{stopSelf();return START_NOT_STICKY}
            "volume"->{applyVolume();return START_NOT_STICKY}
        }
        val sound=intent?.getStringExtra("sound")?:"rain"
        val label=Ambients.all.firstOrNull{it.id==sound}?.label?:"Ambient sound"
        val intentOpen=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val intentStop=PendingIntent.getService(this,1,Intent(this,SoundService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val note=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("STAYCALM · ${label}").setContentText("Ambient audio playing offline")
            .setContentIntent(intentOpen).setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause,"Stop sound",intentStop).build()).build()
        startForeground(24,note)
        if(current!=sound || player==null){releaseAudio()
            audio=getSystemService(AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            val granted=audio?.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if(!granted){stopSelf();return START_NOT_STICKY}
            val id=resources.getIdentifier(sound,"raw",packageName)
            if(id==0){stopSelf();return START_NOT_STICKY}
            player=MediaPlayer.create(this,id)?.apply{
                isLooping=true
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
                applyVolumeTo(this)
                start()
            }
            current=sound
            if(player==null)stopSelf()
        }else{applyVolume();if(player?.isPlaying==false)player?.start()}
        return START_NOT_STICKY
    }
    private fun applyVolumeTo(p:MediaPlayer){
        val v=FocusStore(this).soundVolume().coerceIn(0f,1f)
        p.setVolume(v,v)
    }
    private fun applyVolume(){player?.let{applyVolumeTo(it)}}
    private fun releaseAudio(){
        runCatching{player?.stop()};runCatching{player?.release()};player=null
        @Suppress("DEPRECATION")
        runCatching{audio?.abandonAudioFocus(focusListener)}
    }
    override fun onDestroy(){releaseAudio();super.onDestroy()}
}
