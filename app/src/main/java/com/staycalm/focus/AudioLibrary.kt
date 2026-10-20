package com.staycalm.focus

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Ambient(val id:String,val label:String,val detail:String,val raw:Int=0,val localFile:String?=null)
object Ambients {
    val all=listOf(
        Ambient("focus_clip_1","Through the Glass Pane","Your collection · 2:14",R.raw.focus_clip_1),
        Ambient("focus_clip_2","The Tree Line","Your collection · 2:27",R.raw.focus_clip_2),
        Ambient("focus_clip_3","Under the Grey Bough","Your collection · 2:41",R.raw.focus_clip_3),
        Ambient("focus_clip_4","Midnight Windowpane","Your collection · 2:46",R.raw.focus_clip_4)
    )
}

class AudioLibrary(private val context:Context){
    private val prefs=AccountData.preferences(context)
    private val root=AccountData.root(context)
    fun imported():List<Ambient> = runCatching{
        val array=JSONArray(prefs.getString("imported_audio","[]"))
        (0 until array.length()).map{index->
            val item=array.getJSONObject(index)
            Ambient(item.getString("id"),item.getString("title"),"Your audio · loops during focus",localFile=item.getString("file"))
        }.filter{File(root,"audio/${it.localFile}").isFile}
    }.getOrDefault(emptyList())
    fun all():List<Ambient> = Ambients.all+imported()
    fun find(id:String):Ambient = all().firstOrNull{it.id==id}?:Ambients.all.first()
    private fun save(items:List<Ambient>){
        check(prefs.edit().putString("imported_audio",JSONArray().apply{items.forEach{put(JSONObject().put("id",it.id).put("title",it.label).put("file",it.localFile))}}.toString()).commit()){"Could not save your audio library."}
    }
    /** Copy to private storage so playback does not depend on a cloud provider or expiring URI grant. */
    fun import(uri:Uri):Ambient {
        val title=runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
            if(cursor.moveToFirst())cursor.getString(0) else null
        }}.getOrNull()?.take(120)?.ifBlank{null}?:"My audio"
        val id="personal_"+UUID.randomUUID().toString().replace("-","")
        val folder=File(root,"audio").apply{mkdirs()}
        val file=File(folder,"$id.audio")
        try {
            context.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{output->input.copyTo(output)}}?:error("This file could not be opened.")
            val metadata=MediaMetadataRetriever()
            try{
                metadata.setDataSource(file.absolutePath)
                require(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes"){"Choose a file containing audio."}
                require((metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0)>0){"This audio file is empty or unsupported."}
            }finally{metadata.release()}
            val item=Ambient(id,title,"Your audio · loops during focus",localFile=file.name)
            save(imported()+item)
            return item
        }catch(error:Exception){file.delete();throw error}
    }
    fun remove(item:Ambient){
        if(item.localFile==null)return
        save(imported().filterNot{it.id==item.id})
        File(root,"audio/${item.localFile}").delete()
    }
}
