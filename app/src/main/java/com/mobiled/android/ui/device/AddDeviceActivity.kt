package com.mobiled.android.ui.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.mobiled.android.LogSystem
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import com.mobiled.android.R
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareDeviceListResult
import com.mobiled.android.base.model.HardwareDeviceResult
import com.mobiled.android.base.network.Failure
import com.mobiled.android.base.network.Success
import com.mobiled.android.databinding.ActivityAddDeviceBinding
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show


class AddDeviceActivity : com.mobiled.android.base.BaseActivity<ActivityAddDeviceBinding, DeviceViewModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding.viewToolbar.viewLogo.show()
        binding.viewToolbar.viewPageTitle.hide()
        binding.viewToolbar.viewDeviceSwitchRoot.hide()
        binding.viewToolbar.viewPageTitle.setText("Home")

        binding.viewToolbar.ivBack.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        binding.viewToolbar.viewShowOptions.show()



        try {
            registerReceiver(gpsSwitchStateReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
            registerReceiver(gpsSwitchStateReceiver, IntentFilter(Intent.ACTION_PROVIDER_CHANGED))
            registerReceiver(gpsSwitchStateReceiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        bindUI()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(gpsSwitchStateReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    var prevWifiState = false
    var prevGpsState = false

    private fun bindUI(
        isWifiConnected: Boolean = UdpClient.getClient(this@AddDeviceActivity)
            .isWifiConnected(), isGpsProviderEnabled: Boolean = UdpClient.getClient(this@AddDeviceActivity)
            .isGpsProviderEnabled()
    ) {

        prevWifiState = isWifiConnected
        prevGpsState = isGpsProviderEnabled

        if (!isWifiConnected) {
            binding.tvWelcomeMessage.setText("We're sorry, but we're having trouble detecting your Wi-Fi network. Please make sure that your GPS and Wi-Fi settings are turned on and properly configured.")
            binding.buttonConfirm.setText("Enable")
            binding.buttonConfirm.setOnClickListener {
                showLoader()

                val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                startActivity(wifiIntent)

            }
        } else if (!isGpsProviderEnabled) {
            binding.viewNetworkImage.setImageResource(R.drawable.location_off)
            binding.tvWelcomeMessage.setText("We're sorry, but we're having trouble detecting your Wi-Fi network. Please make sure that your GPS and Wi-Fi settings are turned on and properly configured.")

            binding.buttonConfirm.setText("Enable")
            binding.buttonConfirm.setOnClickListener {
                showLoader()

                // If GPS is not enabled, prompt the user to enable it
                // If GPS is not enabled, prompt the user to enable it
                val gpsIntent: Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(gpsIntent)

            }
        } else {
            var wifiName = UdpClient.getClient(this@AddDeviceActivity)
                .getConnectedWifiName()

            binding.viewNetworkImage.setImageResource(R.drawable.ic_network)
            if (wifiName?.contains("MobileD", true) == true) {
                binding.tvWelcomeMessage.setText(
                    "You are connected with $wifiName, By pressing on yes, you are confirming that, app will listen for the data up-to ${
                        com.mobiled.android.base.comman.TextUtil.getDurationMessageByMillis(
                            duration
                        )
                    }, if device not respond, it will show error"
                )
            } else {
                binding.tvWelcomeMessage.setText(
                    "Make sure you are connected with right network,Currently you are connected with $wifiName, By pressing on yes, you are confirming that, app will listen for the data up-to ${
                        com.mobiled.android.base.comman.TextUtil.getDurationMessageByMillis(
                            duration
                        )
                    }, if device not respond, it will show error"
                )
            }
            binding.buttonConfirm.setText("Yes")
            binding.buttonConfirm.setOnClickListener {
                binding.viewLoader.show()
                viewModel.getDevicesAsync()
            }

        }
    }

    private fun showLoader() {
        binding.viewLoadCount.setText("")
        binding.viewLoader.show()
        startLoaderAnimation()
    }

    private val gpsSwitchStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogSystem.d("TAG", "onReceive() called with intent = ${intent.action} State : ${UdpClient.getClient(this@AddDeviceActivity).isWifiConnected()}")
            var action = intent.action
            if (action != null && action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)

                if (prevWifiState != (networkInfo?.isConnected() == true)) {
                    binding.viewLoadCount.setText("")
                    binding.viewLoader.hide()
                    binding.viewLoaderImage.clearAnimation()
                    bindUI(isWifiConnected = (networkInfo?.isConnected() == true))
                }
            } else if (action != null && action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                val locationManager = context.getSystemService(LOCATION_SERVICE) as LocationManager
                val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)


                if (prevGpsState != (gpsEnabled)) {
                    binding.viewLoadCount.setText("")
                    binding.viewLoader.hide()
                    binding.viewLoaderImage.clearAnimation()
                    bindUI(isGpsProviderEnabled = gpsEnabled)
                }
            }
        }
    }

    var deviceList = listOf<HardwareDevice>()
    override fun registerObservers() {
        viewModel.deviceResult.observe(this) { result ->
            binding.viewFinalMessage.show()
            binding.viewLoaderImage.clearAnimation()
            if (result.status == com.mobiled.android.base.network.Status.SUCCESS) {
                binding.ivStatus.setImageResource(R.drawable.ic_network_success)
                binding.tvFinalMessage.text =
                    (result as Success<HardwareDeviceResult>).value.getMessage()
                setResult(RESULT_OK, Intent())
            } else if (result.status == com.mobiled.android.base.network.Status.FAIL) {
                binding.ivStatus.setImageResource(R.drawable.ic_network_fail)
                binding.tvFinalMessage.text = (result as Failure<HardwareDeviceResult>).errorMessage
                setResult(RESULT_CANCELED)
            }

            binding.root.postDelayed({
                if (isNotSafe()) return@postDelayed
                finish()
            }, 2000L)
        }

        viewModel.deviceListResult.observe(this) {
            if (isNotSafe()) return@observe
            if (it.status == com.mobiled.android.base.network.Status.SUCCESS) {
                deviceList = (it as Success<HardwareDeviceListResult>).value.value
                startLoaderAnimation()
                startThread()
                binding.viewIndication2.setText("Listening....")
                viewModel.startListening(udpClient)
            } else {
                showToast("Something wrong!, please connect with support.")
                setResult(RESULT_CANCELED)
                finish()
            }
        }


    }

    private fun startLoaderAnimation() {
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )

        rotate.duration = 900
        rotate.repeatCount = Animation.INFINITE
        binding.viewLoaderImage.startAnimation(rotate)
    }

    var counterThread: Thread? = null
    var threadExTime = -1L
    var duration = 10 * 1000L
    var counterThreadRun = false
    var counterHandler = Handler(Looper.getMainLooper()) {
        if (isNotSafe()) return@Handler true
        try {
            var dif = System.currentTimeMillis() - threadExTime
            var intDif = ((duration - dif) / 1000F).toInt()
            if (intDif < 0) {
                intDif = 0
            }
            binding.viewLoadCount.setText("$intDif")
            if (threadExTime != -1L && intDif == 0) {
                binding.viewIndication2.text = "Processing result..."
                counterThreadRun = false
                counterThread?.interrupt()
                counterThread = null
                viewModel.getUdpResult()
            }
        } catch (ignore: Exception) {
        }
        return@Handler true
    }

    private fun startThread() {
        threadExTime = System.currentTimeMillis()
        counterThreadRun = true
        counterThread?.interrupt()
        counterThread = Thread {
            while (counterThreadRun) {
                counterHandler.sendEmptyMessage(0)
                try {
                    Thread.sleep(100)
                } catch (e: Exception) {
                }
            }
        }
        counterThread?.start()
    }

    override fun unregisterObservers() {
        viewModel.deviceResult.removeObservers(this)
        viewModel.deviceListResult.removeObservers(this)
    }

    var opExTime = -1L

    override fun getActivityBinding(): ActivityAddDeviceBinding =
        ActivityAddDeviceBinding.inflate(layoutInflater)

    override fun onPause() {
        //UdpClient.getClient(this@AddDeviceActivity).closeSocket()
        super.onPause()
    }

    override fun getViewModelObject(): DeviceViewModel = DeviceViewModel(this@AddDeviceActivity)

    override fun getFragmentContainerId(): Int = -1
}
