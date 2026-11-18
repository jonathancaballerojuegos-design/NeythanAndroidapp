                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        ).build()
                    audioManager.requestAudioFocus(focusReq)
                }

                val params = Bundle()
                params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "NEYTHAN_UTTERANCE")
            } catch (_: Exception) { }
        }
    }

    private fun verificarPermisosEIniciar() {
        val permisos = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val faltantes = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltantes.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            iniciarServiciosYCargarWeb()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            iniciarServiciosYCargarWeb()
        }
    }

    private fun iniciarServiciosYCargarWeb() {
        try {
            val serviceIntent = Intent(this, NeythanBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) { }

        webView.loadUrl(SERVER_URL)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            Handler(Looper.getMainLooper()).postDelayed({
                simularToqueYActivarManosLibres()
            }, 800)
        }
    }

    inner class NeythanAndroidBridge {
        @JavascriptInterface
        fun speak(texto: String) {
            hablarNativo(texto)
        }

        @JavascriptInterface
        fun abrirSpotify(cancion: String) {
            runOnUiThread {
                try {
                    val query = Uri.encode(cancion)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (e: Exception) {
                    val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")
                    if (launch != null) startActivity(launch)
                }
            }
        }

        @JavascriptInterface
        fun activarBurbuja() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                } else {
                    val intent = Intent(this@MainActivity, FloatingService::class.java)
                    startService(intent)
                }
            }
        }

        @JavascriptInterface
        fun leerNotificaciones(): String {
            val prefs = getSharedPreferences("NeythanNotificaciones", Context.MODE_PRIVATE)
            return prefs.getString("ultimas", "No hay notificaciones recientes en redes sociales.") ?: ""
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
