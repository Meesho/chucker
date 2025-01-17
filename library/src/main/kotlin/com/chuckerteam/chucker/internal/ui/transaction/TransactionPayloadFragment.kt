package com.chuckerteam.chucker.internal.ui.transaction

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
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
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

internal class TransactionPayloadFragment :
    Fragment(), SearchView.OnQueryTextListener {

    private val viewModel: TransactionViewModel by activityViewModels { TransactionViewModelFactory() }

    var plain = true
    private lateinit var result: MutableList<TransactionPayloadItem>

    private val payloadType: PayloadType by lazy(LazyThreadSafetyMode.NONE) {
        arguments?.getSerializable(ARG_TYPE) as PayloadType
    }

    private val saveToFile = activity?.registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
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

    private lateinit var smoothScroller: LinearSmoothScroller

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
        smoothScroller = object : LinearSmoothScroller(context) {
            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }
        payloadBinding.scrollerFab.setOnClickListener {
            onScrollerFabClick()
        }
        payloadBinding.scrollerFab.backgroundTintList =
            AppCompatResources.getColorStateList(requireContext(), R.color.chucker_color_primary)
        payloadBinding.scrollerFab.imageTintList =
            AppCompatResources.getColorStateList(requireContext(), R.color.chucker_color_surface)
        return payloadBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        payloadBinding.payloadRecyclerView.apply {
            setHasFixedSize(true)
            adapter = payloadAdapter
        }

        viewModel.transaction.combineLatest(viewModel.formatRequestBody).observe(
            viewLifecycleOwner,
            Observer { (transaction, formatRequestBody) ->
                if (transaction == null) return@Observer
                lifecycleScope.launch {
                    payloadBinding.loadingProgress.visibility = View.VISIBLE

                        result = processPayload(payloadType, transaction, formatRequestBody)
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

        payloadBinding.expandBtn.setOnClickListener {
            expand()
        }

        payloadBinding.collapseBtn.setOnClickListener {
            collapse()
        }

        payloadBinding.plainHighlightedToggle.setOnClickListener {
            togglePlainHighlighted()
        }

    }

    private fun expand() {
        payloadBinding.jsonView.expandAll()
        payloadBinding.collapseBtn.visibility = View.VISIBLE
        payloadBinding.expandBtn.visibility = View.GONE
    }

    private fun collapse() {
        payloadBinding.jsonView.collapseAll()
        payloadBinding.expandBtn.visibility = View.VISIBLE
        payloadBinding.collapseBtn.visibility = View.GONE
    }

    private fun togglePlainHighlighted() {
        if (plain) {
            bindJson()
        } else {
            showPlainText()
        }
    }

    private fun bindJson() {
        plain = false
        payloadBinding.payloadRecyclerView.visibility = View.GONE
        payloadBinding.jsonView.visibility = View.VISIBLE
        payloadBinding.plainHighlightedToggle.setText(R.string.chucker_show_plain)
        expand()
        payloadBinding.collapseBtn.visibility = View.VISIBLE
    }

    private fun showPlainText() {
        plain = true
        if (result.isEmpty()) {
            showEmptyState()
        } else {
            payloadBinding.payloadRecyclerView.visibility = View.VISIBLE
        }
        payloadBinding.jsonView.visibility = View.GONE
        payloadBinding.plainHighlightedToggle.setText(R.string.chucker_highlight)
        payloadBinding.expandBtn.visibility = View.GONE
        payloadBinding.collapseBtn.visibility = View.GONE
    }

    private fun isJson(body: String): Boolean {
        return try {
            val element = JsonParser.parseString(body)
            element.isJsonObject || element.isJsonArray
        } catch (e: JsonSyntaxException) {
            false
        }
    }

    private fun prettyPrint(str: String): String? {
        val parser: JsonElement = JsonParser.parseString(str)
        return when {
            parser.isJsonObject -> {
                GsonInstance.get()?.toJson(parser.asJsonObject)
            }
            parser.isJsonArray -> {
                GsonInstance.get()?.toJson(parser.asJsonArray)
            }
            else -> {
                str
            }
        }
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
        }
    }

    private fun showPayloadState() {
        payloadBinding.apply {
            emptyStateGroup.visibility = View.GONE
            payloadRecyclerView.visibility = View.VISIBLE
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
        // SAF is not available on pre-Kit Kat so let's hide the icon.
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
        saveToFile?.launch("$DEFAULT_FILE_PREFIX${System.currentTimeMillis()}")
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    private fun onScrollerFabClick() {
        val layoutManager = payloadBinding.payloadRecyclerView.layoutManager as LinearLayoutManager
        var offset = layoutManager.findFirstVisibleItemPosition() + 1
        var index =
            payloadAdapter.findNextHighlightedItem(offset = offset)
        if (index == -1) {
            offset = 0
            index = payloadAdapter.findNextHighlightedItem(offset = offset)
        }
        if (index != -1) {
            smoothScroller.targetPosition = index + offset
            layoutManager.startSmoothScroll(smoothScroller)
        } else {
            Toast.makeText(context, R.string.chucker_no_matches_found, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onQueryTextChange(newText: String): Boolean {
        if (newText.isNotBlank() && newText.length > NUMBER_OF_IGNORED_SYMBOLS) {
            payloadAdapter.highlightQueryWithColors(
                newText,
                backgroundSpanColor,
                foregroundSpanColor
            )
            payloadBinding.scrollerFab.visibility = View.VISIBLE
        } else {
            payloadAdapter.resetHighlight()
            payloadBinding.scrollerFab.visibility = View.INVISIBLE
        }
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
                val bitmapLuminance = responseBitmap.calculateLuminance()
                result.add(TransactionPayloadItem.ImageItem(responseBitmap, bitmapLuminance))
            } else if (isBodyEncoded) {
                requireContext().getString(R.string.chucker_body_omitted).let {
                    result.add(TransactionPayloadItem.BodyLineItem(SpannableStringBuilder.valueOf(it)))
                }
            } else if (bodyString.isBlank()) {
                requireContext().getString(R.string.chucker_body_empty).let {
                    result.add(TransactionPayloadItem.BodyLineItem(SpannableStringBuilder.valueOf(it)))
                }
            } else {
                    withContext(Dispatchers.Main) {
                        if (isJson(bodyString)) {
                            payloadBinding.jsonView.bindJson(prettyPrint(bodyString))
                            payloadBinding.plainHighlightedToggle.visibility = View.VISIBLE
                        }
                    }

                    bodyString.lines().forEach {
                        result.add(
                            TransactionPayloadItem.BodyLineItem(
                                SpannableStringBuilder.valueOf(
                                    it
                                )
                            )
                        )
                    }
            }
            return@withContext result
        }
    }

    private suspend fun saveToFile(
        type: PayloadType,
        uri: Uri,
        transaction: HttpTransaction
    ): Boolean {
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
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                return@withContext false
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

        const val DEFAULT_FILE_PREFIX = "chucker-export-"

        fun newInstance(type: PayloadType): TransactionPayloadFragment =
            TransactionPayloadFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_TYPE, type)
                }
            }
    }
}
