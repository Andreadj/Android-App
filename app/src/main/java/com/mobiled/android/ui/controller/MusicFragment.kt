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
import com.mobiled.android.adapters.GeneralUtil
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

class MusicFragment : BaseFragment<MusicBinding>() {
    companion object {
        private const val SACN_UDP_PORT = 5568

        fun newInstance() = MusicFragment()
    }

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
    @Volatile private var running = false
    @Volatile private var captureGeneration = 0
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
    private var serviceBindingRequested = false
    private var boundMusicService: MusicCaptureKeepAliveService? = null
    private val musicServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBound = true
            if (!capturePending && !running) {
                releaseMusicServiceBinding()
                return
            }
            val service = (binder as? MusicCaptureKeepAliveService.LocalBinder)?.getService()
            if (service?.captureReady != true) {
                failCapture("Music audio capture service could not start")
                return
            }
            boundMusicService = service
            service.captureStopListener = { failCapture("Music audio capture stopped") }
            if (capturePending) {
                if (Build.VERSION.SDK_INT >= 29) createProjectionAndStartCapture()
                else failCapture("Music audio playback capture requires Android 10 or later")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            boundMusicService = null
            if (running || capturePending) failCapture("Music audio capture service disconnected")
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
    private val musicSacnLastLogAt = HashMap<String,Long>()
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
    private var bpmPickerBrightness = 100
    private var bpmPickerWhite = 0
    private var spectrumPickerBrightness = 100
    private var spectrumPickerWhite = 0

    // PC MusicEngine runtime state. These values intentionally mirror the
    // renderer/music_engine.js state rather than introducing an Android-only
    // timing model.
    private var beatPeriodMs = 600L
    private var beatPulse = false
    private var prevEnergy = 0f
    private val energyHistory = ArrayList<Float>()
    private var lightLevel = 0f
    private var spectrumLightLevels = FloatArray(0)
    private val spectrumInput = FloatArray(2048)
    private val spectrumFftReal = DoubleArray(2048)
    private val spectrumFftImag = DoubleArray(2048)
    private var spectrumWriteIndex = 0
    private var spectrumSamplesWritten = 0
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
        root = view.findViewById(R.id.music_root)
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

    override fun onDestroy() {
        // Background/page navigation leaves the Fragment alive. A destroyed
        // Fragment must release its recorder, projection, service and worker.
        stopCapture(releaseProjection = true)
        super.onDestroy()
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
        loadColors()
        loadRuntimeState()
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
            // Match the PC App: send the Music routing command before requesting
            // capture permissions or starting AudioRecord.
            if (!running && sendMusicSelection()) ensureCaptureAndStart()
        } catch (e: Throwable) {
            android.util.Log.e("MobileDMusic", "Music effect selection failed", e)
            Toast.makeText(requireContext(), "Music could not be started. Check the selected lights and allow audio sharing when prompted.", Toast.LENGTH_LONG).show()
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
        } else {
            captureRequested = false
            capturePending = false
        }
    }

    private val requestProjectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        projectionConsentInFlight = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            projectionResultCode = result.resultCode
            projectionData = result.data
            if (captureRequested && !running && Build.VERSION.SDK_INT >= 29) startCaptureAfterAuthorization()
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
            if (projection == null) {
                val data = projectionData ?: throw IllegalStateException("MediaProjection consent data missing")
                val manager = requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val session = manager.getMediaProjection(projectionResultCode, data)
                    ?: throw IllegalStateException("MediaProjection unavailable")
                projection = session
                // A consent Intent is single use on Android 14. Keep the live
                // projection, and request fresh consent after ending it.
                projectionData = null
                session.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (projection === session) failCapture("Music audio capture authorization ended")
                    }
                }, handler)
            }
            startCapture()
        } catch (e: Throwable) {
            android.util.Log.e("MobileDMusic", "MediaProjection could not be created", e)
            failCapture("Audio capture could not be started")
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
                serviceBindingRequested = requireContext().bindService(intent, musicServiceConnection, Context.BIND_AUTO_CREATE)
                if (!serviceBindingRequested) throw IllegalStateException("Music capture service binding failed")
            } else {
                createProjectionAndStartCapture()
            }
        } catch (e: Throwable) {
            android.util.Log.e("MobileDMusic", "Music foreground service could not start", e)
            failCapture("Music audio capture unavailable")
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
        val isBpm = effect == "bpm"
        val targetPalette=if(effect=="spectrum")spectrumColors else palette
        val saved=if(effect=="spectrum")spectrumSaved else paletteSaved
        val grid=GridLayout(requireContext()).apply{columnCount=5;useDefaultMargins=false}
        repeat(10){i->
            val cell=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL}
            val sw=TextView(requireContext()).apply{
                text="${i+1}";gravity=Gravity.CENTER;textSize=16f;setTypeface(typeface,android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setBackgroundColor(if(saved[i]) musicColorSwatch(targetPalette[i]) else Color.rgb(55,55,55))
                setTextColor(if(saved[i] && musicColorSwatch(targetPalette[i])==Color.WHITE) Color.BLACK else Color.WHITE)
            }
            bindThreeSecondSave(sw,{
                selectedColorIndex=i
                saveColor(i,isBpm)
                Toast.makeText(requireContext(),"Color ${i+1} saved",Toast.LENGTH_SHORT).show()
            },{
                if(isBpm || effect=="spectrum"){
                    selectedColorIndex=i
                    pickerColor=targetPalette[i].copyOf()
                    val rgbBrightness=(maxOf(pickerColor[0],pickerColor[1],pickerColor[2])*100/255).coerceIn(0,100)
                    val whiteBrightness=(pickerColor[3]*100/255).coerceIn(0,100)
                    if(isBpm){bpmPickerBrightness=rgbBrightness;bpmPickerWhite=whiteBrightness}
                    else{spectrumPickerBrightness=rgbBrightness;spectrumPickerWhite=whiteBrightness}
                    savePickerColor()
                    renderControls()
                }
            })
            val reset=TextView(requireContext()).apply{text="RESET";gravity=Gravity.CENTER;textSize=10f;setTextColor(Color.WHITE);setBackgroundColor(Color.rgb(70,70,70));setOnClickListener{resetMusicColor(i,isBpm);if((isBpm || effect=="spectrum") && selectedColorIndex==i){pickerColor=intArrayOf(0,0,0,0);if(isBpm){bpmPickerBrightness=0;bpmPickerWhite=0}else{spectrumPickerBrightness=0;spectrumPickerWhite=0};savePickerColor()};renderControls()}}
            cell.addView(sw,LinearLayout.LayoutParams(-1,dp(78)));cell.addView(reset,LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(12)})
            grid.addView(cell,GridLayout.LayoutParams().apply{width=0;height=-2;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(6),dp(6),dp(6),dp(12))})
        }
        colorsBox.addView(grid)
        colorsBox.addView(buildMusicPickerPreview(),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
        host.addView(colorsBox,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)})
    }

    private fun spinnerRowMusic(label: String, options: List<String>, selected: Int, onChange: (Int) -> Unit): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val rowHeight = dp(42)
        addView(TextView(requireContext()).apply {
            text = label; textSize = 16f; setTextColor(Color.WHITE); maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, rowHeight, 0.95f))
        val spinner = Spinner(requireContext()).apply {
            val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = TextView(requireContext()).apply {
                    text = options[position]; textSize = 16f; setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(10, 0, 10, 0)
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = TextView(requireContext()).apply {
                    text = options[position]; textSize = 16f; setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(12, 0, 12, 0); minimumHeight = dp(42)
                }
            }
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(45, 45, 45)))
            setSelection(selected.coerceIn(0, options.lastIndex), false)
            setBackgroundResource(R.drawable.grey_box)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) {} ; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onChange(position) } }
        }
        addView(spinner, LinearLayout.LayoutParams(0, rowHeight, 1.05f).apply { leftMargin = 10 })
    }

    private fun bindThreeSecondSave(view:View,onSave:()->Unit,onClick:()->Unit){var task:Runnable?=null;var longDone=false;var down=0L;view.setOnTouchListener{v,e->when(e.action){MotionEvent.ACTION_DOWN->{longDone=false;down=System.currentTimeMillis();task?.let(handler::removeCallbacks);task=Runnable{longDone=true;onSave()};handler.postDelayed(task!!,3000);true};MotionEvent.ACTION_UP->{task?.let(handler::removeCallbacks);task=null;if(!longDone&&System.currentTimeMillis()-down<3000){onClick();v.performClick()};true};MotionEvent.ACTION_CANCEL->{task?.let(handler::removeCallbacks);task=null;true};else->true}}}

    private var pickerColor = intArrayOf(255,0,0,0)

    private fun loadPickerColor() {
        val p = requireContext().getSharedPreferences(when(effect){"bpm"->"music_picker_bpm";"spectrum"->"music_picker_spectrum";else->"music_picker_current"}, Context.MODE_PRIVATE)
        pickerColor[0] = p.getInt("r", 255).coerceIn(0,255)
        pickerColor[1] = p.getInt("g", 0).coerceIn(0,255)
        pickerColor[2] = p.getInt("b", 0).coerceIn(0,255)
        pickerColor[3] = p.getInt("w", 0).coerceIn(0,255)
        if (effect == "bpm") {
            bpmPickerBrightness = p.getInt("brightness", bpmPickerBrightness).coerceIn(0,100)
            bpmPickerWhite = p.getInt("white", (pickerColor[3] * 100 / 255)).coerceIn(0,100)
        } else if (effect == "spectrum") {
            spectrumPickerBrightness = p.getInt("brightness", spectrumPickerBrightness).coerceIn(0,100)
            spectrumPickerWhite = p.getInt("white", (pickerColor[3] * 100 / 255)).coerceIn(0,100)
        }
    }

    private fun savePickerColor() {
        val brightness = if(effect=="spectrum") spectrumPickerBrightness else bpmPickerBrightness
        val white = if(effect=="spectrum") spectrumPickerWhite else bpmPickerWhite
        requireContext().getSharedPreferences(when(effect){"bpm"->"music_picker_bpm";"spectrum"->"music_picker_spectrum";else->"music_picker_current"}, Context.MODE_PRIVATE).edit()
            .putInt("r", pickerColor[0]).putInt("g", pickerColor[1])
            .putInt("b", pickerColor[2]).putInt("w", pickerColor[3])
            .putInt("brightness", brightness).putInt("white", white).apply()
    }

    private fun buildMusicPickerPreview(): View {
        if (effect == "bpm" || effect == "spectrum") return buildBpmPickerPreview()
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
        val colorBrightness = (maxOf(pickerColor[0], pickerColor[1], pickerColor[2]) * 100 / 255).coerceIn(0,100)
        addLegacyMusicSlider(box,"Color Brightness",colorBrightness,100,"%"){ value ->
            picker.setBrightness(value)
            picker.setColorAlpha((pickerColor[3] * 100 / 255).coerceIn(0,100))
            // ColorPickerView emits the updated RGB through colorChangedListener;
            // that listener persists the independent Current Color picker state.
        }
        if(effects.first{it.id==effect}.white || effect=="bpm" || effect=="spectrum"){
            addLegacyMusicSlider(box,"White Brightness",(pickerColor[3]*100/255),100,"%"){
                pickerColor[3]=it*255/100
                picker.setColorAlpha(it)
                savePickerColor()
            }
        }
        val hue=LinearLayout(requireContext()).apply{gravity=Gravity.CENTER;orientation=LinearLayout.HORIZONTAL}
        fun hueButton(label:String,click:()->Unit)=TextView(requireContext()).apply{
            text=label;gravity=Gravity.CENTER;textSize=20f;setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.white_box);setOnClickListener{click()}
        }
        hue.addView(hueButton("− REV"){picker.decreaseHue()},LinearLayout.LayoutParams(0,dp(36),1f).apply{rightMargin=dp(6)})
        hue.addView(hueButton("+ FWD"){picker.increaseHue()},LinearLayout.LayoutParams(0,dp(36),1f).apply{leftMargin=dp(6)})
        box.addView(hue,LinearLayout.LayoutParams(-1,dp(40)).apply{topMargin=dp(6)})
        return box
    }

    /** BPM palette editor follows the same interaction/layout rules as Matrix BPM,
     * while storing its colors and presets in Music-specific preferences. */
    private fun buildBpmPickerPreview(): View {
        loadPickerColor()
        val box=LinearLayout(requireContext()).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(8),dp(4),dp(6))}
        box.addView(TextView(requireContext()).apply{text="Current Color";textSize=16f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(0,dp(12),0,dp(8))})
        val pickerFrame=FrameLayout(requireContext())
        val picker=ColorPickerView(requireContext())
        pickerFrame.addView(picker,FrameLayout.LayoutParams(-1,dp(360)))
        val hue=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
        fun hueButton(label:String,click:()->Unit)=TextView(requireContext()).apply{
            text=label;gravity=Gravity.CENTER;textSize=14f;maxLines=1;setPadding(dp(4),0,dp(4),0);setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.white_box);setOnClickListener{click()}
        }
        hue.addView(hueButton("− REV"){picker.decreaseHue()},LinearLayout.LayoutParams(-2,dp(24)))
        hue.addView(View(requireContext()),LinearLayout.LayoutParams(0,1,1f))
        hue.addView(hueButton("+ FWD"){picker.increaseHue()},LinearLayout.LayoutParams(-2,dp(24)))
        pickerFrame.addView(hue,FrameLayout.LayoutParams(-1,dp(24),Gravity.BOTTOM).apply{leftMargin=dp(4);rightMargin=dp(4);bottomMargin=dp(2)})
        box.addView(pickerFrame,LinearLayout.LayoutParams(-1,dp(360)))
        val readout=TextView(requireContext()).apply{gravity=Gravity.CENTER;textSize=13f;setTextColor(Color.WHITE);setPadding(0,dp(8),0,dp(8))}
        box.addView(readout,LinearLayout.LayoutParams(-1,-2))
        fun updateFromPicker(){
            val hsv=picker.getHsv().copyOf()
            val rgb=Color.HSVToColor(hsv)
            pickerColor[0]=Color.red(rgb).coerceIn(0,255);pickerColor[1]=Color.green(rgb).coerceIn(0,255);pickerColor[2]=Color.blue(rgb).coerceIn(0,255)
            val whiteValue = if(effect=="spectrum") spectrumPickerWhite else bpmPickerWhite
            pickerColor[3]=(whiteValue*255f/100f).roundToInt().coerceIn(0,255)
            readout.text="RGB ${pickerColor[0]} / ${pickerColor[1]} / ${pickerColor[2]}    W ${pickerColor[3]}"
            savePickerColor()
        }
        picker.setColorChangedListener(object:ColorPickerView.OnColorChangedListener{
            override fun colorChanged(centerColor:Int,argb:IntArray,hsv:FloatArray){updateFromPicker()}
        })
        picker.setColor(255,pickerColor[0],pickerColor[1],pickerColor[2])
        val initialBrightness = if(effect=="spectrum") spectrumPickerBrightness else bpmPickerBrightness
        val initialWhite = if(effect=="spectrum") spectrumPickerWhite else bpmPickerWhite
        picker.setBrightness(initialBrightness)
        picker.setColorAlpha(GeneralUtil.generateAlphaByWhiteBrightness(initialWhite.toFloat(),initialBrightness.toFloat()))
        updateFromPicker()
        val currentBrightness = if(effect=="spectrum") spectrumPickerBrightness else bpmPickerBrightness
        val currentWhite = if(effect=="spectrum") spectrumPickerWhite else bpmPickerWhite
        addLegacyMusicSlider(box,"Color Brightness",currentBrightness,100,"%") { value ->
            if(effect=="spectrum") spectrumPickerBrightness=value else bpmPickerBrightness=value
            picker.setBrightness(value)
            picker.setColorAlpha(GeneralUtil.generateAlphaByWhiteBrightness(currentWhite.toFloat(),value.toFloat()))
            updateFromPicker()
        }
        addLegacyMusicSlider(box,"White Brightness",currentWhite,100,"%") { value ->
            if(effect=="spectrum") spectrumPickerWhite=value else bpmPickerWhite=value
            pickerColor[3]=(value*255f/100f).roundToInt().coerceIn(0,255)
            picker.setColorAlpha(GeneralUtil.generateAlphaByWhiteBrightness(value.toFloat(),currentBrightness.toFloat()))
            updateFromPicker()
        }
        return box
    }

    private fun addLegacyMusicSlider(container:LinearLayout,label:String,value:Int,maxValue:Int,suffix:String,onChange:(Int)->Unit){
        val labelRow=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        labelRow.addView(TextView(requireContext()).apply{text=label;textSize=15f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,dp(32),1f))
        val out=TextView(requireContext()).apply{text="$value$suffix";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL or Gravity.END};labelRow.addView(out,LinearLayout.LayoutParams(dp(60),dp(32)))
        container.addView(labelRow,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8);bottomMargin=dp(5)})
        val bar=LayoutInflater.from(requireContext()).inflate(R.layout.slider_button,container,false);val slider=bar.findViewById<com.google.android.material.slider.Slider>(R.id.viewSlider);val minus=bar.findViewById<ImageView>(R.id.ivMinus);val plus=bar.findViewById<ImageView>(R.id.ivAdd)
        bar.layoutDirection=View.LAYOUT_DIRECTION_LTR
        slider.layoutDirection=View.LAYOUT_DIRECTION_LTR
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
            val b=Button(requireContext()).apply{
                text="Color ${colorIndex+1}"
                setTextColor(if(musicColorSwatch(spectrumColors[colorIndex])==Color.WHITE) Color.BLACK else Color.WHITE)
                setBackgroundColor(musicColorSwatch(spectrumColors[colorIndex]))
            }
            row.addView(b,LinearLayout.LayoutParams(0,dp(48),1f).apply{rightMargin=dp(6)})
            if(count<=3){
                val sp=Spinner(requireContext()).apply{
                    val bandOptions=listOf("Low / Bass","Mid","High")
                    adapter=object : ArrayAdapter<String>(requireContext(),android.R.layout.simple_spinner_item,bandOptions) {
                        override fun getView(position:Int, convertView:View?, parent:android.view.ViewGroup):View {
                            val item=super.getView(position,convertView,parent) as TextView
                            item.setTextColor(Color.WHITE)
                            item.textSize=14f
                            item.setPadding(dp(8),0,dp(8),0)
                            return item
                        }
                        override fun getDropDownView(position:Int, convertView:View?, parent:android.view.ViewGroup):View {
                            val item=super.getDropDownView(position,convertView,parent) as TextView
                            item.setTextColor(Color.BLACK)
                            item.setBackgroundColor(Color.WHITE)
                            item.textSize=14f
                            item.setPadding(dp(12),dp(8),dp(12),dp(8))
                            return item
                        }
                    }
                    setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
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
                row.addView(sp,LinearLayout.LayoutParams(0,dp(48),1.15f).apply{rightMargin=dp(6)})
            } else {
                row.addView(TextView(requireContext()).apply{text=bandLabel(displayIndex,count);setTextColor(Color.WHITE);gravity=Gravity.CENTER; textSize=11f},LinearLayout.LayoutParams(0,dp(48),1.15f).apply{rightMargin=dp(6)})
            }
            row.addView(Button(requireContext()).apply {
                text="×"; minWidth=dp(42)
                setOnClickListener {
                    spectrumSaved[colorIndex]=false
                    spectrumColors[colorIndex]=intArrayOf(0,0,0,0)
                    requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE).edit()
                        .putInt("$colorIndex.r",0).putInt("$colorIndex.g",0).putInt("$colorIndex.b",0).putInt("$colorIndex.w",0)
                        .putBoolean("$colorIndex.saved",false).apply()
                    renderControls()
                }
            }, LinearLayout.LayoutParams(dp(44),dp(48)))
            panel.addView(row,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4)})
        }
        panel.addView(TextView(requireContext()).apply{text=if(count<=3) "Assign each saved color to Low / Bass, Mid or High. More than 3 colors: 80 Hz–20 kHz is divided into equal frequency bands." else "80 Hz – 20 kHz divided into equal frequency bands.";setTextColor(Color.LTGRAY);textSize=12f})
    }

    private fun bandLabel(i:Int,count:Int):String {
        val low=80.0
        val high=20000.0
        val bands=maxOf(1,count)
        val a=low+(high-low)*i.toDouble()/bands
        val b=low+(high-low)*(i+1).toDouble()/bands
        fun fmt(hz:Double)=if(hz>=1000) String.format(java.util.Locale.US,"%.1f kHz",hz/1000.0) else "${hz.toInt()} Hz"
        return "${fmt(a)} – ${fmt(b)}"
    }

    private fun musicColorSwatch(c:IntArray):Int {
        val r=c.getOrElse(0){0}.coerceIn(0,255);val g=c.getOrElse(1){0}.coerceIn(0,255)
        val b=c.getOrElse(2){0}.coerceIn(0,255);val w=c.getOrElse(3){0}.coerceIn(0,255)
        if(r==0 && g==0 && b==0) return Color.rgb(w,w,w)
        if(w==0) return Color.rgb(r,g,b)
        val mix=(w/255f)*0.8f
        return Color.rgb((r+(255-r)*mix).toInt().coerceIn(0,255),(g+(255-g)*mix).toInt().coerceIn(0,255),(b+(255-b)*mix).toInt().coerceIn(0,255))
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
        if (!(paletteTarget && effect == "bpm")) loadPickerColor()
        target[index]=pickerColor.copyOf()
        saved[index]=true
        requireContext().getSharedPreferences(if(paletteTarget)"music_colors_$effect" else "music_spectrum_colors",Context.MODE_PRIVATE).edit()
            .putInt("$index.r",target[index][0]).putInt("$index.g",target[index][1]).putInt("$index.b",target[index][2]).putInt("$index.w",target[index][3])
            .putBoolean("$index.saved",true).apply()
        renderControls()
    }

    private fun saveRuntimeState() {
        requireContext().getSharedPreferences("music_runtime_shared", Context.MODE_PRIVATE).edit()
            .putInt("brightness",brightness).putInt("white",white).apply()
        requireContext().getSharedPreferences("music_runtime_$effect", Context.MODE_PRIVATE).edit()
            .putString("state", JSONObject().put("transition",transition).toString())
            .apply()
    }

    private fun loadRuntimeState() {
        val raw=requireContext().getSharedPreferences("music_runtime_$effect",Context.MODE_PRIVATE).getString("state",null)
        val previous=raw?.let { runCatching{JSONObject(it)}.getOrNull() }
        val shared=requireContext().getSharedPreferences("music_runtime_shared",Context.MODE_PRIVATE)
        // Main brightness and procedural White are shared across Music effects.
        // Migrate the current effect's old values once, without changing palettes.
        brightness=shared.getInt("brightness",previous?.optInt("brightness",brightness) ?: brightness).coerceIn(0,100)
        white=shared.getInt("white",previous?.optInt("white",white) ?: white).coerceIn(0,100)
        shared.edit().putInt("brightness",brightness).putInt("white",white).apply()
        if(effect=="bpm") transition=previous?.optString("transition",transition)?.ifBlank{"Fade"} ?: transition
    }

    private fun loadColors() {
        val defaultHues = floatArrayOf(0f, 120f, 240f)
        fun defaultRgb(index: Int): IntArray = if (index < defaultHues.size) {
            // Canonical hue defaults converted to RGB for preference fallbacks.
            val color = Color.HSVToColor(floatArrayOf(defaultHues[index], 1f, 1f))
            intArrayOf(Color.red(color), Color.green(color), Color.blue(color))
        } else intArrayOf(0, 0, 0)

        val p=requireContext().getSharedPreferences("music_colors_$effect",Context.MODE_PRIVATE)
        repeat(10){i->
            val d=defaultRgb(i)
            palette[i][0]=p.getInt("$i.r",d[0]);palette[i][1]=p.getInt("$i.g",d[1]);palette[i][2]=p.getInt("$i.b",d[2]);palette[i][3]=p.getInt("$i.w",0);paletteSaved[i]=p.getBoolean("$i.saved",i < 3)
        }
        val s=requireContext().getSharedPreferences("music_spectrum_colors",Context.MODE_PRIVATE)
        repeat(10){i->
            val d=defaultRgb(i)
            spectrumColors[i][0]=s.getInt("$i.r",d[0]);spectrumColors[i][1]=s.getInt("$i.g",d[1]);spectrumColors[i][2]=s.getInt("$i.b",d[2]);spectrumColors[i][3]=s.getInt("$i.w",0);spectrumSaved[i]=s.getBoolean("$i.saved",i < 3);spectrumBandMap[i]=s.getInt("$i.band",i.coerceAtMost(2))
        }
    }

    @RequiresApi(29)
    private fun startCapture(){
        val p=projection ?: return
        if(running)return
        try{
            // The selection command was sent before requesting capture authorization.
            // Do not resend here: Discovery may already have changed after navigation.
            val sr=48000;val minBuffer=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);if(minBuffer<=0)throw IllegalStateException("AudioRecord buffer unavailable")
            val cfg=AudioPlaybackCaptureConfiguration.Builder(p).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
            if (requireContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                failCapture("Music audio capture requires audio recording permission")
                return
            }
            recorder=AudioRecord.Builder().setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()).setBufferSizeInBytes(max(8192,minBuffer*2)).setAudioPlaybackCaptureConfig(cfg).build();recorder?.startRecording();if(recorder?.recordingState!=AudioRecord.RECORDSTATE_RECORDING)throw IllegalStateException("AudioRecord did not start")
            lastBeat=0L
            beatPeriodMs=600L
            prevEnergy=0f
            energyHistory.clear()
            lightLevel=0f
            spectrumLightLevels=FloatArray(0)
            spectrumInput.fill(0f)
            spectrumWriteIndex=0
            spectrumSamplesWritten=0
            rainbowHue=0f
            chaseOffset=0f
            randomHues=FloatArray(0)
            bpmFromIndex=0
            bpmToIndex=0
            bpmTransitionStart=System.currentTimeMillis()
            bpmTransitionMode="fade"
            musicSequenceCid=UUID.randomUUID().toString().replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            lastMusicFrameAt=0L
            val activeRecorder = recorder ?: throw IllegalStateException("AudioRecord unavailable")
            val generation = ++captureGeneration
            running=true
            worker=Thread{loop(sr, activeRecorder, generation)}.also{it.start()}
         }catch(e:Exception){
             android.util.Log.e("MobileDMusic", "Music AudioRecord capture failed: ${e.javaClass.name}: ${e.message}", e)
             failCapture("Music audio capture unavailable")
         }
    }

    private fun releaseMusicServiceBinding() {
        boundMusicService?.captureStopListener = null
        boundMusicService = null
        if (serviceBindingRequested || serviceBound) {
            try { context?.unbindService(musicServiceConnection) } catch (_: Exception) {}
        }
        serviceBound = false
        serviceBindingRequested = false
    }

    private fun failCapture(message: String) {
        stopCapture(releaseProjection = true)
        context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
    }

    private fun stopCapture(releaseProjection:Boolean=true){
        running=false
        ++captureGeneration
        capturePending=false
        worker?.interrupt();worker=null
        val activeRecorder=recorder
        recorder=null
        try{activeRecorder?.stop()}catch(_:Exception){}
        try{activeRecorder?.release()}catch(_:Exception){}
        releaseMusicServiceBinding()
        stopMusicKeepAlive()
        closeMusicSacnSocket()
        if(releaseProjection){
            captureRequested = false
            val activeProjection = projection
            projection = null
            projectionData = null
            projectionResultCode = Activity.RESULT_CANCELED
            try { activeProjection?.stop() } catch (_: Exception) {}
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
        context?.let { it.stopService(Intent(it, MusicCaptureKeepAliveService::class.java)) }
    }

    fun deactivateMusic(){
        stopCapture(releaseProjection = true)
    }

    private fun onlineMembers():List<HardwareGroupItem> =
        (requireActivity() as ControllerActivity).getControllerMusicVirtualStripSnapshot()

    fun retargetMusicSelection() {
        if (!running) return
        if (!sendMusicSelection()) stopCapture(releaseProjection = true)
    }

    private fun musicPixelIds(items:List<HardwareGroupItem>):List<Int> =
        items.map { it.PixelID }.distinct().sorted()

    private fun validMusicGroup(items:List<HardwareGroupItem>):Boolean {
        if(items.isEmpty()) return false
        val uniqueIds=musicPixelIds(items)
        return uniqueIds.firstOrNull() == 0 &&
            uniqueIds.withIndex().all { it.value == it.index }
    }

    private fun sendMusicSelection(): Boolean {
        saveRuntimeState()
        val a=requireActivity() as ControllerActivity
        if(!a.canOpenDistributedEffects("Music")) return false
        val group=a.group ?: run {
            Toast.makeText(requireContext(),"Music requires a group.",Toast.LENGTH_SHORT).show(); return false
        }
        // Use the strip captured when the group was selected. Starting or
        // changing Music does not admit later arrivals or renumber offline IDs.
        val items=a.refreshControllerMusicSelection()
        if(items.isEmpty() || a.getCurrentControllerMusicMembers().isEmpty()){
            Toast.makeText(requireContext(),"No reachable MobileD in this group.",Toast.LENGTH_SHORT).show(); return false
        }
        if(!validMusicGroup(items)){
            Toast.makeText(requireContext(),"Music requires a consecutive Pixel-ID prefix starting at 0; later gaps are ignored.",Toast.LENGTH_LONG).show(); return false
        }

        val universe=group.GUniverse.takeIf { if (group.allDevices) it == 32000 else it in 32010..35000 }
            ?: run { Toast.makeText(requireContext(),"This group is not ready for Music. Open the group editor and save its configuration.",Toast.LENGTH_LONG).show(); return false }
        val gPort=if(group.allDevices) 8890 else group.groupItems.orEmpty()
            .firstOrNull()?.Gport?.toIntOrNull()?.takeIf { it in 10000..65535 }
            ?: run { Toast.makeText(requireContext(),"This group is not ready for Music. Open the group editor and save its configuration.",Toast.LENGTH_LONG).show(); return false }

        val total=musicPixelIds(items).distinct().size
        val client=UdpClient.getClient(requireContext())

        // PC App: one discrete Music selection command immediately, followed
        // by the normal 50 ms / 15 ms trailing reliability sequence.
        items.sortedBy{it.PixelID}.forEach{item->
            val d=item.hardwareDevice ?: return@forEach
            val ip=a.currentControllerDeviceIp(d) ?: return@forEach
            val discovery=JSONObject(d.deviceFrame.ifBlank { "{}" })
            if(!discovery.has("Command")) return@forEach
            val payload=JSONObject().apply{
                // Selecting Music explicitly enables the selected lights.
                put("Command", 1)
                put("GLights",100)
                put("GPort",gPort)
                put("GUniverse",universe)
                put("PixelID",item.PixelID)
                put("PixelCount",total)
            }.toString()
            client.writeCommandString(payload,ip)
            d.rememberSentCommand(payload)
        }
        return true
    }

    private fun loop(sr:Int, activeRecorder:AudioRecord, generation:Int) {
        val buf=ShortArray(1024)
        try {
            while(running && captureGeneration == generation){
                val n=activeRecorder.read(buf,0,buf.size)
                if (!running || captureGeneration != generation) break
                if (n < 0) throw IllegalStateException("AudioRecord read failed ($n)")
                if(n == 0) { Thread.sleep(5); continue }

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
                val fd=frequencyByteData(buf,n,sr)
                val low=(0 until 18).map { fd[it]/255f }.average().toFloat()
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
                    if(running && captureGeneration == generation && ::vuLeft.isInitialized){
                        vuLeft.level=left
                        vuRight.level=right
                        vuLeft.invalidate()
                        vuRight.invalidate()
                    }
                }
                if(now-lastMusicFrameAt>=33L){
                    lastMusicFrameAt=now
                    // Rendering/routing failures must not terminate AudioRecord.
                    // In particular, transient Activity/Fragment lifecycle changes
                    // while navigating to Home must not be treated as capture failure.
                    try {
                        renderFrame(fd,n,raw,left,right,now,sr)
                    } catch (frameError: Throwable) {
                        android.util.Log.e("MobileDMusic", "Music frame render/routing failed; audio capture remains active", frameError)
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MobileDMusic", "Music capture loop failed", e)
            handler.post { if (running && captureGeneration == generation) failCapture("Music audio capture stopped") }
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

    private fun writeMusicPixel(out:MutableList<IntArray>,index:Int,hue:Float,level:Float,whiteExtra:Float=0f,useGlobalWhite:Boolean=true){
        val rgb=Color.HSVToColor(floatArrayOf(((hue%360f)+360f)%360f,1f,1f))
        val b=brightness.coerceIn(0,100)/100f
        val whiteValue=if(useGlobalWhite) Math.max(whiteExtra,this.white/100f*255f) else whiteExtra.coerceIn(0f,255f)
        out[index]=intArrayOf(
            (Color.red(rgb)*level*b).roundToInt().coerceIn(0,255),
            (Color.green(rgb)*level*b).roundToInt().coerceIn(0,255),
            (Color.blue(rgb)*level*b).roundToInt().coerceIn(0,255),
            (whiteValue*level*b).roundToInt().coerceIn(0,255)
        )
    }

    private fun renderFrame(fd:FloatArray,n:Int,level:Float,leftLevel:Float,rightLevel:Float,now:Long,sampleRate:Int) {
        val a=requireActivity() as ControllerActivity
        val group=a.group ?: return
        // Render the frozen strip and route to its currently online devices.
        // Duplicate IDs share a strip position.
        val configuredItems=onlineMembers()
        val routeItems=a.getCurrentControllerMusicMembers()
        if(configuredItems.isEmpty() || routeItems.isEmpty())return
        val count=configuredItems.map { it.PixelID }.distinct().size
        if(spectrumLightLevels.size!=count)spectrumLightLevels=FloatArray(count)
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
                    val pixel=MusicOutput.bpmPixel(
                        palette[seq[bpmFromIndex%seq.size]], palette[seq[bpmToIndex%seq.size]],
                        bpmTransitionMode, now-bpmTransitionStart, beatPeriodMs,
                        level, smoothLightLevel(level,dt), brightness, seq.size
                    )
                    for(i in 0 until count)frame[i]=pixel.copyOf()
                }
            }
            "spectrum"->{
                val assign=spectrumSaved.indices.filter { spectrumSaved[it] }
                val colorCount=assign.size.coerceIn(0,10)
                if(colorCount>0){
                    for(i in 0 until count){
                        val pixelGroupCount=min(colorCount,count)
                        val groupIndex=min(pixelGroupCount-1,(i*pixelGroupCount)/max(1,count))
                        val bandIndex=if(colorCount<=3)spectrumBandMap[assign[groupIndex]].coerceIn(0,2) else groupIndex
                        val range=bandRange(bandIndex,if(colorCount<=3)3 else colorCount)
                        val e=audioBand(fd,range.first,range.second,sampleRate)
                        val raw=(e*12f).coerceIn(0f,1f)
                        val l=smoothSpectrumLevel(i,raw,dt)
                        val c=spectrumColors[assign[groupIndex]]
                        frame[i]=MusicOutput.scalePalette(MusicOutput.palette(c),l,brightness)
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

        // Reachability selects the network route. The complete fixed virtual
        // strip supplies every RGBW position for external fixtures on DMX OUT.
        val universes=MusicOutput.dmxUniverses(frame,group.GUniverse)
        val routeIp=routeItems.firstOrNull()?.hardwareDevice?.ip?.trim().orEmpty()
        if(routeIp.isBlank())return
        universes.forEach{(u,dmx)->
            // Each group owns exactly ten consecutive universes from its base.
            if (u in group.GUniverse..(group.GUniverse + 9)) {
                sendSacnUniverse(u,dmx,routeIp)
            }
        }
    }

    private fun frequencyByteData(samples:ShortArray,n:Int,sampleRate:Int):FloatArray {
        val size=spectrumInput.size
        var sourceIndex=0
        while(sourceIndex+1<n){
            spectrumInput[spectrumWriteIndex]=
                (samples[sourceIndex].toInt()+samples[sourceIndex+1].toInt())/(2f*32768f)
            spectrumWriteIndex=(spectrumWriteIndex+1)%size
            spectrumSamplesWritten=(spectrumSamplesWritten+1).coerceAtMost(size)
            sourceIndex+=2
        }

        val real=spectrumFftReal
        val imag=spectrumFftImag
        imag.fill(0.0)
        var windowSum=0.0
        for(i in 0 until size){
            val sampleIndex=(spectrumWriteIndex+i)%size
            val window=.5-.5*kotlin.math.cos(2.0*Math.PI*i/(size-1))
            real[i]=spectrumInput[sampleIndex]*window
            windowSum+=window
        }

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

        val scale=if(windowSum>0.0)2.0/windowSum else 0.0
        return FloatArray(size/2+1){k->
            (kotlin.math.hypot(real[k],imag[k])*scale).toFloat()
        }
    }

    private fun audioBand(fd:FloatArray,minHz:Double,maxHz:Double,sampleRate:Int):Float {
        val size=spectrumInput.size
        val low=max(1,kotlin.math.ceil(minHz*size/sampleRate).toInt())
        val high=min(fd.lastIndex,floor(maxHz*size/sampleRate).toInt())
        if(high<low)return 0f
        var energy=0.0
        for(bin in low..high){
            val magnitude=fd[bin].toDouble()
            energy+=magnitude*magnitude
        }
        return sqrt(energy).toFloat()
    }

    private fun bandRange(index:Int,count:Int):Pair<Double,Double> {
        val minHz=80.0
        val maxHz=12000.0
        if(count<=3){
            val ranges=arrayOf(80.0 to 250.0,250.0 to 2000.0,2000.0 to 12000.0)
            return ranges[index.coerceIn(0,2)]
        }
        val ratio=maxHz/minHz
        val i=index.coerceIn(0,count-1)
        return minHz*ratio.pow(i.toDouble()/count) to
            minHz*ratio.pow((i+1).toDouble()/count)
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

    private fun sendSacnUniverse(universe:Int,dmx:ByteArray,routeIp:String){
        // GPort identifies the group in command/configuration payloads. E1.31
        // frames always use the standard UDP port; universe selects multicast.
        // Standard E1.31: 126-byte header/start code plus all 512 DMX slots.
        if(universe !in 1..63999 || dmx.size < 512 || routeIp.isBlank()) return
        try{
            if(musicSacnSocket==null || musicSacnRouteIp!=routeIp){
                closeMusicSacnSocket()
                val socket=MulticastSocket()
                socket.timeToLive=1
                socket.reuseAddress=true
                musicSacnNetworkInterface=findInterfaceForRoute(routeIp)
                if(musicSacnNetworkInterface!=null) socket.networkInterface=musicSacnNetworkInterface
                musicSacnSocket=socket
                musicSacnRouteIp=routeIp
            }
            val socket=musicSacnSocket ?: return
            val seq=((musicSequences[universe] ?: 0)+1) and 255
            musicSequences[universe]=seq
            val p=MusicOutput.sacnPacket(dmx,universe,seq,musicSequenceCid)
            val addr=InetAddress.getByName("239.255.${(universe ushr 8) and 255}.${universe and 255}")
            socket.send(DatagramPacket(p,p.size,addr,SACN_UDP_PORT))
            val logKey="$routeIp:$universe"
            val now=System.currentTimeMillis()
            if(now-(musicSacnLastLogAt[logKey] ?: 0L)>=1000L){
                musicSacnLastLogAt[logKey]=now
                val activeChannels=dmx.count { (it.toInt() and 0xff)!=0 }
                val firstRgbw=(0..3).joinToString(",") { (dmx[it].toInt() and 0xff).toString() }
                android.util.Log.i("MobileDMusic", "sACN sent UDP=$SACN_UDP_PORT universe=$universe route=$routeIp bytes=${p.size} activeChannels=$activeChannels firstRGBW=$firstRgbw")
            }
        }catch(e:Exception){
            closeMusicSacnSocket()
            android.util.Log.e("MobileDMusic","sACN send failed for universe $universe",e)
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
            val l=LinearLayout(c).apply{id=R.id.music_root;orientation=LinearLayout.VERTICAL;setPadding(12,10,12,20)}
            scroll.addView(l)
            return MusicBinding(scroll)
        }
    }
}
