package com.staycalm.focus

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

@Composable
fun FocusMusicPlayer(selected:Ambient,onBrowse:()->Unit,onImport:()->Unit,importing:Boolean){
    val context=LocalContext.current
    val state=AudioPlayback.state
    val track=if(state.id.isNotEmpty())AudioLibrary(context).find(state.id) else selected
    var dragging by remember{mutableStateOf(false)}
    var seekPosition by remember{mutableFloatStateOf(0f)}
    var volume by remember{mutableFloatStateOf(FocusStore(context).soundVolume())}
    fun send(action:String,position:Long=0){
        val intent=Intent(context,SoundService::class.java).setAction(action).putExtra("sound",track.id).putExtra("position",position)
        try{if(action=="play"||action=="next"||action=="previous")context.startForegroundService(intent) else context.startService(intent)}
        catch(_:Exception){AudioPlayback.state=state.copy(error="Playback could not start. Please try again.")}
    }
    Surface(shape=RoundedCornerShape(24.dp),color=Color.White,border=BorderStroke(1.dp,lightRose)){
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Surface(color=lightRose,shape=RoundedCornerShape(14.dp)){Icon(Icons.Rounded.Headphones,null,tint=roseInk,modifier=Modifier.padding(13.dp).size(24.dp))}
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){Text("FOCUS PLAYER",fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp,color=roseInk);Text(track.label,fontWeight=FontWeight.Bold,color=plumInk,maxLines=2,overflow=TextOverflow.Ellipsis)}
                IconButton(onClick=onBrowse){Icon(Icons.Rounded.QueueMusic,"Choose audio",tint=roseInk)}
            }
            Text("Loops until your session ends",fontSize=12.sp,color=mutedInk)
            Slider(value=if(dragging)seekPosition else state.position.toFloat().coerceIn(0f,state.duration.coerceAtLeast(1).toFloat()),onValueChange={dragging=true;seekPosition=it},onValueChangeFinished={send("seek",seekPosition.toLong());dragging=false},valueRange=0f..state.duration.coerceAtLeast(1).toFloat(),enabled=state.duration>0 && !state.loading)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(FocusClock.displayTime(if(dragging)seekPosition.toLong() else state.position),fontSize=11.sp,color=mutedInk);Text(FocusClock.displayTime(state.duration),fontSize=11.sp,color=mutedInk)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={send("previous")}){Icon(Icons.Rounded.SkipPrevious,"Previous audio",tint=roseInk)}
                Spacer(Modifier.width(14.dp))
                FilledIconButton(onClick={send(if(state.playing)"pause" else "play")},enabled=!state.loading,modifier=Modifier.size(60.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=roseInk)){
                    if(state.loading)CircularProgressIndicator(Modifier.size(24.dp),color=Color.White,strokeWidth=2.dp)
                    else Icon(if(state.playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(state.playing)"Pause audio" else "Play audio",modifier=Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                IconButton(onClick={send("next")}){Icon(Icons.Rounded.SkipNext,"Next audio",tint=roseInk)}
            }
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.VolumeDown,null,tint=mutedInk);Slider(value=volume,onValueChange={volume=it;FocusStore(context).setVolume(it);if(state.id.isNotBlank())send("volume")},modifier=Modifier.weight(1f));Icon(Icons.Rounded.VolumeUp,null,tint=mutedInk)}
            if(state.error!=null)Text(state.error,color=roseInk,fontSize=12.sp)
            OutlinedButton(onClick=onImport,enabled=!importing,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                Icon(Icons.Rounded.Add,null);Spacer(Modifier.width(7.dp));Text(if(importing)"Importing audio…" else "Add your audio")
            }
        }
    }
}
