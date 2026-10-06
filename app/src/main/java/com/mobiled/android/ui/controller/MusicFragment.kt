package com.mobiled.android.ui.controller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.LightCommand
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class MusicFragment : BaseFragment<MusicBinding>() {
    companion object {
        fun newInstance() = MusicFragment()
    }
    override fun getFragmentBinding(inflater: LayoutInflater, container: ViewGroup?) = MusicBinding.inflate(inflater, container, false)
    override fun handleBackPress() = false
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var running=false
    private var effect=0
    private var phase=0f
    private var lastBeat=0L
    private var beatBpm=100
    private var musicUniverse=32000
    private var frameSeq=0
    private lateinit var status:TextView
    private lateinit var effectSpinner:Spinner
    private lateinit var start:Button

    override fun onViewCreated(view:View,saved:Bundle?) {
        super.onViewCreated(view,saved)
        status=view.findViewById(9101); effectSpinner=view.findViewById(9102); start=view.findViewById(9103)
        effectSpinner.adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("Rainbow","Rainbow Chase","Random","BPM","Spectrum","VU Meter"))
        effectSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,pos:Int,id:Long){effect=pos}}
        start.setOnClickListener { if(running) stopCapture() else requestCapture() }
    }
    private fun requestCapture(){
        if(Build.VERSION.SDK_INT<29){status.text="Music playback capture requires Android 10 or newer";return}
        val mgr=requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(),7001)
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==7001 && resultCode==Activity.RESULT_OK && data!=null && Build.VERSION.SDK_INT>=29){val mgr=requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; projection=mgr.getMediaProjection(resultCode,data); startCapture()}}
    @RequiresApi(29)
    private fun startCapture(){
        val p=projection ?: return
        val sr=44100; val min=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        val cfg=AudioPlaybackCaptureConfiguration.Builder(p).addMatchingUsage(AudioAttributes.USAGE_MEDIA).build()
        recorder=AudioRecord.Builder().setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(max(min*2,8192)).setAudioPlaybackCaptureConfig(cfg).build()
        recorder!!.startRecording(); running=true; start.text="Stop Music"; status.text="Music running"; sendMusicSelection(); worker=Thread{loop(sr)}.also{it.start()}
    }
    private fun sendMusicSelection(){
        val a=requireActivity() as ControllerActivity
        val f=a.getFrame(); val c=LightCommand().apply{fromJson(f);GLights=100;GUniverse=f.optInt("GUniverse",musicUniverse).coerceIn(32000,32500);GState="X";GPort=f.optString("GPort","8890")}
        val groupField=a.javaClass.getDeclaredField("group").apply{isAccessible=true}.get(a) as? com.mobiled.android.base.model.HardwareGroup
        val items=groupField?.groupItems?.filter{it.hardwareDevice?.ip?.isNotBlank()==true} ?: emptyList()
        if(items.isNotEmpty()){c.PixelCount=items.size;items.forEachIndexed{idx,it->val x=c.copy();x.PixelID=idx;x.Command=if(it.hardwareDevice!!.deviceFrame.isBlank()) 1 else org.json.JSONObject(it.hardwareDevice!!.deviceFrame).optInt("Command",1);x.GPort=it.Gport;x.GState="X";it.hardwareDevice!!.deviceFrame=x.toJsonString();UdpClient.getClient(requireContext()).writeString(x.toJsonString(),it.hardwareDevice!!.ip!!,it.hardwareDevice!!.port.toInt().takeIf{p->p>0}?:8889)}} else {c.PixelCount=1;c.PixelID=0;c.Command=if(f.optInt("Command",0)==1)1 else 0;val ip=f.optString("IP","");if(ip.isNotBlank())UdpClient.getClient(requireContext()).writeString(c.toJsonString(),ip,f.optInt("Port",8889))}
    }
    private fun stopCapture(){running=false;worker?.interrupt();worker=null;recorder?.stop();recorder?.release();recorder=null;projection?.stop();projection=null;start.text="Start Music";status.text="Music stopped"}
    private fun loop(sr:Int){
        val buf=ShortArray(2048); var previous=0f
        while(running){val n=recorder?.read(buf,0,buf.size)?:0;if(n<=0)continue
            var peak=0f;var sum=0.0;for(i in 0 until n){val x=abs(buf[i].toInt())/32768f;peak=max(peak,x);sum+=x*x};val rms=kotlin.math.sqrt(sum/n).toFloat();
            val now=System.currentTimeMillis(); if(peak>max(0.45f,previous*1.45f) && now-lastBeat>280){if(lastBeat>0){val dt=now-lastBeat;beatBpm=(60000/dt).toInt().coerceIn(60,180)};lastBeat=now}
            previous=previous*0.8f+peak*0.2f; render(peak,rms,now);Thread.sleep(33)
        }
    }
    private fun render(level:Float,rms:Float,now:Long){
        val a=requireActivity() as ControllerActivity; val base=a.getFrame(); val groupField=a.javaClass.getDeclaredField("group").apply{isAccessible=true}.get(a) as? com.mobiled.android.base.model.HardwareGroup
        val count=max(1,groupField?.groupItems?.count{it.hardwareDevice?.ip?.isNotBlank()==true} ?: 1)
        val colors=Array(count){LightCommand()}
        colors.forEachIndexed { idx,c -> c.fromJson(base); c.GLights=100; c.GUniverse=base.optInt("GUniverse",musicUniverse).coerceIn(32000,32500); c.PixelID=idx; c.PixelCount=count; c.GPort=if(groupField?.groupItems?.isNotEmpty()==true) groupField.groupItems!!.first().Gport else base.optString("GPort","8890"); c.GState="X" }
        val cmd=colors[0]
        when(effect){0->{phase=(phase+0.01f*(1+level*8))%1f; hsvToRgb((phase*360f)%360f,1f,level.coerceIn(0.05f,1f),cmd)}
            1->{phase=(phase+0.025f*(1+beatBpm/120f))%1f; hsvToRgb(((phase+0.25f)*360f)%360f,1f,level.coerceIn(0.05f,1f),cmd)}
            2->{if(now-lastBeat<120) phase=(phase+0.17f)%1f; hsvToRgb(phase*360f,1f,level.coerceIn(0.05f,1f),cmd)}
            3->{if(now-lastBeat<120) phase=(phase+1f/12f)%1f; val colors=arrayOf(0f,0.10f,0.20f,0.33f,0.50f,0.66f,0.82f);hsvToRgb(colors[(phase*colors.size).toInt()%colors.size]*360f,1f,1f,cmd)}
            4->{hsvToRgb((phase*360f)%360f,1f,level.coerceIn(0.05f,1f),cmd)}
            5->{val v=level.coerceIn(0.05f,1f);cmd.red=(255*v).toInt();cmd.green=(255*max(0f,(v-0.55f)/0.45f)).toInt();cmd.blue=0;cmd.white=0}
        }
        colors.forEach { c -> c.red=cmd.red; c.green=cmd.green; c.blue=cmd.blue; c.white=cmd.white }
        sendMusicFrames(colors)
    }
    private fun hsvToRgb(h:Float,s:Float,v:Float,c:LightCommand){val x=(h/60f);val i=kotlin.math.floor(x).toInt()%6;val f=x-kotlin.math.floor(x);val p=v*(1-s);val q=v*(1-f*s);val t=v*(1-(1-f)*s);val rgb=when(i){0->floatArrayOf(v,t,p);1->floatArrayOf(q,v,p);2->floatArrayOf(p,v,t);3->floatArrayOf(p,q,v);4->floatArrayOf(t,p,v);else->floatArrayOf(v,p,q)};c.red=(rgb[0]*255).toInt();c.green=(rgb[1]*255).toInt();c.blue=(rgb[2]*255).toInt();c.white=0}
    private fun sendMusicFrames(colors: Array<LightCommand>){
        val a=requireActivity() as ControllerActivity
        val f=a.getFrame()
        val ip=f.optString("IP","")
        if (ip.isNotBlank() && colors.isNotEmpty()) {
            // Music selection is sent only when the session/effect is selected; realtime frames are sACN only.
        }
        val universeBase=colors.firstOrNull()?.GUniverse ?: musicUniverse
        var idx=0
        while(idx<colors.size){
            val chunk=colors.slice(idx until minOf(idx+128,colors.size))
            sendSacnFrame(universeBase+(idx/128),chunk)
            idx += chunk.size
        }
    }

    private fun sendSacnFrame(universe:Int, pixels:List<LightCommand>){
        try {
            val propCount=1+pixels.size*4
            val total=130+pixels.size*4
            val p=ByteArray(total)
            fun u16(o:Int,v:Int){p[o]=((v shr 8) and 255).toByte();p[o+1]=(v and 255).toByte()}
            fun u32(o:Int,v:Long){p[o]=((v shr 24) and 255).toByte();p[o+1]=((v shr 16) and 255).toByte();p[o+2]=((v shr 8) and 255).toByte();p[o+3]=(v and 255).toByte()}
            p[0]=0; p[1]=0x10; p[2]=0; p[3]=0; "ASC-E1.17".toByteArray().copyInto(p,4);
            // Root layer flags/length and a stable non-zero CID for the mobile sender.
            u16(16,0x7000 or (total-16)); for(i in 0 until 16) p[22+i]=(i+1).toByte()
            "MobileD Android Music".padEnd(64,'\u0000').take(64).toByteArray().copyInto(p,44)
            p[108]=100; p[109]=0; p[110]=0; p[111]=(frameSeq++ and 255).toByte(); u16(113,universe)
            u16(115,0x7000 or (total-115)); p[118]=2; p[119]=0xA1.toByte(); p[120]=0; p[121]=0; p[122]=2; u16(123,0); u16(125,1); u16(127,propCount); p[129]=0
            var o=130; for(c in pixels){p[o]=c.red.toByte();p[o+1]=c.green.toByte();p[o+2]=c.blue.toByte();p[o+3]=c.white.toByte();o+=4}
            val addr=InetAddress.getByName("239.255.${(universe shr 8) and 255}.${universe and 255}")
            MulticastSocket().use { s -> s.timeToLive=1; s.send(DatagramPacket(p,p.size,addr,5568)) }
        } catch (_:Exception) {}
    }

}

class MusicBinding private constructor(private val root:View):androidx.viewbinding.ViewBinding{override fun getRoot()=root;companion object{fun inflate(inflater:LayoutInflater,container:ViewGroup?,attach:Boolean=false):MusicBinding{val c=inflater.context;val l=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(20,20,20,20)};l.addView(TextView(c).apply{id=9101;text="Music";textSize=20f},LinearLayout.LayoutParams(-1,60));val s=Spinner(c).apply{id=9102};l.addView(s,LinearLayout.LayoutParams(-1,60));l.addView(Button(c).apply{id=9103;text="Start Music"},LinearLayout.LayoutParams(-1,60));l.addView(TextView(c).apply{text="Android playback capture requires Android 10+ and system consent. The capture source is playback audio, not the microphone.";textSize=13f},LinearLayout.LayoutParams(-1,-2));return MusicBinding(l)}}}
