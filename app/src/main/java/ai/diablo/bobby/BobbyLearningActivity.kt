package ai.diablo.bobby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

class BobbyLearningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = BobbyTraceStore(this)
        setContent {
            val traces = remember { mutableStateOf(store.recent(60)) }
            val performance = remember { mutableStateOf(store.performance()) }
            val bg = Color(0xFF050505)
            val accent = Color(0xFFFF1744)
            MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = Color(0xFF171017), primary = accent)) {
                Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("BOBBY LEARNING", color = Color.White, fontSize = 20.sp)
                            Text("Local performance traces • prompts and API keys are not stored", color = Color.LightGray, fontSize = 11.sp)
                        }
                        TextButton(onClick = { store.clear(); traces.value = emptyList(); performance.value = emptyList() }) { Text("CLEAR", color = accent) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("MODEL PERFORMANCE", color = accent)
                    if (performance.value.isEmpty()) Text("No AI request history yet.", color = Color.LightGray, modifier = Modifier.padding(12.dp))
                    performance.value.forEach { row ->
                        Surface(color = Color(0xFF171017), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(row.model, color = Color.White)
                                Text("${row.successRate}% success • ${row.requests} requests • avg ${row.averageLatencyMs} ms", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("RECENT ACTIVITY", color = accent)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(traces.value) { row ->
                            Surface(color = Color(0xFF171017), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("${row.taskType.uppercase()} • ${if (row.success) "SUCCESS" else "FAILED"}", color = if (row.success) Color(0xFF62D6AE) else Color(0xFFFF8A80))
                                    Text("${row.model} • ${row.latencyMs} ms", color = Color.LightGray, fontSize = 12.sp)
                                    Text(DateFormat.getDateTimeInstance().format(Date(row.timestamp)), color = Color.Gray, fontSize = 10.sp)
                                    if (row.failureType.isNotBlank()) Text(row.failureType, color = Color(0xFFFF8A80), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
