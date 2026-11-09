package ai.diablo.bobby

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight procedural 3D human built from articulated ellipsoid meshes.
 * Uses OpenGL ES 2.0 (available on Android 8/API 26); no downloaded model or network required.
 */
class Bobby3DHumanView(context: Context, look: BobbyAvatarLook, active: Boolean) : GLSurfaceView(context) {
    private val avatarRenderer = HumanRenderer(look, active)
    init {
        setEGLContextClientVersion(2)
        setRenderer(avatarRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setPreserveEGLContextOnPause(true)
    }
    fun updateLook(look: BobbyAvatarLook, active: Boolean) = avatarRenderer.update(look, active)
}

private class HumanRenderer(initialLook: BobbyAvatarLook, initialActive: Boolean) : GLSurfaceView.Renderer {
    @Volatile private var look = initialLook
    @Volatile private var active = initialActive
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private lateinit var vertices: FloatBuffer
    private lateinit var indices: ShortBuffer
    private var indexCount = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private var startTime = System.nanoTime()

    fun update(value: BobbyAvatarLook, isActive: Boolean) { look = value; active = isActive }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.012f, 0.025f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            void main(){ gl_Position = uMvp * vec4(aPosition,1.0); }
        """.trimIndent())
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float;
            uniform vec4 uColor;
            void main(){ gl_FragColor = uColor; }
        """.trimIndent())
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glBindAttribLocation(program, 0, "aPosition")
        GLES20.glLinkProgram(program)
        positionHandle = 0
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        buildSphere()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1)
        Matrix.frustumM(projection, 0, -ratio * 0.78f, ratio * 0.78f, -0.78f, 0.78f, 2f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 1.35f, 7.2f, 0f, 1.35f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        vertices.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertices)
        GLES20.glEnableVertexAttribArray(positionHandle)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        val bob = if (active) sin(t * 3.2f) * 0.035f else sin(t * 1.7f) * 0.018f
        val sway = sin(t * 1.25f) * 0.045f
        val current = look
        val palette = intArrayOf(0xFFE85D75.toInt(),0xFF8E44AD.toInt(),0xFF2878B5.toInt(),0xFF168A72.toInt(),0xFFE08B32.toInt(),0xFF343A52.toInt(),0xFFB13B62.toInt(),0xFF6B7D35.toInt(),0xFF252525.toInt(),0xFFE8D7C5.toInt())
        val hairPalette = intArrayOf(0xFF211713.toInt(),0xFF4B2A1B.toInt(),0xFF8B542E.toInt(),0xFF171717.toInt(),0xFFB77B42.toInt(),0xFF5B344A.toInt(),0xFF292B46.toInt(),0xFFD6B36A.toInt(),0xFF6E6E6E.toInt(),0xFF9A3C35.toInt())
        val top = palette[current.outfit % 10]
        val lower = palette[(current.outfit / 10) % 10]
        val shoes = palette[(current.outfit / 100) % 10]
        val hair = hairPalette[current.hair.coerceIn(0,9)]
        val skin = 0xFFE9B99E.toInt()
        val bustScale = 0.78f + current.bustSize.coerceIn(1,10) * 0.075f
        val outfitStyle = (current.outfit / 10) % 5
        // Procedural idle/walk-in-place leg articulation (low-cost ES 2.0).
        val legSwing = if (active) sin(t * 3.2f) * 10f else sin(t * 1.1f) * 1.8f
        part(-0.25f,0.72f,0f,0.17f,0.58f,0.19f,lower,bob,legSwing)
        part(0.25f,0.72f,0f,0.17f,0.58f,0.19f,lower,bob,-legSwing)
        // A separate lower-garment layer changes silhouette by generated style.
        if (outfitStyle % 2 == 0) {
            part(0f,1.02f,0.012f,0.43f,0.25f,0.255f,lower,bob)
        } else {
            part(0f,1.12f,0.012f,0.36f,0.17f,0.245f,lower,bob)
        }
        part(-0.27f,0.12f,0.12f,0.22f,0.12f,0.34f,shoes,bob)
        part(0.27f,0.12f,0.12f,0.22f,0.12f,0.34f,shoes,bob)
        // Arms, hands and torso
        val armSwing = if (active) sin(t * 3.2f) * 0.13f else sway * 0.5f
        part(-0.52f,1.73f + bob,0f,0.15f,0.58f,0.16f,skin,0f,armSwing)
        part(0.52f,1.73f + bob,0f,0.15f,0.58f,0.16f,skin,0f,-armSwing)
        part(-0.57f,1.15f + bob,0.02f,0.13f,0.13f,0.13f,skin)
        part(0.57f,1.15f + bob,0.02f,0.13f,0.13f,0.13f,skin)
        // Adult feminine silhouette: shaped waist and a fuller, clothed bust.
        // These are garment-coloured mesh forms, not exposed anatomy.
        part(0f,1.48f + bob,0f,0.39f,0.62f,0.245f,top,0f,sway)
        part(-0.19f,1.72f + bob,0.205f,0.245f*bustScale,0.235f,0.16f*bustScale,top,0f,sway)
        part(0.19f,1.72f + bob,0.205f,0.245f*bustScale,0.235f,0.16f*bustScale,top,0f,sway)
        // Optional fashion details: jacket panels or a waist belt, depending on style.
        if (outfitStyle == 1 || outfitStyle == 3) {
            part(-0.31f,1.58f + bob,0.19f,0.055f,0.34f,0.075f,lower,0f,sway)
            part(0.31f,1.58f + bob,0.19f,0.055f,0.34f,0.075f,lower,0f,sway)
        } else if (outfitStyle == 2 || outfitStyle == 4) {
            part(0f,1.36f + bob,0.245f,0.34f,0.045f,0.035f,shoes,0f,sway)
        }
        // Subtle fitted-waist panel keeps the silhouette readable in 3D.
        part(0f,1.27f + bob,0.012f,0.325f,0.22f,0.235f,top,0f,sway)
        // Small animated side panels add a restrained cloth-like secondary motion.
        val clothFlutter = sin(t * 2.6f + 0.7f) * 4.5f
        if (outfitStyle == 0 || outfitStyle == 2 || outfitStyle == 4) {
            part(-0.31f,1.04f + bob,0.12f,0.075f,0.22f,0.055f,lower,0f,-clothFlutter)
            part(0.31f,1.04f + bob,0.12f,0.075f,0.22f,0.055f,lower,0f,clothFlutter)
        }
        part(0f,2.13f + bob,0f,0.13f,0.18f,0.13f,skin)
        // Head, ears and hair cap
        part(0f,2.52f + bob,0f,0.36f,0.43f,0.31f,skin,0f,sway)
        part(-0.355f,2.49f + bob,0f,0.075f,0.12f,0.075f,skin)
        part(0.355f,2.49f + bob,0f,0.075f,0.12f,0.075f,skin)
        part(0f,2.82f + bob,-0.01f,0.37f,0.18f,0.33f,hair,0f,sway)
        // Long hair panels and a soft fringe; hair style selection changes length and fringe.
        val hairLength = 0.38f + (current.hair % 5) * 0.12f
        val hairSway = sin(t * 2.1f) * 3.2f
        part(-0.29f,2.28f - hairLength*0.34f + bob,-0.19f,0.105f,hairLength,0.12f,hair,0f,-0.04f + hairSway)
        part(0.29f,2.28f - hairLength*0.34f + bob,-0.19f,0.105f,hairLength,0.12f,hair,0f,0.04f + hairSway)
        part(-0.13f,2.69f + bob,0.235f,0.16f,0.105f,0.105f,hair,0f,-12f)
        part(0.08f,2.68f + bob,0.245f,0.15f,0.09f,0.105f,hair,0f,9f)
        // Face details: eye whites, irises, pupils, brows, nose and lips.
        val expression = current.expression.coerceIn(0, 99)
        val expressionPhase = expression / 99f
        val blink = if (t % 4.1f < 0.13f || (expression % 17 == 0 && t % 2.7f < 0.18f)) 0.18f else 1f
        val eyeOpen = (0.72f + ((expression * 7 % 29) / 100f)).coerceIn(0.72f, 1f)
        val eyeHeight = 0.038f * blink * eyeOpen
        val eyeWhite = 0xFFFFF5EC.toInt()
        val iris = intArrayOf(0xFF397C78.toInt(),0xFF5B4636.toInt(),0xFF405D91.toInt(),0xFF292929.toInt())[current.hair % 4]
        part(-0.135f,2.555f + bob,0.294f,0.078f,eyeHeight,0.022f,eyeWhite)
        part(0.135f,2.555f + bob,0.294f,0.078f,eyeHeight,0.022f,eyeWhite)
        part(-0.135f,2.555f + bob,0.316f,0.027f,0.031f*blink,0.012f,iris)
        part(0.135f,2.555f + bob,0.316f,0.027f,0.031f*blink,0.012f,iris)
        part(-0.135f,2.555f + bob,0.328f,0.012f,0.022f*blink,0.008f,0xFF171313.toInt())
        part(0.135f,2.555f + bob,0.328f,0.012f,0.022f*blink,0.008f,0xFF171313.toInt())
        val browTilt = ((expression % 9) - 4) * 2.1f
        part(-0.135f,2.62f + bob,0.292f,0.082f,0.018f,0.018f,hair,0f,-5f + browTilt)
        part(0.135f,2.62f + bob,0.292f,0.082f,0.018f,0.018f,hair,0f,5f - browTilt)
        part(0f,2.47f + bob,0.302f,0.045f,0.075f,0.035f,0xFFD99F86.toInt())
        part(0f,2.425f + bob,0.327f,0.045f,0.025f,0.025f,0xFFC78370.toInt())
        // 100 deterministic expression presets vary mouth shape, eye openness and brow angle.
        val mouthY = 2.355f + bob + ((expression % 11) - 5) * 0.0035f
        val mouthWidth = 0.042f + ((expression * 13 % 10) * 0.005f)
        val mouthHeight = if (expression % 7 == 0) 0.032f else 0.009f + ((expression % 4) * 0.003f)
        val lipColor = if (expression % 3 == 1) 0xFFB94E65.toInt() else 0xFF914653.toInt()
        part(0f,mouthY,0.309f,mouthWidth,mouthHeight,0.018f,lipColor)
        // Outfit finishing: collar, buttons, wrist cuffs and shoe heels.
        part(0f,1.91f + bob,0.19f,0.19f,0.045f,0.035f,lower)
        part(0f,1.58f + bob,0.251f,0.018f,0.11f,0.012f,0xFFFFD6A0.toInt())
        part(-0.52f,1.28f + bob,0.09f,0.16f,0.045f,0.035f,top)
        part(0.52f,1.28f + bob,0.09f,0.16f,0.045f,0.035f,top)
        if (outfitStyle == 4) {
            part(-0.27f,0.19f,0.31f,0.045f,0.12f,0.05f,shoes)
            part(0.27f,0.19f,0.31f,0.045f,0.12f,0.05f,shoes)
        }
    }

    private fun part(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,color:Int,bob:Float=0f,rotation:Float=0f) {
        Matrix.setIdentityM(model,0)
        Matrix.translateM(model,0,x,y+bob,z)
        if (rotation != 0f) Matrix.rotateM(model,0,rotation,0f,0f,1f)
        Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniform4f(colorHandle,((color shr 16) and 255)/255f,((color shr 8) and 255)/255f,(color and 255)/255f,1f)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES,indexCount,GLES20.GL_UNSIGNED_SHORT,indices)
    }

    private fun buildSphere() {
        val lat = 18
        val lon = 24
        val data = ArrayList<Float>()
        for (i in 0..lat) {
            val theta = Math.PI * i / lat
            for (j in 0..lon) {
                val phi = 2.0 * Math.PI * j / lon
                data.add((sin(theta) * cos(phi)).toFloat())
                data.add(cos(theta).toFloat())
                data.add((sin(theta) * sin(phi)).toFloat())
            }
        }
        val idx = ArrayList<Short>()
        for (i in 0 until lat) for (j in 0 until lon) {
            val a = (i * (lon + 1) + j).toShort()
            val b = (a + lon + 1).toShort()
            idx.add(a); idx.add(b); idx.add((a + 1).toShort())
            idx.add((a + 1).toShort()); idx.add(b); idx.add((b + 1).toShort())
        }
        val vb = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        vertices = vb.asFloatBuffer().apply { data.forEach { put(it) }; position(0) }
        val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder())
        indices = ib.asShortBuffer().apply { idx.forEach { put(it) }; position(0) }
        indexCount = idx.size
    }

    private fun compile(type:Int, source:String):Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader,source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }
}

