package com.mobiled.android.base

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding
import com.mobiled.android.R
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.ui.splash.SplashActivity

open abstract class BaseActivity<VB : ViewBinding, VM : ViewModel> : AppCompatActivity() {
    private var _binding: VB? = null
    val binding get() = _binding!!


    private var _viewModel: VM? = null
    val viewModel get() = _viewModel!!

    lateinit var udpClient: UdpClient

    var mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        udpClient = UdpClient.getClient(this)
        _binding = getActivityBinding()
        _viewModel = getViewModelObject()
        setContentView(binding.root)

        com.mobiled.android.base.comman.KeyboardUtil.setupUI(binding.root)
        registerObservers()

        applyActivityTransitionAnimation()
    }

    abstract fun getActivityBinding(): VB
    abstract fun getViewModelObject(): VM
    abstract fun registerObservers()
    abstract fun unregisterObservers()
    abstract fun getFragmentContainerId() : Int

    override fun onDestroy() {
        (viewModel as com.mobiled.android.base.BaseViewModel).destroyViewModel()
        unregisterObservers()
        _viewModel = null
        super.onDestroy()
        _binding = null
    }

    fun showDialog(
        title: String = "Wifi Scanner",
        message: String,
        positiveButton: String = "OK",
        positiveButtonListener: DialogInterface.OnClickListener = DialogInterface.OnClickListener { dialog, _ -> dialog?.dismiss() },
        negativeButton: String = "Cancel",
        negativeButtonListener: DialogInterface.OnClickListener = DialogInterface.OnClickListener { dialog, _ -> dialog?.dismiss() }
    ) {

        var dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this@BaseActivity)
        dialogBuilder.setTitle(title)
        dialogBuilder.setMessage(message)
        dialogBuilder.setPositiveButton(positiveButton, positiveButtonListener)

        if (!negativeButton.isEmpty()) {
            dialogBuilder.setNegativeButton(negativeButton, negativeButtonListener)
        }

        var dialog= dialogBuilder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    fun showToast(message: String) {
        try {
            Toast.makeText(this@BaseActivity, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
        }
    }


    fun isNotSafe(): Boolean {
        return isFinishing
    }


    override fun onResume() {
        super.onResume()
        if(!(this is SplashActivity)) udpClient.openUdpClient()
    }

    var activityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

        }

    protected fun showFragment(containerId: Int, fragment: Fragment, addBackStack: Boolean = true) {
        var transition = supportFragmentManager.beginTransaction()
        transition.replace(containerId, fragment, fragment.javaClass.simpleName)
        if (addBackStack) transition.addToBackStack(fragment.javaClass.simpleName)
        else transition.addToBackStack(null)
        transition.commit()
    }

    fun getCurrentFragment(): Fragment? {
        val fragmentManager = supportFragmentManager
        if (fragmentManager.backStackEntryCount == 0) return null
        val fragmentTag =
            fragmentManager.getBackStackEntryAt(fragmentManager.backStackEntryCount - 1).name
        try {
            return fragmentManager.findFragmentByTag(fragmentTag)?: fragmentManager.findFragmentById(getFragmentContainerId())
        } catch (e: Exception) {
            return null
        }
    }

    private fun applyActivityTransitionAnimation() {
        overridePendingTransition(
            R.anim.animate_swipe_left_enter,
            R.anim.animate_swipe_left_exit
        )
    }


    private fun applyActivityTransitionAnimation2() {
        overridePendingTransition(
            R.anim.animate_swipe_right_enter,
            R.anim.animate_swipe_right_exit
        )
    }

    override fun onBackPressed() {
        super.onBackPressed()
        applyActivityTransitionAnimation2()
    }
}
