package ai.diablo.bobby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ChartRed=Color(0xFFFF1744)
class BobbyChartAgentsActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MaterialTheme(colorScheme=darkColorScheme(primary=ChartRed,background=Color(0xFF050505),surface=Color(0xFF171017))){ChartAgentsScreen(onClose={finish()})}}}
 override fun onDestroy(){super.onDestroy()}
}
@Composable private fun ChartAgentsScreen(onClose:()->Unit){
 BackHandler(onBack=onClose)
 val context= androidx.compose.ui.platform.LocalContext.current
 val prefs=remember{context.getSharedPreferences("bobby_deriv_market",0)}
 var appId by remember{mutableStateOf(prefs.getString("app_id","")?: "")}
 var status by remember{mutableStateOf("Enter your Deriv App ID and connect to public market data.")}
 var markets by remember{mutableStateOf(emptyList<BobbyMarket>())}
 var selected by remember{mutableStateOf("")}
 var candles by remember{mutableStateOf(emptyList<BobbyMarketCandle>())}
 var ticks by remember{mutableStateOf(emptyList<BobbyPricePoint>())}
 var busy by remember{mutableStateOf(false)}
 var analysis by remember{mutableStateOf("Connect to Deriv, select a market and run Chart Analysis Agent.")}
 val feed=remember{BobbyDerivMarketData()}
 DisposableEffect(Unit){onDispose{feed.disconnect()}}
 fun connect(){
  prefs.edit().putString("app_id",appId.trim()).apply();busy=true
  feed.connect(appId.trim(),{s->status=s;busy=false},{list->markets=list;if(selected.isBlank())selected=list.firstOrNull()?.symbol.orEmpty()},{rows->candles=rows},{tick->ticks=(ticks+tick).takeLast(240)})
 }
 Column(Modifier.fillMaxSize().background(Color(0xFF050505)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("BOBBY • CHART & AGENT DESK",color=Color.White,fontSize=18.sp);Text("LIVE DERIV MARKET DATA",color=ChartRed,fontSize=11.sp)};TextButton(onClick=onClose){Text("CLOSE")}}
  OutlinedTextField(value=appId,onValueChange={appId=it.filter(Char::isDigit).take(12)},label={Text("Deriv App ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={connect()},enabled=!busy){Text(if(busy)"CONNECTING" else "CONNECT LIVE")};OutlinedButton(onClick={feed.disconnect();status="Disconnected"}){Text("DISCONNECT")}}
  Text(status,color=if(status.startsWith("Connected"))Color(0xFF45D483) else Color.LightGray,fontSize=12.sp)
  if(markets.isNotEmpty()){
   var expanded by remember{mutableStateOf(false)}
   Box{OutlinedButton(onClick={expanded=true}){Text(markets.firstOrNull{it.symbol==selected}?.name?: "Select market")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){markets.take(250).forEach{m->DropdownMenuItem(text={Text(m.name)},onClick={selected=m.symbol; candles=emptyList();ticks=emptyList();feed.selectMarket(m.symbol,{candles=it},{t->ticks=(ticks+t).takeLast(240)});expanded=false})}}}
  }
  Text("1-MINUTE CANDLES • LAST 100",color=ChartRed,fontSize=11.sp)
  PriceChart(candles,ticks)
  Text("Price: ${ticks.lastOrNull()?.price?.toString()?:candles.lastOrNull()?.close?.toString()?:"—"}   •   ticks: ${ticks.size}   •   candles: ${candles.size}",color=Color.LightGray,fontSize=12.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Button(onClick={analysis=ChartAgentEngine.analyse(selected,candles,ticks)}){Text("CHART ANALYSIS AGENT")}
   OutlinedButton(onClick={analysis=ChartAgentEngine.riskBrief(candles,ticks)}){Text("RISK AGENT")}
  }
  Text("AGENT OUTPUT",color=ChartRed,fontSize=12.sp)
  Text(analysis,color=Color.White,fontSize=13.sp)
  Text("Agents provide analysis only. No orders are placed. Live data availability depends on Deriv API access and your network.",color=Color.Gray,fontSize=10.sp)
 }
}
@Composable private fun PriceChart(candles:List<BobbyMarketCandle>,ticks:List<BobbyPricePoint>){
 Canvas(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF101010),RoundedCornerShape(12.dp)).padding(10.dp)){
  for(i in 1..3){val y=size.height*i/4f;drawLine(Color.DarkGray,Offset(0f,y),Offset(size.width,y),1f)}
  if(candles.size>=2){
   val lo=candles.minOf{it.low};val hi=candles.maxOf{it.high};val span=(hi-lo).takeIf{it>0}?:1.0
   val step=size.width/candles.size;val bodyWidth=(step*0.58f).coerceAtLeast(2f)
   candles.forEachIndexed{index,candle->
    val x=step*(index+0.5f)
    fun y(price:Double)=size.height-(((price-lo)/span).toFloat()*size.height)
    val rising=candle.close>=candle.open;val tint=if(rising)Color(0xFF45D483) else ChartRed
    drawLine(tint,Offset(x,y(candle.high)),Offset(x,y(candle.low)),1.5f)
    val top=minOf(y(candle.open),y(candle.close));val bottom=maxOf(y(candle.open),y(candle.close))
    drawLine(tint,Offset(x,top),Offset(x,maxOf(top+2f,bottom)),bodyWidth)
   }
  }else if(ticks.size>=2){
   val values=ticks.map{it.price};val lo=values.minOrNull()?:return@Canvas;val hi=values.maxOrNull()?:return@Canvas;val span=(hi-lo).takeIf{it>0}?:1.0
   val pts=values.mapIndexed{i,v->Offset(size.width*i/(values.size-1),size.height-(((v-lo)/span).toFloat()*size.height))}
   pts.zipWithNext().forEach{(a,b)->drawLine(ChartRed,a,b,2.5f)}
  }
 }
}
private object ChartAgentEngine{
 fun analyse(symbol:String,candles:List<BobbyMarketCandle>,ticks:List<BobbyPricePoint>):String{
  val closes=candles.map{it.close}.ifEmpty{ticks.map{it.price}}
  if(closes.size<20)return "WAIT • Need at least 20 usable price observations. Available: ${closes.size}."
  val fast=closes.takeLast(9).average();val slow=closes.takeLast(21).average()
  val momentum=closes.last()-closes[closes.size-6]
  val direction=when{fast>slow&&momentum>0->"UPWARD BIAS";fast<slow&&momentum<0->"DOWNWARD BIAS";else->"MIXED / WAIT"}
  val change=(closes.last()-closes.first())/closes.first()*100.0
  return "CHART AGENT • $symbol\n$direction\nEMA proxy 9: ${"%.5f".format(fast)} • EMA proxy 21: ${"%.5f".format(slow)}\n5-observation momentum: ${"%.5f".format(momentum)}\nObserved range change: ${"%.3f".format(change)}%\nThis is a basic rule-based observation, not a probability or trade signal. Candles: ${candles.size}; ticks: ${ticks.size}."
 }
 fun riskBrief(candles:List<BobbyMarketCandle>,ticks:List<BobbyPricePoint>):String{
  val closes=candles.map{it.close}.ifEmpty{ticks.map{it.price}}
  if(closes.size<2)return "RISK AGENT • WAIT for market observations before estimating volatility."
  val changes=closes.zipWithNext{a,b->kotlin.math.abs(b-a)}
  val avg=changes.average()
  val latest=closes.last()
  return "RISK AGENT • READ ONLY\nLatest: ${"%.5f".format(latest)}\nAverage absolute step: ${"%.5f".format(avg)}\nObserved points: ${closes.size}\nNo stake or contract size assumed. This agent does not place trades."
 }
}
