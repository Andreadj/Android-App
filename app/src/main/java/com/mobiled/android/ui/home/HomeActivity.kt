package com.mobiled.android.ui.home

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.mobiled.android.R
import com.mobiled.android.base.AppSettingsManager
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.databinding.ActivityHomeBinding
import com.mobiled.android.ui.about.AboutFragment
import com.mobiled.android.ui.about.HelpFragment
import com.mobiled.android.ui.about.PrivacyPolicyFragment
import com.mobiled.android.ui.device.AddDeviceActivity
import com.mobiled.android.ui.group.AddGroupActivity
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show


class HomeActivity : com.mobiled.android.base.BaseActivity<ActivityHomeBinding, HomeViewModel>() {


    override fun onDestroy() {
        UdpClient.getClient(this@HomeActivity).closeSocket()
        UdpClient.getClient(this@HomeActivity).closeWatcher()
        super.onDestroy()
    }

    override fun getActivityBinding(): ActivityHomeBinding =
        ActivityHomeBinding.inflate(layoutInflater)



    var deviceActionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if(it.resultCode == RESULT_OK)
            {
                if(getCurrentFragment() is HomeFragment)
                {
                    (getCurrentFragment() as HomeFragment)?.addDeviceByResult()
                }
            }
        }

    private val saveConfigurationLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val json = AppSettingsManager.exportConfiguration(this@HomeActivity)
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toString(2).toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Unable to open configuration file")
                Toast.makeText(this@HomeActivity, "Configuration saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@HomeActivity, "Configuration save failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

    private val loadConfigurationLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException("Unable to open configuration file")
                val configuration = org.json.JSONObject(text)
                if (!AppSettingsManager.validateConfiguration(configuration)) {
                    throw IllegalArgumentException("Unsupported MobileD configuration file")
                }
                showDialog(
                    "Load Configuration",
                    "Load this configuration? The current MobileD device and group configuration will be replaced.",
                    "YES",
                    { dialog, _ ->
                        dialog.dismiss()
                        runCatching {
                            if (!AppSettingsManager.importConfiguration(this@HomeActivity, configuration)) {
                                throw IllegalArgumentException("Invalid configuration")
                            }
                            if (getCurrentFragment() is HomeFragment) {
                                (getCurrentFragment() as HomeFragment).addDeviceByResult()
                            }
                            Toast.makeText(this@HomeActivity, "Configuration loaded", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(this@HomeActivity, "Configuration load failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    "No",
                    { dialog, _ -> dialog.dismiss() }
                )
            }.onFailure {
                Toast.makeText(this@HomeActivity, "Configuration load failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        binding.menuFrame.post {
            binding.menuFrame.translationX = -(binding.menuFrame.width.toFloat())
            binding.menuFrame.show()
        }


        binding.viewToolbar.viewLogo.show()
        binding.viewToolbar.viewPageTitle.hide()
        binding.viewToolbar.viewDeviceSwitchRoot.hide()
        binding.viewToolbar.viewPageTitle.setText("Home")

        binding.viewToolbar.ivBack.setImageResource(R.drawable.ic_menu)
        binding.viewToolbar.viewShowOptions.show()

        binding.viewToolbar.ivBack.setOnClickListener {
            toggleMenu()
        }

        binding.viewWebsite.setOnClickListener {
            var browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.mobiledonline.eu"))
            activityLauncher.launch(browserIntent)
        }


        binding.viewToolbar.viewShowOptions.setOnClickListener { view ->

            var popupMenu = PopupMenu(this, view)
            menuInflater.inflate(com.mobiled.android.R.menu.menu1, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (menuItem.title?.equals("Remove Devices") == true) {

                    var activity = this
                    var positiveListener = DialogInterface.OnClickListener { dialog, _ ->
                        dialog?.dismiss()
                        if(activity.getCurrentFragment() is HomeFragment)
                        {
                            (activity.getCurrentFragment() as HomeFragment)?.removeAllDevices()
                        }
                    }

                    var negativeListener = DialogInterface.OnClickListener { dialog, _ ->
                        dialog?.dismiss()
                    }
                    showDialog(
                        "Remove Devices",
                        "Are you sure you want to remove all the saved devices ?.",
                        "YES",
                        positiveListener,
                        "No",
                        negativeListener
                    )


                } else if (menuItem.title?.equals("Add Device") == true) {
                    //childItems[0].add("Device ${childItems[0].size}")
                    var intent = Intent(
                        this@HomeActivity, AddDeviceActivity::class.java
                    )
                    deviceActionLauncher.launch(
                        intent
                    )
                } else if (menuItem.title?.equals("Add Group") == true) {
                    var intent = Intent(
                        this@HomeActivity, AddGroupActivity::class.java
                    )
                    deviceActionLauncher.launch(
                        intent
                    )
                } else if (menuItem.title?.equals("Reset to Default Settings") == true) {
                    showDialog(
                        "Reset Settings",
                        "Restore all effect, color and preset settings to the MobileD default values? Devices, groups and network settings will be preserved.",
                        "YES",
                        { dialog, _ ->
                            dialog.dismiss()
                            AppSettingsManager.resetToDefaults(this@HomeActivity)
                            Toast.makeText(this@HomeActivity, "Default settings restored", Toast.LENGTH_SHORT).show()
                        },
                        "No",
                        { dialog, _ -> dialog.dismiss() }
                    )
                }

                return@setOnMenuItemClickListener true
            }
            popupMenu.show()
        }


        showFragment(R.id.viewContainer, HomeFragment.newInstance(), false)


        binding.viewMenuItem1.setOnClickListener {
            hideMenu()
            if (!(getCurrentFragment() is HomeFragment)) {
                showFragment(R.id.viewContainer, HomeFragment.newInstance(), false)
            }
        }

        binding.viewMenuItem3.setOnClickListener {
            hideMenu()
            if (!(getCurrentFragment() is HelpFragment)) {
                showFragment(R.id.viewContainer, HelpFragment.newInstance())
            }
        }


        binding.viewMenuItem4.setOnClickListener {
            hideMenu()
            if (!(getCurrentFragment() is PrivacyPolicyFragment)) {
                showFragment(R.id.viewContainer, PrivacyPolicyFragment.newInstance())
            }
        }

        binding.viewMenuItem5.setOnClickListener {
            hideMenu()
            if (!(getCurrentFragment() is AboutFragment)) {
                showFragment(R.id.viewContainer, AboutFragment.newInstance())
            }
        }

        binding.viewMenuItem6.setOnClickListener {
            hideMenu()
            saveConfigurationLauncher.launch("MobileD Configuration.mobiled")
        }

        binding.viewMenuItem7.setOnClickListener {
            hideMenu()
            loadConfigurationLauncher.launch(arrayOf("application/json", "text/json", "application/octet-stream"))
        }

        supportFragmentManager.addFragmentOnAttachListener { fragmentManager, fragment ->
            if ((fragment is HomeFragment)) {
                binding.viewMenuItem1.setBackgroundResource(R.drawable.dark_box_corner)
                binding.viewMenuItem3.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem4.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem5.setBackgroundColor(Color.TRANSPARENT)
            } else if ((fragment is HelpFragment)) {
                binding.viewMenuItem1.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem3.setBackgroundResource(R.drawable.dark_box_corner)
                binding.viewMenuItem4.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem5.setBackgroundColor(Color.TRANSPARENT)

            } else if ((fragment is PrivacyPolicyFragment)) {
                binding.viewMenuItem1.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem3.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem4.setBackgroundResource(R.drawable.dark_box_corner)
                binding.viewMenuItem5.setBackgroundColor(Color.TRANSPARENT)

            } else if ((fragment is AboutFragment)) {
                binding.viewMenuItem1.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem3.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem4.setBackgroundColor(Color.TRANSPARENT)
                binding.viewMenuItem5.setBackgroundResource(R.drawable.dark_box_corner)
            }
        }
    }


    override fun getViewModelObject(): HomeViewModel = HomeViewModel(this)


    override fun registerObservers() {

    }

    override fun unregisterObservers() {

    }


    var isMenuOpen = false
    private fun toggleMenu() {
        if (isMenuOpen) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        binding.contentFrame.animate().translationX(binding.menuFrame.width.toFloat())
            .setDuration(300)
        binding.menuFrame.animate().translationX(0f).setDuration(300)
        isMenuOpen = true
    }


    private fun hideMenu() {
        binding.contentFrame.animate().translationX(0f).setDuration(300)
        binding.menuFrame.animate().translationX(-binding.menuFrame.width.toFloat())
            .setDuration(300)
        isMenuOpen = false
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // Check if the touch event is outside the menu frame
        if (isMenuOpen) {
            binding.menuFrame.requestLayout()
            val menuRect = Rect().apply {
                binding.menuFrame.getDrawingRect(this)
                binding.root.offsetDescendantRectToMyCoords(binding.menuFrame, this)
            }

            // Check if the touch event is inside the menu frame
            if (event?.action == MotionEvent.ACTION_DOWN && !menuRect.contains(
                    event.x.toInt(),
                    event.y.toInt()
                )
            ) {
                // Touch event is outside the menu frame
                // Do something here
                toggleMenu()
                return true
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onBackPressed() {
        if (isMenuOpen) {
            toggleMenu()
        } else {
            if (getCurrentFragment() is HomeFragment || getCurrentFragment() == null) {
                finish()
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun getFragmentContainerId(): Int = R.id.viewContainer
}