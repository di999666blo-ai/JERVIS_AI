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
    val lastChangeMillis: Long = 0L,
    val bustSize: Int = 4,
    val skinTone: Int = 5, val hairColor: Int = 3, val shoeColor: Int = 0,
    val necklaceStyle: Int = 1, val necklaceColor: Int = 8, val eyeColor: Int = 1,
    val makeup: Int = 2
)

/** 10 tops x 10 bottoms x 10 shoe styles/colours = 1,000 procedural outfit combinations. */
class BobbyAvatarStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_avatar_v1", Context.MODE_PRIVATE)
    fun load() = BobbyAvatarLook(
        prefs.getInt("expression", 0).coerceIn(0,99), prefs.getInt("outfit",0).coerceIn(0,999),
        prefs.getInt("hair",0).coerceIn(0,9), prefs.getInt("hours",24).coerceIn(1,168),
        prefs.getBoolean("auto",true), prefs.getLong("last",0L), prefs.getInt("bustSize",4).coerceIn(1,10), prefs.getInt("skinTone",5).coerceIn(0,9), prefs.getInt("hairColor",3).coerceIn(0,9), prefs.getInt("shoeColor",0).coerceIn(0,9), prefs.getInt("necklaceStyle",1).coerceIn(0,5), prefs.getInt("necklaceColor",8).coerceIn(0,9), prefs.getInt("eyeColor",1).coerceIn(0,5), prefs.getInt("makeup",2).coerceIn(0,5))
    fun save(v: BobbyAvatarLook) {
        prefs.edit().putInt("expression",v.expression.coerceIn(0,99)).putInt("outfit",v.outfit.coerceIn(0,999))
            .putInt("hair",v.hair.coerceIn(0,9)).putInt("hours",v.autoChangeHours.coerceIn(1,168))
            .putBoolean("auto",v.autoChangeEnabled).putLong("last",v.lastChangeMillis).putInt("bustSize",v.bustSize.coerceIn(1,10)).putInt("skinTone",v.skinTone.coerceIn(0,9)).putInt("hairColor",v.hairColor.coerceIn(0,9)).putInt("shoeColor",v.shoeColor.coerceIn(0,9)).putInt("necklaceStyle",v.necklaceStyle.coerceIn(0,5)).putInt("necklaceColor",v.necklaceColor.coerceIn(0,9)).putInt("eyeColor",v.eyeColor.coerceIn(0,5)).putInt("makeup",v.makeup.coerceIn(0,5)).apply()
    }
}

private val bobbyColours = listOf(Color(0xFFE85D75),Color(0xFF8E44AD),Color(0xFF2878B5),Color(0xFF168A72),Color(0xFFE08B32),Color(0xFF343A52),Color(0xFFB13B62),Color(0xFF6B7D35),Color(0xFF252525),Color(0xFFE8D7C5))
private val bobbyHair = listOf(Color(0xFF211713),Color(0xFF4B2A1B),Color(0xFF8B542E),Color(0xFF171717),Color(0xFFB77B42),Color(0xFF5B344A),Color(0xFF292B46),Color(0xFFD6B36A),Color(0xFF6E6E6E),Color(0xFF9A3C35))

/** Native OpenGL ES 2.0 full-body 3D avatar. Android 8+ supports this renderer. */
@Composable
fun BobbyHumanAvatar(look: BobbyAvatarLook, modifier: Modifier = Modifier, active: Boolean = true) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context -> Bobby3DHumanView(context, look, active) },
        update = { view -> view.updateLook(look, active) }
    )
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
            Text("Bust proportion: "+look.bustSize+" / 10")
            Slider(look.bustSize.toFloat(),{look=look.copy(bustSize=it.toInt().coerceIn(1,10))},valueRange=1f..10f,steps=8)
            Text("Skin tone: "+(look.skinTone+1)+" / 10")
            Slider(look.skinTone.toFloat(),{look=look.copy(skinTone=it.toInt())},valueRange=0f..9f)
            Text("Hair style: "+(look.hair+1)+" / 10")
            Slider(look.hair.toFloat(),{look=look.copy(hair=it.toInt())},valueRange=0f..9f)
            Text("Hair colour: "+(look.hairColor+1)+" / 10")
            Slider(look.hairColor.toFloat(),{look=look.copy(hairColor=it.toInt())},valueRange=0f..9f)
            Text("Shoe colour: "+(look.shoeColor+1)+" / 10")
            Slider(look.shoeColor.toFloat(),{look=look.copy(shoeColor=it.toInt())},valueRange=0f..9f)
            Text("Necklace style: "+(look.necklaceStyle+1)+" / 6")
            Slider(look.necklaceStyle.toFloat(),{look=look.copy(necklaceStyle=it.toInt())},valueRange=0f..5f,steps=4)
            Text("Necklace colour: "+(look.necklaceColor+1)+" / 10")
            Slider(look.necklaceColor.toFloat(),{look=look.copy(necklaceColor=it.toInt())},valueRange=0f..9f)
            Text("Eye colour: "+(look.eyeColor+1)+" / 6")
            Slider(look.eyeColor.toFloat(),{look=look.copy(eyeColor=it.toInt())},valueRange=0f..5f,steps=4)
            Text("Makeup: "+(look.makeup+1)+" / 6")
            Slider(look.makeup.toFloat(),{look=look.copy(makeup=it.toInt())},valueRange=0f..5f,steps=4)
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Random outfit changes",Modifier.weight(1f)); Switch(look.autoChangeEnabled,{look=look.copy(autoChangeEnabled=it)})
            }
            Text("Change interval: "+look.autoChangeHours+" hour(s)")
            Slider(look.autoChangeHours.toFloat(),{look=look.copy(autoChangeHours=it.toInt().coerceIn(1,168))},valueRange=1f..168f,steps=166)
            Text("Switch automatic changes off to keep one outfit. BOBBY schedules outfit changes in the background; Android battery restrictions may delay delivery.")
        }
    },confirmButton={TextButton(onClick={onSave(look);onClose()}){Text("SAVE LOOK",color=Color(0xFFFF1744))}},dismissButton={TextButton(onClick=onClose){Text("CANCEL")}})
}
