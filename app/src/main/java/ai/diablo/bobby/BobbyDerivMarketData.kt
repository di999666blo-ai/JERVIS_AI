package ai.diablo.bobby

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BobbyMarket(val symbol:String,val name:String)
data class BobbyPricePoint(val epoch:Long,val price:Double)
data class BobbyMarketCandle(val epoch:Long,val open:Double,val high:Double,val low:Double,val close:Double)

class BobbyDerivMarketData {
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build()
    @Volatile private var socket:WebSocket?=null
    @Volatile private var currentSymbol:String=""
    fun connect(appId:String,onStatus:(String)->Unit,onMarkets:(List<BobbyMarket>)->Unit,onCandles:(List<BobbyMarketCandle>)->Unit,onTick:(BobbyPricePoint)->Unit) {
        disconnect()
        if(!appId.matches(Regex("[0-9]{1,12}"))){onStatus("Enter a valid numeric Deriv App ID.");return}
        val request=Request.Builder().url("wss://ws.derivws.com/websockets/v3?app_id=$appId").build()
        socket=client.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(ws:WebSocket,response:Response){onStatus("Connected • loading active markets");ws.send(JSONObject().put("active_symbols","brief").put("product_type","basic").toString())}
            override fun onMessage(ws:WebSocket,text:String){
                try{
                    val json=JSONObject(text)
                    if(json.has("error")){onStatus(json.getJSONObject("error").optString("message","Deriv API error"));return}
                    when(json.optString("msg_type")){
                        "active_symbols"->{
                            val arr=json.optJSONArray("active_symbols")?:return
                            val list=(0 until arr.length()).mapNotNull{ i->arr.optJSONObject(i)?.let{BobbyMarket(it.optString("symbol"),it.optString("display_name",it.optString("symbol")))}}.filter{it.symbol.isNotBlank()}.sortedBy{it.name}
                            onMarkets(list);onStatus("Connected • ${list.size} markets")
                            if(list.isNotEmpty()) selectMarket(ws,list.first().symbol,onCandles,onTick)
                        }
                        "candles"->{
                            val arr=json.optJSONArray("candles")?:return
                            val list=(0 until arr.length()).mapNotNull{i->arr.optJSONObject(i)?.let{BobbyMarketCandle(it.optLong("epoch"),it.optDouble("open"),it.optDouble("high"),it.optDouble("low"),it.optDouble("close"))}}
                            onCandles(list)
                        }
                        "tick"->{
                            val tick=json.optJSONObject("tick")?:return
                            onTick(BobbyPricePoint(tick.optLong("epoch"),tick.optDouble("quote")))
                        }
                    }
                }catch(e:Exception){onStatus("Data parse error: ${e.message}")}
            }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){onStatus("Connection failed: ${t.message}")}
            override fun onClosed(ws:WebSocket,code:Int,reason:String){onStatus("Disconnected")}
        })
    }
    fun selectMarket(symbol:String,onCandles:(List<BobbyMarketCandle>)->Unit,onTick:(BobbyPricePoint)->Unit){
        currentSymbol=symbol
        val ws=socket?:return
        ws.send(JSONObject().put("forget_all","ticks").toString())
        ws.send(JSONObject().put("ticks_history",symbol).put("adjust_start_time",1).put("count",100).put("end","latest").put("style","candles").put("granularity",60).toString())
        ws.send(JSONObject().put("ticks",symbol).put("subscribe",1).toString())
    }
    private fun selectMarket(ws:WebSocket,symbol:String,onCandles:(List<BobbyMarketCandle>)->Unit,onTick:(BobbyPricePoint)->Unit){
        currentSymbol=symbol
        ws.send(JSONObject().put("forget_all","ticks").toString())
        ws.send(JSONObject().put("ticks_history",symbol).put("adjust_start_time",1).put("count",100).put("end","latest").put("style","candles").put("granularity",60).toString())
        ws.send(JSONObject().put("ticks",symbol).put("subscribe",1).toString())
    }
    fun disconnect(){socket?.close(1000,"BOBBY market screen closed");socket=null}
}
