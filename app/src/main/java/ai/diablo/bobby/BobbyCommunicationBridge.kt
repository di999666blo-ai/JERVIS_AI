package ai.diablo.bobby

import android.content.Context
import android.content.Intent

/**
 * Uses Android's system Sharesheet so the user chooses the destination app
 * and explicitly confirms every outgoing message or post.
 */
object BobbyCommunicationBridge {
    fun shareText(context: Context, text: String, title: String = "Share with…") {
        require(text.isNotBlank()) { "There is nothing to share." }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun composeEmail(context: Context, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Choose email app"))
    }
}
