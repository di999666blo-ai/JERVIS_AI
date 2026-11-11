package ai.diablo.bobby

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

data class BobbyMcpServer(val id:String,val name:String,val endpoint:String,val toolCount:Int=0)

class BobbyMcpStore(context:Context) {
    private val prefs=context.getSharedPreferences("bobby_mcp_servers",Context.MODE_PRIVATE)
    private val vault=BobbySecretVault(context.applicationContext)
    fun servers():List<BobbyMcpServer> {
        val a=JSONArray(prefs.getString("servers","[]"))
        return (0 until a.length()).map { val o=a.getJSONObject(it); BobbyMcpServer(o.getString("id"),o.getString("name"),o.getString("endpoint"),o.optInt("tool_count")) }
    }
    fun save(s:BobbyMcpServer,token:String) {
        val a=JSONArray()
        (servers().filterNot{it.id==s.id}+s).forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("endpoint",it.endpoint).put("tool_count",it.toolCount))}
        prefs.edit().putString("servers",a.toString()).apply()
        if(token.isNotBlank()) vault.put("mcp_token_${s.id}",token.trim())
    }
    fun remove(id:String) {
        val a=JSONArray()
        servers().filterNot{it.id==id}.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("endpoint",it.endpoint).put("tool_count",it.toolCount))}
        prefs.edit().putString("servers",a.toString()).apply(); vault.remove("mcp_token_$id")
    }
}

/** MCP Streamable HTTP handshake and tools/list discovery; tool execution is deliberately not enabled yet. */
class BobbyMcpClient {
    private var session:String?=null
    private var id=0
    fun discover(endpoint:String,token:String):List<String> {
        require(endpoint.startsWith("https://",true)&&Uri.parse(endpoint).host?.isNotBlank()==true){"Use a valid HTTPS MCP endpoint."}
        val init=JSONObject().put("jsonrpc","2.0").put("id",++id).put("method","initialize")
            .put("params",JSONObject().put("protocolVersion","2025-03-26").put("capabilities",JSONObject())
                .put("clientInfo",JSONObject().put("name","BOBBY Android").put("version","0.1.0")))
        val first=post(endpoint,init,token)
        val result=first.optJSONObject("result")?:throw IllegalStateException(first.optJSONObject("error")?.optString("message")?:"MCP initialize failed.")
        val version=result.optString("protocolVersion","2025-03-26")
        post(endpoint,JSONObject().put("jsonrpc","2.0").put("method","notifications/initialized"),token,version,true)
        val listed=post(endpoint,JSONObject().put("jsonrpc","2.0").put("id",++id).put("method","tools/list").put("params",JSONObject()),token,version)
        val tools=listed.optJSONObject("result")?.optJSONArray("tools")
            ?:throw IllegalStateException(listed.optJSONObject("error")?.optString("message")?:"Connected, but server returned no MCP tools list.")
        return (0 until tools.length()).map{tools.getJSONObject(it).optString("name").ifBlank{"Unnamed tool"}}
    }
    private fun post(url:String,body:JSONObject,token:String,version:String="2025-03-26",allowEmpty:Boolean=false):JSONObject {
        val c=URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod="POST"; c.connectTimeout=12000; c.readTimeout=20000; c.doOutput=true
            c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Accept","application/json, text/event-stream")
            c.setRequestProperty("MCP-Protocol-Version",version)
            if(token.isNotBlank()) c.setRequestProperty("Authorization","Bearer $token")
            session?.let{c.setRequestProperty("Mcp-Session-Id",it)}
            c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val code=c.responseCode
            if(code==401||code==403) throw IllegalStateException("MCP authentication rejected (HTTP $code). Check the token or server login requirements.")
            if(code !in 200..299) {val e=c.errorStream?.bufferedReader()?.use{it.readText()}.orEmpty();throw IllegalStateException("MCP HTTP $code: ${e.take(220)}")}
            c.getHeaderField("Mcp-Session-Id")?.let{session=it}
            val raw=c.inputStream?.bufferedReader()?.use{it.readText()}.orEmpty()
            if(raw.isBlank()&&allowEmpty)return JSONObject()
            if(raw.isBlank())throw IllegalStateException("MCP server returned an empty response.")
            val json=if(raw.trimStart().startsWith("data:"))raw.lineSequence().filter{it.startsWith("data:")}.joinToString("\n"){it.removePrefix("data:").trim()}else raw
            return runCatching{JSONObject(json)}.getOrElse{throw IllegalStateException("Unsupported MCP response format. This endpoint may require another transport.")}
        } finally {c.disconnect()}
    }
}

@Composable
fun BobbyMcpManagerDialog(onDismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val store=remember{BobbyMcpStore(context)}
    val executor=remember{Executors.newSingleThreadExecutor()}
    val servers=remember{mutableStateListOf<BobbyMcpServer>().apply{addAll(store.servers())}}
    var name by remember{mutableStateOf("")}; var endpoint by remember{mutableStateOf("")}; var token by remember{mutableStateOf("")}
    var status by remember{mutableStateOf("Add an MCP Streamable HTTP server. BOBBY will verify it and discover tools.")}
    var busy by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("BOBBY • MCP SERVER MANAGER")},text={
        Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text("Connect Model Context Protocol servers. This Android build supports Streamable HTTP over HTTPS and optional bearer tokens. stdio servers and OAuth-only MCP servers need a bridge or provider-specific login.")
            OutlinedTextField(name,{name=it.take(48)},label={Text("Server name")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(endpoint,{endpoint=it.take(500)},label={Text("MCP HTTPS endpoint")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(token,{token=it.take(2000)},label={Text("Bearer token (optional)")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            Button(enabled=!busy&&name.isNotBlank()&&endpoint.isNotBlank(),onClick={
                busy=true; status="Connecting • MCP handshake • discovering tools…"
                val n=name.trim(); val u=endpoint.trim(); val secret=token
                executor.execute{
                    val result=runCatching{
                        val found=BobbyMcpClient().discover(u,secret)
                        val sid=UUID.nameUUIDFromBytes(u.toByteArray()).toString().replace("-","").take(24)
                        BobbyMcpServer(sid,n,u,found.size).also{store.save(it,secret)} to found
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post{
                        busy=false
                        result.onSuccess{(server,tools)->
                            servers.removeAll{it.id==server.id};servers.add(server);name="";endpoint="";token=""
                            status="CONNECTED • ${server.name} • ${tools.size} tools discovered"+if(tools.isEmpty())"" else "\n"+tools.take(20).joinToString(" • ")
                        }.onFailure{status="NOT CONNECTED • ${it.message?: "Connection failed"}"}
                    }
                }
            },modifier=Modifier.fillMaxWidth()){Text(if(busy)"CONNECTING…" else "CONNECT & DISCOVER TOOLS")}
            Text(status,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("CONNECTED MCP SERVERS • ${servers.size}")
            if(servers.isEmpty())Text("No MCP servers connected.")
            servers.forEach{s->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){Text(s.name);Text("${s.toolCount} tools • ${s.endpoint}",style=MaterialTheme.typography.bodySmall)}
                    TextButton(onClick={store.remove(s.id);servers.remove(s);status="Disconnected ${s.name} on this device."}){Text("REMOVE")}
                }
            }
            Text("Tokens are stored in Android Keystore-backed encrypted storage. Current stage verifies the server and discovers tools; executing discovered tools through BOBBY's approval system is the next stage.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(enabled=!busy,onClick=onDismiss){Text("DONE")}})
}
