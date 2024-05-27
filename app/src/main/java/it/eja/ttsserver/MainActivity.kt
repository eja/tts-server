// Copyright (C) 2024 by Ubaldo Porcheddu <ubaldo@eja.it>

package it.eja.ttsserver

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.EditText
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import kotlin.concurrent.thread
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import android.widget.Toast
import java.net.NetworkInterface
import java.net.SocketException
import java.net.Inet4Address

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var sharedPreferences: SharedPreferences
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        tts = TextToSpeech(this, this)
        sharedPreferences = getSharedPreferences("TTS_SERVER_PREFS", Context.MODE_PRIVATE)

        val portEditText = findViewById<EditText>(R.id.port)
        val saveButton = findViewById<Button>(R.id.save)
        val urlText = findViewById<TextView>(R.id.url)

        val savedPort = sharedPreferences.getInt("port", 35248)
        portEditText.setText(savedPort.toString())

        saveButton.setOnClickListener {
            val port = portEditText.text.toString().toIntOrNull()
            if (port != null && port > 1024 && port < 65535) {
                sharedPreferences.edit().putInt("port", port).apply()
                restartServer(port)
                urlText.setText("http://"+getIPAddress()+":"+port)
                Toast.makeText(this, "Port updated and server restarted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show()
            }
        }
        urlText.setText("http://"+getIPAddress()+":"+savedPort)
        startServer(savedPort)
    }

    override fun onInit(status: Int) {}

    private fun startServer(port: Int) {
        serverThread = thread {
            try {
                serverSocket = ServerSocket(port)
                Log.i("MainActivity", "Server started on port $port")
                while (!Thread.currentThread().isInterrupted) {
                    val clientSocket = serverSocket?.accept()
                    if (clientSocket != null) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "Error starting server", e)
            }
        }
    }

    private fun restartServer(port: Int) {
        serverThread?.interrupt()
        serverSocket?.close()
        startServer(port)
    }

    private fun handleClient(clientSocket: Socket) {
        thread {
            try {
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val requestLine = reader.readLine()
                if (requestLine != null && requestLine.startsWith("GET")) {
                    val params = requestLine.split(" ")[1].split("?").getOrNull(1)
                    if (params != null) {
                        val text = parseTextParameter(params)
                        val locale = parseLocaleParameter(params)

                        if (text != "") {
                            synthesizeTextToAudio(text, locale)
                            while (!synthesisDone) {
                                Thread.sleep(100)
                            }
                        } else {
                            sendErrorResponse(clientSocket, 400, "Bad Request")
                        }
                        sendAudioResponse(clientSocket, audioData)
                    } else {
                        sendWebResponse(clientSocket)
                    }
                } else {
                    sendErrorResponse(clientSocket, 400, "Bad Request")
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "Error handling client", e)
            } finally {
                clientSocket.close()
            }
        }
    }

    private fun parseTextParameter(params: String): String? {
        return params.split("&").find { it.startsWith("text=") }?.substringAfter("text=")?.let {
            URLDecoder.decode(it, "UTF-8")
        }
    }

    private fun parseLocaleParameter(params: String): Locale {
        return params.split("&").find { it.startsWith("locale=") }?.substringAfter("locale=")?.let { localeCode ->
            val parts = localeCode.split("_")
            if (parts.size == 2) {
                Locale(parts[0], parts[1])
            } else {
                Locale(localeCode)
            }
        } ?: Locale.getDefault()
    }

    private fun sendWebResponse(clientSocket: Socket) {
        try {
            val output = clientSocket.getOutputStream()
            val writer = BufferedWriter(OutputStreamWriter(output))

            val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body><div align="center"><form action="?">
                <p><input name="locale" placeholder="language..."></p>
                <p><textarea name="text" placeholder="text to speech..." cols="40" rows="5"></textarea></p>
                <p><input type="submit"></p>
            </div></form></body>
            </html>
        """.trimIndent()

            writer.write("HTTP/1.1 200 OK\r\n")
            writer.write("Content-Type: text/html\r\n")
            writer.write("Content-Length: ${htmlContent.toByteArray().size}\r\n")
            writer.write("\r\n")
            writer.write(htmlContent)
            writer.flush()
        } catch (e: IOException) {
            Log.e("MainActivity", "Error sending response", e)
        } finally {
            clientSocket.close()
        }
    }

    private fun sendAudioResponse(clientSocket: Socket, audioData: ByteArray) {
        try {
            val output = clientSocket.getOutputStream()
            val writer = BufferedWriter(OutputStreamWriter(output))

            writer.write("HTTP/1.1 200 OK\r\n")
            writer.write("Content-Type: audio/wav\r\n")
            writer.write("Content-Length: ${audioData.size}\r\n")
            writer.write("\r\n")
            writer.flush()

            output.write(audioData)
            output.flush()
        } catch (e: IOException) {
            Log.e("MainActivity", "Error sending response", e)
        } finally {
            clientSocket.close()
        }
    }

    private fun sendErrorResponse(clientSocket: Socket, code: Int, message: String) {
        try {
            val output = clientSocket.getOutputStream()
            val writer = BufferedWriter(OutputStreamWriter(output))

            writer.write("HTTP/1.1 $code $message\r\n")
            writer.write("Content-Length: 0\r\n")
            writer.write("\r\n")
            writer.flush()
        } catch (e: IOException) {
            Log.e("MainActivity", "Error sending error response", e)
        } finally {
            clientSocket.close()
        }
    }

    override fun onDestroy() {
        if (this::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        serverThread?.interrupt()
        serverSocket?.close()
        super.onDestroy()
    }

    private var synthesisDone = false
    private var audioData = ByteArray(0)

    private fun synthesizeTextToAudio(text: String?, locale: Locale): ByteArray {
        val audioFile = File.createTempFile("tts_", ".wav", cacheDir)
        val params = HashMap<String, String>()

        tts.language = locale
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "ttsId"

        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("MainActivity", "Synthesis started")
            }

            override fun onDone(utteranceId: String?) {
                Log.d("MainActivity", "Synthesis done")
                try {
                    val fis = FileInputStream(audioFile)
                    val buffer = ByteArrayOutputStream()
                    var nRead: Int
                    val data = ByteArray(1024)
                    while (fis.read(data, 0, data.size).also { nRead = it } != -1) {
                        buffer.write(data, 0, nRead)
                    }
                    buffer.flush()
                    fis.close()
                    audioData = buffer.toByteArray()
                    synthesisDone = true
                } catch (e: IOException) {
                    Log.e("MainActivity", "Error reading audio file", e)
                    synthesisDone = true
                }
            }

            override fun onError(utteranceId: String?) {
                Log.e("MainActivity", "Text synthesis failed")
                synthesisDone = true
            }
        }

        tts.setOnUtteranceProgressListener(listener)
        synthesisDone = false
        tts.synthesizeToFile(text, params, audioFile.absolutePath)
        return ByteArray(0)
    }

    private fun getIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (ex: SocketException) {
            ex.printStackTrace()
        }
        return null
    }

}