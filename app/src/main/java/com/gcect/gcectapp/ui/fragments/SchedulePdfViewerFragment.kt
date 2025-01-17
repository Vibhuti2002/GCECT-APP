package com.gcect.gcectapp.ui.fragments

import android.Manifest
import android.app.ProgressDialog
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.downloader.OnDownloadListener
import com.downloader.PRDownloader
import com.gcect.gcectapp.R
import com.gcect.gcectapp.databinding.PdfViewerWithDownloadBinding
import com.gcect.gcectapp.model.DownloadViewListModel
import com.gcect.gcectapp.viewmodels.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class SchedulePdfViewerFragment() : Fragment() {
    private lateinit var binding: PdfViewerWithDownloadBinding
    private val viewModel22: SemScheduleViewModel22 by activityViewModels()
    private val viewModel21: SemScheduleViewModel21 by activityViewModels()
    private val viewModel20: SemScheduleViewModel20 by activityViewModels()
    private var list: List<DownloadViewListModel>? = null
    private lateinit var sharedViewModel: MainActivityViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.pdf_viewer_with_download, container, false)

        PRDownloader.initialize(requireActivity().applicationContext)
        sharedViewModel = ViewModelProvider(
            requireActivity(),
            MainActivityViewModelFactory()
        ).get(MainActivityViewModel::class.java)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.progress.visibility = View.VISIBLE
        Log.d("chkListEven", sharedViewModel.examScheduleYear.toString())
        list = when (sharedViewModel.examScheduleYear) {
            22 -> viewModel22.getList(viewModel22.isEvenList)
            21 -> viewModel21.getList(viewModel21.isEvenList)
            20 -> viewModel20.getList(viewModel20.isEvenList)
            else -> null
        }
        if (sharedViewModel.examScheduleYear == 22) {
            list = viewModel22.oddSemList
        }
        Log.d("chkListEven", list.toString())
        val pos = when (sharedViewModel.examScheduleYear) {
            22 -> viewModel22.listPos
            21 -> viewModel21.listPos
            20 -> viewModel20.listPos
            else -> null
        }
        CoroutineScope(Dispatchers.Main).launch {
            // Retrieve PDF File from URL only when examScheduleYear is not null
            pos?.let { pos ->
                list?.let { list ->
                    retrivePDFFromUrl(list[pos].pdfUrl)
                }
            }

        }

        binding.flBtnDownload.setOnClickListener {
            // Check PDF File download Permission only when examScheduleYear is not null
            pos?.let { pos ->
                list?.let { list ->
                    checkDownloadPermission(list[pos].pdfUrl)
                }
            }
        }
        onBackPressed()
    }


    /**
     * downloading pdf file from the link and passing the inputstream
     * to the showPdfFromStream()
     */
    private suspend fun retrivePDFFromUrl(url: String) {
        val result = withContext(Dispatchers.IO) {
            // we are using inputstream
            // for getting out PDF.
            var inputStream: InputStream? = null
            try {
                val url = URL(url)
                // below is the step where we are
                // creating our connection.
                val urlConnection: HttpURLConnection = url.openConnection() as HttpsURLConnection
                if (urlConnection.responseCode == 200) {
                    // response is success.
                    // we are getting input stream from url
                    // and storing it in our variable.
                    inputStream = BufferedInputStream(urlConnection.inputStream)
                }
            } catch (e: IOException) {
                // this is the method
                // to handle errors.
                e.printStackTrace()
            }
            inputStream
        }

        showPdfFromStream(result)
        binding.progress.visibility = View.GONE
    }

    /**
     * Showing the pdf to the pdf viewer
     */
    private fun showPdfFromStream(inputStream: InputStream?) {
        binding.pdfView.fromStream(inputStream)
            .password(null)
            .defaultPage(0)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .onPageError { page, _ ->
                Toast.makeText(
                    context,
                    getString(R.string.error_at_page_number, page), Toast.LENGTH_LONG
                ).show()
            }
            .load()
    }

    private fun onBackPressed() {
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Do custom work here
                    navigate(
                        R.id.examScheduleFragment
                    )
                }
            }
            )
    }

    /**
     * For handling navigation
     */
    private fun navigate(navFragId: Int) {
        val id = findNavController().currentDestination?.id
        id?.let { findNavController().popBackStack(it, true) }
        findNavController().navigate(navFragId)
    }

    private fun checkDownloadPermission(url: String) {
        Dexter.withContext(requireActivity())
            .withPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        downloadPdf(url)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.allow_download_permission_message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest?>?,
                    token: PermissionToken?
                ) { /* ... */
                }
            }).check()
    }

    private fun downloadPdf(url: String) {
        val pd = ProgressDialog(requireContext())
        pd.setMessage(getString(R.string.downloading))
        pd.setCancelable(false)
        pd.show()
        val file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        PRDownloader.download(url, file.path, URLUtil.guessFileName(url, null, null))
            .build()
            .setOnStartOrResumeListener { }
            .setOnPauseListener { }
            .setOnCancelListener { }
            .setOnProgressListener {
                val percentage = it.currentBytes * 100 / it.totalBytes
                pd.setMessage(getString(R.string.download_percentage_completed, percentage))
            }
            .start(object : OnDownloadListener {
                override fun onDownloadComplete() {
                    pd.cancel()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.download_complete),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(error: com.downloader.Error?) {
                    pd.cancel()
                }
            })
    }
}
