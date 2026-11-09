package ai.diablo.bobby

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class BobbyAvatarLook(
    val expression: Int = 0, val outfit: Int = 0, val hair: Int = 0,
    val autoChangeHours: Int = 24, val autoChangeEnabled: Boolean = true,
    val lastChangeMillis: Long = 0L
)

/** 10 tops x 10 bottoms x 10 shoe styles/colours = 1,000 procedural outfit combinations. */
class BobbyAvatarStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_avatar_v1", Context.MODE_PRIVATE)
    fun load() = BobbyAvatarLook(
        prefs.getInt("expression", 0).coerceIn(0,99), prefs.getInt("outfit",0).coerceIn(0,999),
        prefs.getInt("hair",0).coerceIn(0,9), prefs.getInt("hours",24).coerceIn(1,168),
        prefs.getBoolean("auto",true), prefs.getLong("last",0L))
    fun save(v: BobbyAvatarLook) {
        prefs.edit().putInt("expression",v.expression.coerceIn(0,99)).putInt("outfit",v.outfit.coerceIn(0,999))
            .putInt("hair",v.hair.coerceIn(0,9)).putInt("hours",v.autoChangeHours.coerceIn(1,168))
            .putBoolean("auto",v.autoChangeEnabled).putLong("last",v.lastChangeMillis).apply()
    }
}

private val bobbyColours = listOf(Color(0xFFE85D75),Color(0xFF8E44AD),Color(0xFF2878B5),Color(0xFF168A72),Color(0xFFE08B32),Color(0xFF343A52),Color(0xFFB13B62),Color(0xFF6B7D35),Color(0xFF252525),Color(0xFFE8D7C5))
private val bobbyHair = listOf(Color(0xFF211713),Color(0xFF4B2A1B),Color(0xFF8B542E),Color(0xFF171717),Color(0xFFB77B42),Color(0xFF5B344A),Color(0xFF292B46),Color(0xFFD6B36A),Color(0xFF6E6E6E),Color(0xFF9A3C35))

/** Lightweight full-body human-like 2D avatar with continuous idle motion, blink and expression variants. */
@Composable
fun BobbyHumanAvatar(look: BobbyAvatarLook, modifier: Modifier = Modifier, active: Boolean = true) {
    val motion = rememberInfiniteTransition(label = "bobby-human")
    val bob by motion.animateFloat(-3f,3f,infiniteRepeatable(tween(1500,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="bob")
    val sway by motion.animateFloat(-1.5f,1.5f,infiniteRepeatable(tween(2100,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="sway")
    val blink by motion.animateFloat(0f,1f,infiniteRepeatable(tween(110,delayMillis=2600),RepeatMode.Reverse),label="blink")
    Canvas(modifier) {
        val s = minOf(size.width/180f,size.height/300f); val cx=size.width/2
        fun px(v:Float)=cx+(v-90f)*s+sway*s
        fun py(v:Float)=v*s+bob*s
        fun box(l:Float,t:Float,r:Float,b:Float,c:Color,rad:Float=7f) {
            drawRoundRect(c,Offset(px(l),py(t)),Size((r-l)*s,(b-t)*s),androidx.compose.ui.geometry.CornerRadius(rad*s))
        }
        val skin=Color(0xFFE9B99E); val top=bobbyColours[look.outfit%10]
        val lower=bobbyColours[(look.outfit/10)%10]; val shoes=bobbyColours[(look.outfit/100)%10]
        val hair=bobbyHair[look.hair.coerceIn(0,9)]
        box(65f,188f,85f,260f,lower); box(95f,188f,115f,260f,lower)
        box(58f,254f,88f,270f,shoes); box(92f,254f,122f,270f,shoes)
        val swing=if(active) bob*1.4f else 0f
        val arms=Path().apply { moveTo(px(61f),py(112f)); quadraticTo(px(48f-swing),py(155f),px(55f-swing),py(183f)); moveTo(px(119f),py(112f)); quadraticTo(px(132f+swing),py(155f),px(125f+swing),py(183f)) }
        drawPath(arms,skin,style=Stroke(12f*s,cap=StrokeCap.Round))
        box(78f,87f,102f,112f,skin); box(58f,105f,122f,195f,top,17f)
        box(82f,105f,98f,119f,Color(0xFFF1D8CF),4f); box(88f,130f,92f,177f,Color.White.copy(alpha=.25f),2f)
        drawCircle(skin,31f*s,Offset(px(90f),py(64f)))
        drawOval(hair,Offset(px(59f),py(31f)),Size(62f*s,32f*s))
        when(look.hair%5) { 1->box(57f,25f,123f,43f,hair,13f); 2->{box(55f,34f,67f,92f,hair);box(113f,34f,125f,92f,hair)}; 3->box(65f,20f,115f,39f,hair); 4->{box(58f,27f,122f,39f,hair);box(83f,15f,98f,33f,hair)} }
        val e=look.expression.coerceIn(0,99); val eyeY=62f+((e/10)%3-1)*2f
        val eyeH=if(blink>.92f)1.2f else 4f+(e%5)
        drawOval(Color(0xFF30201D),Offset(px(71f),py(eyeY)),Size((3f+e%3)*s,eyeH*s))
        drawOval(Color(0xFF30201D),Offset(px(106f),py(eyeY)),Size((3f+e%3)*s,eyeH*s))
        val brows=Path().apply { moveTo(px(68f),py(52f+e%4)); quadraticTo(px(77f),py(48f-e%3),px(82f),py(53f)); moveTo(px(98f),py(53f)); quadraticTo(px(106f),py(48f-e%3),px(112f),py(52f+e%4)) }
        drawPath(brows,hair,style=Stroke(2f*s,cap=StrokeCap.Round))
        val mouth=Path().apply { moveTo(px(80f),py(79f)); quadraticTo(px(90f),py(83f+((e%9)-4)*1.1f),px(100f),py(79f)) }
        drawPath(mouth,Color(0xFF8E4548),style=Stroke(1.8f*s,cap=StrokeCap.Round))
    }
}

@Composable
fun BobbyAvatarCustomizer(initial:BobbyAvatarLook,onSave:(BobbyAvatarLook)->Unit,onClose:()->Unit) {
    var look by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onClose,title={Text("BOBBY • AVATAR STUDIO")},text={
        Column(Modifier.heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            BobbyHumanAvatar(look,Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF09070A),RoundedCornerShape(14.dp)))
            Text("Facial expression preset: "+(look.expression+1)+" / 100")
            Slider(look.expression.toFloat(),{look=look.copy(expression=it.toInt())},valueRange=0f..99f)
            Text("Generated outfit: "+(look.outfit+1)+" / 1,000")
            Slider(look.outfit.toFloat(),{look=look.copy(outfit=it.toInt())},valueRange=0f..999f)
            Text("Hair style / colour: "+(look.hair+1)+" / 10")
            Slider(look.hair.toFloat(),{look=look.copy(hair=it.toInt())},valueRange=0f..9f)
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Random outfit changes",Modifier.weight(1f)); Switch(look.autoChangeEnabled,{look=look.copy(autoChangeEnabled=it)})
            }
            Text("Change interval: "+look.autoChangeHours+" hour(s)")
            Slider(look.autoChangeHours.toFloat(),{look=look.copy(autoChangeHours=it.toInt().coerceIn(1,168))},valueRange=1f..168f,steps=166)
            Text("Switch automatic changes off to keep one outfit. Scheduled changes are applied when BOBBY is next active; background scheduling is not yet installed.")
        }
    },confirmButton={TextButton(onClick={onSave(look);onClose()}){Text("SAVE LOOK",color=Color(0xFFFF1744))}},dismissButton={TextButton(onClick=onClose){Text("CANCEL")}})
}
