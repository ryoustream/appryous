package com.ryou.appryous.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ryou.appryous.BuildConfig
import com.ryou.appryous.container
import com.ryou.appryous.data.repository.GistException
import com.ryou.appryous.databinding.ActivitySplashBinding
import com.ryou.appryous.ui.main.MainActivity
import com.ryou.appryous.util.hide
import com.ryou.appryous.util.show
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvVersion.text = "v${BuildConfig.APP_VERSION} ${BuildConfig.APP_CODENAME}"
        binding.btnRetry.setOnClickListener { resolveServer() }

        resolveServer()
    }

    private fun resolveServer() {
        showLoading()
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = getString(com.ryou.appryous.R.string.splash_resolving)

                // 1. Resolve URL dari Gist
                val url = container.gistRepo.resolveUrl()

                // 2. Ping backend — pastikan online
                binding.tvStatus.text = getString(com.ryou.appryous.R.string.splash_connecting)
                val status = container.backendRepo.ping()

                // 3. Navigasi ke MainActivity
                delay(300) // brief pause agar user lihat "terhubung"
                goToMain()

            } catch (e: GistException) {
                showError(getString(com.ryou.appryous.R.string.splash_failed) + "\n${e.message}")
            } catch (e: Exception) {
                showError(getString(com.ryou.appryous.R.string.splash_failed) + "\n${e.message}")
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val opts = android.app.ActivityOptions.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
            startActivity(intent, opts.toBundle())
        } else {
            startActivity(intent)
            @Suppress("DEPRECATION")
            @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }

    private fun showLoading() {
        binding.progress.show()
        binding.layoutError.hide()
        binding.tvStatus.text = getString(com.ryou.appryous.R.string.splash_connecting)
    }

    private fun showError(msg: String) {
        binding.progress.hide()
        binding.layoutError.show()
        binding.tvStatus.text = msg
    }
}
