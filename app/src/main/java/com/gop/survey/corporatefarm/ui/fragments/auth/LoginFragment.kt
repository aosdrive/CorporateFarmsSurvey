package com.gop.survey.corporatefarm.ui.fragments.auth

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.gop.survey.corporatefarm.R
import com.gop.survey.corporatefarm.common.Resource
import com.gop.survey.corporatefarm.common.Utility
import com.gop.survey.corporatefarm.databinding.FragmentLoginBinding
import com.gop.survey.corporatefarm.presentation.login.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.gop.survey.corporatefarm.ui.activities.MenuActivity
import com.gop.survey.corporatefarm.common.Constants
import com.gop.survey.corporatefarm.presentation.util.DialogUtil
import com.gop.survey.corporatefarm.presentation.util.ToastUtil
import javax.inject.Inject
import android.view.animation.AnimationUtils


@AndroidEntryPoint
class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        showStatusBar()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
        hideStatusBar()
        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            btnLogin.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    val username = getUsername()?.trim()
                    username?.let {
                        val password = getPassword()
                        password?.let {
                            viewModel.login(username, password)
                        }
                    }
                } else {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
            }

            tvForgotPassword.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    if (isAdded) {
                        findNavController().navigate(R.id.action_loginFragment_to_forgotFragment)
                    }
                } else {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
            }
        }

        binding.govBrandingBar.startAnimation(
            AnimationUtils.loadAnimation(context, R.anim.fade_in_delayed)
        )
        binding.heroSection.startAnimation(
            AnimationUtils.loadAnimation(context, R.anim.slide_in_fade)
        )
        binding.loginCard.startAnimation(
            AnimationUtils.loadAnimation(context, R.anim.scale_in)
        )

        binding.etUsername.setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.02f else 1.0f
            binding.tilUsername.animate().scaleX(scale).scaleY(scale).setDuration(200).start()
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.02f else 1.0f
            binding.tilPassword.animate().scaleX(scale).scaleY(scale).setDuration(200).start()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.login.collect { it ->
                    when (it) {
                        is Resource.Loading -> {
                            binding.btnLogin.startAnimation()
                        }

//                        is Resource.Success -> {
//                            binding.btnLogin.revertAnimation()
//
//                            it.data?.let {
//                                if (it.changePassword) {
//
//                                    Toast.makeText(
//                                        activity,
//                                        "OTP has been sent to your registered mobile and email",
//                                        Toast.LENGTH_LONG
//                                    ).show()
//
//                                    val action =
//                                        LoginFragmentDirections.actionLoginFragmentToOtpVerificationFragment(
//                                            cnic = it.cnic,
//                                        )
//
//                                    findNavController().navigate(action)
//
//                                } else {
//
//                                    sharedPreferences.edit()
//                                        .putInt(
//                                            Constants.SHARED_PREF_LOGIN_STATUS,
//                                            Constants.LOGIN_STATUS_ACTIVE
//                                        )
//                                        .putLong(Constants.SHARED_PREF_USER_ID, it.userID)
//                                        .putString(Constants.SHARED_PREF_TOKEN, it.token)
//                                        .putString(Constants.SHARED_PREF_USER_CNIC, it.cnic)
//                                        .putString(Constants.SHARED_PREF_USER_NAME, it.name)
//                                        .putLong(
//                                            Constants.SHARED_PREF_USER_ASSIGNED_MOUZA,
//                                            it.mauzaId
//                                        )
//                                        .putString(
//                                            Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME,
//                                            it.mauzaName
//                                        )
//                                        .apply()
//
//                                    Intent(requireActivity(), MenuActivity::class.java).apply {
//                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
//                                        startActivity(this)
//                                        requireActivity().finish()
//                                    }
//                                }
//                            } ?: Toast.makeText(
//                                activity,
//                                "Data not found, contact administration",
//                                Toast.LENGTH_LONG
//                            ).show()
//                            binding.btnLogin.revertAnimation()
//
//                        }

                        is Resource.Success -> {
                            binding.btnLogin.revertAnimation()
                            it.data?.let { loginData ->
                                sharedPreferences.edit()
                                    .putInt(Constants.SHARED_PREF_LOGIN_STATUS, Constants.LOGIN_STATUS_ACTIVE)
                                    .putLong(Constants.SHARED_PREF_USER_ID, loginData.userId)
                                    .putString(Constants.SHARED_PREF_TOKEN, loginData.token)
                                    .putString(Constants.SHARED_PREF_USER_NAME, loginData.name)
                                    .apply()

                                ToastUtil.showShort(requireContext(),"Login successful! Welcome ${loginData.name}")
                                Intent(requireActivity(), MenuActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(this)
                                    requireActivity().finish()
                                }

                            } ?:ToastUtil.showShort(requireContext(),"Login failed: no user data")
                        }


                        is Resource.Error -> {
                            binding.btnLogin.revertAnimation()

                            val errorMessage = it.message ?: "Login failed. Please try again."

                            Log.d("LoginFragment", "Login error: $errorMessage")

                            // Check if it's the "already logged in" error
                            if (errorMessage.contains("already logged in", ignoreCase = true) ||
                                errorMessage.contains("another device", ignoreCase = true) ||
                                errorMessage.contains("other machine", ignoreCase = true)) {

                                // Show specific dialog for already logged in
                                DialogUtil.showAlreadyLoggedInDialog(requireContext())
                            } else {
                                // Show generic error dialog
                                DialogUtil.showErrorDialog(
                                    requireContext(),
                                    "Login Error",
                                    errorMessage
                                )
                            }
                        }
                        else -> Unit

                    }
                }
            }
        }
    }

    private fun hideStatusBar() {
        val window = requireActivity().window

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
            @Suppress("DEPRECATION")
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }
    private fun showStatusBar() {
        val window = requireActivity().window

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }
    private fun getCNIC(): String? {
        val cnic = binding.etUsername.text.toString().trim()

        if (cnic.isEmpty()) {
            binding.etUsername.apply {
                error = resources.getString(R.string.error_username_field_empty)
                requestFocus()
            }
            return null
        }

        if (cnic.length != 13) {
            binding.etUsername.apply {
                error = resources.getString(R.string.not_a_valid_cnic)
                requestFocus()
            }
            return null
        }

        return cnic
    }

    private fun getUsername(): String? {
        val username = binding.etUsername.text.toString().trim()

        if (username.isEmpty()) {
            binding.etUsername.apply {
                error = resources.getString(R.string.error_username_field_empty)
                requestFocus()
            }
            return null
        }

        return username
    }

    private fun getPassword(): String? {
        val password = binding.etPassword.text.toString()

        if (password.isEmpty()) {
            binding.etPassword.apply {
                error = resources.getString(R.string.error_password_field_empty)
                requestFocus()
            }
            return null
        }

        if (password.length < 8) {
            binding.etPassword.apply {
                error = resources.getString(R.string.invalid_password)
                requestFocus()
            }
            return null
        }
        return password
    }
}