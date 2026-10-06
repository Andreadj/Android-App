package com.mobiled.android.ui.splash

import android.Manifest.permission
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.mobiled.android.LogSystem
import androidx.activity.result.contract.ActivityResultContracts
import com.mobiled.android.BuildConfig
import com.mobiled.android.base.AppConfiguration
import com.mobiled.android.base.comman.LogSocketManager
import com.mobiled.android.base.comman.LogSocketManager.LogSocketState
import com.mobiled.android.databinding.ActivitySplashBinding
import com.mobiled.android.ui.TestingActivity
import com.mobiled.android.ui.group.MasterSlaveActivity
import com.mobiled.android.ui.home.HomeActivity


class SplashActivity : com.mobiled.android.base.BaseActivity<ActivitySplashBinding, SplashViewModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.viewBuildNumber.setText("v${BuildConfig.VERSION_NAME}")
    }

    var permissionRequestContact =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermissions()) {
                startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
                finish()
            } else {
                showPermissionDenied()
            }
        }

    private fun showPermissionDenied() {

        var positiveListener = DialogInterface.OnClickListener { dialog, _ ->
            dialog?.dismiss()
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts(
                    "package",
                    applicationContext.packageName,
                    null
                )
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        var negativeListener = DialogInterface.OnClickListener { dialog, _ ->
            dialog?.dismiss()
            finish()
        }

        showDialog(
            "Permission",
            "We need this permission in order to display the location details.",
            "Grant",
            positiveListener,
            "No",
            negativeListener
        )
    }

    var permissionList = emptyArray<String>()
    private fun hasPermissions(): Boolean {
        permissionList = arrayOf(
            permission.ACCESS_COARSE_LOCATION, permission.ACCESS_FINE_LOCATION,
            permission.ACCESS_WIFI_STATE, permission.CHANGE_WIFI_STATE
        )

        var permissionArray = ArrayList<String>()
        for (permission in permissionList) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissionArray.add(permission)
            }
        }
        permissionList = Array(permissionArray.size) { index -> "" }
        permissionArray.toArray(permissionList)

        return permissionArray.isEmpty()
    }

    override fun getActivityBinding(): ActivitySplashBinding =
        ActivitySplashBinding.inflate(layoutInflater)

    private var launchRunnable = Runnable {
        if (hasPermissions()) {
            if (com.mobiled.android.base.AppConfiguration.MASTER_SLAVE_DEBUG) {
                startActivity(Intent(
                    this@SplashActivity, MasterSlaveActivity::class.java
                ).also {
                    it.putExtra("groupId", "3")
                })
            } else {
                LogSystem.e("TAG", "Launching Main Page")
                startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
            }
            finish()
            mainHandler.removeCallbacksAndMessages(null)
        } else {
            permissionRequestContact.launch(permissionList)
        }
    }

    var handler: Handler? = null

    override fun onResume() {
        super.onResume()
        handler?.removeCallbacksAndMessages(null)
        binding.root.post {
            handler = Handler(Looper.getMainLooper())
            //KeyStorage.getKeyStorage(this@SplashActivity).clear()
            val exTime = System.currentTimeMillis()
            LogSocketManager.getSocketManager().connect {
                LogSystem.d("TAG", "getSocketManager().connect ${it} isNotSafe : ${isNotSafe()}")
                if(it == LogSocketState.CONNECTED || it == LogSocketState.ERROR) {
                    var delay = 3000 - (System.currentTimeMillis() - exTime)
                    if (delay < 0) delay = 100
                    handler?.removeCallbacksAndMessages(null)
                    handler?.postDelayed(launchRunnable, delay)
                }
            }
        }
    }

    override fun getViewModelObject(): SplashViewModel = SplashViewModel()

    override fun registerObservers() {

    }

    override fun unregisterObservers() {

    }

    override fun getFragmentContainerId(): Int = -1
}