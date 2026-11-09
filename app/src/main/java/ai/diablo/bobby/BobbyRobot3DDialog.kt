package ai.diablo.bobby

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Original BOBBY robot avatar rendered as a real interactive Three.js 3D scene. */
@Composable
fun BobbyRobot3DDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOBBY • 3D COMPANION", color = Color(0xFFFF1744)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Drag to rotate • Pinch to zoom • Double-tap to reset", color = Color.LightGray)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(430.dp),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.rgb(5, 5, 7))
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            loadDataWithBaseURL("https://cdn.jsdelivr.net/", BOBBY_ROBOT_HTML, "text/html", "UTF-8", null)
                        }
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = Color(0xFFFF1744)) } },
        containerColor = Color(0xFF100A10)
    )
}

private val BOBBY_ROBOT_HTML = """
<!doctype html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#050507}
#scene{width:100%;height:100%;display:block;touch-action:none}
#label{position:absolute;left:0;right:0;bottom:12px;text-align:center;color:#ff315d;font:11px Arial;letter-spacing:3px;text-shadow:0 0 10px #ff1744;pointer-events:none}
#loading{position:absolute;top:45%;width:100%;text-align:center;color:#ff315d;font:12px Arial;letter-spacing:2px}
</style>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/build/three.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>
</head><body>
<div id="loading">BUILDING BOBBY 3D AVATAR…</div><canvas id="scene"></canvas><div id="label">BOBBY • NEURAL COMPANION</div>
<script>
(function(){
if(!window.THREE){document.getElementById('loading').textContent='3D ENGINE NEEDS INTERNET ACCESS';return;}
const THREE=window.THREE, canvas=document.getElementById('scene');
const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:false});
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1.6));
renderer.setSize(window.innerWidth,window.innerHeight,false);
renderer.setClearColor(0x050507,1);
renderer.outputEncoding=THREE.sRGBEncoding;
renderer.toneMapping=THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure=1.15;
const scene=new THREE.Scene();
scene.background=new THREE.Color(0x050507);
scene.fog=new THREE.Fog(0x050507,5,11);
const camera=new THREE.PerspectiveCamera(34,window.innerWidth/window.innerHeight,0.1,100);
camera.position.set(0,1.48,5.2);
const controls=new THREE.OrbitControls(camera,renderer.domElement);
controls.target.set(0,1.18,0);controls.enableDamping=true;controls.dampingFactor=.07;
controls.minDistance=2.5;controls.maxDistance=7;controls.maxPolarAngle=Math.PI*.86;
scene.add(new THREE.HemisphereLight(0x9da8c8,0x140812,1.2));
const key=new THREE.DirectionalLight(0xffdce5,2.2);key.position.set(3,5,4);scene.add(key);
const rim=new THREE.PointLight(0xff1744,2.4,7);rim.position.set(-2,2.6,-1);scene.add(rim);
const front=new THREE.PointLight(0x8295ff,1.0,6);front.position.set(2,1.5,3);scene.add(front);
const mat=(color,metalness=.7,roughness=.28,emissive=0x000000,intensity=0)=>new THREE.MeshStandardMaterial({color,metalness,roughness,emissive,emissiveIntensity:intensity});
const obs=mat(0x11131b,.9,.24), gun=mat(0x343541,.85,.3), silver=mat(0x9aa0ae,.8,.23), face=mat(0x05070d,.7,.12), red=mat(0xff1744,.45,.2,0xff123e,2.4), redGlass=mat(0x62051f,.55,.13,0x8e001e,.8), white=mat(0xd8dbe5,.25,.25);
const robot=new THREE.Group();scene.add(robot);
function mesh(g,m,p,s){const o=new THREE.Mesh(g,m);o.position.set(...p);if(s)o.scale.set(...s);o.castShadow=true;o.receiveShadow=true;robot.add(o);return o;}
function ball(p,s,m){return mesh(new THREE.SphereGeometry(1,24,16),m,p,s);}
function cube(p,s,m,r=.08){const o=mesh(new THREE.BoxGeometry(...s),m,p);return o;}
function rod(a,b,r,m,r2){const A=new THREE.Vector3(...a),B=new THREE.Vector3(...b),d=new THREE.Vector3().subVectors(B,A);const o=mesh(new THREE.CylinderGeometry(r2||r,r,d.length(),18),m,A.clone().add(B).multiplyScalar(.5).toArray());o.quaternion.setFromUnitVectors(new THREE.Vector3(0,1,0),d.normalize());return o;}
function ring(p,major,minor,m,rot){const o=mesh(new THREE.TorusGeometry(major,minor,10,48),m,p);if(rot)o.rotation.set(...rot);return o;}
function cone(p,r,h,m){return mesh(new THREE.ConeGeometry(r,h,8),m,p);}
const floor=mesh(new THREE.CircleGeometry(1.8,64),mat(0x120811,.2,.7,0x24000c,.15),[0,.015,0]);floor.rotation.x=-Math.PI/2;floor.scale.set(1.2,1.2,1);
const halo=ring([0,.08,-.25],.9,.012,red,[Math.PI/2,0,0]);halo.scale.set(1.25,1,1);
// boots and legs
for(const x of [-.205,.205]){
 ball([x,.13,.07],[.145,.12,.24],obs);
 cube([x,.055,.09],[.28,.05,.4],redGlass);
 rod([x,.24,0],[x*.94,.63,0],.09,gun);
 ball([x*.94,.64,.015],[.12,.105,.12],obs);
 ball([x*.94,.64,.12],[.035,.025,.018],red);
 rod([x*.94,.7,0],[x*.84,1.02,0],.115,obs);
 ball([x*.84,1.02,0],[.12,.12,.12],silver);
}
ball([0,1.04,0],[.30,.19,.17],gun);
ball([0,1.22,0],[.20,.17,.15],obs);ring([0,1.22,0],.19,.022,red,[Math.PI/2,0,0]);
// torso, chest plate and reactor
ball([0,1.48,0],[.34,.31,.20],obs);
ball([0,1.52,.125],[.28,.22,.105],gun);
ball([0,1.51,.222],[.105,.105,.028],redGlass);
ring([0,1.51,.245],.105,.012,red,[0,0,0]);
ball([0,1.51,.257],[.045,.045,.018],red);
for(const x of [-.25,.25])ball([x,1.5,.2],[.035,.13,.025],red);
for(const y of [1.30,1.35])cube([0,y,.145],[.065,.012,.012],red);
rod([0,1.69,0],[0,1.79,0],.08,silver);ring([0,1.72,0],.11,.018,red,[Math.PI/2,0,0]);
// shoulders and articulated arms
for(const x of [-1,1]){
 const sx=x*.39, ex=x*.437, wx=x*.449;
 ball([sx,1.61,0],[.15,.15,.15],gun);ball([sx,1.61,.13],[.055,.055,.025],red);
 rod([sx,1.54,0],[ex,1.29,.01],.078,obs);
 ball([ex,1.28,.01],[.085,.085,.085],silver);
 rod([ex,1.25,.01],[wx,1.02,.04],.065,gun);
 ball([wx,1,.04],[.055,.055,.055],redGlass);
 ball([wx,.91,.055],[.075,.09,.055],obs);
 for(let i=0;i<4;i++){let fx=wx+(i-1.5)*.028;rod([fx,.90,.08],[fx,.84,.08],.012,silver);}
}
// back spinal detail
cube([0,1.45,-.19],[.10,.42,.07],silver);
for(const y of [1.31,1.43,1.55,1.67])ball([0,y,-.23],[.025,.025,.012],red);
// head shell, face, eyes and expressive details
ball([0,1.91,0],[.235,.275,.20],obs);
ball([0,1.90,.178],[.185,.19,.045],face);
for(const x of [-.078,.078]){
 ball([x,1.93,.219],[.05,.035,.018],gun);
 ball([x,1.93,.235],[.034,.021,.012],red);
 cube([x,1.985,.215],[.075,.012,.012],redGlass);
}
for(let i=0;i<3;i++)cube([0,1.835+i*.017,.22],[.07,.006,.009],silver);
// headphones and luminous side rings
for(const x of [-1,1]){
 ball([x*.245,1.91,0],[.065,.14,.105],gun);
 ball([x*.29,1.91,.02],[.018,.065,.07],red);
 const ear=ring([x*.22,1.91,0],.12,.014,red,[0,Math.PI/2,0]);
}
// crown band and five points
ring([0,2.115,0],.205,.018,red,[Math.PI/2,0,0]);
for(let i=0;i<5;i++){const x=(i-2)*.07,h=i===2?.16:.11;cone([x,2.16+h/2,0],i===2?.035:.024,h,silver);ball([x,2.16+h,0],[.018,.018,.018],red);}
// soft shoulder cape panels and glowing chest insignia
for(const x of [-1,1]){const panel=cube([x*.29,1.43,-.015],[.11,.28,.08],obs);panel.rotation.z=x*.22;}
const aura=ring([0,1.45,-.3],.55,.009,red,[Math.PI/2,0,0]);aura.scale.set(1,.75,1);
document.getElementById('loading').style.display='none';
function resize(){camera.aspect=window.innerWidth/window.innerHeight;camera.updateProjectionMatrix();renderer.setSize(window.innerWidth,window.innerHeight,false);}
window.addEventListener('resize',resize);
let down=false;
canvas.addEventListener('dblclick',()=>{camera.position.set(0,1.48,5.2);controls.target.set(0,1.18,0);controls.update();});
function animate(){requestAnimationFrame(animate);if(!controls.enabled)robot.rotation.y+=.004;controls.update();renderer.render(scene,camera);}
animate();
})();
</script></body></html>
""".trimIndent()
