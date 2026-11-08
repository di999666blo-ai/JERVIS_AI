package ai.diablo.bobby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun BobbyOnboarding(
    initial: BobbyProfile?,
    onSave: (BobbyProfile) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "") }
    var birthday by remember { mutableStateOf(initial?.birthday ?: "") }
    var city by remember { mutableStateOf(initial?.city ?: "") }
    var dayTitle by remember { mutableStateOf("") }
    var dayDate by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { onDismiss?.invoke() }) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(26.dp), color = Color(0xFF13070D),
            border = BorderStroke(1.dp, Color(0xFFFF1744))
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("LET'S GET ACQUAINTED", color = Color(0xFFFF1744), style = MaterialTheme.typography.headlineSmall)
                Text("I'm Bobby 😊 Your buddy, assistant and occasional terrible-joke supplier. Tell me a little about you.", color = Color.White)
                OutlinedTextField(name, { name = it }, label = { Text("What should I call you?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("Your age") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(birthday, { birthday = it }, label = { Text("Birthday (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(city, { city = it }, label = { Text("City for daily weather") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider(color = Color(0xFFFF1744).copy(alpha = .4f))
                Text("IMPORTANT DAYS 📅", color = Color(0xFFFF1744))
                OutlinedTextField(dayTitle, { dayTitle = it }, label = { Text("Event name (e.g. Mum's birthday)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dayDate, { dayDate = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Your profile and dates are stored locally on this phone. Bobby will show reminders when you open the app. Automatic background notifications are not enabled yet.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B))
                Button(onClick = {
                    val years = age.toIntOrNull()
                    if (name.isBlank() || years !in 1..120 || runCatching { java.time.LocalDate.parse(birthday) }.isFailure) {
                        error = "Enter your name, age (1–120), and a valid birthday as YYYY-MM-DD."
                    } else if (dayTitle.isNotBlank() && runCatching { java.time.LocalDate.parse(dayDate) }.isFailure) {
                        error = "The important date must use YYYY-MM-DD."
                    } else {
                        val days = (initial?.importantDays ?: emptyList()).toMutableList()
                        if (dayTitle.isNotBlank()) days.add(BobbyImportantDay(dayTitle.trim(), dayDate))
                        onSave(BobbyProfile(name.trim(), years!!, birthday, city.trim(), days))
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))) {
                    Text("SAVE PROFILE & CONTINUE")
                }
                if (onDismiss != null) TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("NOT NOW", color = Color.LightGray) }
            }
        }
    }
}
