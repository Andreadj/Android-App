package com.mobiled.android.ui.controller

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.model.LightCommand
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.R
import org.json.JSONObject

class MatrixFragment : BaseFragment<androidx.viewbinding.ViewBinding>() {
    override fun getFragmentBinding(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?) =
        MatrixBinding.inflate(inflater, container, false)
    override fun handleBackPress() = false

    private lateinit var title: TextView
    private lateinit var speed: SeekBar
    private lateinit var brightness: SeekBar
    private var effect = 101

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = view.findViewById(9001)
        speed = view.findViewById(9002)
        brightness = view.findViewById(9003)
        speed.progress = 50; brightness.progress = 100
        val effects = listOf(101 to "Color Change",102 to "Comet",103 to "Rainbow Chase",104 to "Fill-Unfill",105 to "Theater",106 to "Sparkling",107 to "Fireworks",108 to "Fire",109 to "Wave",110 to "BPM",111 to "Amplitude",112 to "Spectrum")
        val row = view.findViewById<LinearLayout>(9004)
        effects.forEach { (id,name) ->
            val b=Button(requireContext()); b.text=name; b.setOnClickListener { effect=id; title.text="Matrix • $name"; send() }; row.addView(b, LinearLayout.LayoutParams(0,48,1f))
        }
        speed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){if(f)send()}; override fun onStartTrackingTouch(s:SeekBar?){ }; override fun onStopTrackingTouch(s:SeekBar?){ } })
        brightness.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){if(f)send()}; override fun onStartTrackingTouch(s:SeekBar?){ }; override fun onStopTrackingTouch(s:SeekBar?){ } })
    }

    private fun baseCommand(): LightCommand? {
        val a=requireActivity() as ControllerActivity
        val f=a.getFrame()
        val c=LightCommand(); c.fromJson(f); c.Command = f.optInt("Command",0); c.GLights=effect; c.Speed=speed.progress; c.Brightness=brightness.progress
        c.GUniverse=f.optInt("GUniverse",32000).coerceIn(32000,32500)
        return c
    }
    private fun send(){
        val a=requireActivity() as ControllerActivity
        val c=baseCommand() ?: return
        val groupField= a.javaClass.getDeclaredField("group").apply{isAccessible=true}.get(a) as? com.mobiled.android.base.model.HardwareGroup
        val devField= a.javaClass.getDeclaredField("device").apply{isAccessible=true}.get(a) as? HardwareDevice
        if (groupField?.groupItems?.isNotEmpty()==true) {
            val items=groupField.groupItems!!.filter{it.hardwareDevice?.ip?.isNotBlank()==true}.sortedBy{it.hardwareDevice?.ApName ?: ""}
            val count=items.size
            items.forEachIndexed { idx,it -> val x=c.copy(); x.GState=it.GState; x.GPort=it.Gport; x.PixelID=idx; x.PixelCount=count; x.Command=it.hardwareDevice!!.deviceFrame.let{raw-> if(raw.isBlank()) 1 else JSONObject(raw).optInt("Command",1)}; it.hardwareDevice!!.deviceFrame=x.toJsonString(); UdpClient.getClient(requireContext()).writeString(x.toJsonString(),it.hardwareDevice!!.ip!!,it.hardwareDevice!!.port.toInt().takeIf{p->p>0}?:8889) }
        } else if (devField?.ip?.isNotBlank()==true) {
            c.GState="X"; c.GPort="8889"; c.PixelID=0; c.PixelCount=1; devField.deviceFrame=c.toJsonString(); UdpClient.getClient(requireContext()).writeString(c.toJsonString(),devField.ip!!,devField.port.toInt().takeIf{p->p>0}?:8889)
        }
    }
    companion object { fun newInstance()=MatrixFragment() }
}

class MatrixBinding private constructor(root: View): androidx.viewbinding.ViewBinding {
    override fun getRoot(): View = root
    companion object {
        fun inflate(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, attach:Boolean=false): MatrixBinding {
            val ctx=inflater.context; val root=LinearLayout(ctx).apply{orientation=LinearLayout.VERTICAL; setPadding(16,16,16,16); setBackgroundColor(Color.TRANSPARENT)}
            root.addView(TextView(ctx).apply{id=9001;text="Matrix";textSize=20f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(-1,60))
            root.addView(TextView(ctx).apply{text="Speed";setTextColor(Color.WHITE)},LinearLayout.LayoutParams(-1,40))
            root.addView(SeekBar(ctx).apply{id=9002;max=100;progress=50},LinearLayout.LayoutParams(-1,50))
            root.addView(TextView(ctx).apply{text="Brightness";setTextColor(Color.WHITE)},LinearLayout.LayoutParams(-1,40))
            root.addView(SeekBar(ctx).apply{id=9003;max=100;progress=100},LinearLayout.LayoutParams(-1,50))
            val scroll=android.widget.HorizontalScrollView(ctx); val row=LinearLayout(ctx).apply{id=9004;orientation=LinearLayout.HORIZONTAL}
            scroll.addView(row); root.addView(scroll,LinearLayout.LayoutParams(-1,70));
            return MatrixBinding(root)
        }
    }
}
