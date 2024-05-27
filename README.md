# TTS Server

TTS Server is an Android application that functions as an HTTP server, enabling the usage of the Android Text-to-Speech (TTS) engine to generate speech from text input via HTTP requests.

## Features

- Provides an HTTP server interface to utilize the Android Text-to-Speech engine.
- Supports generating speech from text input received via HTTP requests.
- Easy integration into existing Android projects or applications.
- Lightweight and efficient implementation.

## Getting Started

To get started with TTS Server, follow these steps:

1. Clone this repository:

   ```bash
   git clone https://github.com/eja/tts-server.git
   ```

2. Open the project in Android Studio.

3. Build and run the project on your Android device or emulator.

4. Once the application is running, it will start the HTTP server automatically.

5. Send the requests with the text you want to convert to speech to the server endpoint.

## Usage

To use TTS Server in your project, follow these steps:

1. Ensure that the TTS Server application is installed and running on your Android device.

2. Send HTTP GET requests to the server endpoint `http://<your_device_ip>:<port>` with the text you want to convert to speech.

3. The server will respond with the synthesized speech audio.

4. Integrate the HTTP request mechanism into your application to dynamically generate speech from text input.

## Example

```bash
curl "http://<your_device_ip>:<port>/?text=test&locale=it-IT" --output output.wav
```

This command sends a request with the text "test" and locale "it-IT" to the TTS Server running on your device and saves the synthesized speech audio to `output.wav`.

## License

TTS Server is licensed under the GNU General Public License v3.0 (GPL-3.0). See the [LICENSE](LICENSE) file for details.
