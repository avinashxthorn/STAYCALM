package com.staycalm.focus

import android.app.*
import android.content.*
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.*
import java.io.File

// The service owns playback; the UI only observes it. No Activity reference is retained.
data class AudioStatus(val id:String="",val playing:Boolean=false,val loading:Boolean=false,val position:Long=0,val duration:Long=0,val error:String?=null)
object AudioPlayback { var state by mutableStateOf(AudioStatus()); internal set }

class SoundService:Service(){
    private val channel="staycalm_ambient"
    private var player:MediaPlayer?=null
    private var track:Ambient?=null
    private var podcastUrl:String?=null
    private var podcastTitle:String?=null
    private var prepared=false
    private var playWhenReady=true
    private var resumeOnGain=false
    private var ducked=false
    private lateinit var audio:AudioManager
    private lateinit var session:MediaSession
    private lateinit var focusRequest:AudioFocusRequest
    private val handler=Handler(Looper.getMainLooper())
    private val pulse=object:Runnable{override fun run(){
        if(FocusStore(this@SoundService).completeIfDue())return
        publish()
        handler.postDelayed(this,500)
    }}
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){
        super.onCreate()
        audio=getSystemService(AudioManager::class.java)
        focusRequest=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({change->
                when(change){
                    AudioManager.AUDIOFOCUS_LOSS->{resumeOnGain=false;pausePlayback();audio.abandonAudioFocusRequest(focusRequest)}
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT->{resumeOnGain=prepared && player?.isPlaying==true;pausePlayback(manual=false)}
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK->{ducked=true;applyVolume()}
                    AudioManager.AUDIOFOCUS_GAIN->{ducked=false;applyVolume();if(resumeOnGain){resumeOnGain=false;playPrepared()}}
                }
            },handler).build()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"Focus music",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null)})
        session=MediaSession(this,"STAYCALM focus player").apply{
            setCallback(object:MediaSession.Callback(){
                override fun onPlay(){resumePlayback()}
                override fun onPause(){pausePlayback()}
                override fun onStop(){stopSelf()}
                override fun onSeekTo(pos:Long){seek(pos)}
                override fun onSkipToNext(){skip(1)}
                override fun onSkipToPrevious(){skip(-1)}
            },handler)
            isActive=true
        }
        handler.post(pulse)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action){
            "stop"->{stopSelf();return START_NOT_STICKY}
            "pause"->{pausePlayback();return START_NOT_STICKY}
            "seek"->{seek(intent.getLongExtra("position",0));return START_NOT_STICKY}
            "volume"->{applyVolume();if(player==null)stopSelf();return START_NOT_STICKY}
        }
        // Must enter the foreground before requesting audio focus on Android 15.
        startForeground(24,notification())
        when(intent?.action){
            "next"->skip(1)
            "previous"->skip(-1)
            "play"->resumePlayback()
            "play_podcast"->{
                val url=StudyMediaLinks.secureAudioUrl(intent.getStringExtra("podcast_url").orEmpty())
                val title=intent.getStringExtra("podcast_title").orEmpty().take(110)
                if(url==null)fail("This podcast link is not a valid HTTPS audio URL.")
                else if(podcastUrl==url && player!=null)resumePlayback()
                else loadPodcast(url,title.ifBlank{"Podcast episode"})
            }
            else->{
                val selected=AudioLibrary(this).find(intent?.getStringExtra("sound")?:FocusStore(this).lastSound())
                if(track?.id==selected.id && player!=null)resumePlayback() else load(selected)
            }
        }
        return START_NOT_STICKY
    }
    private fun load(selected:Ambient){
        releasePlayer()
        playWhenReady=true
        podcastUrl=null
        podcastTitle=null
        track=selected
        FocusStore(this).setSound(selected.id)
        AudioPlayback.state=AudioStatus(id=selected.id,loading=true)
        updateNotification()
        try{
            val p=MediaPlayer()
            player=p
            p.setAudioAttributes(attributes)
            p.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK)
            p.isLooping=true
            if(selected.localFile!=null){p.setDataSource(File(filesDir,"audio/${selected.localFile}").absolutePath)}
            else resources.openRawResourceFd(selected.raw).use{p.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
            p.setOnPreparedListener{
                if(player===it){prepared=true;it.isLooping=true;if(playWhenReady)playPrepared() else {publish();updateNotification()}}
            }
            p.setOnSeekCompleteListener{publish();updateSession()}
            p.setOnErrorListener{_,_,_->fail("This file cannot be played. Choose another audio file.");true}
            p.prepareAsync()
        }catch(_:Exception){fail("Could not open this audio. Try importing it again.")}
    }
    private fun loadPodcast(url:String,title:String){
        releasePlayer()
        track=null
        podcastUrl=url
        podcastTitle=title
        playWhenReady=true
        AudioPlayback.state=AudioStatus(id="podcast",loading=true)
        updateNotification()
        try{
            val p=MediaPlayer()
            player=p
            p.setAudioAttributes(attributes)
            p.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK)
            p.isLooping=false
            p.setDataSource(url)
            p.setOnPreparedListener{
                if(player===it){prepared=true;if(playWhenReady)playPrepared() else {publish();updateNotification()}}
            }
            p.setOnCompletionListener{ if(player===it)stopSelf() }
            p.setOnSeekCompleteListener{publish();updateSession()}
            p.setOnErrorListener{_,_,_->fail("Unable to stream this episode. Choose another HTTPS podcast.");true}
            p.prepareAsync()
        }catch(_:Exception){fail("Could not open this podcast stream. Check your connection.")}
    }
    private fun resumePlayback(){
        playWhenReady=true
        if(player==null){
            if(podcastUrl!=null)loadPodcast(podcastUrl!!,podcastTitle?:"Podcast")
            else load(AudioLibrary(this).find(FocusStore(this).lastSound()))
            return
        }
        if(prepared)playPrepared()
    }
    private fun playPrepared(){
        if(!prepared)return
        try{
            startForeground(24,notification())
            if(audio.requestAudioFocus(focusRequest)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                AudioPlayback.state=AudioPlayback.state.copy(playing=false,loading=false,error="Audio is busy. Tap play to try again.")
                return
            }
            applyVolume();player?.start();publish();updateNotification()
        }catch(_:Exception){fail("Playback could not start. Please try another file.")}
    }
    private fun pausePlayback(manual:Boolean=true){
        playWhenReady=false
        if(manual){resumeOnGain=false;audio.abandonAudioFocusRequest(focusRequest)}
        if(prepared)runCatching{player?.pause()}
        publish();updateNotification()
    }
    private fun seek(position:Long){
        if(prepared)runCatching{player?.seekTo(position.coerceIn(0,(player?.duration?.toLong()?:0).coerceAtLeast(0)),MediaPlayer.SEEK_CLOSEST)}
    }
    private fun skip(direction:Int){
        if(podcastUrl!=null)return
        val choices=AudioLibrary(this).all()
        val index=choices.indexOfFirst{it.id==track?.id}.coerceAtLeast(0)
        load(choices[(index+direction+choices.size)%choices.size])
    }
    private fun publish(){
        val p=player
        if(prepared && p!=null)runCatching{
            AudioPlayback.state=AudioStatus(if(podcastUrl!=null)"podcast" else track?.id.orEmpty(),p.isPlaying,false,p.currentPosition.toLong(),p.duration.toLong())
            updateSession()
        }
    }
    private fun updateSession(){
        val state=AudioPlayback.state
        val status=if(state.loading)PlaybackState.STATE_BUFFERING else if(state.playing)PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(status,state.position,if(state.playing)1f else 0f).build())
    }
    private fun action(name:String):PendingIntent {
        val intent=Intent(this,SoundService::class.java).setAction(name)
        return if(name=="play"||name=="next"||name=="previous")PendingIntent.getForegroundService(this,name.hashCode(),intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        else PendingIntent.getService(this,name.hashCode(),intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    private fun notification():Notification {
        val state=AudioPlayback.state
        val isPodcast=podcastUrl!=null
        val title=if(isPodcast)podcastTitle?:"Podcast" else track?.label?:"Focus music"
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title)
            .setContentText(if(state.loading)"Preparing your audio…" else if(state.playing)
                if(isPodcast)"Podcast streaming · focus session continues" else "Looping until your session ends"
                else "Paused · resume when you’re ready")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(state.playing).setVisibility(Notification.VISIBILITY_PUBLIC)
        if(!isPodcast)builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous",action("previous")).build())
        builder.addAction(Notification.Action.Builder(if(state.playing)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,if(state.playing)"Pause" else "Play",action(if(state.playing)"pause" else "play")).build())
        if(!isPodcast)builder.addAction(Notification.Action.Builder(android.R.drawable.ic_media_next,"Next",action("next")).build())
        builder.addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",action("stop")).build())
        return builder.setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(if(isPodcast)0 else 0,if(isPodcast)1 else 1,if(isPodcast)1 else 2)).build()
    }
    private fun updateNotification(){
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,podcastTitle?:track?.label?:"Focus music").putString(MediaMetadata.METADATA_KEY_ARTIST,"STAYCALM").putLong(MediaMetadata.METADATA_KEY_DURATION,AudioPlayback.state.duration).build())
        updateSession()
        runCatching{getSystemService(NotificationManager::class.java).notify(24,notification())}
    }
    private fun applyVolume(){
        val volume=FocusStore(this).soundVolume().coerceIn(0f,1f)*(if(ducked).2f else 1f)
        if(prepared)runCatching{player?.setVolume(volume,volume)}
    }
    private fun fail(message:String){
        AudioPlayback.state=AudioStatus(id=track?.id.orEmpty(),error=message)
        stopSelf()
    }
    private fun releasePlayer(){prepared=false;runCatching{player?.release()};player=null;resumeOnGain=false}
    override fun onDestroy(){
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        audio.abandonAudioFocusRequest(focusRequest)
        session.isActive=false;session.release()
        val error=AudioPlayback.state.error
        AudioPlayback.state=AudioStatus(error=error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
