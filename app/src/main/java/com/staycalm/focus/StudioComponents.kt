
package com.staycalm.focus

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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlin.math.*

private val roseInk=Color(0xFFC75C81)
private val plumInk=Color(0xFF211C26)
private val lavenderInk=Color(0xFF3A2C40)
private val mutedInk=Color(0xFF8D7D8C)
private val lightRose=Color(0xFFF8EEF1)
private val turtleGreen=Color(0xFF477B6E)

@Composable
fun StudioFocus(
    durationMinutes:Int,
    remainingMs:Long,
    running:Boolean,
    totalMinutes:Int,
    minutesToday:Int,
    dailyGoal:Int,
    selectedSound:Ambient,
    playing:Boolean,
    onDuration:(Int)->Unit,
    onToggle:()->Unit,
    onAbandon:()->Unit,
    onSoundSelect:()->Unit,
    onSoundToggle:()->Unit,
    onOpenPlanner:()->Unit
){
    val progress=(1f-remainingMs/(durationMinutes.coerceAtLeast(1)*60_000f)).coerceIn(0f,1f)
    val stage=Evolution.stage(totalMinutes)
    val next=Evolution.next(totalMinutes)
    Column(verticalArrangement=Arrangement.spacedBy(19.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("YOUR DAILY STUDIO",fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp,color=roseInk)
                Spacer(Modifier.height(6.dp))
                Text("Make space for focus.",fontSize=30.sp,fontWeight=FontWeight.Bold,color=plumInk,letterSpacing=(-1.1).sp,lineHeight=37.sp)
            }
            Surface(onClick=onOpenPlanner,shape=CircleShape,color=Color.White,border=BorderStroke(1.dp,Color(0xFFEADFE3))){
                Icon(Icons.Rounded.CalendarToday,"Open planner",modifier=Modifier.padding(14.dp).size(20.dp),tint=plumInk)
            }
        }
        Surface(shape=RoundedCornerShape(30.dp),color=plumInk){
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(plumInk,lavenderInk)))
                .padding(horizontal=23.dp,vertical=23.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(8.dp).background(if(running)Color(0xFF88C5AC) else Color(0xFFD29BAE),CircleShape))
                        Spacer(Modifier.width(9.dp))
                        Text(if(running)"IN SESSION" else "DEEP WORK",fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.7.sp,color=Color(0xFFE9D9E2))
                    }
                    Text(if(running)"Stay present" else "Ready when you are",fontSize=11.sp,color=Color(0xFFB8A3B5))
                }
                Spacer(Modifier.height(28.dp))
                Box(contentAlignment=Alignment.Center,modifier=Modifier.size(252.dp)){
                    Canvas(Modifier.fillMaxSize()){
                        val center=this.center
                        val radius=size.minDimension/2.0f-15.dp.toPx()
                        drawCircle(Brush.radialGradient(listOf(Color(0xFF865467).copy(alpha=.32f),Color.Transparent),center=center,radius=radius*1.28f),radius=radius*1.23f,center=center)
                        drawCircle(Color.White.copy(alpha=.11f),radius=radius,center=center,style=Stroke(width=7.dp.toPx()))
                        drawArc(brush=Brush.sweepGradient(colors=listOf(Color(0xFFFFA7C1),roseInk,Color(0xFFDD789B))),startAngle=-90f,sweepAngle=360f*progress,useCenter=false,topLeft=Offset(center.x-radius,center.y-radius),size=Size(radius*2,radius*2),style=Stroke(width=8.dp.toPx(),cap=StrokeCap.Round))
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Text("%02d:%02d".format(remainingMs/60_000,(remainingMs/1000)%60),fontSize=48.sp,letterSpacing=(-2).sp,fontWeight=FontWeight.Medium,color=Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(if(running)"ONE THING AT A TIME" else "YOUR TIME IS YOURS",fontSize=10.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp,color=Color(0xFFC4AFBE))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf(15,25,45,60).forEach {minutes ->
                        val active=durationMinutes==minutes
                        Surface(
                            onClick={if(!running)onDuration(minutes)},
                            modifier=Modifier.weight(1f).height(42.dp),
                            shape=RoundedCornerShape(13.dp),
                            color=if(active)Color(0xFFF5CFDB) else Color.White.copy(alpha=.08f),
                            border=if(active)null else BorderStroke(1.dp,Color.White.copy(alpha=.11f))
                        ){
                            Box(contentAlignment=Alignment.Center){
                                Text(minutes.toString()+"m",fontSize=13.sp,fontWeight=FontWeight.Bold,color=if(active)plumInk else Color.White.copy(alpha=.85f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick=onToggle,
                    modifier=Modifier.fillMaxWidth().height(58.dp),
                    shape=RoundedCornerShape(18.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=roseInk,contentColor=Color.White)
                ){
                    Icon(if(running)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null)
                    Spacer(Modifier.width(11.dp))
                    Text(if(running)"Pause focus" else if(progress>0f)"Resume your session" else "Start focusing",fontSize=15.sp,fontWeight=FontWeight.Bold)
                }
                if(running||progress>0f){
                    TextButton(onClick=onAbandon){Text("End session without saving",fontSize=12.sp,color=Color(0xFFE2BFCD))}
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            StudioMetric("TODAY",minutesToday.toString()+" min",Modifier.weight(1f))
            StudioMetric("YOUR GOAL",dailyGoal.toString()+" min",Modifier.weight(1f))
        }
        Surface(shape=RoundedCornerShape(25.dp),color=Color.White,border=BorderStroke(1.dp,Color(0xFFF0E6E9))){
            Column(Modifier.fillMaxWidth().padding(18.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("YOUR COMPANION",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,color=mutedInk)
                        Spacer(Modifier.height(5.dp))
                        Text(Evolution.title(stage),fontSize=19.sp,fontWeight=FontWeight.Bold,color=plumInk)
                    }
                    Surface(shape=RoundedCornerShape(9.dp),color=lightRose){
                        Text("STAGE "+(stage+1).toString()+" / 6",modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp),fontSize=10.sp,fontWeight=FontWeight.Bold,color=roseInk)
                    }
                }
                TurtleAtelier(stage,Modifier.fillMaxWidth().height(174.dp))
                val thresholds=listOf(0,30,90,180,360,720)
                val previous=thresholds[stage]
                val growth=if(next==null)1f else ((totalMinutes-previous).toFloat()/(next-previous)).coerceIn(0f,1f)
                LinearProgressIndicator(progress={growth},modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=roseInk,trackColor=lightRose)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(totalMinutes.toString()+" total focus minutes",fontSize=11.sp,color=mutedInk)
                    Text(if(next==null)"Fully evolved" else (next-totalMinutes).toString()+" min to evolve",fontSize=11.sp,fontWeight=FontWeight.Bold,color=roseInk)
                }
            }
        }
        Surface(shape=RoundedCornerShape(23.dp),color=Color.White,border=BorderStroke(1.dp,Color(0xFFF0E6E9))){
            Column(Modifier.padding(18.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("SOUND STUDIO",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,color=mutedInk)
                        Spacer(Modifier.height(5.dp))
                        Text(selectedSound.label,fontSize=19.sp,fontWeight=FontWeight.Bold,color=plumInk)
                        Text(selectedSound.detail,fontSize=12.sp,color=mutedInk)
                    }
                    IconButton(onClick=onSoundToggle,modifier=Modifier.size(50.dp)){
                        Icon(if(playing)Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                            if(playing)"Pause sound" else "Play sound",tint=roseInk,modifier=Modifier.size(39.dp))
                    }
                }
                HorizontalDivider(color=Color(0xFFF1E7EB),modifier=Modifier.padding(vertical=12.dp))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Rounded.GraphicEq,null,tint=roseInk,modifier=Modifier.size(21.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Seven offline soundscapes",fontSize=12.sp,color=mutedInk,modifier=Modifier.weight(1f))
                    TextButton(onClick=onSoundSelect){Text("Browse sounds",fontWeight=FontWeight.Bold,fontSize=12.sp)}
                }
            }
        }
        Text("FOCUS IS THE PRACTICE. PROGRESS IS THE REWARD.",fontSize=9.sp,letterSpacing=1.1.sp,color=mutedInk,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
    }
}

@Composable private fun StudioMetric(label:String,value:String,modifier:Modifier){
    Surface(modifier=modifier,shape=RoundedCornerShape(19.dp),color=Color.White,border=BorderStroke(1.dp,Color(0xFFF0E6E9))){
        Column(Modifier.padding(horizontal=17.dp,vertical=18.dp)){
            Text(label,fontSize=10.sp,letterSpacing=1.5.sp,fontWeight=FontWeight.Bold,color=mutedInk)
            Spacer(Modifier.height(9.dp))
            Text(value,fontSize=23.sp,fontWeight=FontWeight.Bold,color=plumInk)
        }
    }
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
            val eggshell=Color(0xFFFFFAF4)
            val shade=Color(0xFFEAD5D3)
            if(stage==2){
                drawOval(turtleGreen,Offset(-32f,-67f+bob),Size(64f,72f))
                drawCircle(Color(0xFF2A2930),4.5f,Offset(-13f,-42f+bob))
                drawCircle(Color(0xFF2A2930),4.5f,Offset(13f,-42f+bob))
                drawArc(Color(0xFF2A2930),12f,150f,false,Offset(-9f,-34f+bob),Size(18f,12f),style=Stroke(width=2f))
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
            drawOval(Color(0xFF31594E),Offset(-65f,-45f+y),Size(130f,120f))
            drawOval(Color(0xFF659483),Offset(-54f,-36f+y),Size(108f,102f))
            val lines=Color(0xFFA1C2A9)
            drawLine(lines,Offset(0f,-34f+y),Offset(0f,66f+y),2.8f)
            drawLine(lines,Offset(-52f,14f+y),Offset(52f,14f+y),2.8f)
            drawLine(lines,Offset(-43f,-18f+y),Offset(0f,14f+y),2.8f)
            drawLine(lines,Offset(43f,-18f+y),Offset(0f,14f+y),2.8f)
            drawLine(lines,Offset(-42f,43f+y),Offset(0f,14f+y),2.8f)
            drawLine(lines,Offset(42f,43f+y),Offset(0f,14f+y),2.8f)
            drawOval(turtleGreen,Offset(-51f,-95f+y),Size(102f,83f))
            drawCircle(Color(0xFF272B2B),5f,Offset(-20f,-53f+y))
            drawCircle(Color(0xFF272B2B),5f,Offset(20f,-53f+y))
            drawCircle(Color.White,1.9f,Offset(-22f,-55f+y))
            drawCircle(Color.White,1.9f,Offset(18f,-55f+y))
            drawArc(Color(0xFF2E3B37),10f,160f,false,Offset(-14f,-42f+y),Size(28f,16f),style=Stroke(2f))
        }
    }
}
