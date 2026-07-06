package com.gamecenter.app.wrongbook.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.gamecenter.app.wrongbook.databinding.FragmentFullscreenImageBinding
import java.io.File

class FullscreenImageDialogFragment : DialogFragment() {

    private var _binding: FragmentFullscreenImageBinding? = null
    private val binding get() = _binding!!

    private var imagePath: String = ""
    private var scaleFactor = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        imagePath = arguments?.getString(ARG_IMAGE_PATH) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFullscreenImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        if (imagePath.isNotBlank() && File(imagePath).exists()) {
            binding.ivFullImage.setImageURI(Uri.fromFile(File(imagePath)))
        }

        // 缩放手势识别
        val scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.8f, 5.0f)
                binding.ivFullImage.scaleX = scaleFactor
                binding.ivFullImage.scaleY = scaleFactor
                return true
            }
        })

        // 绑定手势触碰监听
        binding.root.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IMAGE_PATH = "image_path"

        fun newInstance(imagePath: String): FullscreenImageDialogFragment {
            val fragment = FullscreenImageDialogFragment()
            val args = Bundle().apply {
                putString(ARG_IMAGE_PATH, imagePath)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
