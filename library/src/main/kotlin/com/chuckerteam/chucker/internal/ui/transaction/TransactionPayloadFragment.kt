package com.chuckerteam.chucker.internal.ui.transaction

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import com.chuckerteam.chucker.GsonInstance
import com.chuckerteam.chucker.R
import com.chuckerteam.chucker.databinding.ChuckerFragmentTransactionPayloadBinding
import com.chuckerteam.chucker.internal.data.entity.HttpTransaction
import com.chuckerteam.chucker.internal.support.Logger
import com.chuckerteam.chucker.internal.support.calculateLuminance
import com.chuckerteam.chucker.internal.support.combineLatest
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException

internal class TransactionPayloadFragment :
    Fragment(), SearchView.OnQueryTextListener {

    private val viewModel: TransactionViewModel by activityViewModels { TransactionViewModelFactory() }

    private val payloadType: PayloadType by lazy(LazyThreadSafetyMode.NONE) {
        arguments?.getSerializable(ARG_TYPE) as PayloadType
    }

    private val saveToFile = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        val transaction = viewModel.transaction.value
        if (uri != null && transaction != null) {
            lifecycleScope.launch {
                val result = saveToFile(payloadType, uri, transaction)
                val toastMessageId = if (result) {
                    R.string.chucker_file_saved
                } else {
                    R.string.chucker_file_not_saved
                }
                Toast.makeText(context, toastMessageId, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                requireContext(),
                R.string.chucker_save_failed_to_open_document,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private lateinit var payloadBinding: ChuckerFragmentTransactionPayloadBinding
    private val payloadAdapter = TransactionBodyAdapter()

    private var backgroundSpanColor: Int = Color.YELLOW
    private var foregroundSpanColor: Int = Color.RED

    private var isHighlightedMode: Boolean = false
    private var currentBodyString: String = ""
    private var hasJsonBody: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        payloadBinding = ChuckerFragmentTransactionPayloadBinding.inflate(
            inflater,
            container,
            false
        )
        return payloadBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        payloadBinding.payloadRecyclerView.apply {
            setHasFixedSize(true)
            adapter = payloadAdapter
        }

        payloadBinding.plainToggleButton.setOnClickListener {
            isHighlightedMode = !isHighlightedMode
            applyDisplayMode()
        }

        payloadBinding.expandAllButton.setOnClickListener {
            payloadBinding.jsonView.expandAll()
            payloadBinding.expandAllButton.visibility = View.GONE
            payloadBinding.collapseAllButton.visibility = View.VISIBLE
        }

        payloadBinding.collapseAllButton.setOnClickListener {
            payloadBinding.jsonView.collapseAll()
            payloadBinding.collapseAllButton.visibility = View.GONE
            payloadBinding.expandAllButton.visibility = View.VISIBLE
        }

        payloadBinding.nextHighlightFab.setOnClickListener { scrollToNextHighlight() }

        viewModel.transaction.combineLatest(viewModel.formatRequestBody).observe(
            viewLifecycleOwner,
            Observer { (transaction, formatRequestBody) ->
                if (transaction == null) return@Observer
                lifecycleScope.launch {
                    payloadBinding.loadingProgress.visibility = View.VISIBLE

                    val result = processPayload(payloadType, transaction, formatRequestBody)
                    if (result.isEmpty()) {
                        showEmptyState()
                    } else {
                        payloadAdapter.setItems(result)
                        showPayloadState()
                    }
                    // Invalidating menu, because we need to hide menu items for empty payloads
                    requireActivity().invalidateOptionsMenu()

                    payloadBinding.loadingProgress.visibility = View.GONE
                }
            }
        )
    }

    private fun showEmptyState() {
        payloadBinding.apply {
            emptyPayloadTextView.text = if (payloadType == PayloadType.RESPONSE) {
                getString(R.string.chucker_response_is_empty)
            } else {
                getString(R.string.chucker_request_is_empty)
            }
            emptyStateGroup.visibility = View.VISIBLE
            payloadRecyclerView.visibility = View.GONE
            jsonView.visibility = View.GONE
            plainToggleButton.visibility = View.GONE
            expandAllButton.visibility = View.GONE
            collapseAllButton.visibility = View.GONE
            nextHighlightFab.visibility = View.GONE
        }
    }

    private fun showPayloadState() {
        payloadBinding.apply {
            emptyStateGroup.visibility = View.GONE
            plainToggleButton.visibility = if (hasJsonBody) View.VISIBLE else View.GONE
        }
        applyDisplayMode()
    }

    private fun applyDisplayMode() {
        payloadBinding.apply {
            if (isHighlightedMode && hasJsonBody) {
                plainToggleButton.text = getString(R.string.chucker_show_plain)
                payloadRecyclerView.visibility = View.GONE
                jsonView.visibility = View.VISIBLE
                expandAllButton.visibility = View.GONE
                collapseAllButton.visibility = View.VISIBLE
                nextHighlightFab.visibility = View.GONE
                jsonView.setJson(prettyPrint(currentBodyString))
            } else {
                plainToggleButton.text = getString(R.string.chucker_highlight)
                payloadRecyclerView.visibility = View.VISIBLE
                jsonView.visibility = View.GONE
                expandAllButton.visibility = View.GONE
                collapseAllButton.visibility = View.GONE
            }
        }
    }

    private fun scrollToNextHighlight() {
        val recyclerView = payloadBinding.payloadRecyclerView
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val currentPosition = layoutManager.findFirstVisibleItemPosition()
        var target = payloadAdapter.findNextHighlightedItem(currentPosition + 1)
        if (target < 0) {
            target = payloadAdapter.findNextHighlightedItem(0)
        }
        if (target < 0) {
            Toast.makeText(requireContext(), R.string.chucker_no_matches_found, Toast.LENGTH_SHORT).show()
            return
        }
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics?): Float {
                return SCROLL_MILLIS_PER_INCH / (displayMetrics?.densityDpi?.toFloat() ?: 1f)
            }
        }
        smoothScroller.targetPosition = target
        layoutManager.startSmoothScroll(smoothScroller)
    }

    private fun updateFabVisibility() {
        val hasMatch = payloadAdapter.findNextHighlightedItem(0) >= 0
        payloadBinding.nextHighlightFab.visibility =
            if (hasMatch && !isHighlightedMode) View.VISIBLE else View.GONE
    }

    private fun prettyPrint(str: String): String {
        val gson = GsonInstance.get() ?: return str
        return try {
            val element = gson.fromJson(str, Any::class.java) ?: return str
            gson.toJson(element)
        } catch (ignore: JsonSyntaxException) {
            str
        }
    }

    private fun isJson(body: String): Boolean {
        if (body.isBlank()) return false
        val gson = GsonInstance.get() ?: return false
        return try {
            gson.fromJson(body, Any::class.java)
            true
        } catch (ignore: JsonSyntaxException) {
            false
        }
    }

    @SuppressLint("NewApi")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val transaction = viewModel.transaction.value

        if (shouldShowSearchIcon(transaction)) {
            val searchMenuItem = menu.findItem(R.id.search)
            searchMenuItem.isVisible = true
            val searchView = searchMenuItem.actionView as SearchView
            searchView.setOnQueryTextListener(this)
            searchView.setIconifiedByDefault(true)
        }

        if (shouldShowSaveIcon(transaction)) {
            menu.findItem(R.id.save_body).apply {
                isVisible = true
                setOnMenuItemClickListener {
                    createFileToSaveBody()
                    true
                }
            }
        }

        if (payloadType == PayloadType.REQUEST) {
            viewModel.doesRequestBodyRequireEncoding.observe(
                viewLifecycleOwner,
                { menu.findItem(R.id.encode_url).isVisible = it }
            )
        } else {
            menu.findItem(R.id.encode_url).isVisible = false
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun shouldShowSaveIcon(transaction: HttpTransaction?) = when {
        (payloadType == PayloadType.REQUEST) -> (0L != (transaction?.requestPayloadSize))
        (payloadType == PayloadType.RESPONSE) -> (0L != (transaction?.responsePayloadSize))
        else -> true
    }

    private fun shouldShowSearchIcon(transaction: HttpTransaction?) = when (payloadType) {
        PayloadType.REQUEST -> {
            (false == transaction?.isRequestBodyEncoded) && (0L != (transaction.requestPayloadSize))
        }
        PayloadType.RESPONSE -> {
            (false == transaction?.isResponseBodyEncoded) && (0L != (transaction.responsePayloadSize))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        backgroundSpanColor = ContextCompat.getColor(context, R.color.chucker_background_span_color)
        foregroundSpanColor = ContextCompat.getColor(context, R.color.chucker_foreground_span_color)
    }

    private fun createFileToSaveBody() {
        saveToFile.launch("$DEFAULT_FILE_PREFIX${System.currentTimeMillis()}")
    }

    override fun onQueryTextSubmit(query: String): Boolean = false

    override fun onQueryTextChange(newText: String): Boolean {
        if (newText.isNotBlank() && newText.length > NUMBER_OF_IGNORED_SYMBOLS) {
            payloadAdapter.highlightQueryWithColors(newText, backgroundSpanColor, foregroundSpanColor)
        } else {
            payloadAdapter.resetHighlight()
        }
        updateFabVisibility()
        return true
    }

    private suspend fun processPayload(
        type: PayloadType,
        transaction: HttpTransaction,
        formatRequestBody: Boolean
    ): MutableList<TransactionPayloadItem> {
        return withContext(Dispatchers.Default) {
            val result = mutableListOf<TransactionPayloadItem>()

            val headersString: String
            val isBodyEncoded: Boolean
            val bodyString: String

            if (type == PayloadType.REQUEST) {
                headersString = transaction.getRequestHeadersString(true)
                isBodyEncoded = transaction.isRequestBodyEncoded
                bodyString = if (formatRequestBody) {
                    transaction.getFormattedRequestBody()
                } else {
                    transaction.requestBody ?: ""
                }
            } else {
                headersString = transaction.getResponseHeadersString(true)
                isBodyEncoded = transaction.isResponseBodyEncoded
                bodyString = transaction.getFormattedResponseBody()
            }

            currentBodyString = bodyString
            hasJsonBody = !isBodyEncoded && isJson(bodyString)

            if (headersString.isNotBlank()) {
                result.add(
                    TransactionPayloadItem.HeaderItem(
                        HtmlCompat.fromHtml(
                            headersString,
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        )
                    )
                )
            }

            // The body could either be an image, plain text, decoded binary or not decoded binary.
            val responseBitmap = transaction.responseImageBitmap

            if (type == PayloadType.RESPONSE && responseBitmap != null) {
                result.add(TransactionPayloadItem.ImageItem(responseBitmap, responseBitmap.calculateLuminance()))
                return@withContext result
            }

            when {
                isBodyEncoded -> {
                    val text = requireContext().getString(R.string.chucker_body_omitted)
                    result.add(TransactionPayloadItem.BodyLineItem(SpannableStringBuilder.valueOf(text)))
                }
                bodyString.isBlank() -> {
                    val text = requireContext().getString(R.string.chucker_body_empty)
                    result.add(TransactionPayloadItem.BodyLineItem(SpannableStringBuilder.valueOf(text)))
                }
                else -> bodyString.lines().forEach {
                    result.add(TransactionPayloadItem.BodyLineItem(SpannableStringBuilder.valueOf(it)))
                }
            }

            return@withContext result
        }
    }

    private suspend fun saveToFile(type: PayloadType, uri: Uri, transaction: HttpTransaction): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        when (type) {
                            PayloadType.REQUEST -> {
                                transaction.requestBody?.byteInputStream()?.copyTo(fos)
                                    ?: throw IOException(TRANSACTION_EXCEPTION)
                            }
                            PayloadType.RESPONSE -> {
                                transaction.responseBody?.byteInputStream()?.copyTo(fos)
                                    ?: throw IOException(TRANSACTION_EXCEPTION)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Logger.error("Failed to save transaction to a file", e)
                return@withContext false
            }
            return@withContext true
        }
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val TRANSACTION_EXCEPTION = "Transaction not ready"

        private const val NUMBER_OF_IGNORED_SYMBOLS = 1
        private const val SCROLL_MILLIS_PER_INCH = 100f

        const val DEFAULT_FILE_PREFIX = "chucker-export-"

        fun newInstance(type: PayloadType): TransactionPayloadFragment =
            TransactionPayloadFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_TYPE, type)
                }
            }
    }
}
