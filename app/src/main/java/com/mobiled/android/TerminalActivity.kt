package com.mobiled.android

import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.LightCommand
import com.mobiled.android.databinding.ActivityTerminalBinding
import kotlin.random.Random

class TerminalActivity : AppCompatActivity(), UdpClient.Listener {
    lateinit var binding: ActivityTerminalBinding
    var device: HardwareDevice? = null
    var group: HardwareGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!intent.hasExtra("device")) {
            if (!intent.hasExtra("group")) {
                finish()
                return
            }
        }

        if (intent.hasExtra("device")) {
            device = intent.getSerializableExtra("device") as HardwareDevice
        } else {
            group = intent.getSerializableExtra("group") as HardwareGroup
        }

        Thread(Runnable {
            while (canSend()) {
                if (true) {
                    var command = LightCommand()
                    command.Command = 1
                    command.GLights = Random.nextInt(0, 12)
                    command.Speed = Random.nextInt(10, 100)
                    command.white = Random.nextInt(10, 255)
                    command.red = Random.nextInt(10, 255)
                    command.blue = Random.nextInt(10, 255)
                    command.green = Random.nextInt(10, 255)
                    clientHandler.sendMessage(clientHandler.obtainMessage(1).also {
                        it.obj = command.toJsonString()
                    })
                } else {
                    LogSystem.e("TAG", "Not Connected to the Socket!")
                }
                try {
                    Thread.sleep(5000)
                } catch (e: Exception) {
                }
            }
        }).start()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPause() {
        //UdpClient.getClient(this).closeSocket()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (intent.hasExtra("group")) {
            UdpClient.getClient(this@TerminalActivity).openUdpClient()
            UdpClient.getClient(this@TerminalActivity).startListen()
            UdpClient.getClient(this@TerminalActivity).with(null)
            group?.groupItems?.forEach {
                var device = it.hardwareDevice
                UdpClient.getClient(this@TerminalActivity).startListen(device?.ApName ?: "", this@TerminalActivity)
            }
            if ((group?.groupItems?.size ?: 0) == 0) {
                postReceiveMessage("No devices in the group")
            }
        } else {
            UdpClient.getClient(this@TerminalActivity).openUdpClient()
            UdpClient.getClient(this@TerminalActivity).startListen()
            UdpClient.getClient(this@TerminalActivity).with(this)
        }
    }


    fun canSend() = !isFinishing


    fun writeBytes(value: String) {
        preCheckLength()
        if (device != null) {
            UdpClient.getClient(this@TerminalActivity)
                .writeString(value, device?.ip ?: "", device?.port?.toInt() ?: 8232)
        } else {
            group?.groupItems?.forEach {
                var device = it.hardwareDevice
                UdpClient.getClient(this@TerminalActivity)
                    .writeString(value, device?.ip ?: "", device?.port?.toInt() ?: 8232)
            }
        }

        postWriteMessage(value)
    }

    private fun postWriteMessage(value: String) {
        val spn = SpannableStringBuilder(value + '\n')
        spn.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.textView.append(spn)
        binding.nestedScrollView.fullScroll(View.FOCUS_DOWN);
    }

    private fun preCheckLength() {
        if (binding.textView.text.length >= (1024 * 5)) {
            binding.textView.text = ""
        }
    }

    fun receiveBytes(value: String) {
        preCheckLength()
        postReceiveMessage(value)
    }

    private fun postReceiveMessage(value: String) {
        val spn = SpannableStringBuilder(value + '\n')
        spn.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorRecieveText)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.textView.append(spn)

        binding.nestedScrollView.fullScroll(View.FOCUS_DOWN);
    }

    var clientHandler: Handler = Handler() { msg ->
        if (msg.what == 0) {
            var data = msg.obj as String
            receiveBytes(data)
        } else if (msg.what == 1) {
            var data = msg.obj as String
            writeBytes(data)
        }
        return@Handler true
    }

    override fun onUdpMessage(bytes: ByteArray) {
        runOnUiThread {
            receiveBytes(String(bytes))
        }
    }
}