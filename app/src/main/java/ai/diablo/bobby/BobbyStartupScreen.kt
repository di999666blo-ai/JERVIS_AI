package ai.diablo.bobby

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BobbyStartupScreen() {
    val transition = rememberInfiniteTransition(label = "bobby-startup")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "robot-pulse"
    )
    val progress by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "loading-progress"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF240813), Color(0xFF090609), Color(0xFF030303)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                Modifier.size(178.dp).scale(pulse)
                    .background(Brush.radialGradient(listOf(Color(0x66FF1744), Color(0x221B0A12), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Box(
                        Modifier.width(96.dp).height(86.dp)
                            .background(Brush.verticalGradient(listOf(Color(0xFF35313A), Color(0xFF09090D))), RoundedCornerShape(30.dp))
                            .border(2.dp, Color(0xFFFF1744), RoundedCornerShape(30.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                Box(Modifier.size(13.dp).background(Color(0xFFFF315D), CircleShape))
                                Box(Modifier.size(13.dp).background(Color(0xFFFF315D), CircleShape))
                            }
                            Box(Modifier.width(34.dp).height(3.dp).background(Color(0xFFFF1744), RoundedCornerShape(3.dp)))
                        }
                    }
                    Box(Modifier.width(24.dp).height(13.dp).background(Color(0xFFB51D50), RoundedCornerShape(5.dp)))
                    Box(
                        Modifier.width(126.dp).height(28.dp)
                            .background(Brush.horizontalGradient(listOf(Color(0xFF17131B), Color(0xFF5B1028), Color(0xFF17131B))), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                            .border(1.dp, Color(0x99FF1744), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    )
                }
            }
            Text("BOBBY", color = Color(0xFFFF1744), fontSize = 32.sp, letterSpacing = 7.sp)
            Text("ADVANCED PERSONAL AI", color = Color.LightGray, fontSize = 11.sp, letterSpacing = 2.5.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.width(220.dp).height(4.dp),
                color = Color(0xFFFF1744),
                trackColor = Color(0xFF3B1724)
            )
            Text("INITIALIZING NEURAL SYSTEMS", color = Color(0xFFBCAEB4), fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text("VOICE  •  MEMORY  •  VISION", color = Color(0xFF795D68), fontSize = 9.sp, letterSpacing = 1.3.sp)
        }
    }
}
