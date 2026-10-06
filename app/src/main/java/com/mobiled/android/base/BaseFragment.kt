package com.mobiled.android.base

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show

abstract class BaseFragment<T : ViewBinding> : Fragment() {
    private var _binding: T? = null
    val viewBinding get() = _binding!!


    var viewLoader: FrameLayout? = null
    var tvLoaderMessage: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = getFragmentBinding(inflater, container)
        return viewBinding.root
    }

    abstract fun getFragmentBinding(inflater: LayoutInflater, container: ViewGroup?): T

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    fun showDialog(
        title: String = "Wifi Scanner",
        message: String,
        positiveButton: String = "OK",
        positiveButtonListener: DialogInterface.OnClickListener = DialogInterface.OnClickListener { dialog, which -> dialog?.dismiss() },
        negativeButton: String = "Cancel",
        negativeButtonListener: DialogInterface.OnClickListener = DialogInterface.OnClickListener { dialog, which -> dialog?.dismiss() }
    ) {

        var dialog: AlertDialog.Builder = AlertDialog.Builder(requireContext())
        dialog.setTitle(title)
        dialog.setMessage(message)
        dialog.setPositiveButton(positiveButton, positiveButtonListener)

        dialog.setNegativeButton(negativeButton, negativeButtonListener)
        dialog.create().show()
    }

    fun showToast(message: String) {
        try {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
        }
    }

    abstract fun handleBackPress(): Boolean


    var activityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

        }

    fun showLoader() {
        viewLoader?.show()
        tvLoaderMessage?.text = ""
    }

    fun showLoader(message: String) {
        viewLoader?.show()
        tvLoaderMessage?.text = message
    }

    fun hideLoader() {
        viewLoader?.hide()
    }

    fun getCurrentFragment(): Fragment? {
        val fragmentManager = childFragmentManager
        if (fragmentManager.backStackEntryCount == 0) return null
        val fragmentTag =
            fragmentManager.getBackStackEntryAt(fragmentManager.backStackEntryCount - 1).name
        return fragmentManager.findFragmentByTag(fragmentTag)
    }
}
