package com.mobiled.android.ui.about

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.network.Success
import com.mobiled.android.databinding.FragmentHelpBinding
import com.mobiled.android.model.StaticPageResult
import com.mobiled.android.repository.HelpRepository
import com.mobiled.android.viewmodel.ViewModelFactory

class HelpFragment : BaseFragment<FragmentHelpBinding>() {

    lateinit var viewModel: HelpViewModel

    override fun getFragmentBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHelpBinding = FragmentHelpBinding.inflate(inflater, container, false)

    override fun handleBackPress(): Boolean {
        return false
    }

    companion object {
        @JvmStatic
        fun newInstance() = HelpFragment().apply {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val factory = ViewModelFactory(HelpRepository(requireContext()))
        viewModel = ViewModelProvider(this@HelpFragment, factory).get(HelpViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()
        showLoader()
        viewModel.getContentAsync()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLoader = viewBinding.viewIncLoader.viewLoader
        handleViewModel(viewModel)

        viewBinding.tvPageContent.setMovementMethod(LinkMovementMethod.getInstance())
    }

    private fun handleViewModel(viewModel: HelpViewModel) {
        viewModel.TermsPageResult.observe(viewLifecycleOwner, Observer {
            var content = (it as Success<StaticPageResult>).value.value
            viewBinding.tvPageContent.setText(
                HtmlCompat.fromHtml(
                    content,
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                )
            )

            hideLoader()
        })
    }
}