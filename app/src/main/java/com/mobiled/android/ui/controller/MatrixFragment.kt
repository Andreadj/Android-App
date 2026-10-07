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
        effectSelected = true
        effectId = id
        root.findViewById<Button>(9008)?.visibility = View.GONE
        root.findViewById<LinearLayout>(9004)?.visibility = View.GONE
        root.findViewById<LinearLayout>(9010)?.visibility = View.VISIBLE
        val spec = specs.first { it.id == id }
        applyEffectDefaults(spec)
        loadSavedColors(spec)
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
        send()
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
                    setBackgroundColor(if (colorSaved[i]) Color.rgb(colors[i][0], colors[i][1], colors[i][2]) else Color.rgb(70,70,70))
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
                    onClick = { /* The picker is independent; normal tap does not select or modify a color slot. */ }
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
            if (spec.random) {
                main.addView(CheckBox(requireContext()).apply {
                    text = "Random"; setTextColor(Color.WHITE); isChecked = randomEnabled
                    setOnCheckedChangeListener { _, checked -> randomEnabled = checked; send() }
                }, LinearLayout.LayoutParams(-1, 44).apply { topMargin = 10 })
            }
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

    private fun aPixelCount(): Int = requireActivity().let { (it as ControllerActivity).group?.groupItems?.size ?: 1 }

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
        addView(TextView(requireContext()).apply { text = label; textSize = 13f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, dp(68), 0.95f))
        val spinner = Spinner(requireContext()).apply {
            val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = TextView(requireContext()).apply { text = options[position]; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL; setPadding(10,0,10,0) }
            }
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter; setSelection(selected.coerceIn(0, options.lastIndex), false); setBackgroundResource(R.drawable.grey_box)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) {} ; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onChange(position) } }
        }
        addView(spinner, LinearLayout.LayoutParams(0, dp(68), 1.05f).apply { leftMargin = 10 })
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

    private fun applyEffectDefaults(spec: Spec) {
        speed.progress = when (spec.id) { 103 -> 70; 105 -> 0; 106 -> 100; else -> 50 }
        brightness.progress = 255
        customValues.fill(0); onOff1 = 0; onOff2 = 0; randomEnabled = false
        when (spec.id) {
            101 -> { customValues[0]=10; customValues[1]=10; customValues[2]=10 }
            102 -> { customValues[0]=36; customValues[1]=47; customValues[2]=25 }
            103 -> { customValues[0]=60; customValues[2]=0 }
            105 -> { customValues[0]=0 }
            106 -> { customValues[0]=15 }
            108 -> { customValues[0]=45; customValues[1]=100 }
        }
        if (spec.id == 101 || spec.id == 102 || spec.id == 107 || spec.id == 110 || spec.id == 111) randomEnabled = false
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
            colorSaved[i] = p.getBoolean("$i.saved", i < min(3, spec.colors))
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
        rebuildControls(spec)
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
        rebuildControls(specs.first { it.id == effectId })
        send()
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
        val picker = ColorPickerView(requireContext())
        host.addView(picker, LinearLayout.LayoutParams(-1, dp(360)))
        picker.setColor(255, pickerColor[0], pickerColor[1], pickerColor[2])
        picker.setColorAlpha(com.mobiled.android.adapters.GeneralUtil.generateAlphaByWhiteBrightness(pickerWhite.toFloat(), 100f))
        picker.setBrightness(pickerBrightness)
        val readout = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        host.addView(readout, LinearLayout.LayoutParams(-1, -2))
        fun updateFromPicker(argb: IntArray) {
            pickerColor[0] = argb[1].coerceIn(0, 255)
            pickerColor[1] = argb[2].coerceIn(0, 255)
            pickerColor[2] = argb[3].coerceIn(0, 255)
            readout.text = "RGB ${pickerColor[0]} / ${pickerColor[1]} / ${pickerColor[2]}    W ${pickerColor[3]}"
        }
        picker.setColorChangedListener(object : ColorPickerView.OnColorChangedListener {
            override fun colorChanged(centerColor: Int, argb: IntArray, hsv: FloatArray) {
                updateFromPicker(argb)
            }
        })
        updateFromPicker(picker.toArgb())
        addStepSlider(host, "Color Brightness", pickerBrightness, 0, 100, "%") { value ->
            pickerBrightness = value
            picker.setBrightness(value)
            updateFromPicker(picker.toArgb())
        }
        addStepSlider(host, "White Brightness", pickerWhite, 0, 100, "%") { value ->
            pickerWhite = value
            pickerColor[3] = (value * 255f / 100f).roundToInt().coerceIn(0, 255)
            picker.setColorAlpha(value.toFloat())
            updateFromPicker(picker.toArgb())
        }
        val hue = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER }
        hue.addView(TextView(requireContext()).apply {
            text = "−"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.white_box)
            setOnClickListener { picker.decreaseHue() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(6) })
        hue.addView(TextView(requireContext()).apply {
            text = "+"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.BLACK)
            setBackgroundResource(R.drawable.white_box)
            setOnClickListener { picker.increaseHue() }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(6) })
        host.addView(hue, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
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
        val spec=specs.first{it.id==id};rebuildControls(spec);send();preview.invalidate()
    }

    private fun snapshot(): JSONObject {
        val o=JSONObject().put("speed",speed.progress).put("brightness",brightness.progress).put("custom1",customValues[0]).put("custom2",customValues[1]).put("custom3",customValues[2]).put("onoff1",onOff1).put("onoff2",onOff2).put("random",randomEnabled)
        val a=JSONArray();colors.forEachIndexed{index,c->a.put(JSONObject().put("r",c[0]).put("g",c[1]).put("b",c[2]).put("w",c[3]).put("saved",colorSaved[index]))};o.put("colors",a);return o
    }

    private fun onlineMembers(): List<HardwareGroupItem> {
        val a = requireActivity() as ControllerActivity
        return a.group?.groupItems.orEmpty().filter {
            val d = it.hardwareDevice
            !d?.deviceFrame.isNullOrBlank() && !d?.ip.isNullOrBlank()
        }.sortedBy { it.hardwareDevice?.ApName.orEmpty() }
    }

    private fun pixelId(item: HardwareGroupItem): Int = item.PixelID.coerceIn(0, 1023)

    private fun send() {
        val a = requireActivity() as ControllerActivity
        val base = a.getFrame()
        val items = onlineMembers()
        if (items.isEmpty()) {
            val d = a.device ?: return
            val c = LightCommand().apply { fromJson(base); GLights=effectId; GState="X"; GPort="8889"; PixelID=0; PixelCount=1; Speed=speed.progress; Brightness=brightness.progress; applyColors(this) }
            d.deviceFrame = c.toJsonString()
            UdpClient.getClient(requireContext()).writeString(c.toJsonString(), d.ip ?: return, if (d.port > 0) d.port.toInt() else 8889)
            return
        }
        val ids = items.map { pixelId(it) }
        val allGroupItems = a.group?.groupItems.orEmpty()
        val allIds = allGroupItems.map { pixelId(it) }.sorted()
        val masters = allGroupItems.filter { it.GState.equals("M", ignoreCase = true) }
        if (masters.size != 1) {
            Toast.makeText(requireContext(), "Matrix requires exactly one Master.", Toast.LENGTH_LONG).show()
            return
        }
        if (pixelId(masters.first()) != 0) {
            Toast.makeText(requireContext(), "Matrix Master must have Pixel ID 0.", Toast.LENGTH_LONG).show()
            return
        }
        if (allIds.isEmpty() || allIds.first() != 0 || allIds.distinct().size != allIds.size ||
            allIds.withIndex().any { it.value != it.index }) {
            Toast.makeText(requireContext(), "Matrix Pixel IDs must be consecutive starting from 0.", Toast.LENGTH_LONG).show()
            return
        }
        val count = allIds.size
        items.forEachIndexed { i, item ->
            val d = item.hardwareDevice ?: return@forEachIndexed
            val c = LightCommand().apply {
                fromJson(JSONObject(d.deviceFrame))
                GLights = effectId
                GState = item.GState
                GPort = item.Gport
                PixelID = ids[i]
                PixelCount = count
                Speed = speed.progress
                Brightness = brightness.progress
                ColorCount = specs.first { it.id == effectId }.colors
                Random = randomEnabled
                Custom1 = customValues[0]
                Custom2 = if (effectId == 103) 100 else customValues[1]
                Custom3 = customValues[2]
                OnOff1Value = onOff1
                OnOff2Value = onOff2
                OnOff1 = onOff1 != 0
                OnOff2 = onOff2 != 0
                applyColors(this)
            }
            d.deviceFrame = c.toJsonString()
            UdpClient.getClient(requireContext()).writeString(c.toJsonString(), d.ip ?: return@forEachIndexed, if (d.port > 0) d.port.toInt() else 8889)
        }
        root.findViewById<TextView>(9012)?.text = "Pixel IDs: ${ids.sorted().joinToString()} / Count $count"
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
    private fun sine(v:Float)=kotlin.math.sin(v.toDouble()).toFloat()
    fun startAnimation(){ if(animating)return; animating=true; frameHandler.post(frameRunnable) }
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
        fun rgb(c:IntArray,f:Float=1f)=Color.rgb((c.getOrElse(0){0}*f).toInt().coerceIn(0,255),(c.getOrElse(1){0}*f).toInt().coerceIn(0,255),(c.getOrElse(2){0}*f).toInt().coerceIn(0,255))
        fun hsv(h:Float,v:Float=1f)=Color.HSVToColor(floatArrayOf(((h%360f)+360f)%360f,1f,v.coerceIn(0f,1f)))
        val out=IntArray(n){Color.BLACK}
        val count=colors.size.coerceAtLeast(1)
        val dir=if(onOff2==1) -1 else 1
        when(effect){
            101->{val idx=((t*(1f+speed/18f)).toInt().mod(count));for(i in 0 until n)out[i]=rgb(colors.getOrNull(idx)?:intArrayOf(255,0,0))}
            102->{val headRaw=(t*(1.5f+speed/18f)*dir);val head=((headRaw.toInt()%n)+n)%n;for(i in 0 until n){val d=if(dir>0) ((i-head+n)%n) else ((head-i+n)%n);val tail=custom.getOrElse(0){36}.coerceIn(1,100);val q=when{d==0->1f;d<=max(1,tail*n/100)->(1f-d.toFloat()/max(1,tail*n/100));else->0f};out[i]=when{d==0->rgb(colors.getOrNull(0)?:intArrayOf(255,0,0));q>0->rgb(colors.getOrNull(1)?:intArrayOf(255,0,0),q);else->rgb(colors.getOrNull(2)?:intArrayOf(0,0,0))}}}
            103->{for(i in 0 until n){val h=t*90f + i.toFloat()/n*360f;out[i]=hsv(h)}}
            104->{val fill=((t*(1f+speed/50f))%2f);val p=if(fill<=1f)fill else 2f-fill;for(i in 0 until n){val x=i.toFloat()/n;val lit=if(onOff1==1||onOff1==4)x>=1f-p else x<=p;out[i]=if(lit)rgb(colors.getOrNull(0)?:intArrayOf(255,0,0)) else rgb(colors.getOrNull(1)?:intArrayOf(0,0,0))}}
            105->{val block=max(1,custom.getOrElse(0){0});val shift=((t*(1f+speed/30f)).toInt()*max(1,block+1));for(i in 0 until n)out[i]=rgb(colors.getOrNull((i+shift)/max(1,block+1)%count)?:intArrayOf(255,0,0))}
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
