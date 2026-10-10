package com.mobiled.android.ui.controller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.mobiled.android.R
import com.mobiled.android.adapters.GeneralUtil
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.component.ColorPickerView
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.model.LightCommand
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MatrixFragment : BaseFragment<androidx.viewbinding.ViewBinding>() {
    override fun getFragmentBinding(inflater: LayoutInflater, container: ViewGroup?) =
        MatrixBinding.inflate(inflater, container, false)


    private data class Spec(
        val id: Int, val name: String, val colors: Int, val colorLabels: List<String> = emptyList(),
        val speed: Boolean = true, val brightness: Boolean = true,
        val random: Boolean = false, val custom: List<String> = emptyList(),
        val modes: List<Pair<String, List<String>>> = emptyList()
    )

    private val specs = listOf(
        Spec(101,"Color Change",10,colorLabels=List(10){"Color ${it+1}"},custom=listOf("Transition duration","Pause","Hold"),random=true,modes=listOf("Transition" to listOf("Fade","Direct change","Fade out / fade in"))),
        Spec(102,"Comet",3,colorLabels=listOf("Head","Tail","Background"),custom=listOf("Tail length","Tail smoothing","Tail intensity"),random=true,modes=listOf("Movement" to listOf("One way / restart","Back and forth"),"Direction" to listOf("Left → Right","Right → Left","Extremes → Center","Center → Extremes"))),
        Spec(103,"Rainbow Chase",0,custom=listOf("Progressive spatial adjustment","White %"),modes=listOf("Movement" to listOf("One way / restart","Back and forth"),"Direction" to listOf("Left → Right","Right → Left","Extremes → Center","Center → Extremes"))),
        Spec(104,"Fill-Unfill",2,colorLabels=listOf("Fill color","Background color"),custom=emptyList(),modes=listOf("On/Off 1" to listOf("Fill SX → DX","Fill from Center","Fill to Center","Fill Reverse DX → SX","Fill-Unfill SX → DX","Fill-Unfill from Center","Fill-Unfill to Center","Fill-Unfill Reverse DX → SX"),"On/Off 2" to listOf("One Way","Restart"))),
        Spec(105,"Theater",10,colorLabels=List(10){"Color ${it+1}"},custom=listOf("Block size"),modes=listOf("Sequence Mode" to listOf("Normal","Black Background","Halloween Eyes"),"Direction" to listOf("Left → Right","Right → Left"))),
        Spec(106,"Sparkling",2,colorLabels=listOf("Sparkle color","Base color"),custom=listOf("Density","Peak hold")),
        Spec(107,"Fireworks",3,colorLabels=listOf("Launch color","Explosion color","Stars color"),custom=listOf("Stars Mode","Explosion Width"),random=true,modes=listOf("Direction" to listOf("Left → Right","Right → Left"))),
        Spec(108,"Fire",1,colorLabels=listOf("Flame color"),custom=listOf("Intensity","Turbulence")),
        Spec(109,"Wave",2,colorLabels=listOf("Wave color","Background"),custom=listOf("Wave width","Smoothness / gradient","Number of waves"),modes=listOf("Direction" to listOf("Left → Right","Right → Left","Extremes → Center","Center → Extremes"))),
        Spec(110,"BPM",10,colorLabels=List(10){"Color ${it+1}"},speed=false,random=true),
        Spec(111,"Amplitude",10,colorLabels=List(10){"Color ${it+1}"},speed=false,custom=listOf("Mic sensitivity","Transition type","Transition duration"),random=true),
        Spec(112,"Spectrum",0,speed=false,custom=listOf("Mic sensitivity"),modes=listOf("Orientation" to listOf("Horizontal / Spectrum","Vertical / VU meter")))
    )

    private var effectId = 101
    private var effectSelected = false
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var speed: SeekBar
    private lateinit var brightness: SeekBar
    private lateinit var speedValue: TextView
    private lateinit var brightnessValue: TextView
    private lateinit var preview: PixelPreview
    private val colors = MutableList(10) { intArrayOf(255, 255, 255, 0) }
    private val handler = Handler(Looper.getMainLooper())
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private var previewPhase = 0f
    private val customValues = IntArray(3)
    private var onOff1 = 0
    private var onOff2 = 0
    private var randomEnabled = false
    private var selectedColorIndex = 0
    private var pickerColor = intArrayOf(255, 0, 0, 0)
    private var pickerBrightness = 100
    private var pickerWhite = 0
    private val colorSaved = BooleanArray(10)
    // Preserve each effect configuration while switching between Matrix effects.
    private val runtimeConfigs = mutableMapOf<Int, JSONObject>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        root = view.findViewById(9000)
        title = view.findViewById(9001)
        preview = view.findViewById(9007)
        speed = SeekBar(requireContext()).apply { max = 100; progress = 50 }
        brightness = SeekBar(requireContext()).apply { max = 255; progress = 255 }
        speedValue = TextView(requireContext()).apply { setTextColor(Color.WHITE); text = "Speed 50" }
        brightnessValue = TextView(requireContext()).apply { setTextColor(Color.WHITE); text = "Brightness 255" }
        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) { speedValue.text = "Speed $p"; if (fromUser) send() }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {}
        })
        brightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) { brightnessValue.text = "Brightness $p"; if (fromUser) send() }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {}
        })
        view.findViewById<Button>(9008)?.setOnClickListener { showEffectList() }
        buildEffectCards()
        showEffectList()
    }

    override fun handleBackPress(): Boolean {
        if (effectSelected) {
            showEffectList()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        preview.stopAnimation()
        super.onDestroyView()
    }

    private fun showEffectList() {
        effectSelected = false
        preview.stopAnimation()
        root.findViewById<Button>(9008)?.visibility = View.GONE
        root.findViewById<LinearLayout>(9004)?.visibility = View.VISIBLE
        root.findViewById<LinearLayout>(9010)?.visibility = View.GONE
        title.visibility = View.GONE
    }

    private fun selectEffect(id: Int) {
        // PC App parity: selecting a non-Music effect must terminate the Music
        // capture/session before the new effect is applied.
        (activity as? ControllerActivity)?.stopMusicForExternalControl()
        effectSelected = true
        effectId = id
        root.findViewById<Button>(9008)?.visibility = View.GONE
        root.findViewById<LinearLayout>(9004)?.visibility = View.GONE
        root.findViewById<LinearLayout>(9010)?.visibility = View.VISIBLE
        val spec = specs.first { it.id == id }
        val members = onlineMembers()
        val shouldSelect = members.any {
            JSONObject(it.hardwareDevice?.deviceFrame.orEmpty()).optInt("GLights", -1) != id
        }
        applyEffectDefaults(spec)
        loadSavedColors(spec)
        val hasRuntimeConfig = restoreRuntimeConfig(id)
        if (!shouldSelect && !hasRuntimeConfig) loadStateFromDiscovery(spec, members)
        rebuildControls(spec)
        preview.effect = id
        preview.speed = speed.progress
        preview.brightness = brightness.progress
        preview.custom = customValues.copyOf()
        preview.onOff1 = onOff1
        preview.onOff2 = onOff2
        val activeCount = (0 until spec.colors.coerceAtMost(10)).takeWhile { colorSaved[it] }.size
        preview.colors = colors.take(activeCount).map { it.copyOf() }
        preview.pixelCount = onlineMembers().size.coerceAtLeast(1)
        preview.startAnimation()
        if (shouldSelect) send()
    }

    private fun buildEffectCards() {
        val container = root.findViewById<LinearLayout>(9004)
        container.removeAllViews()
        specs.forEach { spec ->
            val item = LayoutInflater.from(requireContext()).inflate(R.layout.effect_item, container, false)
            item.findViewById<TextView>(R.id.tvEffect).text = spec.name
            val image = item.findViewById<ImageView>(R.id.ivEffect)
            val res = resources.getIdentifier("matrix_${spec.id}", "drawable", requireContext().packageName)
            if (res != 0) image.setImageResource(res)
            item.setOnClickListener { selectEffect(spec.id) }
            container.addView(item)
        }
    }

    private fun sectionBox(label: String): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(18))
        setBackgroundResource(R.drawable.dark_box_corner)
        addView(TextView(requireContext()).apply {
            text = label; textSize = 18f; setTextColor(Color.WHITE); setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }

    private fun rebuildControls(spec: Spec) {
        val panel = root.findViewById<LinearLayout>(9010)
        panel.removeAllViews()
        panel.setPadding(0, 0, 0, 28)

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 12)
            addView(TextView(requireContext()).apply {
                text = spec.name
                textSize = 24f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(requireContext()).apply {
                text = "${onlineMembers().size} Matrix member${if (onlineMembers().size == 1) "" else "s"}"
                textSize = 12f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(4), 0, 0)
            })
        }
        panel.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 4 })

        addPresetSection(panel, spec)

        val previewBox = sectionBox("Preview")
        (preview.parent as? ViewGroup)?.removeView(preview)
        preview.visibility = View.VISIBLE
        previewBox.addView(preview, LinearLayout.LayoutParams(-1, dp(92)))
        panel.addView(previewBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })

        if (spec.colors > 0) {
            val colorBox = sectionBox("Colors")
            if (spec.random) {
                // Keep Random aligned on the same header row, immediately to the right of Colors.
                val title = colorBox.getChildAt(0) as TextView
                colorBox.removeViewAt(0)
                val titleRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(title, LinearLayout.LayoutParams(-2, -2))
                    // Keep Random at the far right edge of the Colors header row.
                    addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
                    addView(CheckBox(requireContext()).apply {
                        text = "Random"
                        setTextColor(Color.WHITE)
                        isChecked = randomEnabled
                        setPadding(0, 0, 0, 0)
                        setOnCheckedChangeListener { _, checked -> randomEnabled = checked; send(forceParameterUpdate = true) }
                    }, LinearLayout.LayoutParams(-2, dp(44)))
                }
                colorBox.addView(titleRow, 0, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
            }
            colorBox.addView(TextView(requireContext()).apply {
                text = "Tap a color to edit • hold 3 seconds to save the current picker color"
                setTextColor(Color.LTGRAY); textSize = 12f
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
            val grid = GridLayout(requireContext()).apply { columnCount = 5; useDefaultMargins = false }
            repeat(spec.colors) { i ->
                val cell = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                val swatch = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    setBackgroundColor(if (colorSaved[i]) colors[i].let { rgbwPreviewColor(it) } else Color.rgb(70,70,70))
                    addView(TextView(requireContext()).apply {
                        text = "${i + 1}"
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        textSize = 17f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }, LinearLayout.LayoutParams(-1, 0, 1f))
                    addView(TextView(requireContext()).apply {
                        text = spec.colorLabels.getOrNull(i) ?: "Color ${i + 1}"
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        textSize = 10f
                        maxLines = 2
                    }, LinearLayout.LayoutParams(-1, -2))
                }
                val reset = TextView(requireContext()).apply {
                    text = "RESET"
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.rgb(70, 70, 70))
                    setOnClickListener { resetColor(i, spec) }
                }
                bindThreeSecondSave(swatch,
                    onSave = { saveColor(i); Toast.makeText(requireContext(), "Color ${i + 1} saved", Toast.LENGTH_SHORT).show() },
                    onClick = { selectColorSlot(i, spec) }
                )
                cell.addView(swatch, LinearLayout.LayoutParams(-1, dp(92)))
                cell.addView(reset, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(8) })
                grid.addView(cell, GridLayout.LayoutParams().apply {
                    width = 0; height = -2; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(6), dp(6), dp(6), dp(10))
                })
            }
            colorBox.addView(grid)
            colorBox.addView(LinearLayout(requireContext()).apply { id = 9013; orientation = LinearLayout.VERTICAL }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
            panel.addView(colorBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
            renderInlineColorEditor(colorBox, spec.colors)
        }

        if (spec.speed || spec.brightness || spec.random) {
            val main = sectionBox("Main controls")
            if (spec.speed) addStepSlider(main, "Speed", speed.progress, 0, 100, "") { speed.progress = it; speedValue.text = "Speed $it"; send() }
            if (spec.brightness) addStepSlider(main, "Brightness", brightness.progress, 0, 255, "") { brightness.progress = it; brightnessValue.text = "Brightness $it"; send() }
            panel.addView(main, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }

        if (spec.custom.isNotEmpty()) {
            val box = sectionBox("Parameters")
            spec.custom.forEachIndexed { index, label ->
                if (label == "Transition type" || label == "Stars Mode") {
                    val opts = if (label == "Stars Mode") listOf("Normal Stars", "Normal + Sparkling Stars") else listOf("Fade", "Direct change", "Fade out / fade in")
                    box.addView(spinnerRow(label, opts, customValues[index]) { customValues[index] = it; send() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
                } else {
                    addStepSlider(box, label, customValues[index], 0, 100, "") { customValues[index] = it; send() }
                }
            }
            panel.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }

        if (spec.modes.isNotEmpty()) {
            val box = sectionBox("Mode / Direction")
            spec.modes.forEachIndexed { idx, pair ->
                box.addView(spinnerRow(pair.first, pair.second, if (idx == 0) onOff1 else onOff2) {
                    if (idx == 0) onOff1 = it else onOff2 = it
                    send()
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
            }
            panel.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }

        panel.addView(TextView(requireContext()).apply {
            id = 9012
            text = "Pixel IDs: ${onlineMembers().map { pixelId(it) }.sorted().joinToString()} / Count ${aPixelCount()}"
            setTextColor(Color.LTGRAY); textSize = 11f; setPadding(4, 10, 4, 10)
        })

        root.post { (root.parent as? ScrollView)?.smoothScrollTo(0, 0) }
    }

    private fun aPixelCount(): Int = onlineMembers().size.coerceAtLeast(1)

    private fun addStepSlider(container: LinearLayout, label: String, value: Int, minValue: Int, maxValue: Int, suffix: String, onChange: (Int) -> Unit) {
        val labelRow=LinearLayout(requireContext()).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        labelRow.addView(TextView(requireContext()).apply{text=label;textSize=15f;setTextColor(Color.WHITE);setTypeface(typeface,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,dp(32),1f))
        val out=TextView(requireContext()).apply{text="$value$suffix";textSize=14f;setTextColor(Color.WHITE);gravity=Gravity.CENTER_VERTICAL or Gravity.END}
        labelRow.addView(out,LinearLayout.LayoutParams(dp(64),dp(32)))
        container.addView(labelRow,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8);bottomMargin=dp(5)})
        val bar=LayoutInflater.from(requireContext()).inflate(R.layout.slider_button,container,false)
        val slider=bar.findViewById<com.google.android.material.slider.Slider>(R.id.viewSlider)
        val minus=bar.findViewById<ImageView>(R.id.ivMinus)
        val plus=bar.findViewById<ImageView>(R.id.ivAdd)
        slider.valueFrom=minValue.toFloat();slider.valueTo=maxValue.toFloat();slider.stepSize=1f;slider.value=value.coerceIn(minValue,maxValue).toFloat()
        val applyValue={v:Int->val n=v.coerceIn(minValue,maxValue);slider.value=n.toFloat();out.text="$n$suffix";onChange(n)}
        minus.setOnClickListener{applyValue(slider.value.toInt()-1)}
        plus.setOnClickListener{applyValue(slider.value.toInt()+1)}
        slider.addOnChangeListener{_,v,fromUser->out.text="${v.toInt()}$suffix";if(fromUser)onChange(v.toInt())}
        container.addView(bar,LinearLayout.LayoutParams(-1,dp(35)).apply{bottomMargin=dp(12)})
    }

    private fun spinnerRow(label: String, options: List<String>, selected: Int, onChange: (Int) -> Unit): LinearLayout = LinearLayout(requireContext()).apply {
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

    private fun bindThreeSecondSave(view: View, onSave: () -> Unit, onClick: () -> Unit) {
        var task: Runnable? = null; var longDone = false; var downAt = 0L
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { longDone = false; downAt = System.currentTimeMillis(); task?.let(handler::removeCallbacks); task = Runnable { longDone = true; onSave() }; handler.postDelayed(task!!, 3000); true }
                MotionEvent.ACTION_UP -> { task?.let(handler::removeCallbacks); task = null; if (!longDone && System.currentTimeMillis() - downAt < 3000) { onClick(); v.performClick() }; true }
                MotionEvent.ACTION_CANCEL -> { task?.let(handler::removeCallbacks); task = null; true }
                else -> true
            }
        }
    }

    private fun addLabeledSeek(panel: LinearLayout, label: String, minValue: Int, maxValue: Int, value: Int) {
        panel.addView(TextView(requireContext()).apply { text = label; setTextColor(Color.WHITE) })
        val s = SeekBar(requireContext()).apply {
            max = maxValue - minValue
            progress = value - minValue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) send() }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        panel.addView(s, LinearLayout.LayoutParams(-1, 48))
    }

    private fun addPresetSection(panel: LinearLayout, spec: Spec) {
        val box = sectionBox("Presets")
        box.addView(TextView(requireContext()).apply {
            text = "Click = load • hold 3 seconds = save"
            setTextColor(Color.LTGRAY); textSize = 12f
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        val grid = GridLayout(requireContext()).apply { columnCount = 5; useDefaultMargins = false }
        val prefs = requireContext().getSharedPreferences("matrix_presets", Context.MODE_PRIVATE)
        repeat(5) { i ->
            val cell = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            val slot = TextView(requireContext()).apply {
                text = "${i + 1}"; gravity = Gravity.CENTER; textSize = 16f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setBackgroundColor(if (prefs.contains("${spec.id}.$i")) Color.rgb(0,155,65) else Color.rgb(55,55,55))
            }
            bindThreeSecondSave(slot,
                onSave = { savePreset(spec.id, i); slot.setBackgroundColor(Color.rgb(0,155,65)); Toast.makeText(requireContext(), "Preset ${i + 1} saved", Toast.LENGTH_SHORT).show() },
                onClick = { loadPreset(spec.id, i) }
            )
            val reset = TextView(requireContext()).apply {
                text = "RESET"; gravity = Gravity.CENTER; textSize = 8f; setTextColor(Color.WHITE); setBackgroundResource(R.drawable.grey_box)
                setOnClickListener { prefs.edit().remove("${spec.id}.$i").apply(); slot.setBackgroundColor(Color.rgb(55,55,55)) }
            }
            cell.addView(slot, LinearLayout.LayoutParams(-1, dp(82)))
            cell.addView(reset, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(12) })
            grid.addView(cell, GridLayout.LayoutParams().apply { width=0; height=-2; columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(6),dp(6),dp(6),dp(12)) })
        }
        box.addView(grid)
        panel.addView(box, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=12 })
    }

    private fun loadStateFromDiscovery(spec: Spec, members: List<HardwareGroupItem>) {
        val frame = members.asSequence()
            .mapNotNull { it.hardwareDevice?.deviceFrame?.takeIf { raw -> raw.isNotBlank() } }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .firstOrNull { it.optInt("GLights", -1) == effectId }
            ?: return

        speed.progress = frame.optInt("Speed", speed.progress).coerceIn(0, 100)
        brightness.progress = frame.optInt("Brightness", brightness.progress).coerceIn(0, 255)
        customValues[0] = frame.optInt("Custom1", customValues[0]).coerceIn(0, 100)
        customValues[1] = frame.optInt("Custom2", customValues[1]).coerceIn(0, 100)
        customValues[2] = frame.optInt("Custom3", customValues[2]).coerceIn(0, 100)
        onOff1 = frame.optInt("OnOff1", onOff1)
        onOff2 = frame.optInt("OnOff2", onOff2)
        randomEnabled = frame.optInt("Random", if (randomEnabled) 1 else 0) != 0

        val array = frame.optJSONArray("Colors")
        if (array != null) {
            for (i in 0 until min(10, array.length())) {
                val value = array.optJSONArray(i) ?: continue
                colors[i][0] = value.optInt(0, colors[i][0]).coerceIn(0, 255)
                colors[i][1] = value.optInt(1, colors[i][1]).coerceIn(0, 255)
                colors[i][2] = value.optInt(2, colors[i][2]).coerceIn(0, 255)
                colors[i][3] = value.optInt(3, colors[i][3]).coerceIn(0, 255)
                colorSaved[i] = true
            }
        }
    }

    private fun applyEffectDefaults(spec: Spec) {
        speed.progress = when (spec.id) { 103 -> 70; 105 -> 0; 106 -> 100; else -> 50 }
        brightness.progress = 255
        customValues.fill(0); onOff1 = 0; onOff2 = 0; randomEnabled = false
        when (spec.id) {
            101 -> { customValues[0]=10; customValues[1]=10; customValues[2]=10 }
            104 -> { onOff2 = 1 }
            102 -> { customValues[0]=36; customValues[1]=47; customValues[2]=25 }
            103 -> { customValues[0]=60; customValues[2]=0 }
            105 -> { customValues[0]=0 }
            106 -> { customValues[0]=15 }
            108 -> { customValues[0]=45; customValues[1]=100 }
        }
        if (spec.id == 101 || spec.id == 102 || spec.id == 107 || spec.id == 110 || spec.id == 111) randomEnabled = false
    }

    private fun rgbwPreviewColor(c: IntArray): Int {
        val r = c.getOrElse(0) { 0 }.coerceIn(0, 255)
        val g = c.getOrElse(1) { 0 }.coerceIn(0, 255)
        val b = c.getOrElse(2) { 0 }.coerceIn(0, 255)
        val w = c.getOrElse(3) { 0 }.coerceIn(0, 255)
        if (w == 0) return Color.rgb(r, g, b)
        if (r == 0 && g == 0 && b == 0) return Color.rgb(w, w, w)
        // Match PC App rgbwDisplayRgb(): W contributes up to 80% of the
        // remaining distance to white, while preserving the RGB hue.
        val mix = (w / 255f) * 0.8f
        return Color.rgb(
            (r + (255 - r) * mix).roundToInt().coerceIn(0, 255),
            (g + (255 - g) * mix).roundToInt().coerceIn(0, 255),
            (b + (255 - b) * mix).roundToInt().coerceIn(0, 255)
        )
    }

    private fun loadSavedColors(spec: Spec) {
        val p = requireContext().getSharedPreferences("matrix_colors_${spec.id}", Context.MODE_PRIVATE)
        val defaults = when (spec.id) {
            101,110,111 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,255,0,0),intArrayOf(0,0,255,0),intArrayOf(255,0,255,0),intArrayOf(255,255,0,0),intArrayOf(0,255,255,0))
            102 -> listOf(intArrayOf(255,0,0,0),intArrayOf(255,0,0,0),intArrayOf(0,0,0,0))
            104 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,0,0,0))
            105 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,255,0,0))
            106 -> listOf(intArrayOf(0,0,0,255),intArrayOf(0,0,0,0))
            107 -> listOf(intArrayOf(0,0,0,255),intArrayOf(255,0,0,0),intArrayOf(255,255,0,0))
            108 -> listOf(intArrayOf(255,0,0,0))
            109 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,0,0,0))
            else -> emptyList()
        }
        colorSaved.fill(false)
        for (i in 0 until spec.colors.coerceAtMost(10)) {
            val d = defaults.getOrNull(i) ?: intArrayOf(0,0,0,0)
            colors[i][0]=p.getInt("$i.r",d[0]); colors[i][1]=p.getInt("$i.g",d[1]); colors[i][2]=p.getInt("$i.b",d[2]); colors[i][3]=p.getInt("$i.w",d[3])
            // Match PC App matrixEffectDefaults(): every default palette
            // entry defined by the effect is active. There is no generic
            // "first three" rule.
            colorSaved[i] = p.getBoolean("$i.saved", defaults.getOrNull(i) != null)
        }
    }

    private fun defaultColor(spec: Spec, index: Int): IntArray = when (spec.id) {
        101,110,111 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,255,0,0),intArrayOf(0,0,255,0),intArrayOf(255,0,255,0),intArrayOf(255,255,0,0),intArrayOf(0,255,255,0)).getOrNull(index)
        102 -> listOf(intArrayOf(255,0,0,0),intArrayOf(255,0,0,0),intArrayOf(0,0,0,0)).getOrNull(index)
        104 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,0,0,0)).getOrNull(index)
        105 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,255,0,0)).getOrNull(index)
        106 -> listOf(intArrayOf(0,0,0,255),intArrayOf(0,0,0,0)).getOrNull(index)
        107 -> listOf(intArrayOf(0,0,0,255),intArrayOf(255,0,0,0),intArrayOf(255,255,0,0)).getOrNull(index)
        108 -> listOf(intArrayOf(255,0,0,0)).getOrNull(index)
        109 -> listOf(intArrayOf(255,0,0,0),intArrayOf(0,0,0,0)).getOrNull(index)
        else -> null
    } ?: intArrayOf(255,255,255,0)

    private fun resetColor(index: Int, spec: Spec) {
        colors[index][0] = 0
        colors[index][1] = 0
        colors[index][2] = 0
        colors[index][3] = 0
        colorSaved[index] = false
        requireContext().getSharedPreferences("matrix_colors_${spec.id}", Context.MODE_PRIVATE).edit()
            .remove("$index.r").remove("$index.g").remove("$index.b").remove("$index.w").remove("$index.saved").apply()
        saveRuntimeConfig()
        rebuildControls(spec)
        syncPreview()
        send()
    }

    private fun saveColor(index: Int) {
        val c = pickerColor.copyOf()
        colors[index][0] = c[0]
        colors[index][1] = c[1]
        colors[index][2] = c[2]
        colors[index][3] = c[3]
        colorSaved[index] = true
        requireContext().getSharedPreferences("matrix_colors_$effectId", Context.MODE_PRIVATE).edit()
            .putInt("$index.r", c[0]).putInt("$index.g", c[1]).putInt("$index.b", c[2]).putInt("$index.w", c[3]).putBoolean("$index.saved", true).apply()
        val spec = specs.first { it.id == effectId }
        saveRuntimeConfig()
        rebuildControls(spec)
        syncPreview()
        send()
    }

    private fun selectColorSlot(index: Int, spec: Spec) {
        selectedColorIndex = index
        val c = colors[index].copyOf()
        pickerColor = c
        pickerWhite = (c[3] * 100f / 255f).roundToInt().coerceIn(0, 100)
        pickerBrightness = pickerBrightness.coerceIn(0, 100)
        rebuildControls(spec)
        syncPreview()
    }

    private fun renderInlineColorEditor(panel: LinearLayout, count: Int) {
        val host = panel.findViewById<LinearLayout>(9013) ?: return
        host.removeAllViews()
        host.addView(TextView(requireContext()).apply {
            text = "Current Color"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(8))
        })
        val pickerFrame = FrameLayout(requireContext())
        val picker = ColorPickerView(requireContext())
        pickerFrame.addView(picker, FrameLayout.LayoutParams(-1, dp(360)))

        val hue = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun hueButton(symbol: String, click: () -> Unit) = TextView(requireContext()).apply {
            text = symbol
            gravity = Gravity.CENTER
            textSize = 14f
            maxLines = 1
            setPadding(dp(4), 0, dp(4), 0)
            setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.white_box)
            setOnClickListener { click() }
        }
        // Match the Color screen: compact buttons anchored to opposite sides,
        // with the free space kept between them (not two half-width buttons).
        hue.addView(hueButton("− REV") { picker.decreaseHue() }, LinearLayout.LayoutParams(-2, dp(24)))
        hue.addView(View(requireContext()), LinearLayout.LayoutParams(0, 1, 1f))
        hue.addView(hueButton("+ FWD") { picker.increaseHue() }, LinearLayout.LayoutParams(-2, dp(24)))
        pickerFrame.addView(hue, FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM).apply { leftMargin = dp(4); rightMargin = dp(4); bottomMargin = dp(2) })
        host.addView(pickerFrame, LinearLayout.LayoutParams(-1, dp(360)))

        val readout = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        host.addView(readout, LinearLayout.LayoutParams(-1, -2))
        fun updateFromPicker(centerColor: Int) {
            // Store the RGB component at the selected Color Brightness, and
            // store White as its own protocol channel. Do not store the
            // composited center-preview RGB as the RGB channels: that would
            // bake W into RGB and make the saved RGBW preview incorrect.
            val hsv = picker.getHsv().copyOf()
            // ColorPickerView.getHsv() already contains its configured V
            // (Color Brightness); applying the slider again would square it.
            val rgb = Color.HSVToColor(hsv)
            pickerColor[0] = Color.red(rgb).coerceIn(0, 255)
            pickerColor[1] = Color.green(rgb).coerceIn(0, 255)
            pickerColor[2] = Color.blue(rgb).coerceIn(0, 255)
            pickerColor[3] = (pickerWhite * 255f / 100f).roundToInt().coerceIn(0, 255)
            readout.text = "RGB ${pickerColor[0]} / ${pickerColor[1]} / ${pickerColor[2]}    W ${pickerColor[3]}"
        }
        picker.setColorChangedListener(object : ColorPickerView.OnColorChangedListener {
            override fun colorChanged(centerColor: Int, argb: IntArray, hsv: FloatArray) {
                updateFromPicker(centerColor)
            }
        })
        picker.setColor(255, pickerColor[0], pickerColor[1], pickerColor[2])
        picker.setBrightness(pickerBrightness)
        picker.setColorAlpha(GeneralUtil.generateAlphaByWhiteBrightness(pickerWhite.toFloat(), pickerBrightness.toFloat()))
        updateFromPicker(Color.HSVToColor(picker.getHsv()))
        addStepSlider(host, "Color Brightness", pickerBrightness, 0, 100, "%") { value ->
            pickerBrightness = value
            picker.setBrightness(value)
            picker.setColorAlpha(GeneralUtil.generateAlphaByWhiteBrightness(pickerWhite.toFloat(), pickerBrightness.toFloat()))
            updateFromPicker(Color.HSVToColor(picker.getHsv()))
        }
        addStepSlider(host, "White Brightness", pickerWhite, 0, 100, "%") { value ->
            pickerWhite = value
            pickerColor[3] = (value * 255f / 100f).roundToInt().coerceIn(0, 255)
            picker.setColorAlpha(GeneralUtil.generateAlphaByWhiteBrightness(pickerWhite.toFloat(), pickerBrightness.toFloat()))
            updateFromPicker(Color.HSVToColor(picker.getHsv()))
        }

    }

    private fun savePreset(id: Int, index: Int) {
        val p = requireContext().getSharedPreferences("matrix_presets", Context.MODE_PRIVATE)
        p.edit().putString("$id.$index", snapshot().toString()).apply()
    }

    private fun loadPreset(id: Int, index: Int) {
        val s=requireContext().getSharedPreferences("matrix_presets",Context.MODE_PRIVATE).getString("$id.$index",null) ?: return
        val o=JSONObject(s)
        speed.progress=o.optInt("speed",speed.progress); brightness.progress=o.optInt("brightness",brightness.progress)
        customValues[0]=o.optInt("custom1",customValues[0]);customValues[1]=o.optInt("custom2",customValues[1]);customValues[2]=o.optInt("custom3",customValues[2])
        onOff1=o.optInt("onoff1",onOff1);onOff2=o.optInt("onoff2",onOff2);randomEnabled=o.optBoolean("random",randomEnabled)
        o.optJSONArray("colors")?.let{a->for(i in 0 until min(10,a.length())){val c=a.getJSONObject(i);colors[i][0]=c.optInt("r",colors[i][0]);colors[i][1]=c.optInt("g",colors[i][1]);colors[i][2]=c.optInt("b",colors[i][2]);colors[i][3]=c.optInt("w",colors[i][3]);colorSaved[i]=c.optBoolean("saved",colorSaved[i])}}
        val spec=specs.first{it.id==id};saveRuntimeConfig();rebuildControls(spec);send();preview.invalidate()
    }

    private fun snapshot(): JSONObject {
        val o=JSONObject().put("speed",speed.progress).put("brightness",brightness.progress).put("custom1",customValues[0]).put("custom2",customValues[1]).put("custom3",customValues[2]).put("onoff1",onOff1).put("onoff2",onOff2).put("random",randomEnabled)
        val a=JSONArray();colors.forEachIndexed{index,c->a.put(JSONObject().put("r",c[0]).put("g",c[1]).put("b",c[2]).put("w",c[3]).put("saved",colorSaved[index]))};o.put("colors",a);return o
    }

    private fun saveRuntimeConfig() {
        if (!::speed.isInitialized || !::brightness.isInitialized) return
        runtimeConfigs[effectId] = snapshot()
    }

    private fun restoreRuntimeConfig(id: Int): Boolean {
        val o = runtimeConfigs[id] ?: return false
        speed.progress = o.optInt("speed", speed.progress).coerceIn(0, 100)
        brightness.progress = o.optInt("brightness", brightness.progress).coerceIn(0, 255)
        customValues[0] = o.optInt("custom1", customValues[0]).coerceIn(0, 100)
        customValues[1] = o.optInt("custom2", customValues[1]).coerceIn(0, 100)
        customValues[2] = o.optInt("custom3", customValues[2]).coerceIn(0, 100)
        onOff1 = o.optInt("onoff1", onOff1)
        onOff2 = o.optInt("onoff2", onOff2)
        randomEnabled = o.optBoolean("random", randomEnabled)
        o.optJSONArray("colors")?.let { a ->
            for (i in 0 until min(10, a.length())) {
                val c = a.optJSONObject(i) ?: continue
                colors[i][0] = c.optInt("r", colors[i][0]).coerceIn(0, 255)
                colors[i][1] = c.optInt("g", colors[i][1]).coerceIn(0, 255)
                colors[i][2] = c.optInt("b", colors[i][2]).coerceIn(0, 255)
                colors[i][3] = c.optInt("w", colors[i][3]).coerceIn(0, 255)
                colorSaved[i] = c.optBoolean("saved", colorSaved[i])
            }
        }
        return true
    }

    private fun onlineMembers(): List<HardwareGroupItem> {
        val a = requireActivity() as ControllerActivity
        return a.getControllerVirtualStripSnapshot().sortedBy { it.PixelID }
    }

    private fun pixelId(item: HardwareGroupItem): Int = item.PixelID.coerceIn(0, 255)

    private fun matrixBaseUniverse(items: List<HardwareGroupItem>): Int? {
        val group = (requireActivity() as ControllerActivity).group ?: return null
        return group.GUniverse.takeIf { it in 32000..32500 }
    }

    private fun send(forceParameterUpdate: Boolean = false) {
        if (::speed.isInitialized && ::brightness.isInitialized) saveRuntimeConfig()
        val a = requireActivity() as ControllerActivity
        if (!a.canOpenDistributedEffects("Matrix")) return
        val group = a.group ?: run {
            Toast.makeText(requireContext(), "Matrix is available for groups only.", Toast.LENGTH_LONG).show()
            return
        }
        val items = onlineMembers()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "No reachable MobileD in the Matrix strip.", Toast.LENGTH_LONG).show()
            return
        }

        val allGroupItems = group.groupItems.orEmpty()
        val ids = allGroupItems.map { pixelId(it) }.sorted()
        val masters = allGroupItems.filter { it.GState.equals("M", ignoreCase = true) }
        if (masters.size != 1) {
            Toast.makeText(requireContext(), "Matrix requires exactly one Master.", Toast.LENGTH_LONG).show()
            return
        }
        if (pixelId(masters.first()) != 0) {
            Toast.makeText(requireContext(), "Matrix Master must have Pixel ID 0.", Toast.LENGTH_LONG).show()
            return
        }
        val uniqueIds = ids.distinct()
        if (uniqueIds.isEmpty() || uniqueIds.first() != 0 ||
            uniqueIds.withIndex().any { it.value != it.index }) {
            Toast.makeText(requireContext(), "Matrix Pixel IDs must be consecutive starting from 0.", Toast.LENGTH_LONG).show()
            return
        }

        val universe = matrixBaseUniverse(items)
        if (universe == null) {
            Toast.makeText(requireContext(), "Invalid Matrix GUniverse (32000-32500).", Toast.LENGTH_LONG).show()
            return
        }

        val spec = specs.first { it.id == effectId }
        val colorCount = if (spec.colors > 0)
            (0 until spec.colors.coerceAtMost(10)).takeWhile { colorSaved[it] }.size
        else 1
        val pixelCount = items.size

        // Effect selection is a group configuration change and reaches every
        // operational member. Parameter changes after the group is already on
        // this Matrix effect go only to the Master.
        val effectChanged = items.any { item ->
            val d = item.hardwareDevice ?: return@any true
            JSONObject(d.deviceFrame.ifBlank { "{}" }).optInt("GLights", -1) != effectId
        }
        val masterOnly = effectSelected && !effectChanged

        items.forEach { item ->
            val d = item.hardwareDevice ?: return@forEach
            val discovery = JSONObject(d.deviceFrame)
            val role = if (item.GState.equals("M", ignoreCase = true)) "M" else "S"
            val payload = JSONObject().apply {
                put("Command", discovery.optInt("Command", 0))
                put("GLights", effectId)
                put("Speed", speed.progress.coerceIn(0, 100))
                put("Brightness", brightness.progress.coerceIn(0, 255))
                put("GState", role)
                put("GPort", if (group.allDevices) "8890" else item.Gport)
                put("GUniverse", universe)
                put("PixelID", pixelId(item))
                put("PixelCount", pixelCount)
                put("ColorCount", colorCount)
                put("Random", if (randomEnabled) 1 else 0)
                put("Custom1", customValues[0])
                put("Custom2", if (effectId == 103) 100 else customValues[1])
                put("Custom3", customValues[2])
                put("OnOff1", onOff1)
                put("OnOff2", onOff2)
                val colorsArray = JSONArray()
                if (spec.colors > 0) {
                    repeat(colorCount) { index ->
                        val c = colors[index]
                        colorsArray.put(JSONArray().put(c[0]).put(c[1]).put(c[2]).put(c[3]))
                    }
                }
                put("Colors", colorsArray)
            }.toString()

            if (masterOnly && !item.GState.equals("M", ignoreCase = true)) return@forEach
            // A direct Random toggle must be transmitted even if the cached command frame matches.
            if (!forceParameterUpdate && d.activeCommandFrame == payload) return@forEach

            UdpClient.getClient(requireContext()).writeCommandString(
                payload,
                d.ip ?: return@forEach
            )
            d.rememberSentCommand(payload)
        }

        root.findViewById<TextView>(9012)?.text =
            "Pixel IDs: ${items.map { pixelId(it) }.joinToString()} / Count $pixelCount"
        syncPreview()
    }

    private fun syncPreview() {
        if (!::preview.isInitialized) return
        val spec = specs.first { it.id == effectId }
        preview.effect = effectId
        preview.speed = speed.progress
        preview.brightness = brightness.progress
        preview.custom = customValues.copyOf()
        preview.onOff1 = onOff1
        preview.onOff2 = onOff2
        val activeCount = (0 until spec.colors.coerceAtMost(10)).takeWhile { colorSaved[it] }.size
        preview.colors = colors.take(activeCount).map { it.copyOf() }
        preview.pixelCount = onlineMembers().size.coerceAtLeast(1)
        preview.startAnimation()
        preview.invalidate()
    }

    private fun applyColors(c: LightCommand) {
        val maxCount = specs.first { it.id == effectId }.colors
        val count = (0 until maxCount.coerceAtMost(10)).takeWhile { colorSaved[it] }.size
        c.ColorCount = count
        val a = JSONArray()
        for (i in 0 until count) a.put(JSONObject().put("r",colors[i][0]).put("g",colors[i][1]).put("b",colors[i][2]).put("w",colors[i][3]))
        c.Colors = a
    }

    private fun simpleListener(action: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action()
    }

    companion object { fun newInstance() = MatrixFragment() }
}

class PixelPreview(context: Context) : View(context) {
    var phase=0f
    var effect=101
    var speed=50
    var brightness=255
    var custom=IntArray(3)
    var onOff1=0
    var onOff2=0
    var pixelCount=1
    var colors:List<IntArray> = emptyList()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val frameHandler=Handler(Looper.getMainLooper())
    private var animating=false
    private var animationStartMs=System.currentTimeMillis()
    private fun sine(v:Float)=kotlin.math.sin(v.toDouble()).toFloat()
    fun startAnimation(){ if(animating)return; animating=true; animationStartMs=System.currentTimeMillis(); frameHandler.post(frameRunnable) }
    fun stopAnimation(){ animating=false; frameHandler.removeCallbacks(frameRunnable) }
    private val frameRunnable=object:Runnable{
        override fun run(){
            if(!animating)return
            phase += 0.035f + speed.coerceIn(0,100)*0.0012f
            invalidate()
            frameHandler.postDelayed(this,33L)
        }
    }
    override fun onDetachedFromWindow(){stopAnimation();super.onDetachedFromWindow()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val n=pixelCount.coerceIn(1,128)
        val gap=4f
        val w=(width-gap*(n-1))/n.toFloat().coerceAtLeast(1f)
        val t=phase
        fun rgb(c:IntArray,f:Float=1f):Int {
            val r=(c.getOrElse(0){0}*f).toInt().coerceIn(0,255)
            val g=(c.getOrElse(1){0}*f).toInt().coerceIn(0,255)
            val b=(c.getOrElse(2){0}*f).toInt().coerceIn(0,255)
            val white=(c.getOrElse(3){0}*f).toInt().coerceIn(0,255)
            if(white==0)return Color.rgb(r,g,b)
            if(r==0&&g==0&&b==0)return Color.rgb(white,white,white)
            val mix=(white/255f)*0.8f
            return Color.rgb((r+(255-r)*mix).toInt().coerceIn(0,255),(g+(255-g)*mix).toInt().coerceIn(0,255),(b+(255-b)*mix).toInt().coerceIn(0,255))
        }
        fun hsv(h:Float,v:Float=1f)=Color.HSVToColor(floatArrayOf(((h%360f)+360f)%360f,1f,v.coerceIn(0f,1f)))
        val out=IntArray(n){Color.BLACK}
        val count=colors.size.coerceAtLeast(1)
        val dir=if(onOff2==1) -1 else 1
        when(effect){
            101->{val idx=((t*(1f+speed/18f)).toInt().mod(count));for(i in 0 until n)out[i]=rgb(colors.getOrNull(idx)?:intArrayOf(255,0,0))}
            102->{val headRaw=(t*(1.5f+speed/18f)*dir);val head=((headRaw.toInt()%n)+n)%n;for(i in 0 until n){val d=if(dir>0) ((i-head+n)%n) else ((head-i+n)%n);val tail=custom.getOrElse(0){36}.coerceIn(1,100);val q=when{d==0->1f;d<=max(1,tail*n/100)->(1f-d.toFloat()/max(1,tail*n/100));else->0f};out[i]=when{d==0->rgb(colors.getOrNull(0)?:intArrayOf(255,0,0));q>0->rgb(colors.getOrNull(1)?:intArrayOf(255,0,0),q);else->rgb(colors.getOrNull(2)?:intArrayOf(0,0,0))}}}
            103->{
                 // PC App preview: Rainbow Chase uses the firmware 8.8
                 // phase-rate law. Speed therefore changes temporal movement.
                 val s=speed.coerceIn(0,100)
                 val custom1=custom.getOrElse(0){60}.coerceIn(0,100)
                 val span=3f + 253f*custom1/100f
                 val direction=onOff2.coerceIn(0,3)
                 val movement=onOff1.coerceIn(0,1)
                 val elapsed=(System.currentTimeMillis()-animationStartMs).coerceAtLeast(0L).toFloat()
                 var temporalPhase=0f
                 if(s>0){
                     val level=(s-1)/10
                     val within=(s-1)%10
                     val base=256.0 * 2.0.pow(level.toDouble())
                     val next=if(level>=9) base else base*2.0
                     val rate=base+(next-base)*within/10.0
                     val delta=elapsed*rate/1000.0
                     val oneWayLimit=255.0*256.0
                     temporalPhase=if(movement==1){
                         val period=oneWayLimit*2.0
                         val wrapped=delta%period
                         if(wrapped<=oneWayLimit) wrapped.toFloat() else (period-wrapped).toFloat()
                     }else (delta%(256.0*256.0)).toFloat()
                 }
                 for(i in 0 until n){
                     val spatial=when(direction){
                         1->n-1-i
                         2->min(i,n-1-i)
                         3->(n-1)/2-min(i,n-1-i)
                         else->i
                     }
                     val spatialPhase=if(n<=1)0f else floor(spatial*span/max(1,n-1))
                     val wheelPhase=if(direction<=1) spatialPhase-temporalPhase/256f else spatialPhase+temporalPhase/256f
                     out[i]=hsv(wheelPhase)
                 }
             }
            104->{val fill=((t*(1f+speed/50f))%2f);val p=if(fill<=1f)fill else 2f-fill;for(i in 0 until n){val x=i.toFloat()/n;val lit=if(onOff1==1||onOff1==4)x>=1f-p else x<=p;out[i]=if(lit)rgb(colors.getOrNull(0)?:intArrayOf(255,0,0)) else rgb(colors.getOrNull(1)?:intArrayOf(0,0,0))}}
            105->{
                // Match PC App Theater preview: contiguous blocks in Normal,
                // one black separator per block in Black Background, and
                // paired randomized eyes in Halloween Eyes.
                val cols=if(colors.isNotEmpty()) colors else listOf(intArrayOf(255,0,0),intArrayOf(0,255,0))
                val mode=onOff1.coerceIn(0,2)
                val direction=onOff2.coerceIn(0,1)
                val s=speed.coerceIn(0,100)
                val block=(1 + (custom.getOrElse(0){0}.coerceIn(0,100)*(n-1)/100)).coerceIn(1,n)
                val outColors=Array(n){Color.BLACK}
                if(mode==2){
                    fun hash(value:Int):Int { var x=value; x=x xor (x shl 13); x=x xor (x ushr 17); x=x xor (x shl 5); return x }
                    val fadeMs=max(730,650+(100-s)*15)
                    val minWait=max(80,180-s)
                    val maxWait=max(minWait+1,2600-s*19)
                    val total=max(1,fadeMs+maxWait+80)
                    val now=System.currentTimeMillis()-animationStartMs
                    val cycleIndex=(now/total).toInt()
                    val local=(now%total).toInt()
                    val wait=minWait+(hash(cycleIndex*0x9e3779b9.toInt()).ushr(1)%(max(1,maxWait-minWait+1)))
                    if(local>=wait){
                        val eyeElapsed=local-wait
                        val separation=max(1,min(n-1,1+custom.getOrElse(0){0}.coerceIn(0,100)*(n-2).coerceAtLeast(0)/100))
                        val eyeCount=max(1,min(n/2,custom.getOrElse(2){1}.coerceAtLeast(1)))
                        for(e in 0 until eyeCount){
                            val maxStart=max(0,n-1-separation)
                            val start=if(maxStart>0) hash(cycleIndex*0x9e3779b9.toInt()+e*0x85ebca6b.toInt()).ushr(1)%(maxStart+1) else 0
                            val color=cols[hash(cycleIndex*0x632be59b+e*0x27d4eb2d).ushr(1).rem(cols.size.coerceAtLeast(1))] 
                            val fade=if(eyeElapsed<20) 1f else (1f-(eyeElapsed-20).coerceAtLeast(0).toFloat()/max(1,fadeMs-20)).coerceIn(0f,1f)
                            outColors[start]=rgb(color,fade)
                            if(start+separation<n) outColors[start+separation]=rgb(color,fade)
                        }
                    }
                } else {
                    val interval=max(40,650-(s*6.1f).roundToInt())
                    val phase=(System.currentTimeMillis()-animationStartMs)/interval
                    val span=if(mode==1) block+1 else block
                    val patternLen=max(1,span*cols.size.coerceAtLeast(1))
                    for(i in 0 until n){
                        val pos=if(direction==1) n-1-i else i
                        val p=((pos-phase%patternLen+patternLen)%patternLen).toInt()
                        if(mode==1 && p%span==block) outColors[i]=Color.BLACK
                        else outColors[i]=rgb(cols[(p/span)%cols.size.coerceAtLeast(1)])
                    }
                }
                for(i in 0 until n) out[i]=outColors[i]
            }
            106->{val density=custom.getOrElse(0){15}.coerceIn(1,100)/100f;for(i in 0 until n){val pulse=(sine(t*12f+i*2.9f)+1f)/2f;val spark=if(pulse>1f-density)1f else 0f;out[i]=if(spark>0)rgb(colors.getOrNull(0)?:intArrayOf(0,0,0),spark) else rgb(colors.getOrNull(1)?:intArrayOf(0,0,0))}}
            107->{val launch=((t*(1f+speed/40f)).toInt()%n);val phase2=(t*2.2f)%1f;for(i in 0 until n){val d=kotlin.math.abs(i-launch);val radius=(custom.getOrElse(1){0}/100f)*n/2f+1f;val q=if(phase2<.28f && d<2)1f else if(phase2>.25f && phase2<.85f) (1f-d/(radius*phase2.coerceAtLeast(.1f))).coerceAtLeast(0f) else 0f;out[i]=when{phase2<.22f&&d==0->rgb(colors.getOrNull(0)?:intArrayOf(0,0,0),1f);q>0.1f->rgb(colors.getOrNull(1)?:intArrayOf(255,0,0),q);else->if(custom.getOrElse(0){0}==1 && phase2>.75f)rgb(colors.getOrNull(2)?:intArrayOf(255,255,0),q) else Color.BLACK}}}
            108->{for(i in 0 until n){val q=(sine(t*7+i*1.9f)+sine(t*4+i*.73f)+2f)/4f;out[i]=rgb(colors.getOrNull(0)?:intArrayOf(255,0,0),.35f+.65f*q)}}
            109->{val waves=max(1,custom.getOrElse(2){0}/20+1);for(i in 0 until n){val x=i.toFloat()/n;val travel=(t*.18f)%1f
                val pos=when(onOff2){1->x+travel;2->kotlin.math.abs(x-.5f)-travel;3->x-travel;else->x-travel}
                val q=(sine(pos*waves*6.283f)+1f)/2f;out[i]=if(q>.5f)rgb(colors.getOrNull(0)?:intArrayOf(255,0,0),q) else rgb(colors.getOrNull(1)?:intArrayOf(0,0,0))}}
            110->{val pulse=if((t*(1.5f+speed/35f)%1f)<.18f)1f else .12f;for(i in 0 until n)out[i]=rgb(colors.getOrNull(i%count)?:intArrayOf(255,0,0),pulse)}
            111->{val amp=.2f+.8f*((sine(t*7)+1f)/2f);for(i in 0 until n)out[i]=rgb(colors.getOrNull(i%count)?:intArrayOf(255,0,0),amp)}
            112->{for(i in 0 until n){val band=i.toFloat()/n;out[i]=hsv(band*300f+t*20f,.2f+.8f*((sine(t*11+band*18)+1f)/2f))}}
        }
        val br=brightness/255f
        for(i in 0 until n){val c=out[i];paint.color=Color.rgb((Color.red(c)*br).toInt(),(Color.green(c)*br).toInt(),(Color.blue(c)*br).toInt());val x=i*(w+gap);canvas.drawRoundRect(RectF(x,8f,x+w,height-8f),6f,6f,paint)}
    }
}

class MatrixBinding private constructor(private val rootView: View) : androidx.viewbinding.ViewBinding {
    override fun getRoot(): View = rootView
    companion object {
        fun inflate(inflater: LayoutInflater, container: ViewGroup?, attach: Boolean = false): MatrixBinding {
            val ctx=inflater.context
            val scroll=ScrollView(ctx).apply{isFillViewport=true;clipToPadding=false}
            val body=LinearLayout(ctx).apply{id=9000;orientation=LinearLayout.VERTICAL;setPadding(14,12,14,28)}
            scroll.addView(body)
            body.addView(Button(ctx).apply{id=9008;text="‹  Back to Matrix";visibility=View.GONE},LinearLayout.LayoutParams(-1,46).apply{bottomMargin=8})
            body.addView(TextView(ctx).apply{id=9001;text="";visibility=View.GONE},LinearLayout.LayoutParams(-1,1))
            body.addView(LinearLayout(ctx).apply{id=9004;orientation=LinearLayout.VERTICAL},LinearLayout.LayoutParams(-1,-2))
            body.addView(PixelPreview(ctx).apply{id=9007;visibility=View.GONE},LinearLayout.LayoutParams(-1,76))
            body.addView(LinearLayout(ctx).apply{id=9010;orientation=LinearLayout.VERTICAL},LinearLayout.LayoutParams(-1,-2))
            return MatrixBinding(scroll)
        }
    }
}
