package com.mobiled.android.ui.controller

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
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
import java.net.NetworkInterface
import java.net.Inet4Address
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.log10
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

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
    private var transition = "Fade"
    private var running = false
    private var projection: MediaProjection? = null
    private var projectionResultCode: Int = Activity.RESULT_CANCELED
    private var projectionData: Intent? = null
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var capturePending = false
    private var captureRequested = false
    private var audioPermissionInFlight = false
    private var projectionConsentInFlight = false
    private var serviceBound = false
    private val musicServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBound = true
            if (capturePending) createProjectionAndStartCapture()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
        }
    }
    private var phase = 0f
    private var lastBeat = 0L
    private var bpm = 100
    private var frameSeq = 0
    private var lastMusicFrameAt = 0L
    private var musicSacnSocket: MulticastSocket? = null
    private var musicSacnNetworkInterface: NetworkInterface? = null
    private var musicSacnRouteIp = ""
    private val musicSequences = HashMap<Int,Int>()
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

    // PC MusicEngine runtime state. These values intentionally mirror the
    // renderer/music_engine.js state rather than introducing an Android-only
    // timing model.
    private var beatPeriodMs = 600L
    private var beatPulse = false
    private var prevEnergy = 0f
    private val energyHistory = ArrayList<Float>()
    private var frequencySmoothing = FloatArray(256)
    private var lightLevel = 0f
    private var spectrumLightLevels = FloatArray(0)
    private var rainbowHue = 0f
    private var chaseOffset = 0f
    private var randomHues = FloatArray(0)
    private var bpmFromIndex = 0
    private var bpmToIndex = 0
    private var bpmTransitionStart = 0L
    private var bpmTransitionMode = "fade"
    private var musicSequenceCid: ByteArray = UUID.randomUUID().toString().replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view.findViewById(9200)
        vuLeft = MusicVuBar(requireContext())
        vuRight = MusicVuBar(requireContext())
        buildUi()
        val viewPager = (requireActivity() as ControllerActivity).binding.viewPager
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 3) {
                    requestMusicAuthorization()
                }
                // Music must continue running when the user navigates to
                // another page of the App. It is stopped only by an explicit
                // Music deactivation/app shutdown or by switching to another
                // effect through the normal command flow.
            }
        }
        pageCallback = callback
        viewPager.registerOnPageChangeCallback(callback)
        if (viewPager.currentItem == 3) {
            requestMusicAuthorization()
        }
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
        head.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_menu_item_music)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
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
            loadRuntimeState()
            renderControls()
            // Changing the Music effect is local to the App. The MobileD routing
            // command is sent only when Music capture/selection is started.
            if(!running) ensureCaptureAndStart()
        } catch (e: Throwable) {
            android.util.Log.e("MobileDMusic", "Music effect selection failed", e)
            Toast.makeText(requireContext(), "Music could not be started: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestMusicAuthorization() {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(requireContext(), "Music audio playback capture requires Android 10 or later.", Toast.LENGTH_SHORT).show()
            return
        }
        if (requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!audioPermissionInFlight) {
                audioPermissionInFlight = true
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }
        requestProjectionAuthorization()
    }

    private fun requestProjectionAuthorization() {
        if (projectionData != null || projection != null || projectionConsentInFlight) return
        projectionConsentInFlight = true
        val manager = requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun ensureCaptureAndStart() {
        if (Build.VERSION.SDK_INT < 29) return
        captureRequested = true
        if (requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!audioPermissionInFlight) {
                audioPermissionInFlight = true
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }
        if (projectionData == null && projection == null) {
            requestProjectionAuthorization()
            return
        }
        if (!running) startCaptureAfterAuthorization()
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        audioPermissionInFlight = false
        if (granted) {
            requestProjectionAuthorization()
        }
    }

    private val requestProjectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        projectionConsentInFlight = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            projectionResultCode = result.resultCode
            projectionData = result.data
            if (captureRequested && !running) startCaptureAfterAuthorization()
        } else {
            projectionResultCode = Activity.RESULT_CANCELED
            projectionData = null
            captureRequested = false
        }
    }

    @RequiresApi(29)
    private fun createProjectionAndStartCapture() {
        if (!capturePending || running) return
        capturePending = false
        try {
            val data = projectionData ?: throw IllegalStateException("MediaProjection consent data missing")
            val manager = requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(projectionResultCode, data)
            if (projection == null) throw IllegalStateException("MediaProjection unavailable")
            startCapture()
        } catch (e: Throwable) {
            projection = null
            stopMusicKeepAlive()
            android.util.Log.e("MobileDMusic", "MediaProjection could not be created", e)
            Toast.makeText(requireContext(), "Audio capture could not be started.", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(29)
    private fun startCaptureAfterAuthorization() {
        if (running || capturePending) return
        if (projection == null && projectionData == null) {
            ensureCaptureAndStart()
            return
        }
        capturePending = true
        try {
            startMusicKeepAlive()
            if (!serviceBound) {
                val intent = Intent(requireContext(), MusicCaptureKeepAliveService::class.java)
                requireContext().bindService(intent, musicServiceConnection, Context.BIND_AUTO_CREATE)
            } else {
                createProjectionAndStartCapture()
            }
        } catch (e: Throwable) {
            capturePending = false
            stopMusicKeepAlive()
            android.util.Log.e("MobileDMusic", "Music foreground service could not start", e)
            Toast.makeText(requireContext(), "Music audio capture unavailable", Toast.LENGTH_SHORT).show()
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
        slider(main,"Brightness",brightness,100,"%"){brightness=it;saveRuntimeState()}
        if(effects.first{it.id==effect}.white)slider(main,"White",white,100,"%"){white=it;saveRuntimeState()}
        host.addView(main,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})

        // PC App places the effect presets before the color editor.
        if(effect=="bpm"||effect=="spectrum")addMusicPresetSection(host)
        if(effect=="bpm"||effect=="spectrum")addMusicColors(host)
        if(effect=="spectrum"){val spectrum=box("Spectrum");renderSpectrumControls(spectrum);host.addView(spectrum,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})}
        if(effect=="bpm"){val transitionBox=box("BPM");val options=listOf("Fade","Direct change","Fade out / fade in");transitionBox.addView(spinnerRowMusic("Transition type",options,options.indexOf(transition).coerceAtLeast(0)){position->transition=options[position];saveRuntimeState()},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4)});host.addView(transitionBox,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})}
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
            val reset=TextView(requireContext()).apply{text="RESET";gravity=Gravity.CENTER;textSize=10f;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(70,70,70));setOnClickListener{resetMusicColor(i,effect=="bpm");renderControls()}}
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

    private fun loadPickerColor() {
        val p = requireContext().getSharedPreferences("music_picker_current", Context.MODE_PRIVATE)
        pickerColor[0] = p.getInt("r", 255).coerceIn(0,255)
        pickerColor[1] = p.getInt("g", 0).coerceIn(0,255)
        pickerColor[2] = p.getInt("b", 0).coerceIn(0,255)
        pickerColor[3] = p.getInt("w", 0).coerceIn(0,255)
    }

    private fun savePickerColor() {
        requireContext().getSharedPreferences("music_picker_current", Context.MODE_PRIVATE).edit()
            .putInt("r", pickerColor[0]).putInt("g", pickerColor[1])
            .putInt("b", pickerColor[2]).putInt("w", pickerColor[3]).apply()
    }

    private fun buildMusicPickerPreview(): View {
        loadPickerColor()
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
        val initialColorBrightness = (maxOf(pickerColor[0], pickerColor[1], pickerColor[2]) * 100 / 255).coerceIn(0,100)
        picker.setBrightness(initialColorBrightness)
        picker.setColorAlpha((pickerColor[3] * 100 / 255).coerceIn(0,100))
        picker.setColorChangedListener(object:ColorPickerView.OnColorChangedListener{
            override fun colorChanged(centerColor:Int,argb:IntArray,hsv:FloatArray){
                pickerColor[0]=argb[1]
                pickerColor[1]=argb[2]
                pickerColor[2]=argb[3]
                savePickerColor()
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
        val colorBrightness = (maxOf(pickerColor[0], pickerColor[1], pickerColor[2]) * 100 / 255).coerceIn(0,100)
        addLegacyMusicSlider(box,"Color Brightness",colorBrightness,100,"%"){ value ->
            picker.setBrightness(value)
            picker.setColorAlpha((pickerColor[3] * 100 / 255).coerceIn(0,100))
            // ColorPickerView emits the updated RGB through colorChangedListener;
            // that listener persists the independent Current Color picker state.
        }
        if(effects.first{it.id==effect}.white || effect=="bpm" || effect=="spectrum"){
            box.addView(TextView(requireContext()).apply{
                text="White Brightness"
                textSize=14f
                setTextColor(Color.WHITE)
                setTypeface(typeface,android.graphics.Typeface.BOLD)
                setPadding(0,14,0,4)
            })
            addLegacyMusicSlider(box,"White Brightness",(pickerColor[3]*100/255),100,"%"){
                pickerColor[3]=it*255/100
                picker.setColorAlpha(it)
                savePickerColor()
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

    private fun musicPresetSnapshot(kind:String):JSONObject {
        // Match the PC App: Music presets store the effect's color configuration,
        // not the shared Main Brightness/White controls. BPM transition belongs
        // only to BPM and must never be overwritten by a Spectrum preset.
        val o=JSONObject().put("kind",kind)
        if (kind == "bpm") o.put("transition",transition)
        val targetColors = if (kind == "spectrum") spectrumColors else palette
        val targetSaved = if (kind == "spectrum") spectrumSaved else paletteSaved
        val a=JSONArray()
        targetColors.forEachIndexed { i,c ->
            val color=JSONObject().put("r",c[0]).put("g",c[1]).put("b",c[2]).put("w",c[3]).put("saved",targetSaved[i])
            if (kind == "spectrum") color.put("band",spectrumBandMap[i])
            a.put(color)
        }
        o.put("colors",a)
        return o
    }

    private fun saveMusicPreset(kind:String,index:Int) {
        requireContext().getSharedPreferences("music_presets_$kind",Context.MODE_PRIVATE).edit()
            .putString("$index",musicPresetSnapshot(kind).toString()).apply()
    }

    private fun loadMusicPreset(kind:String,index:Int) {
        val text=requireContext().getSharedPreferences("music_presets_$kind",Context.MODE_PRIVATE).getString("$index",null) ?: return
        val o=runCatching { JSONObject(text) }.getOrNull() ?: return
        if (o.optString("kind", kind) != kind) return
        if (kind == "bpm") transition=o.optString("transition",transition)
        val targetColors = if (kind == "spectrum") spectrumColors else palette
        val targetSaved = if (kind == "spectrum") spectrumSaved else paletteSaved
        o.optJSONArray("colors")?.let { a ->
            for (i in 0 until min(10,a.length())) {
                val c=a.optJSONObject(i) ?: continue
                targetColors[i][0]=c.optInt("r",0).coerceIn(0,255)
                targetColors[i][1]=c.optInt("g",0).coerceIn(0,255)
                targetColors[i][2]=c.optInt("b",0).coerceIn(0,255)
                targetColors[i][3]=c.optInt("w",0).coerceIn(0,255)
                targetSaved[i]=c.optBoolean("saved",false)
                if (kind == "spectrum") {
                    spectrumBandMap[i]=c.optInt("band",i.coerceAtMost(2)).coerceIn(0,2)
                    requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE).edit()
                        .putInt("$i.r",targetColors[i][0]).putInt("$i.g",targetColors[i][1])
                        .putInt("$i.b",targetColors[i][2]).putInt("$i.w",targetColors[i][3])
                        .putBoolean("$i.saved",targetSaved[i]).putInt("$i.band",spectrumBandMap[i]).apply()
                } else {
                    requireContext().getSharedPreferences("music_colors_bpm",Context.MODE_PRIVATE).edit()
                        .putInt("$i.r",targetColors[i][0]).putInt("$i.g",targetColors[i][1])
                        .putInt("$i.b",targetColors[i][2]).putInt("$i.w",targetColors[i][3])
                        .putBoolean("$i.saved",targetSaved[i]).apply()
                }
            }
        }
        saveRuntimeState()
        renderControls()
    }

    private fun renderSpectrumControls(panel:LinearLayout) {
        val activeIndices = spectrumSaved.indices.filter { spectrumSaved[it] }
        val count = activeIndices.size
        panel.addView(TextView(requireContext()).apply{text="Spectrum colors • Active: $count / 10";textSize=18f;setTextColor(Color.WHITE)})
        if(count==0){panel.addView(TextView(requireContext()).apply{text="Hold a color box for 3 seconds to save the current picker color.";setTextColor(Color.LTGRAY);textSize=12f});return}
        activeIndices.forEachIndexed { displayIndex, colorIndex ->
            val row=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(TextView(requireContext()).apply{text=if(count<=3) listOf("Low / Bass","Mid","High")[displayIndex] else bandLabel(displayIndex,count);setTextColor(Color.WHITE)},LinearLayout.LayoutParams(0,48,1f))
            val b=Button(requireContext()).apply{text="Color ${colorIndex+1}";setBackgroundColor(Color.rgb(spectrumColors[colorIndex][0],spectrumColors[colorIndex][1],spectrumColors[colorIndex][2]))}
            row.addView(b,LinearLayout.LayoutParams(105,48))
            row.addView(Button(requireContext()).apply {
                text="×"; minWidth=42
                setOnClickListener {
                    spectrumSaved[colorIndex]=false
                    spectrumColors[colorIndex]=intArrayOf(0,0,0,0)
                    requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE).edit()
                        .putInt("$colorIndex.r",0).putInt("$colorIndex.g",0).putInt("$colorIndex.b",0).putInt("$colorIndex.w",0)
                        .putBoolean("$colorIndex.saved",false).apply()
                    renderControls()
                }
            }, LinearLayout.LayoutParams(48,48))
            if(count<=3){
                val sp=Spinner(requireContext()).apply{
                    adapter=ArrayAdapter(requireContext(),android.R.layout.simple_spinner_dropdown_item,listOf("Low / Bass","Mid","High"))
                    setSelection(spectrumBandMap[colorIndex].coerceIn(0,2),false)
                    onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
                        override fun onNothingSelected(parent:AdapterView<*>?){ }
                        override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){
                            if(position in 0..2 && spectrumBandMap[colorIndex] != position){
                                spectrumBandMap[colorIndex]=position
                                requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE).edit().putInt("$colorIndex.band",position).apply()
                            }
                        }
                    }
                }
                row.addView(sp,LinearLayout.LayoutParams(105,48))
            }
            panel.addView(row)
        }
        panel.addView(TextView(requireContext()).apply{text=if(count<=3) "1–3 colors: assign each color to Bass / Mid / High. More than 3 colors: 80 Hz–20 kHz is divided into equal logarithmic bands." else "80 Hz – 20 kHz divided into equal logarithmic bands.";setTextColor(Color.LTGRAY);textSize=12f})
    }

    private fun bandLabel(i:Int,count:Int):String {
        val low=80.0
        val high=20000.0
        val bands=maxOf(1,count)
        val a=low*Math.pow(high/low, i.toDouble()/bands)
        val b=low*Math.pow(high/low, (i+1).toDouble()/bands)
        fun fmt(hz:Double)=if(hz>=1000) String.format(java.util.Locale.US,"%.1f kHz",hz/1000.0) else "${hz.toInt()} Hz"
        return "${fmt(a)} – ${fmt(b)}"
    }

    private fun activeColorCount(saved:BooleanArray):Int = saved.count { it }

    private fun resetMusicColor(index:Int,paletteTarget:Boolean) {
        val target: MutableList<IntArray> = if (paletteTarget) palette else spectrumColors
        val saved = if (paletteTarget) paletteSaved else spectrumSaved
        target[index] = intArrayOf(0, 0, 0, 0)
        saved[index] = false
        val prefs = requireContext().getSharedPreferences(
            if (paletteTarget) "music_colors_$effect" else "music_spectrum_colors",
            Context.MODE_PRIVATE
        )
        prefs.edit().putInt("$index.r",0).putInt("$index.g",0).putInt("$index.b",0)
            .putInt("$index.w",0).putBoolean("$index.saved", false).apply()
    }

    private fun saveColor(index:Int,paletteTarget:Boolean=true){
        val target: MutableList<IntArray> = if (paletteTarget) palette else spectrumColors
        val saved = if(paletteTarget) paletteSaved else spectrumSaved
        loadPickerColor()
        target[index]=pickerColor.copyOf()
        saved[index]=true
        requireContext().getSharedPreferences(if(paletteTarget)"music_colors_$effect" else "music_spectrum_colors",Context.MODE_PRIVATE).edit()
            .putInt("$index.r",target[index][0]).putInt("$index.g",target[index][1]).putInt("$index.b",target[index][2]).putInt("$index.w",target[index][3])
            .putBoolean("$index.saved",true).apply()
        renderControls()
    }

    private fun saveRuntimeState() {
        requireContext().getSharedPreferences("music_runtime_$effect", Context.MODE_PRIVATE).edit()
            .putString("state", JSONObject().put("brightness",brightness).put("white",white).put("transition",transition).toString())
            .apply()
    }

    private fun loadRuntimeState() {
        val raw=requireContext().getSharedPreferences("music_runtime_$effect",Context.MODE_PRIVATE).getString("state",null) ?: return
        val o=runCatching{JSONObject(raw)}.getOrNull() ?: return
        brightness=o.optInt("brightness",brightness).coerceIn(0,100)
        white=o.optInt("white",white).coerceIn(0,100)
        transition=o.optString("transition",transition).ifBlank{"Fade"}
    }

    private fun loadColors() {
        val p=requireContext().getSharedPreferences("music_colors_$effect",Context.MODE_PRIVATE)
        repeat(10){i->palette[i][0]=p.getInt("$i.r", if(i==0)255 else if(i==1)255 else if(i==2)255 else 0);palette[i][1]=p.getInt("$i.g", if(i==1)255 else 0);palette[i][2]=p.getInt("$i.b", if(i==2)255 else 0);palette[i][3]=p.getInt("$i.w",0);paletteSaved[i]=p.getBoolean("$i.saved",false)}
        val s=requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE)
        repeat(10){i->spectrumColors[i][0]=s.getInt("$i.r",if(i==0)255 else if(i==1)255 else if(i==2)255 else 0);spectrumColors[i][1]=s.getInt("$i.g",if(i==1)255 else 0);spectrumColors[i][2]=s.getInt("$i.b",if(i==2)255 else 0);spectrumColors[i][3]=s.getInt("$i.w",0);spectrumSaved[i]=s.getBoolean("$i.saved",false);spectrumBandMap[i]=s.getInt("$i.band",i.coerceAtMost(2))}
    }

    @RequiresApi(29)
    private fun startCapture(){
        val p=projection ?: return
        if(running)return
        try{
            val sr=44100;val minBuffer=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);if(minBuffer<=0)throw IllegalStateException("AudioRecord buffer unavailable")
            val cfg=AudioPlaybackCaptureConfiguration.Builder(p).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build()
            recorder=AudioRecord.Builder().setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()).setBufferSizeInBytes(max(8192,minBuffer*2)).setAudioPlaybackCaptureConfig(cfg).build();recorder?.startRecording();if(recorder?.recordingState!=AudioRecord.RECORDSTATE_RECORDING)throw IllegalStateException("AudioRecord did not start")
            running=true
            lastBeat=0L
            beatPeriodMs=600L
            prevEnergy=0f
            energyHistory.clear()
            lightLevel=0f
            spectrumLightLevels=FloatArray(0)
            frequencySmoothing=FloatArray(256)
            rainbowHue=0f
            chaseOffset=0f
            randomHues=FloatArray(0)
            bpmFromIndex=0
            bpmToIndex=0
            bpmTransitionStart=System.currentTimeMillis()
            bpmTransitionMode="fade"
            musicSequenceCid=UUID.randomUUID().toString().replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            lastMusicFrameAt=0L
            sendMusicSelection()
            worker=Thread{loop(44100)}.also{it.start()}
         }catch(e:Exception){
             recorder?.release();recorder=null;running=false;stopMusicKeepAlive()
             android.util.Log.e("MobileDMusic", "Music AudioRecord capture failed: ${e.javaClass.name}: ${e.message}", e)
             Toast.makeText(requireContext(),"Music audio capture unavailable",Toast.LENGTH_SHORT).show()
         }
    }

    private fun releaseMusicServiceBinding() {
        if (serviceBound) {
            try { requireContext().unbindService(musicServiceConnection) } catch (_: Exception) {}
            serviceBound = false
        }
    }

    private fun stopCapture(releaseProjection:Boolean=true){
        running=false
        worker?.interrupt();worker=null
        try{recorder?.stop()}catch(_:Exception){}
        recorder?.release();recorder=null
        if (serviceBound) {
            try { requireContext().unbindService(musicServiceConnection) } catch (_: Exception) {}
            serviceBound = false
        }
        stopMusicKeepAlive()
        closeMusicSacnSocket()
        if(releaseProjection){
            captureRequested = false
            projection?.stop()
            projection = null
            projectionData = null
            projectionResultCode = Activity.RESULT_CANCELED
        }
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
        stopCapture(releaseProjection = true)
    }

    private fun onlineMembers():List<HardwareGroupItem> =
        (requireActivity() as ControllerActivity).getControllerVirtualStripSnapshot().sortedBy { it.PixelID }

    private fun musicPixelIds(items:List<HardwareGroupItem>):List<Int> = items.map { it.PixelID.coerceIn(0,1023) }.sorted()

    private fun validMusicGroup(items:List<HardwareGroupItem>):Boolean {
        if(items.isEmpty()) return false
        val ids=musicPixelIds(items)
        val uniqueIds=ids.distinct()
        return uniqueIds==uniqueIds.indices.toList()
    }

    private fun sendMusicSelection() {
        saveRuntimeState()
        val a=requireActivity() as ControllerActivity
        if(!a.canOpenDistributedEffects("Music")) return
        val group=a.group ?: run {
            Toast.makeText(requireContext(),"Music requires a group.",Toast.LENGTH_SHORT).show(); return
        }
        val items=onlineMembers()
        if(items.isEmpty()){ Toast.makeText(requireContext(),"No reachable MobileD in this group.",Toast.LENGTH_SHORT).show(); return }
        if(!validMusicGroup(items)){ Toast.makeText(requireContext(),"Music requires consecutive Pixel IDs starting at 0.",Toast.LENGTH_SHORT).show(); return }

        val universe=group.GUniverse.takeIf { it in 32000..32500 }
            ?: run { Toast.makeText(requireContext(),"Invalid Music GUniverse (32000-32500).",Toast.LENGTH_LONG).show(); return }
        val gPort=if(group.allDevices) 8890 else group.groupItems.orEmpty()
            .firstOrNull()?.Gport?.toIntOrNull()?.takeIf { it in 10000..65535 }
            ?: run { Toast.makeText(requireContext(),"Invalid Music GPort.",Toast.LENGTH_LONG).show(); return }

        val command=if(items.any {
            val raw=it.hardwareDevice?.deviceFrame.orEmpty()
            raw.isNotBlank() && JSONObject(raw).optInt("Command",0)==1
        }) 1 else 0
        val total=musicPixelIds(items).distinct().size
        val client=UdpClient.getClient(requireContext())

        // PC App: one discrete Music selection command immediately, followed
        // by the normal 50 ms / 15 ms trailing reliability sequence.
        items.sortedBy{it.PixelID}.forEach{item->
            val d=item.hardwareDevice ?: return@forEach
            val ip=d.ip?.trim().orEmpty()
            if(ip.isBlank()) return@forEach
            val payload=JSONObject().apply{
                put("Command", command)
                put("GLights",100)
                put("GPort",gPort)
                put("GUniverse",universe)
                put("PixelID",item.PixelID)
                put("PixelCount",total)
            }.toString()
            if(payload != d.activeCommandFrame){
                client.writeCommandString(payload,ip)
                d.rememberSentCommand(payload)
            }
        }
    }

    private fun loop(sr:Int) {
        val buf=ShortArray(1024)
        try {
            while(running){
                val n=recorder?.read(buf,0,buf.size) ?: 0
                if(n<=0)continue

                var left=0f
                var right=0f
                var idx=0
                while(idx+1<n){
                    left=max(left,abs(buf[idx].toInt())/32768f)
                    right=max(right,abs(buf[idx+1].toInt())/32768f)
                    idx+=2
                }
                val raw=max(left,right).coerceIn(0f,1f)
                val now=System.currentTimeMillis()
                val fd=frequencyByteData(buf,n)
                val low=(0 until 18).map { (fd[it].toInt() and 0xff).toFloat()/255f }.average().toFloat()
                val energy=.72f*low+.28f*raw

                energyHistory.add(energy)
                if(energyHistory.size>43) energyHistory.removeAt(0)
                val averageEnergy=energyHistory.average().toFloat()
                if(energy > averageEnergy*1.16f &&
                    energy > prevEnergy*1.01f &&
                    now-lastBeat>220L){
                    if(lastBeat>0L){
                        val interval=now-lastBeat
                        if(interval in 220L..1600L) beatPeriodMs=lerpLong(beatPeriodMs,interval,.35f)
                    }
                    lastBeat=now
                    beatPulse=true
                }
                prevEnergy=energy
                bpm=(60000f/max(1L,beatPeriodMs).toFloat()).roundToInt().coerceIn(80,160)

                handler.post{
                    if(::vuLeft.isInitialized){
                        vuLeft.level=left
                        vuRight.level=right
                        vuLeft.invalidate()
                        vuRight.invalidate()
                    }
                }
                if(now-lastMusicFrameAt>=33L){
                    lastMusicFrameAt=now
                    renderFrame(fd,n,raw,left,right,now)
                }
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
            if(online.isEmpty()) return false
            return online.all {
                val raw=it.hardwareDevice?.deviceFrame.orEmpty()
                if(raw.isBlank()) return@all false
                val command=JSONObject(raw).optInt("Command",-1)
                command==0 || command==1
            }
        }
        val d=a.device ?: return false
        val raw=d.deviceFrame.orEmpty()
        if(raw.isBlank()) return false
        val command=JSONObject(raw).optInt("Command",-1)
        return command==0 || command==1
    }

    private fun lerpLong(a:Long,b:Long,t:Float):Long = (a+(b-a)*t).roundToInt().toLong()

    private fun smoothLightLevel(target:Float,dt:Float):Float{
        val delta=target.coerceIn(0f,1f)-lightLevel
        val magnitude=abs(delta)
        val rate=if(magnitude<.08f)5f else 12f
        val alpha=(1f-exp(-rate*dt.coerceIn(.001f,.1f)))
        lightLevel=(lightLevel+delta*alpha).coerceIn(0f,1f)
        return lightLevel
    }

    private fun smoothSpectrumLevel(index:Int,target:Float,dt:Float):Float{
        if(spectrumLightLevels.size!=onlineMembers().size)spectrumLightLevels=FloatArray(onlineMembers().size)
        val current=spectrumLightLevels.getOrElse(index){0f}
        val delta=target.coerceIn(0f,1f)-current
        val magnitude=abs(delta)
        val rate=if(magnitude<.08f)5f else 12f
        val alpha=(1f-exp(-rate*dt.coerceIn(.001f,.1f)))
        spectrumLightLevels[index]=(current+delta*alpha).coerceIn(0f,1f)
        return spectrumLightLevels[index]
    }

    private fun matrixBeatHueStep():Float{
        val detectedBpm=(60000.0/max(1L,beatPeriodMs).toDouble()).coerceIn(80.0,160.0)
        val speed=1.0+((detectedBpm-80.0)/80.0)*99.0
        val level=floor((speed-1.0)/10.0).toInt()
        val within=(speed-1.0)%10.0
        val base=256.0 * 2.0.pow(level.toDouble())
        val next=if(level>=9)base else base*2.0
        val rate=base+(next-base)*within/10.0
        return ((rate*max(1L,beatPeriodMs))/1000.0/256.0).toFloat()%256f
    }

    private fun hueForColor(c:IntArray):Float{
        val hsv=FloatArray(3)
        Color.RGBToHSV(c[0].coerceIn(0,255),c[1].coerceIn(0,255),c[2].coerceIn(0,255),hsv)
        return hsv[0]
    }

    private fun colorValue(c:IntArray):Float{
        return max(c[0],max(c[1],c[2]))/255f
    }

    private fun writeMusicPixel(out:MutableList<IntArray>,index:Int,hue:Float,level:Float,whiteExtra:Float=0f){
        val rgb=Color.HSVToColor(floatArrayOf(((hue%360f)+360f)%360f,1f,1f))
        val b=brightness.coerceIn(0,100)/100f
        val whiteValue=Math.max(whiteExtra,this.white/100f*255f)
        out[index]=intArrayOf(
            (Color.red(rgb)*level*b).roundToInt().coerceIn(0,255),
            (Color.green(rgb)*level*b).roundToInt().coerceIn(0,255),
            (Color.blue(rgb)*level*b).roundToInt().coerceIn(0,255),
            (whiteValue*level*b).roundToInt().coerceIn(0,255)
        )
    }

    private fun renderFrame(fd:ByteArray,n:Int,level:Float,leftLevel:Float,rightLevel:Float,now:Long) {
        val a=requireActivity() as ControllerActivity
        val group=a.group ?: return
        val items=onlineMembers()
        if(items.isEmpty())return
        val count=musicPixelIds(items).distinct().size.coerceAtLeast(1)
        val dt=.033f
        val frame=MutableList(count){intArrayOf(0,0,0,0)}

        bpmTransitionMode=when(transition){
            "Fade"->"fade"
            "Direct change"->"direct"
            "Fade out / fade in"->"crossfade"
            else->"fade"
        }
        val beatDetected = beatPulse
        beatPulse = false

        when(effect){
            "rainbow"->{
                if(beatDetected) rainbowHue=(rainbowHue+matrixBeatHueStep())%360f
                val l=smoothLightLevel(level,dt)
                for(i in 0 until count) writeMusicPixel(frame,i,rainbowHue,l,white.toFloat()/100f*255f)
            }
            "random"->{
                if(randomHues.size!=count)randomHues=FloatArray(count){Random.nextFloat()*360f}
                if(beatDetected)for(i in 0 until count)randomHues[i]=Random.nextFloat()*360f
                val l=smoothLightLevel(level,dt)
                for(i in 0 until count)writeMusicPixel(frame,i,randomHues[i],l,white.toFloat()/100f*255f)
            }
            "rainbowchase"->{
                if(beatDetected)chaseOffset=(chaseOffset+matrixBeatHueStep())%360f
                val l=smoothLightLevel(level,dt)
                val step=360f/max(1,count)
                for(i in 0 until count)writeMusicPixel(frame,i,(chaseOffset+i*step)%360f,l,white.toFloat()/100f*255f)
            }
            "bpm"->{
                val seq=paletteSaved.indices.filter { paletteSaved[it] }
                if(seq.isEmpty()){
                    for(i in 0 until count)frame[i]=intArrayOf(0,0,0,0)
                }else{
                    if(beatDetected){
                        // Exact PC MusicEngine sequencing:
                        // current phase is the source color; the next phase is
                        // selected as the target on each detected beat.
                        bpmFromIndex=bpmToIndex % seq.size
                        bpmToIndex=(bpmFromIndex+1)%seq.size
                        bpmTransitionStart=now
                    }
                    val from=hueForColor(palette[seq[bpmFromIndex%seq.size]])
                    val to=hueForColor(palette[seq[bpmToIndex%seq.size]])
                    val elapsed=max(0L,now-bpmTransitionStart).toFloat()
                    val beatMs=max(80L,beatPeriodMs).toFloat()
                    var h=to
                    var l=smoothLightLevel(level,dt)
                    val p=elapsed/max(40f,beatMs/3f)
                    when(bpmTransitionMode){
                        "fade"->{h=interpolateHue(from,to,p.coerceIn(0f,1f));l=level}
                        "crossfade"->{
                            when{
                                p<1f->{h=from;l=level*(1f-p)}
                                p<2f->{h=to;l=0f}
                                else->{h=to;l=level*(p-2f).coerceIn(0f,1f)}
                            }
                        }
                        else->{h=to;l=level}
                    }
                    for(i in 0 until count)writeMusicPixel(frame,i,h,l,white.toFloat()/100f*255f)
                }
            }
            "spectrum"->{
                val assign=spectrumSaved.indices.filter { spectrumSaved[it] }
                val colorCount=assign.size.coerceIn(0,10)
                if(colorCount>0){
                    for(i in 0 until count){
                        val groupIndex=min(colorCount-1,(i*colorCount)/max(1,count))
                        val bandIndex=if(colorCount<=3)spectrumBandMap[assign[groupIndex]].coerceIn(0,2) else groupIndex
                        val range=bandRange(bandIndex,if(colorCount<=3)3 else colorCount)
                        val e=audioBand(fd,range.first,range.second)
                        val raw=(e*.72f).coerceIn(0f,1f)
                        val l=smoothSpectrumLevel(i,raw,dt)
                        val c=spectrumColors[assign[groupIndex]]
                        val h=hueForColor(c)
                        val whiteValue=c[3].coerceIn(0,255).toFloat()
                        writeMusicPixel(frame,i,h,l,whiteValue)
                    }
                }
            }
            "vumeter"->{
                val l=smoothLightLevel(max(leftLevel,rightLevel),dt)
                val litCount=kotlin.math.ceil(l*count).toInt().coerceIn(0,count)
                for(i in 0 until count){
                    if(i>=litCount)continue
                    val pos=if(count>1)i.toFloat()/(count-1) else 1f
                    val base=when{
                        pos<.70f->intArrayOf(0,255,0,0)
                        pos<.90f->intArrayOf(255,255,0,0)
                        else->intArrayOf(255,0,0,0)
                    }
                    val k=brightness/100f
                    frame[i]=intArrayOf((base[0]*k).roundToInt(),(base[1]*k).roundToInt(),(base[2]*k).roundToInt(),0)
                }
            }
        }

        val universes=HashMap<Int,ByteArray>()
        items.forEach{item->
            val pixel=item.PixelID
            val u=group.GUniverse+floor(pixel/128.0).toInt()
            val ch=(pixel%128)*4
            val dmx=universes.getOrPut(u){ByteArray(512)}
            val p=frame.getOrElse(pixel){intArrayOf(0,0,0,0)}
            dmx[ch]=p[0].toByte();dmx[ch+1]=p[1].toByte();dmx[ch+2]=p[2].toByte();dmx[ch+3]=p[3].toByte()
        }
        val routeIp=items.firstOrNull()?.hardwareDevice?.ip?.trim().orEmpty()
        if(routeIp.isBlank())return
        universes.forEach{(u,dmx)->sendSacnUniverse(if(group.allDevices) 8890 else group.groupItems.orEmpty().firstOrNull()?.Gport?.toIntOrNull() ?: 0,u,dmx,routeIp)}
    }

    private fun frequencyByteData(samples:ShortArray,n:Int):ByteArray{
        val size=512
        val real=DoubleArray(size)
        val imag=DoubleArray(size)
        var p=0
        var t=0
        while(p+1<n && t<size){
            val mono=(samples[p].toInt()+samples[p+1].toInt())/(2.0*32768.0)
            val window=0.42-0.5*kotlin.math.cos(2.0*Math.PI*t/(size-1))+0.08*kotlin.math.cos(4.0*Math.PI*t/(size-1))
            real[t]=mono*window
            p+=2
            t++
        }

        // Android AudioRecord does not expose Web Audio's AnalyserNode.
        // Reproduce the PC analyser's 512-point frequency path here: the
        // same Blackman window and the same 0.05 magnitude smoothing used by
        // the PC MusicEngine's AnalyserNode configuration.
        var j=0
        for(i in 1 until size){
            var bit=size shr 1
            while(j and bit != 0){ j=j xor bit; bit=bit shr 1 }
            j=j xor bit
            if(i<j){
                val tr=real[i];real[i]=real[j];real[j]=tr
                val ti=imag[i];imag[i]=imag[j];imag[j]=ti
            }
        }
        var len=2
        while(len<=size){
            val ang=-2.0*Math.PI/len
            val wLenR=kotlin.math.cos(ang)
            val wLenI=kotlin.math.sin(ang)
            var i=0
            while(i<size){
                var wr=1.0
                var wi=0.0
                for(k in 0 until len/2){
                    val uR=real[i+k]
                    val uI=imag[i+k]
                    val vR=real[i+k+len/2]*wr-imag[i+k+len/2]*wi
                    val vI=real[i+k+len/2]*wi+imag[i+k+len/2]*wr
                    real[i+k]=uR+vR
                    imag[i+k]=uI+vI
                    real[i+k+len/2]=uR-vR
                    imag[i+k+len/2]=uI-vI
                    val nwr=wr*wLenR-wi*wLenI
                    wi=wr*wLenI+wi*wLenR
                    wr=nwr
                }
                i+=len
            }
            len=len shl 1
        }

        val out=ByteArray(256)
        for(k in 0 until 256){
            val mag=2.0*sqrt(real[k]*real[k]+imag[k]*imag[k])/size
            val smoothed=frequencySmoothing[k]*0.05+mag*0.95
            frequencySmoothing[k]=smoothed.toFloat()
            val db=20.0*log10(max(1e-8,smoothed))
            out[k]=((db+100.0)/70.0*255.0).roundToInt().coerceIn(0,255).toByte()
        }
        return out
    }

    private fun audioBand(fd:ByteArray,a:Float,b:Float):Float{
        val lo=max(0,floor(a*255f).toInt())
        val hi=min(255,max(lo+1,kotlin.math.ceil(b*255f).toInt()))
        var sum=0f
        var n=0
        for(i in lo until hi){sum+=(fd[i].toInt() and 0xff)/255f;n++}
        return if(n>0)sum/n else 0f
    }

    private fun bandRange(index:Int,count:Int):Pair<Float,Float>{
        val minHz=80.0
        val maxHz=20000.0
        val nyquist=22050.0
        val bands=maxOf(1,count)
        val i=index.coerceIn(0,bands-1)
        val lo=minHz*Math.pow(maxHz/minHz,i.toDouble()/bands)
        val hi=minHz*Math.pow(maxHz/minHz,(i+1).toDouble()/bands)
        return (lo/nyquist).toFloat() to (hi/nyquist).toFloat()
    }

    private fun interpolateHue(a:Float,b:Float,t:Float):Float{
        val tc=t.coerceIn(0f,1f)
        var d=((b-a+540f)%360f)-180f
        return (a+d*tc)%360f
    }

    private fun closeMusicSacnSocket(){
        try{musicSacnSocket?.close()}catch(_:Exception){}
        musicSacnSocket=null
        musicSacnNetworkInterface=null
        musicSacnRouteIp=""
    }

    private fun sendSacnUniverse(gPort:Int,universe:Int,dmx:ByteArray,routeIp:String){
        if(gPort<1024 || gPort==8889 || gPort==6454 || gPort==5568) return
        if(universe !in 32000..32500)return
        try{
            if(musicSacnSocket==null || musicSacnRouteIp!=routeIp){
                closeMusicSacnSocket()
                val socket=MulticastSocket()
                socket.timeToLive=1
                socket.reuseAddress=true
                musicSacnNetworkInterface=findInterfaceForRoute(routeIp)
                if(musicSacnNetworkInterface!=null)socket.networkInterface=musicSacnNetworkInterface
                musicSacnSocket=socket
                musicSacnRouteIp=routeIp
            }
            val socket=musicSacnSocket ?: return
            val seq=((musicSequences[universe] ?: 0)+1) and 255
            musicSequences[universe]=seq
            val p=ByteArray(127+512)
            fun u16(o:Int,v:Int){p[o]=((v shr 8) and 255).toByte();p[o+1]=(v and 255).toByte()}
            u16(0,0x0010);u16(2,0)
            byteArrayOf(0x41,0x53,0x43,0x2d,0x45,0x31,0x2e,0x31,0x37,0,0,0).copyInto(p,4)
            u16(16,0x727e);u32(p,18,4,musicSequenceCid)
            musicSequenceCid.copyInto(p,22)
            u16(38,0x704d);u32(p,40,2,musicSequenceCid)
            "MobileD Music".toByteArray(Charsets.US_ASCII).copyInto(p,44)
            p[108]=100
            u16(109,0)
            p[111]=seq.toByte()
            u16(112,0)
            u16(114,universe)
            u16(116,0x720b)
            p[118]=2;p[119]=0xA1.toByte()
            u16(120,0)
            u16(122,1)
            u16(124,513)
            p[126]=0
            dmx.copyInto(p,127,0,512)
            val addr=InetAddress.getByName("239.255.${(universe shr 8) and 255}.${universe and 255}")
            socket.send(DatagramPacket(p,p.size,addr,gPort))
        }catch(e:Exception){
            closeMusicSacnSocket()
            android.util.Log.e("MobileDMusic","sACN send failed",e)
        }
    }

    private fun findInterfaceForRoute(routeIp:String):NetworkInterface?{
        return try{
            val target=InetAddress.getByName(routeIp) as? Inet4Address ?: return null
            val targetBytes=target.address
            val en=NetworkInterface.getNetworkInterfaces()
            while(en.hasMoreElements()){
                val ni=en.nextElement()
                if(!ni.isUp || ni.isLoopback)continue
                for(ia in ni.interfaceAddresses){
                    val addr=ia.address as? Inet4Address ?: continue
                    val prefix=ia.networkPrefixLength.toInt()
                    if(prefix<=0 || prefix>32)continue
                    val mask=if(prefix==32)0xffffffffL else (0xffffffffL shl (32-prefix)) and 0xffffffffL
                    val a=java.nio.ByteBuffer.wrap(addr.address).int.toLong() and 0xffffffffL
                    val b=java.nio.ByteBuffer.wrap(targetBytes).int.toLong() and 0xffffffffL
                    if((a and mask)==(b and mask))return ni
                }
            }
            null
        }catch(_:Exception){null}
    }

    private fun u32(p:ByteArray,o:Int,v:Int,cid:ByteArray){
        p[o]=((v ushr 24) and 255).toByte()
        p[o+1]=((v ushr 16) and 255).toByte()
        p[o+2]=((v ushr 8) and 255).toByte()
        p[o+3]=(v and 255).toByte()
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
