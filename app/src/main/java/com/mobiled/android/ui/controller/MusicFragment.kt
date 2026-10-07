package com.mobiled.android.ui.controller

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.viewpager2.widget.ViewPager2
import com.mobiled.android.MobiLedApp
import com.mobiled.android.R
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.component.ColorPickerView
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.model.LightCommand
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

class MusicFragment : BaseFragment<MusicBinding>() {
    companion object { fun newInstance() = MusicFragment() }

    override fun getFragmentBinding(inflater: LayoutInflater, container: ViewGroup?) = MusicBinding.inflate(inflater, container, false)
    override fun handleBackPress() = false

    private data class Effect(val id:String,val name:String,val white:Boolean,val colors:Int)
    private val effects = listOf(
        Effect("rainbow","Rainbow",true,0),
        Effect("random","Random",true,10),
        Effect("rainbowchase","Rainbow Chase",true,0),
        Effect("bpm","BPM",false,10),
        Effect("spectrum","Spectrum",false,10),
        Effect("vumeter","VU Meter",false,0)
    )

    private var effect = "rainbow"
    private var brightness = 100
    private var white = 0
    private var running = false
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var phase = 0f
    private var lastBeat = 0L
    private var bpm = 100
    private var frameSeq = 0
    private var lastLevel = 0f
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var vuLeft: MusicVuBar
    private lateinit var vuRight: MusicVuBar
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null
    private val palette = MutableList(10) { intArrayOf(255,255,255,0) }
    private val spectrumColors = MutableList(10) { intArrayOf(255,255,255,0) }
    private val paletteSaved = BooleanArray(10)
    private val spectrumSaved = BooleanArray(10)
    private val spectrumBandMap = IntArray(10)
    private var selectedColorIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view.findViewById(9200)
        vuLeft = MusicVuBar(requireContext())
        vuRight = MusicVuBar(requireContext())
        buildUi()
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position != 3 && running) deactivateMusic()
            }
        }
        pageCallback = callback
        (requireActivity() as ControllerActivity).binding.viewPager.registerOnPageChangeCallback(callback)
    }

    override fun onPause() {
        // Music is deliberately NOT stopped when the Activity is paused.
        // This includes screen-off/background transitions. The foreground
        // keep-alive service keeps the process important while capture runs.
        super.onPause()
    }

    override fun onDestroyView() {
        pageCallback?.let { (requireActivity() as ControllerActivity).binding.viewPager.unregisterOnPageChangeCallback(it) }
        pageCallback = null
        if (activity?.isFinishing == true) {
            stopCapture(releaseProjection = true)
        }
        super.onDestroyView()
    }

    private fun buildUi() {
        root.removeAllViews()
        val page = FrameLayout(requireContext()).apply {
            setPadding(dp(8), dp(12), dp(8), dp(20))
        }
        val bgRes = resources.getIdentifier("music_window_background", "drawable", requireContext().packageName)
        if (bgRes != 0) {
            page.addView(ImageView(requireContext()).apply {
                setImageResource(bgRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = .58f
            }, FrameLayout.LayoutParams(-1, -1))
        }
        page.addView(View(requireContext()).apply { setBackgroundColor(0x99000000.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        val scroll = androidx.core.widget.NestedScrollView(requireContext()).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(28))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        page.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val head = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val iconRes = resources.getIdentifier("ic_menu_item_music", "drawable", requireContext().packageName)
        if (iconRes != 0) head.addView(ImageView(requireContext()).apply { setImageResource(iconRes); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(40), dp(40)))
        head.addView(TextView(requireContext()).apply {
            text = "Music"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        content.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // PC layout adapted for the phone: six Music effects in 2 columns × 3 rows,
        // with the L/R VU meter immediately to the right and the same total height.
        val effectsAndVu = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val effectsGrid = GridLayout(requireContext()).apply {
            columnCount = 2
            useDefaultMargins = false
        }
        effects.forEachIndexed { index, e ->
            val card = FrameLayout(requireContext()).apply {
                setBackgroundResource(R.drawable.dark_box_corner)
                setOnClickListener { selectEffect(e.id) }
            }
            val imageName = when (e.id) {
                "rainbow" -> "eff_rainbow"
                "random" -> "matrix_101"
                "rainbowchase" -> "matrix_103"
                "bpm" -> "matrix_110"
                "spectrum" -> "matrix_112"
                else -> "music_vumeter_background"
            }
            val res = resources.getIdentifier(imageName, "drawable", requireContext().packageName)
            if (res != 0) card.addView(ImageView(requireContext()).apply { setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP; alpha = .78f }, FrameLayout.LayoutParams(-1, -1))
            card.addView(TextView(requireContext()).apply {
                text = e.name
                textSize = 13f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setShadowLayer(5f, 0f, 2f, Color.BLACK)
            }, FrameLayout.LayoutParams(-1, -1))
            effectsGrid.addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(72)
                columnSpec = GridLayout.spec(index % 2, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        effectsAndVu.addView(effectsGrid, LinearLayout.LayoutParams(0, dp(234), 1f))

        val vuSide = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setBackgroundResource(R.drawable.dark_box_corner)
        }
        vuSide.addView(TextView(requireContext()).apply {
            text = "VU METER"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(24)))
        vuSide.addView(vuRow(), LinearLayout.LayoutParams(-1, dp(198)).apply { topMargin = dp(2) })
        effectsAndVu.addView(vuSide, LinearLayout.LayoutParams(dp(112), dp(234)).apply { leftMargin = dp(6) })
        content.addView(effectsAndVu, LinearLayout.LayoutParams(-1, dp(234)).apply { bottomMargin = dp(10) })

        val controlsHost = LinearLayout(requireContext()).apply {
            tag = "music_controls_host"
            orientation = LinearLayout.VERTICAL
        }
        content.addView(controlsHost, LinearLayout.LayoutParams(-1, -2))
        root.addView(page, LinearLayout.LayoutParams(-1, -1))
        renderControls()
    }

    private fun vuRow(): View {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun channel(label: String, bar: View): View = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), 0)
            addView(TextView(requireContext()).apply { text = label; setTextColor(Color.WHITE); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(20)))
            addView(bar, LinearLayout.LayoutParams(dp(30), dp(165)).apply { topMargin = dp(2) })
        }
        row.addView(channel("L", vuLeft), LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(channel("R", vuRight), LinearLayout.LayoutParams(0, -1, 1f))
        return row
    }

    private fun selectEffect(id:String) {
        try {
            effect=id
            loadColors()
            renderControls()
            if(!running) ensureCaptureAndStart() else sendMusicSelection()
        } catch (e: Throwable) {
            stopCapture(releaseProjection = false)
            android.util.Log.e("MobileDMusic", "Music effect selection failed", e)
            Toast.makeText(requireContext(), "Music could not be started: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureCaptureAndStart(){
        if(Build.VERSION.SDK_INT < 29){ Toast.makeText(requireContext(),"Music audio playback capture requires Android 10 or later.",Toast.LENGTH_SHORT).show(); return }
        if(requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        if (projection != null) {
            startCapture()
            return
        }
        requestProjectionLauncher.launch((requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){granted->if(granted) ensureCaptureAndStart()}
    private val requestProjectionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==Activity.RESULT_OK && result.data!=null){
            try{ projection=(requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(Activity.RESULT_OK,result.data!!); if(projection!=null) startCapture() }catch(_:Exception){projection=null;Toast.makeText(requireContext(),"Audio capture could not be started.",Toast.LENGTH_SHORT).show()}
        }
    }

    private fun renderControls() {
        val host=root.findViewWithTag<LinearLayout>("music_controls_host") ?: return
        host.removeAllViews()
        fun box(titleText:String)=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(18));setBackgroundResource(R.drawable.dark_box_corner);addView(TextView(requireContext()).apply{text=titleText;textSize=18f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})}
        fun slider(container:LinearLayout,label:String,value:Int,maxValue:Int,suffix:String="",change:(Int)->Unit){
            val labelRow=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            labelRow.addView(TextView(requireContext()).apply{text=label;textSize=15f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,dp(32),1f))
            val out=TextView(requireContext()).apply{text="$value$suffix";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL or Gravity.END}
            labelRow.addView(out,LinearLayout.LayoutParams(dp(60),dp(32)))
            container.addView(labelRow,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(5)})
            val bar=LayoutInflater.from(requireContext()).inflate(R.layout.slider_button,container,false)
            val seek=bar.findViewById<com.google.android.material.slider.Slider>(R.id.viewSlider)
            val minus=bar.findViewById<ImageView>(R.id.ivMinus);val plus=bar.findViewById<ImageView>(R.id.ivAdd)
            seek.valueFrom=0f;seek.valueTo=maxValue.toFloat();seek.stepSize=1f;seek.value=value.coerceIn(0,maxValue).toFloat()
            val applyValue={v:Int->val n=v.coerceIn(0,maxValue);seek.value=n.toFloat();out.text="$n$suffix";change(n)}
            minus.setOnClickListener{applyValue(seek.value.toInt()-1)};plus.setOnClickListener{applyValue(seek.value.toInt()+1)}
            seek.addOnChangeListener{_,v,fromUser->out.text="${v.toInt()}$suffix";if(fromUser)change(v.toInt())}
            container.addView(bar,LinearLayout.LayoutParams(-1,dp(35)).apply{bottomMargin=dp(14)})
        }
        val main = box("Main controls")
        slider(main,"Brightness",brightness,100,"%"){brightness=it;sendMusicSelection()}
        if(effects.first{it.id==effect}.white)slider(main,"White",white,100,"%"){white=it;sendMusicSelection()}
        host.addView(main,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})

        if(effect=="bpm"||effect=="spectrum")addMusicColors(host)
        if(effect=="spectrum"){val spectrum=box("Spectrum");renderSpectrumControls(spectrum);host.addView(spectrum,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})}
        if(effect=="bpm"){val transition=box("BPM");transition.addView(spinnerRowMusic("Transition type",listOf("Fade","Direct change","Fade out / fade in"),0){if(running)sendMusicSelection()},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4)});host.addView(transition,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})}
        if(effect=="bpm"||effect=="spectrum")addMusicPresetSection(host)
    }

    private fun addMusicColors(host:LinearLayout){
        val colorsBox=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(18));setBackgroundResource(R.drawable.dark_box_corner)}
        colorsBox.addView(TextView(requireContext()).apply{text="Colors";textSize=18f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
        val targetPalette=if(effect=="spectrum")spectrumColors else palette;val saved=if(effect=="spectrum")spectrumSaved else paletteSaved
        val grid=GridLayout(requireContext()).apply{columnCount=5;useDefaultMargins=false}
        repeat(10){i->
            val cell=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL}
            val sw=TextView(requireContext()).apply{text="${i+1}";gravity=Gravity.CENTER;textSize=16f;setTypeface(typeface,android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);setBackgroundColor(if(saved[i])Color.rgb(targetPalette[i][0],targetPalette[i][1],targetPalette[i][2]) else Color.rgb(55,55,55))}
            bindThreeSecondSave(sw,{saveColor(i,effect=="bpm");sw.setBackgroundColor(Color.rgb(0,155,65));Toast.makeText(requireContext(),"Color ${i+1} saved",Toast.LENGTH_SHORT).show()},{})
            val reset=TextView(requireContext()).apply{text="RESET";gravity=Gravity.CENTER;textSize=10f;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(70,70,70));setOnClickListener{resetMusicColor(i,effect=="bpm");renderControls();if(running)sendMusicSelection()}}
            cell.addView(sw,LinearLayout.LayoutParams(-1,dp(78)));cell.addView(reset,LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(12)})
            grid.addView(cell,GridLayout.LayoutParams().apply{width=0;height=-2;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(6),dp(6),dp(6),dp(12))})
        }
        colorsBox.addView(grid);colorsBox.addView(buildMusicPickerPreview(),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)});host.addView(colorsBox,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})
    }

    private fun spinnerRowMusic(label:String,options:List<String>,selected:Int,onChange:(Int)->Unit):LinearLayout=LinearLayout(requireContext()).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        addView(TextView(requireContext()).apply{text=label;textSize=13f;setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,48,.95f))
        val sp=Spinner(requireContext()).apply{val ad=object:ArrayAdapter<String>(requireContext(),android.R.layout.simple_spinner_item,options){override fun getView(position:Int,convertView:View?,parent:ViewGroup)=TextView(requireContext()).apply{text=options[position];textSize=12f;setTextColor(Color.BLACK);gravity=Gravity.CENTER_VERTICAL;setPadding(10,0,10,0)}};ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);adapter=ad;setSelection(selected.coerceIn(0,options.lastIndex),false);setBackgroundResource(R.drawable.white_box);onItemSelectedListener=listener{onChange(selectedItemPosition)}}
        addView(sp,LinearLayout.LayoutParams(0,48,1.05f).apply{leftMargin=10})
    }

    private fun bindThreeSecondSave(view:View,onSave:()->Unit,onClick:()->Unit){var task:Runnable?=null;var longDone=false;var down=0L;view.setOnTouchListener{v,e->when(e.action){MotionEvent.ACTION_DOWN->{longDone=false;down=System.currentTimeMillis();task?.let(handler::removeCallbacks);task=Runnable{longDone=true;onSave()};handler.postDelayed(task!!,3000);true};MotionEvent.ACTION_UP->{task?.let(handler::removeCallbacks);task=null;if(!longDone&&System.currentTimeMillis()-down<3000){onClick();v.performClick()};true};MotionEvent.ACTION_CANCEL->{task?.let(handler::removeCallbacks);task=null;true};else->true}}}

    private var pickerColor = intArrayOf(255,0,0,0)

    private fun buildMusicPickerPreview(): View {
        val box=LinearLayout(requireContext()).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(4,8,4,6)
        }
        box.addView(TextView(requireContext()).apply{
            text="Current Color"
            textSize=16f
            setTextColor(Color.WHITE)
            setTypeface(typeface,android.graphics.Typeface.BOLD)
            setPadding(0,0,0,8)
        })
        val picker=ColorPickerView(requireContext())
        box.addView(picker,LinearLayout.LayoutParams(-1,dp(300)))
        picker.setColor(255,pickerColor[0],pickerColor[1],pickerColor[2])
        picker.setColorAlpha(pickerColor[3]/255f*100f)
        picker.setBrightness(100)
        picker.setColorChangedListener(object:ColorPickerView.OnColorChangedListener{
            override fun colorChanged(centerColor:Int,argb:IntArray,hsv:FloatArray){
                pickerColor[0]=argb[1]
                pickerColor[1]=argb[2]
                pickerColor[2]=argb[3]
                // White is an independent Music parameter; changing hue must not alter it.
            }
        })
        box.addView(TextView(requireContext()).apply{
            text="Color Brightness"
            textSize=14f
            setTextColor(Color.WHITE)
            setTypeface(typeface,android.graphics.Typeface.BOLD)
            setPadding(0,12,0,4)
        })
        addLegacyMusicSlider(box,"Color Brightness",100,100,"%"){ value -> picker.setBrightness(value.toFloat()) }
        if(effects.first{it.id==effect}.white){
            box.addView(TextView(requireContext()).apply{
                text="White Brightness"
                textSize=14f
                setTextColor(Color.WHITE)
                setTypeface(typeface,android.graphics.Typeface.BOLD)
                setPadding(0,14,0,4)
            })
            addLegacyMusicSlider(box,"White Brightness",(pickerColor[3]*100/255),100,"%"){
                pickerColor[3]=it*255/100
                picker.setColorAlpha(it.toFloat())
            }
        }
        val hue=LinearLayout(requireContext()).apply{gravity=Gravity.CENTER}
        hue.addView(TextView(requireContext()).apply{
            text="− REV";gravity=Gravity.CENTER;textSize=11f;setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.white_box);setOnClickListener{picker.decreaseHue()}
        },LinearLayout.LayoutParams(0,34,1f).apply{rightMargin=6})
        hue.addView(TextView(requireContext()).apply{
            text="+ FWD";gravity=Gravity.CENTER;textSize=11f;setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.white_box);setOnClickListener{picker.increaseHue()}
        },LinearLayout.LayoutParams(0,34,1f).apply{leftMargin=6})
        box.addView(hue,LinearLayout.LayoutParams(-1,40).apply{topMargin=6})
        return box
    }

    private fun addLegacyMusicSlider(container:LinearLayout,label:String,value:Int,maxValue:Int,suffix:String,onChange:(Int)->Unit){
        val labelRow=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        labelRow.addView(TextView(requireContext()).apply{text=label;textSize=15f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,dp(32),1f))
        val out=TextView(requireContext()).apply{text="$value$suffix";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL or Gravity.END};labelRow.addView(out,LinearLayout.LayoutParams(dp(60),dp(32)))
        container.addView(labelRow,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8);bottomMargin=dp(5)})
        val bar=LayoutInflater.from(requireContext()).inflate(R.layout.slider_button,container,false);val slider=bar.findViewById<com.google.android.material.slider.Slider>(R.id.viewSlider);val minus=bar.findViewById<ImageView>(R.id.ivMinus);val plus=bar.findViewById<ImageView>(R.id.ivAdd)
        slider.valueFrom=0f;slider.valueTo=maxValue.toFloat();slider.stepSize=1f;slider.value=value.coerceIn(0,maxValue).toFloat()
        val applyValue={v:Int->val n=v.coerceIn(0,maxValue);slider.value=n.toFloat();out.text="$n$suffix";onChange(n)};minus.setOnClickListener{applyValue(slider.value.toInt()-1)};plus.setOnClickListener{applyValue(slider.value.toInt()+1)};slider.addOnChangeListener{_,v,fromUser->out.text="${v.toInt()}$suffix";if(fromUser)onChange(v.toInt())}
        container.addView(bar,LinearLayout.LayoutParams(-1,dp(35)).apply{bottomMargin=dp(14)})
    }

    private fun addMusicPresetSection(panel:LinearLayout){
        val kind=effect;val box=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(12,12,12,14);setBackgroundResource(R.drawable.dark_box_corner)}
        box.addView(TextView(requireContext()).apply{text="Presets";textSize=17f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=10})
        val grid=GridLayout(requireContext()).apply{columnCount=5;useDefaultMargins=false};val prefs=requireContext().getSharedPreferences("music_presets_$kind",Context.MODE_PRIVATE)
        repeat(5){i->val cell=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL};val b=TextView(requireContext()).apply{text="${i+1}";gravity=Gravity.CENTER;textSize=16f;setTypeface(typeface,android.graphics.Typeface.BOLD);setTextColor(Color.WHITE);setBackgroundColor(if(prefs.contains("$i"))Color.rgb(0,155,65) else Color.rgb(55,55,55))};bindThreeSecondSave(b,{saveMusicPreset(kind,i);b.setBackgroundColor(Color.rgb(0,155,65));Toast.makeText(requireContext(),"Preset ${i+1} saved",Toast.LENGTH_SHORT).show()},{loadMusicPreset(kind,i)});val reset=TextView(requireContext()).apply{text="RESET";gravity=Gravity.CENTER;textSize=8f;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(70,70,70));setOnClickListener{prefs.edit().remove("$i").apply();b.setBackgroundColor(Color.rgb(55,55,55))}};cell.addView(b,LinearLayout.LayoutParams(-1,dp(82)));cell.addView(reset,LinearLayout.LayoutParams(-1,dp(42)).apply{topMargin=dp(12)});grid.addView(cell,GridLayout.LayoutParams().apply{width=0;height=-2;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(6),dp(6),dp(6),dp(12))})}
        box.addView(grid);panel.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=12})
    }

    private fun musicPresetSnapshot():JSONObject {
        val o=JSONObject().put("brightness",brightness).put("white",white)
        val a=JSONArray();palette.forEachIndexed{i,c->a.put(JSONObject().put("r",c[0]).put("g",c[1]).put("b",c[2]).put("w",c[3]).put("saved",paletteSaved[i]))};o.put("palette",a)
        val b=JSONArray();spectrumColors.forEachIndexed{i,c->b.put(JSONObject().put("r",c[0]).put("g",c[1]).put("b",c[2]).put("w",c[3]).put("saved",spectrumSaved[i]).put("band",spectrumBandMap[i]))};o.put("spectrum",b)
        return o
    }
    private fun saveMusicPreset(kind:String,index:Int){requireContext().getSharedPreferences("music_presets_$kind",Context.MODE_PRIVATE).edit().putString("$index",musicPresetSnapshot().toString()).apply()}
    private fun loadMusicPreset(kind:String,index:Int){
        val text=requireContext().getSharedPreferences("music_presets_$kind",Context.MODE_PRIVATE).getString("$index",null) ?: return
        val o=JSONObject(text);brightness=o.optInt("brightness",brightness);white=o.optInt("white",white)
        o.optJSONArray("palette")?.let{a->for(i in 0 until min(10,a.length())){val c=a.getJSONObject(i);palette[i][0]=c.optInt("r",0);palette[i][1]=c.optInt("g",0);palette[i][2]=c.optInt("b",0);palette[i][3]=c.optInt("w",0);paletteSaved[i]=c.optBoolean("saved",false)}}
        o.optJSONArray("spectrum")?.let{a->for(i in 0 until min(10,a.length())){val c=a.getJSONObject(i);spectrumColors[i][0]=c.optInt("r",0);spectrumColors[i][1]=c.optInt("g",0);spectrumColors[i][2]=c.optInt("b",0);spectrumColors[i][3]=c.optInt("w",0);spectrumSaved[i]=c.optBoolean("saved",false);spectrumBandMap[i]=c.optInt("band",i.coerceAtMost(2));requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE).edit().putInt("$i.band",spectrumBandMap[i]).apply()}}
        renderControls();if(running)sendMusicSelection()
    }

    private fun renderSpectrumControls(panel:LinearLayout) {
        val count = activeColorCount(spectrumSaved)
        panel.addView(TextView(requireContext()).apply{text="Spectrum colors • Active: $count / 10";textSize=18f;setTextColor(Color.WHITE)})
        if(count==0){panel.addView(TextView(requireContext()).apply{text="Hold a color box for 3 seconds to save the current picker color.";setTextColor(Color.LTGRAY);textSize=12f});return}
        repeat(count){i->
            val row=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(TextView(requireContext()).apply{text=if(count<=3) listOf("Low / Bass","Mid","High")[i] else bandLabel(i);setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,48,1f))
            val b=Button(requireContext()).apply{text="Color ${i+1}";setBackgroundColor(Color.rgb(spectrumColors[i][0],spectrumColors[i][1],spectrumColors[i][2]));setOnClickListener{selectedColorIndex=i;renderControls()}}
            row.addView(b,LinearLayout.LayoutParams(105,48))
            row.addView(Button(requireContext()).apply { text="×"; minWidth=42; setOnClickListener { spectrumSaved[i]=false; spectrumColors[i]=intArrayOf(0,0,0,0); requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE).edit().putBoolean("$i.saved",false).apply(); renderControls(); if(running) sendMusicSelection() } }, LinearLayout.LayoutParams(48,48))
            if(count<=3){
                val sp=Spinner(requireContext()).apply{adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("Low / Bass","Mid","High"));setSelection(spectrumBandMap[i].coerceIn(0,2));onItemSelectedListener=listener{ spectrumBandMap[i]=selectedItemPosition; requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE).edit().putInt("$i.band",spectrumBandMap[i]).apply(); if(running)sendMusicSelection() }}
                row.addView(sp,LinearLayout.LayoutParams(105,48))
            }
            panel.addView(row)
        }
        panel.addView(TextView(requireContext()).apply{text=if(count<=3) "1–3 colors: assign each color to Bass / Mid / High. More than 3 colors: 80 Hz–20 kHz is divided into equal bands." else "80 Hz – 20 kHz divided into equal frequency bands.";setTextColor(Color.LTGRAY);textSize=12f})
    }

    private fun bandLabel(i:Int):String {
        val low=80.0
        val high=20000.0
        val a=low*Math.pow(high/low,i/10.0)
        val b=low*Math.pow(high/low,(i+1)/10.0)
        return "${a.toInt()}–${b.toInt()} Hz"
    }

    private fun activeColorCount(saved:BooleanArray):Int {
        var n = 0
        while (n < saved.size && saved[n]) n++
        return n
    }

    private fun resetMusicColor(index:Int,paletteTarget:Boolean) {
        val target: MutableList<IntArray> = if (paletteTarget) palette else spectrumColors
        val saved = if (paletteTarget) paletteSaved else spectrumSaved
        target[index] = intArrayOf(0, 0, 0, 0)
        saved[index] = false
        requireContext().getSharedPreferences(
            if (paletteTarget) "music_colors_$effect" else "music_spectrum_colors",
            Context.MODE_PRIVATE
        ).edit().putBoolean("$index.saved", false).apply()
    }

    private fun saveColor(index:Int,paletteTarget:Boolean=true){
        val target: MutableList<IntArray> = if (paletteTarget) palette else spectrumColors
        val saved = if(paletteTarget) paletteSaved else spectrumSaved
        target[index]=pickerColor.copyOf()
        saved[index]=true
        requireContext().getSharedPreferences(if(paletteTarget)"music_colors_$effect" else "music_spectrum_colors",Context.MODE_PRIVATE).edit()
            .putInt("$index.r",target[index][0]).putInt("$index.g",target[index][1]).putInt("$index.b",target[index][2]).putInt("$index.w",target[index][3])
            .putBoolean("$index.saved",true).apply()
        renderControls()
        if(running)sendMusicSelection()
    }

    private fun loadColors() {
        val p=requireContext().getSharedPreferences("music_colors_$effect",Context.MODE_PRIVATE)
        repeat(10){i->palette[i][0]=p.getInt("$i.r", if(i==0)255 else if(i==1)255 else if(i==2)255 else 0);palette[i][1]=p.getInt("$i.g", if(i==1)255 else 0);palette[i][2]=p.getInt("$i.b", if(i==2)255 else 0);palette[i][3]=p.getInt("$i.w",0);paletteSaved[i]=p.getBoolean("$i.saved",i<3)}
        val s=requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE)
        repeat(10){i->spectrumColors[i][0]=s.getInt("$i.r",if(i==0)255 else if(i==1)255 else if(i==2)255 else 0);spectrumColors[i][1]=s.getInt("$i.g",if(i==1)255 else 0);spectrumColors[i][2]=s.getInt("$i.b",if(i==2)255 else 0);spectrumColors[i][3]=s.getInt("$i.w",0);spectrumSaved[i]=s.getBoolean("$i.saved",i<3);spectrumBandMap[i]=s.getInt("$i.band",i.coerceAtMost(2))}
    }

    @RequiresApi(29)
    private fun startCapture(){
        val p=projection ?: return
        if(running)return
        try{
            val sr=44100;val minBuffer=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);if(minBuffer<=0)throw IllegalStateException("AudioRecord buffer unavailable")
            val cfg=AudioPlaybackCaptureConfiguration.Builder(p).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build()
            startMusicKeepAlive()
            recorder=AudioRecord.Builder().setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()).setBufferSizeInBytes(max(8192,minBuffer*2)).setAudioPlaybackCaptureConfig(cfg).build();recorder?.startRecording();if(recorder?.recordingState!=AudioRecord.RECORDSTATE_RECORDING)throw IllegalStateException("AudioRecord did not start")
            running=true
            sendMusicSelection()
            worker=Thread{loop(44100)}.also{it.start()}
         }catch(e:Exception){recorder?.release();recorder=null;running=false;stopMusicKeepAlive();Toast.makeText(requireContext(),"Music audio capture unavailable",Toast.LENGTH_SHORT).show()}
    }

    private fun stopCapture(releaseProjection:Boolean=true){
        running=false
        worker?.interrupt();worker=null
        try{recorder?.stop()}catch(_:Exception){}
        recorder?.release();recorder=null
        stopMusicKeepAlive()
        if(releaseProjection){projection?.stop();projection=null}
    }

    private fun startMusicKeepAlive(){
        if(Build.VERSION.SDK_INT>=26){
            androidx.core.content.ContextCompat.startForegroundService(
                requireContext(),
                Intent(requireContext(), MusicCaptureKeepAliveService::class.java)
            )
        } else {
            requireContext().startService(Intent(requireContext(), MusicCaptureKeepAliveService::class.java))
        }
    }

    private fun stopMusicKeepAlive(){
        requireContext().stopService(Intent(requireContext(), MusicCaptureKeepAliveService::class.java))
    }

    fun deactivateMusic(){
        stopCapture(releaseProjection = false)
    }

    private fun onlineMembers():List<HardwareGroupItem> =
        (requireActivity() as ControllerActivity).group?.groupItems.orEmpty().filter {
            !it.hardwareDevice?.deviceFrame.isNullOrBlank() && !it.hardwareDevice?.ip.isNullOrBlank()
        }

    private fun musicPixelIds(items:List<HardwareGroupItem>):List<Int> = items.map { it.PixelID.coerceIn(0,1023) }.sorted()

    private fun validMusicGroup(items:List<HardwareGroupItem>):Boolean {
        if(items.isEmpty()) return false
        val ids=musicPixelIds(items)
        return ids.size==ids.distinct().size && ids==ids.indices.toList()
    }

    private fun sendMusicSelection() {
        val a=requireActivity() as ControllerActivity
        val group=a.group
        val allItems=group?.groupItems.orEmpty()
        if(group==null){ Toast.makeText(requireContext(),"Music requires a group.",Toast.LENGTH_SHORT).show(); return }
        if(!validMusicGroup(allItems)){ Toast.makeText(requireContext(),"Music requires consecutive Pixel IDs starting at 0.",Toast.LENGTH_SHORT).show(); return }
        val items=onlineMembers()
        if(items.isEmpty()){ Toast.makeText(requireContext(),"No reachable MobileD in this group.",Toast.LENGTH_SHORT).show(); return }
        val base=a.getFrame()
        val command=if(items.any { val raw=it.hardwareDevice?.deviceFrame.orEmpty(); if(raw.isBlank()) false else JSONObject(raw).optInt("Command",0)==1 }) 1 else 0
        val total=allItems.size
        val gPort=if(group.allDevices) "8890" else items.firstOrNull()?.Gport?.ifBlank{"8889"} ?: "8889"
        items.sortedBy{it.PixelID}.forEach{item->
            val d=item.hardwareDevice ?: return@forEach
            val payload=JSONObject().apply{
                put("Command", command)
                put("GLights",100)
                put("GPort",gPort)
                put("GUniverse",base.optInt("GUniverse",32000).coerceIn(32000,32500))
                put("PixelID",item.PixelID.coerceIn(0,1023))
                put("PixelCount",total)
            }.toString()
            d.deviceFrame=payload
            UdpClient.getClient(requireContext()).writeString(payload,d.ip ?: return@forEach,if(d.port>0)d.port.toInt() else 8889)
        }
    }

    private fun loop(sr:Int) {
        val buf=ShortArray(4096)
        try {
            while(running){
                if(!musicTargetIsOn()){
                    handler.post { if(running) stopCapture(releaseProjection = false) }
                    break
                }
                val n=recorder?.read(buf,0,buf.size) ?: 0
                if(n<=0)continue
                var left=0f;var right=0f
                var sum=0.0
                var idx=0
                while(idx+1<n){
                    val l=abs(buf[idx].toInt())/32768f
                    val r=abs(buf[idx+1].toInt())/32768f
                    left=max(left,l);right=max(right,r);sum+=(l*l+r*r)/2.0;idx+=2
                }
                val level=sqrt(sum/max(1,n/2)).toFloat().coerceIn(0f,1f)
                val now=System.currentTimeMillis()
                if(level>max(0.45f,lastLevel*1.5f) && now-lastBeat>280){
                    if(lastBeat>0)bpm=(60000/(now-lastBeat)).toInt().coerceIn(60,200)
                    lastBeat=now
                }
                lastLevel=lastLevel*0.8f+level*0.2f
                handler.post{if(::vuLeft.isInitialized){vuLeft.level=left;vuRight.level=right;vuLeft.invalidate();vuRight.invalidate();}}
                renderFrame(buf,n,level,now)
            }
        } catch (e: Throwable) {
            android.util.Log.e("MobileDMusic", "Music capture loop failed", e)
            handler.post { if (running) stopCapture(releaseProjection = false) }
        }
    }

    private fun musicTargetIsOn():Boolean {
        val a=requireActivity() as ControllerActivity
        val g=a.group
        if(g!=null){
            val online=onlineMembers()
            return online.isNotEmpty() && online.any {
                val raw=it.hardwareDevice?.deviceFrame.orEmpty()
                raw.isNotBlank() && JSONObject(raw).optInt("Command",0)==1
            }
        }
        val d=a.device ?: return false
        val raw=d.deviceFrame.orEmpty()
        return raw.isNotBlank() && JSONObject(raw).optInt("Command",0)==1
    }

    private fun renderFrame(samples:ShortArray,n:Int,level:Float,now:Long) {
        val a=requireActivity() as ControllerActivity
        val allItems=a.group?.groupItems.orEmpty()
        val items=onlineMembers()
        if(items.isEmpty())return
        val count=if(a.group!=null) allItems.size else 1
        val musicBpm=80f + level.coerceIn(0f,1f)*80f
        phase=(phase + (musicBpm/60000f)*0.04f)%1f
        val colors=when(effect){
            "rainbow"->List(count){hsv(phase*360f+it*(360f/count),1f,(brightness/100f)*max(.08f,level))}
            "rainbowchase"->List(count){hsv((phase*360f+it*(360f/count)*3f)%360f,1f,(brightness/100f)*max(.08f,level))}
            "random"->{val pc=activeColorCount(paletteSaved).coerceAtLeast(1);List(count){val c=palette[(it+(now/160).toInt())%pc];scaled(c)}}
            "bpm"->{val pc=activeColorCount(paletteSaved).coerceAtLeast(1);List(count){val c=palette[((now/(60000L/max(1,bpm))).toInt()+it)%pc];scaled(c)}}
            "spectrum"->spectrumFrame(samples,n,count)
            "vumeter"->List(count){val pos=it.toFloat()/max(1,count-1);if(pos<0.6)rgb(0,255,0) else if(pos<0.85)rgb(255,255,0) else rgb(255,0,0)}
            else->List(count){rgb(0,0,0)}
        }
        val compact=colors
        val frame=MutableList(count){intArrayOf(0,0,0,0)}
        items.forEach{item-> val id=item.PixelID.coerceIn(0,1023); if(id<count) frame[id]=compact.getOrElse(id){intArrayOf(0,0,0,0)} }
        sendSacn(frame,allItems.sortedBy{it.PixelID})
    }

    private fun spectrumFrame(samples:ShortArray,n:Int,count:Int):List<IntArray>{
        val out=MutableList(count){intArrayOf(0,0,0,0)}
        val colorCount=activeColorCount(spectrumSaved).coerceIn(1,10)
        val half=min(1024,n/2)
        for(b in 0 until colorCount){
            val low:Double;val high:Double
            if(colorCount<=3){
                low=when(spectrumBandMap[b].coerceIn(0,2)){0->80.0;1->700.0;else->4000.0}
                high=when(spectrumBandMap[b].coerceIn(0,2)){0->700.0;1->4000.0;else->20000.0}
            }else{
                low=80.0*Math.pow(20000.0/80.0,b.toDouble()/colorCount);high=80.0*Math.pow(20000.0/80.0,(b+1).toDouble()/colorCount)
            }
            val k0=max(1,(low*half/44100.0).toInt());val k1=min(half-1,max(k0+1,(high*half/44100.0).toInt()))
            var mag=0.0
            for(k in k0..k1){var re=0.0;var im=0.0;val step=max(1,n/(half*2));for(i in 0 until min(n,2048) step step){val x=samples[i].toDouble()/32768.0;val angle=2.0*Math.PI*k*i/n;re+=x*cos(angle);im-=x*sin(angle)};mag+=sqrt(re*re+im*im)/max(1,n)}
            val v=(mag*18.0).coerceIn(0.0,1.0);val col=scaled(spectrumColors[b],v.toFloat())
            if(colorCount<=3){for(i in 0 until count){val ratio=i.toDouble()/max(1,count-1);val band=when{ratio<.33->0;ratio<.66->1;else->2};if(spectrumBandMap[b].coerceIn(0,2)==band)out[i]=col.copyOf()}}
            else{val start=(b*count)/colorCount;val end=max(start+1,((b+1)*count)/colorCount);for(i in start until min(count,end))out[i]=col.copyOf()}
        }
        return out
    }

    private fun rgb(r:Int,g:Int,b:Int)=intArrayOf(r,g,b,0)
    private fun scaled(c:IntArray,v:Float=1f)=intArrayOf((c[0]*v).toInt(),(c[1]*v).toInt(),(c[2]*v).toInt(),(c[3]*v).toInt())
    private fun hsv(h:Float,s:Float,v:Float):IntArray{
        val c=Color.HSVToColor(floatArrayOf((h%360f+360f)%360f,s,v.coerceIn(0f,1f)))
        return intArrayOf(Color.red(c),Color.green(c),Color.blue(c),0)
    }

    private fun sendSacn(frame:List<IntArray>,items:List<HardwareGroupItem>){
        val universe=(requireActivity() as ControllerActivity).getFrame().optInt("GUniverse",32000).coerceIn(32000,32500)
        var offset=0
        while(offset<frame.size){
            val chunk=frame.subList(offset,min(offset+128,frame.size))
            sendSacnUniverse(universe+(offset/128),chunk)
            offset+=chunk.size
        }
    }

    private fun sendSacnUniverse(universe:Int,pixels:List<IntArray>){
        try{
            val propCount=1+pixels.size*4
            val total=130+pixels.size*4
            val p=ByteArray(total)
            fun u16(o:Int,v:Int){p[o]=((v shr 8) and 255).toByte();p[o+1]=(v and 255).toByte()}
            p[0]=0;p[1]=0x10;p[2]=0;p[3]=0;"ASC-E1.17".toByteArray().copyInto(p,4)
            u16(16,0x7000 or (total-16))
            for(i in 0 until 16)p[22+i]=(i+1).toByte()
            "MobileD Android Music".padEnd(64,'\u0000').toByteArray().copyInto(p,44)
            p[108]=100;p[109]=0;p[110]=0;p[111]=(frameSeq++ and 255).toByte();u16(113,universe)
            u16(115,0x7000 or (total-115));p[118]=2;p[119]=0xA1.toByte();p[120]=0;p[121]=0;p[122]=2;u16(123,0);u16(125,1);u16(127,propCount);p[129]=0
            var o=130
            pixels.forEach{c->p[o]=c[0].toByte();p[o+1]=c[1].toByte();p[o+2]=c[2].toByte();p[o+3]=c[3].toByte();o+=4}
            val addr=InetAddress.getByName("239.255.${(universe shr 8) and 255}.${universe and 255}")
            MulticastSocket().use{it.timeToLive=1;it.send(DatagramPacket(p,p.size,addr,5568))}
        }catch(_:Exception){}
    }

    private fun listener(action:()->Unit)=object:AdapterView.OnItemSelectedListener{
        override fun onNothingSelected(parent:AdapterView<*>?){}
        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long)=action()
    }
}

class MusicVuBar(context: Context) : View(context) {
    var level: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = Color.argb(150, 0, 0, 0)
        canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, 7f, 7f, paint)
        val filled = h * level
        paint.color = Color.rgb(30, 210, 80)
        canvas.drawRoundRect(5f, h - filled, w - 5f, h - 4f, 5f, 5f, paint)
        if (level > .72f) {
            paint.color = Color.rgb(235, 220, 40)
            canvas.drawRect(5f, max(h * .28f, h - filled), w - 5f, h - 4f, paint)
        }
        if (level > .90f) {
            paint.color = Color.rgb(255, 70, 60)
            canvas.drawRect(5f, max(h * .10f, h - filled), w - 5f, h - 4f, paint)
        }
        paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = Color.argb(90, 255, 255, 255)
        canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, 7f, 7f, paint)
    }
}

class MusicBinding private constructor(private val root:View):androidx.viewbinding.ViewBinding{
    override fun getRoot()=root
    companion object{
        fun inflate(inflater:LayoutInflater,container:ViewGroup?,attach:Boolean=false):MusicBinding{
            val c=inflater.context
            val scroll=ScrollView(c)
            val l=LinearLayout(c).apply{id=9200;orientation=LinearLayout.VERTICAL;setPadding(12,10,12,20)}
            scroll.addView(l)
            return MusicBinding(scroll)
        }
    }
}
