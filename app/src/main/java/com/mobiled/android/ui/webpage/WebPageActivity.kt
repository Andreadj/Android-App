package com.mobiled.android.ui.webpage

import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mobiled.android.databinding.ActivityWebPageBinding
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show


class WebPageActivity : com.mobiled.android.base.BaseActivity<ActivityWebPageBinding, WebPageViewModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!intent.hasExtra("siteURL")) {
            showToast("No URL Found!")
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.viewWebPage.getSettings().javaScriptEnabled = true
        binding.viewWebPage.getSettings().domStorageEnabled = true
        binding.viewWebPage.getSettings().loadWithOverviewMode = true
        binding.viewWebPage.webChromeClient = webChromeClient
        binding.viewWebPage.webViewClient = webViewClient
        binding.viewWebPage.loadUrl(intent.getStringExtra("siteURL") ?: "https://www.google.com")
    }

    val webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            binding.progressBar.progress = newProgress
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            showDialog("JsConfirm", message ?: "JS Confirm", "YES",{ dialog, _ ->
                dialog?.dismiss()
               result?.confirm()
            }, "NO", { dialog, _ ->
                dialog?.dismiss()
                result?.cancel()
            })
            return true
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            showDialog("JsAlert", message ?: "JS Confirm", "YES",{ dialog, _ ->
                dialog?.dismiss()
                result?.confirm()
            })
            return true
        }

        override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
            showDialog("JsPrompt", message ?: "JS Confirm", "YES",{ dialog, _ ->
                dialog?.dismiss()
                result?.confirm()
            })
            return true
        }
    }
    val webViewClient: WebViewClient = object : WebViewClient() {

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            view?.loadUrl(request?.url.toString())
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            if (isNotSafe()) return
            binding.progressBar.show()
            binding.progressBar.progress = 0
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            binding.progressBar.hide()
        }
    }


    override fun getActivityBinding(): ActivityWebPageBinding =
        ActivityWebPageBinding.inflate(layoutInflater)

    override fun getViewModelObject(): WebPageViewModel = WebPageViewModel()

    override fun registerObservers() {

    }

    override fun unregisterObservers() {

    }
    override fun getFragmentContainerId(): Int = -1

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return true
    }
}