package com.staycalm.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlin.math.*

internal val roseInk=Color(0xFFAC345F)
internal val plumInk: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFFF9E7F0) else Color(0xFF542536)
internal val mutedInk: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFFD4ACBE) else Color(0xFF88616F)
internal val lightRose: Color
    @Composable get() = if(LocalStaycalmDark.current) Color(0xFF422634) else Color(0xFFFCE8EE)
private val turtleGreen=Color(0xFFD37496)

@Composable
fun StudioFocus(
    durationMinutes:Int, remainingMs:Long, running:Boolean, totalMinutes:Int,
    minutesToday:Int, dailyGoal:Int, selectedSound:Ambient, onImport:()->Unit, importing:Boolean,
    mode:String, intention:String, onIntention:(String)->Unit, onMode:(String)->Unit,
    onDuration:(Int)->Unit, onToggle:()->Unit, onAbandon:()->Unit,
    onSoundSelect:()->Unit, onOpenPlanner:()->Unit
){
    val progress=FocusClock.progress(remainingMs,durationMinutes)
    val cardGradient = if(LocalStaycalmDark.current)
        Brush.verticalGradient(listOf(Color(0xFF372333),Color(0xFF281B2B)))
        else Brush.verticalGradient(listOf(Color(0xFFFFF5F8),Color(0xFFF9DCE7)))
    val animatedProgress by animateFloatAsState(progress,tween(300,easing=LinearEasing),label="session progress")
    val active=running || progress>0f
    var custom by remember{mutableStateOf(false)}
    var customMinutes by remember(durationMinutes){mutableFloatStateOf(durationMinutes.toFloat())}
    val stage=Evolution.stage(totalMinutes)
    val next=Evolution.next(totalMinutes)
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)){
        Column{
            Text("A LITTLE SPACE, JUST FOR YOU",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp,color=roseInk)
            Spacer(Modifier.height(6.dp))
            Text(if(mode=="focus")"Let your focus bloom." else "Rest is part of progress.",fontSize=29.sp,fontWeight=FontWeight.Bold,color=plumInk,letterSpacing=(-1).sp,lineHeight=36.sp)
        }
        Surface(shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
            Column(Modifier.fillMaxWidth().background(cardGradient).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha=.6f),CircleShape).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    listOf("focus" to "Focus","break" to "Short break").forEach{(id,label)->
                        val selected=mode==id
                        val tint by animateColorAsState(if(selected)roseInk else Color.Transparent,label="mode selection")
                        Surface(onClick={onMode(id)},enabled=!active,modifier=Modifier.weight(1f).height(46.dp),shape=CircleShape,color=tint){
                            Box(contentAlignment=Alignment.Center){Text(label,color=if(selected)Color.White else mutedInk,fontSize=13.sp,fontWeight=FontWeight.SemiBold)}
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Box(Modifier.fillMaxWidth().heightIn(max=270.dp).aspectRatio(1f),contentAlignment=Alignment.Center){
                    Canvas(Modifier.fillMaxSize().clearAndSetSemantics{}){
                        val radius=size.minDimension*.44f
                        drawCircle(Color.White.copy(alpha=.38f),radius=radius*.87f)
                        drawCircle(Color(0xFFEFC0D1),radius=radius,style=Stroke(5.dp.toPx()))
                        for(i in 0 until 60){
                            val a=(i*6-90)*PI.toFloat()/180f
                            val r=radius-14.dp.toPx()
                            val length=if(i%5==0)7.dp.toPx() else 3.dp.toPx()
                            drawLine(Color(0xFFCC8CA3).copy(alpha=if(i%5==0).7f else .35f),center+Offset(cos(a)*r,sin(a)*r),center+Offset(cos(a)*(r-length),sin(a)*(r-length)),2f)
                        }
                        drawArc(roseInk,-90f,360f*animatedProgress,false,Offset(center.x-radius,center.y-radius),Size(radius*2,radius*2),style=Stroke(5.dp.toPx(),cap=StrokeCap.Round))
                        val angle=(animatedProgress*360-90)*PI.toFloat()/180f
                        drawCircle(roseInk,6.dp.toPx(),center+Offset(cos(angle)*radius,sin(angle)*radius))
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(if(mode=="focus")Icons.Rounded.LocalFlorist else Icons.Rounded.Coffee,null,tint=roseInk,modifier=Modifier.size(24.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(FocusClock.displayTime(remainingMs),fontSize=49.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-2).sp,color=plumInk,style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"))
                        Text(if(running)"YOU’RE DOING BEAUTIFULLY" else if(active)"PAUSED • TAKE YOUR TIME" else "ONE MOMENT AT A TIME",fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,color=mutedInk)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    (if(mode=="focus")listOf(25,45,60) else listOf(5,10,15)).forEach{minutes->
                        Surface(onClick={onDuration(minutes)},enabled=!active,modifier=Modifier.weight(1f).height(48.dp),shape=RoundedCornerShape(14.dp),color=if(durationMinutes==minutes)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha=.2f),border=if(durationMinutes==minutes)BorderStroke(1.dp,Color(0xFFE8B0C5)) else null){
                            Box(contentAlignment=Alignment.Center){Text("${minutes}m",fontSize=13.sp,fontWeight=FontWeight.Bold,color=plumInk)}
                        }
                    }
                    IconButton(onClick={custom=true},enabled=!active,modifier=Modifier.size(48.dp)){Icon(Icons.Rounded.Tune,"Custom duration",tint=roseInk)}
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick=onToggle,modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=roseInk)){
                    Icon(if(running)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null)
                    Spacer(Modifier.width(8.dp))
                    Text(if(running)"Pause session" else if(active)"Resume session" else if(mode=="break")"Start break" else "Start focus",fontSize=16.sp,fontWeight=FontWeight.Bold)
                }
                AnimatedVisibility(active){TextButton(onClick=onAbandon){Text("End session",color=roseInk)}}
            }
        }
        if(mode=="focus"){
            OutlinedTextField(value=intention,onValueChange={onIntention(it.take(120))},enabled=!active,label={Text("What deserves your attention?")},placeholder={Text("One small intention…")},leadingIcon={Icon(Icons.Rounded.EditNote,null,tint=roseInk)},singleLine=true,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth())
        }
        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,lightRose)){
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Surface(color=lightRose,shape=CircleShape){Icon(Icons.Rounded.WbSunny,null,tint=roseInk,modifier=Modifier.padding(10.dp).size(21.dp))}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text("Your daily bloom",fontWeight=FontWeight.Bold,color=plumInk);Text("${minutesToday} of ${dailyGoal} minutes",fontSize=12.sp,color=mutedInk)}
                    Text("${(minutesToday*100/dailyGoal.coerceAtLeast(1)).coerceAtMost(100)}%",fontSize=20.sp,fontWeight=FontWeight.Bold,color=roseInk)
                }
                LinearProgressIndicator(progress={(minutesToday.toFloat()/dailyGoal.coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),color=roseInk,trackColor=lightRose)
            }
        }
        FocusMusicPlayer(selectedSound,onSoundSelect,onImport,importing)
        Surface(shape=RoundedCornerShape(26.dp),color=lightRose){
            Column(Modifier.fillMaxWidth().padding(20.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("GROW TOGETHER",fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,color=roseInk);Text(Evolution.title(stage),fontSize=18.sp,fontWeight=FontWeight.Bold,color=plumInk)}
                    Text("${stage+1}/6",fontSize=12.sp,color=roseInk,fontWeight=FontWeight.Bold)
                }
                TurtleAtelier(stage,Modifier.fillMaxWidth().height(170.dp).semantics{contentDescription="Pink turtle companion, stage ${stage+1} of 6"})
                Text(if(next==null)"Fully grown. Look how far you’ve come." else "${next-totalMinutes} more focus minutes to grow",fontSize=12.sp,color=mutedInk)
            }
        }
        OutlinedButton(onClick=onOpenPlanner,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFFE8C5D2))){Icon(Icons.Rounded.CalendarToday,null);Spacer(Modifier.width(10.dp));Text("Make a little plan")}
    }
    if(custom)AlertDialog(onDismissRequest={custom=false},title={Text("Your pace, your timer")},text={
        Column{Text("${customMinutes.roundToInt()} minutes",fontSize=24.sp,color=roseInk);Slider(value=customMinutes.coerceIn(5f,180f),onValueChange={customMinutes=(it/5).roundToInt()*5f},valueRange=5f..180f,steps=34)}
    },confirmButton={TextButton(onClick={onDuration(customMinutes.roundToInt());custom=false}){Text("Set duration")}},dismissButton={TextButton(onClick={custom=false}){Text("Cancel")}})
}
/** A deterministic six-stage illustration: spring-driven shell fragments at hatching, then scaled
 *  articulated turtle silhouettes. No simulation runs while the screen is not composed. */
@Composable
fun TurtleAtelier(stage:Int,modifier:Modifier=Modifier){
    val infinite=rememberInfiniteTransition(label="turtle idle")
    val breathe by infinite.animateFloat(
        initialValue=.978f,targetValue=1.022f,
        animationSpec=infiniteRepeatable(tween(2250,easing=EaseInOutSine),RepeatMode.Reverse),
        label="breathe")
    val bob by infinite.animateFloat(
        initialValue=-2.8f,targetValue=2.8f,
        animationSpec=infiniteRepeatable(tween(2400,easing=EaseInOutSine),RepeatMode.Reverse),
        label="float")
    val openness by animateFloatAsState(
        targetValue=if(stage>=2)1f else 0f,
        animationSpec=spring(dampingRatio=.72f,stiffness=140f),
        label="shell open")
    val growth by animateFloatAsState(
        targetValue=when(stage){3->.69f;4->.89f;5->1.08f;else->.72f},
        animationSpec=spring(dampingRatio=.78f,stiffness=90f),
        label="turtle growth")
    Canvas(modifier){
        val factor=min(size.width/270f,size.height/210f)
        drawContext.transform.translate(center.x,center.y)
        drawContext.transform.scale(factor,factor,Offset.Zero)
        drawOval(Color(0xFFE7DCDF),Offset(-89f,79f),Size(178f,13f))
        drawCircle(Color(0xFFF8EFF1),radius=95f,center=Offset(0f,-3f))
        if(stage<=2){
            val eggshell=Color(0xFFFFF7FA)
            val shade=Color(0xFFEAD5D3)
            if(stage==2){
                drawOval(turtleGreen,Offset(-32f,-67f+bob),Size(64f,72f))
                drawCircle(Color(0xFF632C42),4.5f,Offset(-13f,-42f+bob))
                drawCircle(Color(0xFF632C42),4.5f,Offset(13f,-42f+bob))
                drawArc(Color(0xFF632C42),12f,150f,false,Offset(-9f,-34f+bob),Size(18f,12f),style=Stroke(width=2f))
                drawOval(turtleGreen,Offset(-30f,2f+bob),Size(60f,34f))
            }
            if(stage<=1){
                drawOval(shade,Offset(-65f,-73f+bob),Size(130f,155f))
                drawOval(eggshell,Offset(-59f,-68f+bob),Size(118f,144f))
                drawOval(Color.White.copy(alpha=.66f),Offset(-42f,-53f+bob),Size(18f,54f))
                if(stage==1){
                    val fracture=Path().apply{
                        moveTo(-59f,-6f+bob);lineTo(-39f,-17f+bob);lineTo(-25f,-6f+bob)
                        lineTo(-8f,-24f+bob);lineTo(7f,-8f+bob);lineTo(25f,-23f+bob)
                        lineTo(40f,-11f+bob);lineTo(59f,-19f+bob)
                    }
                    drawPath(fracture,roseInk,style=Stroke(width=3f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawLine(Color(0xFFDFAAB6),Offset(-8f,-24f+bob),Offset(-12f,-42f+bob),2f)
                }
            }else{
                val lower=Path().apply{
                    moveTo(-61f,-7f);lineTo(-43f,3f);lineTo(-25f,-5f);lineTo(-7f,6f)
                    lineTo(12f,-7f);lineTo(29f,4f);lineTo(45f,-5f);lineTo(60f,1f)
                    quadraticBezierTo(63f,52f,33f,76f);quadraticBezierTo(0f,93f,-36f,73f)
                    quadraticBezierTo(-64f,49f,-61f,-7f);close()
                }
                translate(top=13f*openness){
                    drawPath(lower,eggshell)
                    drawPath(lower,shade,style=Stroke(3f))
                }
                val upper=Path().apply{
                    moveTo(-61f,-7f);quadraticBezierTo(-65f,-76f,-8f,-78f)
                    quadraticBezierTo(58f,-82f,60f,1f)
                    lineTo(45f,-5f);lineTo(29f,4f);lineTo(12f,-7f)
                    lineTo(-7f,6f);lineTo(-25f,-5f);lineTo(-43f,3f);close()
                }
                translate(left=-22f*openness,top=-37f*openness){
                    rotate(degrees=-14f*openness,pivot=Offset(0f,-37f)){
                        drawPath(upper,eggshell)
                        drawPath(upper,shade,style=Stroke(3f))
                    }
                }
                for(index in 0..2){
                    val x=62f+index*7f+openness*index*9f
                    drawCircle(shade,3f-index*.5f,Offset(x,15f-index*11f))
                }
            }
        }else{
            drawContext.transform.scale(growth*breathe,growth*breathe,Offset.Zero)
            val y=bob
            // Flippers sit behind the carapace, grounding the character.
            drawOval(turtleGreen,Offset(-82f,24f+y),Size(60f,49f))
            drawOval(turtleGreen,Offset(22f,24f+y),Size(60f,49f))
            drawOval(turtleGreen,Offset(-77f,-31f+y),Size(50f,42f))
            drawOval(turtleGreen,Offset(27f,-31f+y),Size(50f,42f))
            drawPath(Path().apply{moveTo(0f,67f+y);lineTo(-10f,87f+y);lineTo(-23f,65f+y);close()},turtleGreen)
            drawOval(Color(0xFFA74B6D),Offset(-65f,-45f+y),Size(130f,120f))
            drawOval(Color(0xFFE29AB3),Offset(-54f,-36f+y),Size(108f,102f))
            val lines=Color(0xFFFFCEDD)
            drawLine(lines,Offset(0f,-34f+y),Offset(0f,66f+y),2.8f)
            drawLine(lines,Offset(-52f,14f+y),Offset(52f,14f+y),2.8f)
            drawLine(lines,Offset(-43f,-18f+y),Offset(0f,14f+y),2.8f)
            drawLine(lines,Offset(43f,-18f+y),Offset(0f,14f+y),2.8f)
            drawLine(lines,Offset(-42f,43f+y),Offset(0f,14f+y),2.8f)
            drawLine(lines,Offset(42f,43f+y),Offset(0f,14f+y),2.8f)
            drawOval(turtleGreen,Offset(-51f,-95f+y),Size(102f,83f))
            drawCircle(Color(0xFF632C42),5f,Offset(-20f,-53f+y))
            drawCircle(Color(0xFF632C42),5f,Offset(20f,-53f+y))
            drawCircle(Color.White,1.9f,Offset(-22f,-55f+y))
            drawCircle(Color.White,1.9f,Offset(18f,-55f+y))
            drawArc(Color(0xFF632C42),10f,160f,false,Offset(-14f,-42f+y),Size(28f,16f),style=Stroke(2f))
        }
    }
}
