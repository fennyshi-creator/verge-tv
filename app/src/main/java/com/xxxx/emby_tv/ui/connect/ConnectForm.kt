package com.xxxx.emby_tv.ui.connect

/**
 * Connect-screen and phone-pairing tokens. The phone page used to paint the
 * user's theme, which defaults to purple. Both surfaces now share this dark set.
 */
object ConnectForm {
    const val HTTP_PORT = "8096"
    const val HTTPS_PORT = "443"
    const val LEFT_FRACTION = 0.42f
    const val PAGE = 0xFF0C111B.toInt()
    const val INK = 0xFF141210.toInt()

    fun portFor(protocol: String, currentPort: String): String {
        val next = protocol.lowercase()
        val port = currentPort.trim()
        if (next == "https" && (port.isEmpty() || port == HTTP_PORT)) return HTTPS_PORT
        if (next == "http" && (port.isEmpty() || port == HTTPS_PORT)) return HTTP_PORT
        return port
    }
}

object PairingPage {
    const val background = "#0c111b"
    const val buttonBackground = "#f4f1ea"
    const val buttonText = "#141210"

    fun css(): String = """
        body {
            font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            padding: 0;
            margin: 0;
            background: $background;
            color: #f4f1ea;
            display: flex;
            justify-content: center;
            align-items: flex-start;
            min-height: 100vh;
        }
        .container {
            background: rgba(20, 18, 16, 0.78);
            border: 1px solid rgba(255, 255, 255, 0.12);
            border-radius: 24px;
            padding: 32px 22px;
            width: 90%;
            max-width: 420px;
            margin-top: 28px;
            margin-bottom: 28px;
            box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
        }
        h2 { text-align: center; margin: 0 0 28px; font-weight: 500; letter-spacing: 0.4px; }
        label { display: block; margin-bottom: 8px; font-size: 14px; color: rgba(244, 241, 234, 0.62); }
        .input-row {
            display: flex; gap: 8px; margin-bottom: 18px; align-items: center;
        }
        input, .protocol-btn {
            padding: 12px;
            box-sizing: border-box;
            background: rgba(12, 17, 27, 0.72);
            border: 1px solid rgba(255, 255, 255, 0.14);
            border-radius: 12px;
            color: #f4f1ea;
            font-size: 16px;
        }
        input { width: 100%; margin-bottom: 18px; }
        .input-row input { margin-bottom: 0; }
        input::placeholder { color: rgba(244, 241, 234, 0.4); }
        input:focus, .protocol-btn:focus {
            border-color: rgba(255, 255, 255, 0.85);
            outline: none;
        }
        .protocol-btn {
            min-width: 84px;
            cursor: pointer;
        }
        .protocol { flex: 0 0 84px; }
        .host { flex: 1; }
        .port { flex: 0 0 84px; }
        button[type="submit"], #btn-submit {
            width: 100%;
            padding: 14px;
            background: $buttonBackground;
            color: $buttonText;
            border: none;
            border-radius: 12px;
            font-size: 16px;
            font-weight: 650;
            cursor: pointer;
        }
        .server-list { margin-bottom: 18px; }
        .server-item {
            display: flex; align-items: center; justify-content: space-between; gap: 8px;
            width: 100%; padding: 12px 14px; margin-bottom: 8px;
            background: rgba(255, 255, 255, 0.06);
            color: #f4f1ea;
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 12px;
            cursor: pointer;
            box-sizing: border-box;
        }
        .server-name { font-size: 15px; font-weight: 650; }
        .server-addr { font-size: 12px; color: rgba(244, 241, 234, 0.55); }
    """.trimIndent()

    fun successDocument(enTitle: String, enDesc: String, zhTitle: String, zhDesc: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Success</title>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta charset="UTF-8">
            <style>
                body {
                    font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    padding: 0; margin: 0;
                    background: $background;
                    color: #f4f1ea;
                    display: flex; justify-content: center; align-items: center;
                    min-height: 100vh;
                }
                .container {
                    background: rgba(20, 18, 16, 0.78);
                    border: 1px solid rgba(255, 255, 255, 0.12);
                    padding: 36px 24px; border-radius: 24px;
                    width: 90%; max-width: 420px; text-align: center;
                    box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
                }
                h2 { margin: 0 0 12px; font-weight: 500; }
                p { color: rgba(244, 241, 234, 0.72); margin: 0; }
                .icon { font-size: 48px; margin-bottom: 12px; color: $buttonBackground; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="icon">✓</div>
                <h2 id="success-title">$enTitle</h2>
                <p id="success-desc">$enDesc</p>
            </div>
            <script>
                if (navigator.language.startsWith('zh')) {
                    document.getElementById('success-title').innerText = '$zhTitle';
                    document.getElementById('success-desc').innerText = '$zhDesc';
                }
            </script>
        </body>
        </html>
    """.trimIndent()

    fun usesLegacyPurple(css: String): Boolean {
        val lower = css.lowercase()
        return lower.contains("--theme-primary") ||
            lower.contains("#e040fb") ||
            lower.contains("#7c4dff") ||
            lower.contains("purple")
    }
}
