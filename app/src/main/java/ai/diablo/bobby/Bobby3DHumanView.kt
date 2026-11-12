package ai.diablo.bobby

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
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
    private var previousTouchX = 0f

    fun updateLook(look: BobbyAvatarLook, active: Boolean) = avatarRenderer.update(look, active)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousTouchX = event.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.x - previousTouchX
                previousTouchX = event.x
                if (kotlin.math.abs(delta) > 0.5f) {
                    queueEvent { avatarRenderer.rotateBy(delta * 0.65f) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private class HumanRenderer(initialLook: BobbyAvatarLook, initialActive: Boolean) : GLSurfaceView.Renderer {
    @Volatile private var look = initialLook
    @Volatile private var active = initialActive
    @Volatile private var rotationDegrees = 0f
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
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
    fun rotateBy(deltaDegrees: Float) {
        rotationDegrees = (rotationDegrees + deltaDegrees).let { ((it % 360f) + 360f) % 360f }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.012f, 0.025f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main(){
                vec4 worldPosition = uModel * vec4(aPosition,1.0);
                vPosition = worldPosition.xyz;
                vNormal = mat3(uModel) * aNormal;
                gl_Position = uMvp * vec4(aPosition,1.0);
            }
        """.trimIndent())
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main(){
                vec3 n = normalize(vNormal);
                vec3 lightDirection = normalize(vec3(-0.45,0.78,0.62));
                float diffuse = max(dot(n,lightDirection),0.0);
                vec3 viewDirection = normalize(vec3(0.0,1.35,7.2)-vPosition);
                vec3 halfDirection = normalize(lightDirection+viewDirection);
                float specular = pow(max(dot(n,halfDirection),0.0),24.0)*0.22;
                float lighting = 0.30 + diffuse*0.70;
                vec3 shaded = uColor.rgb*lighting + vec3(specular);
                gl_FragColor = vec4(shaded,uColor.a);
            }
        """.trimIndent())
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glBindAttribLocation(program, 0, "aPosition")
        GLES20.glBindAttribLocation(program, 1, "aNormal")
        GLES20.glLinkProgram(program)
        positionHandle = 0
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
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
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 12, vertices)
        GLES20.glEnableVertexAttribArray(1)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        val bob = if (active) sin(t * 3.2f) * 0.035f else sin(t * 1.7f) * 0.018f
        val sway = sin(t * 1.25f) * 0.045f
        val current = look
        val palette = intArrayOf(0xFFE85D75.toInt(),0xFF8E44AD.toInt(),0xFF2878B5.toInt(),0xFF168A72.toInt(),0xFFE08B32.toInt(),0xFF343A52.toInt(),0xFFB13B62.toInt(),0xFF6B7D35.toInt(),0xFF252525.toInt(),0xFFE8D7C5.toInt())
        val hairPalette = intArrayOf(0xFF211713.toInt(),0xFF4B2A1B.toInt(),0xFF8B542E.toInt(),0xFF171717.toInt(),0xFFB77B42.toInt(),0xFF5B344A.toInt(),0xFF292B46.toInt(),0xFFD6B36A.toInt(),0xFF6E6E6E.toInt(),0xFF9A3C35.toInt())
        // 10 upper styles x 10 lower styles x 10 shoe styles = 1,000 distinct procedural combinations.
        val topStyle = current.outfit % 10
        val bottomStyle = (current.outfit / 10) % 10
        val shoeStyle = (current.outfit / 100) % 10
        val top = palette[topStyle]
        val lower = palette[(bottomStyle + topStyle * 3) % 10]
        val shoes = palette[(shoeStyle + bottomStyle * 2) % 10]
        val trim = palette[(topStyle + bottomStyle + shoeStyle + 5) % 10]
        val hair = hairPalette[current.hairColor.coerceIn(0,9)]
        val skinPalette = intArrayOf(0xFFFFE0C2.toInt(),0xFFF2C49F.toInt(),0xFFE5AD83.toInt(),0xFFD5966B.toInt(),0xFFC27D54.toInt(),0xFF9B5B3D.toInt(),0xFF7A452F.toInt(),0xFF633824.toInt(),0xFF4B2B20.toInt(),0xFF382017.toInt())
        val skin = skinPalette[current.skinTone.coerceIn(0,9)]
        val shoePalette = intArrayOf(0xFF171717.toInt(),0xFFB51E35.toInt(),0xFFFFD700.toInt(),0xFFFFFFFF.toInt(),0xFF8E44AD.toInt(),0xFF2878B5.toInt(),0xFF168A72.toInt(),0xFFB87333.toInt(),0xFFB9BEC7.toInt(),0xFF5B3528.toInt())
        val shoeTint = shoePalette[current.shoeColor.coerceIn(0,9)]
        val jewelryPalette = intArrayOf(0xFFFFD700.toInt(),0xFFC0C0C0.toInt(),0xFFB87333.toInt(),0xFFFF4D8D.toInt(),0xFF40C8E8.toInt(),0xFFFFFFFF.toInt())
        val necklace = jewelryPalette[current.necklaceColor.coerceIn(0,5)]
        val bustScale = 0.78f + current.bustSize.coerceIn(1,10) * 0.075f
        // Procedural idle/walk-in-place leg articulation (low-cost ES 2.0).
        val legSwing = if (active) sin(t * 3.2f) * 10f else sin(t * 1.1f) * 1.8f
        // Ten lower-garment silhouettes: skirt, wide-leg, shorts, maxi, fitted pants, pleats and layered looks.
        val legWidth = if (bottomStyle == 1 || bottomStyle == 4 || bottomStyle == 7) 0.145f else 0.17f
        part(-0.25f,0.72f,0f,legWidth,0.58f,0.19f,lower,bob,legSwing)
        part(0.25f,0.72f,0f,legWidth,0.58f,0.19f,lower,bob,-legSwing)
        when (bottomStyle) {
            0, 6 -> part(0f,1.02f,0.012f,0.43f,0.25f,0.255f,lower,bob)
            1 -> { part(-0.18f,0.99f,0.012f,0.24f,0.24f,0.23f,lower,bob); part(0.18f,0.99f,0.012f,0.24f,0.24f,0.23f,lower,bob) }
            2, 8 -> part(0f,1.08f,0.012f,0.38f,0.16f,0.24f,lower,bob)
            3 -> part(0f,0.91f,0.012f,0.48f,0.36f,0.26f,lower,bob)
            4 -> { part(-0.25f,0.78f,0.012f,0.16f,0.48f,0.20f,lower,bob); part(0.25f,0.78f,0.012f,0.16f,0.48f,0.20f,lower,bob) }
            5 -> part(0f,1.02f,0.012f,0.40f,0.21f,0.24f,lower,bob)
            7 -> part(0f,1.12f,0.012f,0.37f,0.14f,0.24f,lower,bob)
            9 -> { part(0f,1.00f,0.012f,0.44f,0.28f,0.25f,lower,bob); part(0f,0.84f,0.02f,0.38f,0.12f,0.26f,trim,bob) }
        }
        // Ten shoe silhouettes: flat, trainers, heels, ankle boots and tall boots.
        val shoeH = if (shoeStyle == 2 || shoeStyle == 5 || shoeStyle == 8) 0.17f else 0.12f
        val shoeL = if (shoeStyle == 1 || shoeStyle == 4 || shoeStyle == 7) 0.40f else 0.34f
        part(-0.27f,shoeH,0.12f,0.22f,shoeH,shoeL,shoeTint,bob)
        part(0.27f,shoeH,0.12f,0.22f,shoeH,shoeL,shoeTint,bob)
        if (shoeStyle in 2..5 || shoeStyle == 8) {
            part(-0.27f,0.25f,0.29f,0.055f,0.18f,0.06f,trim,bob)
            part(0.27f,0.25f,0.29f,0.055f,0.18f,0.06f,trim,bob)
        }
        if (shoeStyle == 6 || shoeStyle == 9) {
            part(-0.27f,0.16f,0.02f,0.16f,0.20f,0.18f,trim,bob)
            part(0.27f,0.16f,0.02f,0.16f,0.20f,0.18f,trim,bob)
        }
        // Arms, hands and torso
        val armSwing = if (active) sin(t * 3.2f) * 0.13f else sway * 0.5f
        part(-0.52f,1.73f + bob,0f,0.15f,0.58f,0.16f,skin,0f,armSwing)
        part(0.52f,1.73f + bob,0f,0.15f,0.58f,0.16f,skin,0f,-armSwing)
        part(-0.57f,1.15f + bob,0.02f,0.13f,0.13f,0.13f,skin)
        part(0.57f,1.15f + bob,0.02f,0.13f,0.13f,0.13f,skin)
        // Ten upper-garment cuts: fitted, jacket, cropped, dress bodice, blouse,
        // wrap, hoodie layer, high-neck, vest and evening top.
        val torsoWidth = if (topStyle == 2 || topStyle == 8) 0.36f else 0.39f
        val torsoHeight = if (topStyle == 3 || topStyle == 9) 0.56f else 0.62f
        part(0f,1.48f + bob,0f,torsoWidth,torsoHeight,0.245f,top,0f,sway)
        part(-0.19f,1.72f + bob,0.205f,0.245f*bustScale,0.235f,0.16f*bustScale,top,0f,sway)
        part(0.19f,1.72f + bob,0.205f,0.245f*bustScale,0.235f,0.16f*bustScale,top,0f,sway)
        when (topStyle) {
            1, 6 -> { part(-0.31f,1.58f+bob,0.19f,0.065f,0.36f,0.075f,trim,0f,sway); part(0.31f,1.58f+bob,0.19f,0.065f,0.36f,0.075f,trim,0f,-sway) }
            2, 9 -> { part(0f,1.31f+bob,0.245f,0.34f,0.055f,0.035f,trim,0f,sway); part(-0.20f,1.83f+bob,0.22f,0.08f,0.06f,0.035f,trim); part(0.20f,1.83f+bob,0.22f,0.08f,0.06f,0.035f,trim) }
            3 -> { part(0f,1.22f+bob,0.02f,0.39f,0.16f,0.25f,top); part(0f,1.38f+bob,0.25f,0.29f,0.035f,0.025f,trim) }
            4, 7 -> { part(0f,1.91f+bob,0.19f,0.20f,0.07f,0.07f,trim); part(-0.38f,1.62f+bob,0f,0.10f,0.22f,0.12f,top); part(0.38f,1.62f+bob,0f,0.10f,0.22f,0.12f,top) }
            5 -> { part(-0.10f,1.75f+bob,0.245f,0.06f,0.24f,0.025f,trim,0f,-22f); part(0.10f,1.75f+bob,0.245f,0.06f,0.24f,0.025f,trim,0f,22f) }
            8 -> { part(-0.24f,1.58f+bob,0.22f,0.10f,0.34f,0.035f,trim); part(0.24f,1.58f+bob,0.22f,0.10f,0.34f,0.035f,trim) }
        }
        part(0f,1.27f + bob,0.252f,0.325f,0.22f,0.018f,top,0f,sway)
        if (topStyle % 2 == 0) part(0f,1.36f+bob,0.27f,0.34f,0.025f,0.02f,trim,0f,sway)
        // Small animated side panels add a restrained cloth-like secondary motion.
        val clothFlutter = sin(t * 2.6f + 0.7f) * 4.5f
        if (bottomStyle == 0 || bottomStyle == 2 || bottomStyle == 4) {
            part(-0.31f,1.04f + bob,0.12f,0.075f,0.22f,0.055f,lower,0f,-clothFlutter)
            part(0.31f,1.04f + bob,0.12f,0.075f,0.22f,0.055f,lower,0f,clothFlutter)
        }
        part(0f,2.13f + bob,0f,0.13f,0.18f,0.13f,skin)
        // Necklace presets: none, fine chain, pendant, choker, layered and statement.
        if (current.necklaceStyle > 0) {
            val chainY = if (current.necklaceStyle == 3) 2.19f else 2.13f
            part(-0.09f,chainY+bob,0.14f,0.018f,0.09f,0.018f,necklace,0f,-22f)
            part(0.09f,chainY+bob,0.14f,0.018f,0.09f,0.018f,necklace,0f,22f)
            part(0f,(chainY-0.075f)+bob,0.15f,0.025f,0.035f,0.025f,necklace)
            if (current.necklaceStyle >= 2) part(0f,2.015f+bob,0.16f,0.035f,0.055f,0.025f,necklace)
            if (current.necklaceStyle >= 4) {
                part(-0.13f,2.16f+bob,0.14f,0.016f,0.10f,0.016f,necklace,0f,-30f)
                part(0.13f,2.16f+bob,0.14f,0.016f,0.10f,0.016f,necklace,0f,30f)
            }
        }
        // Head, ears and hair cap
        part(0f,2.52f + bob,0f,0.36f,0.43f,0.31f,skin,0f,sway)
        part(-0.355f,2.49f + bob,0f,0.075f,0.12f,0.075f,skin)
        part(0.355f,2.49f + bob,0f,0.075f,0.12f,0.075f,skin)
        part(0f,2.82f + bob,-0.01f,0.37f,0.18f,0.33f,hair,0f,sway)
        // Long hair panels and a soft fringe; hair style selection changes length and fringe.
        val hairLength = 0.38f + (current.hair % 5) * 0.12f
        val hairSway = sin(t * 2.1f) * 3.2f
        val sideHairY = 2.28f - hairLength*0.34f + bob
        val sideHairWidth = if (current.hair == 4 || current.hair == 8) 0.14f else 0.105f
        part(-0.29f,sideHairY,-0.19f,sideHairWidth,hairLength,0.12f,hair,0f,-0.04f + hairSway)
        part(0.29f,sideHairY,-0.19f,sideHairWidth,hairLength,0.12f,hair,0f,0.04f + hairSway)
        // Ten visibly different low-poly hairstyles: bob, long, tied-back, fringe,
        // curls (clustered locks), side sweep, high bun, twin tails, shoulder cut, and ponytail.
        when (current.hair) {
            0 -> { part(0f,2.34f+bob,-0.16f,0.34f,0.20f,0.22f,hair,0f,0f) }
            1 -> { part(-0.34f,2.18f+bob,-0.12f,0.08f,0.43f,0.12f,hair,0f,-8f); part(0.34f,2.18f+bob,-0.12f,0.08f,0.43f,0.12f,hair,0f,8f) }
            2 -> { part(0f,2.17f+bob,-0.28f,0.19f,0.40f,0.12f,hair,0f,0f); part(0.02f,2.62f+bob,-0.24f,0.22f,0.20f,0.15f,hair) }
            3 -> { part(-0.20f,2.66f+bob,0.23f,0.18f,0.13f,0.10f,hair,0f,-18f); part(0.16f,2.66f+bob,0.23f,0.18f,0.13f,0.10f,hair,0f,12f) }
            4 -> { for (i in 0..2) { part(-0.36f,2.43f-i*0.18f+bob,-0.10f,0.12f,0.13f,0.13f,hair,0f,hairSway); part(0.36f,2.43f-i*0.18f+bob,-0.10f,0.12f,0.13f,0.13f,hair,0f,-hairSway) } }
            5 -> { part(-0.20f,2.66f+bob,0.24f,0.20f,0.12f,0.11f,hair,0f,-30f); part(0.10f,2.70f+bob,0.24f,0.18f,0.10f,0.10f,hair,0f,18f) }
            6 -> { part(0f,2.94f+bob,-0.03f,0.20f,0.16f,0.18f,hair,0f,0f) }
            7 -> { part(-0.43f,2.10f+bob,-0.16f,0.12f,0.34f,0.13f,hair,0f,hairSway); part(0.43f,2.10f+bob,-0.16f,0.12f,0.34f,0.13f,hair,0f,-hairSway) }
            8 -> { part(-0.31f,2.30f+bob,-0.18f,0.13f,0.25f,0.13f,hair); part(0.31f,2.30f+bob,-0.18f,0.13f,0.25f,0.13f,hair) }
            9 -> { part(0.34f,2.12f+bob,-0.30f,0.11f,0.44f,0.12f,hair,0f,18f); part(0.39f,2.48f+bob,-0.20f,0.13f,0.13f,0.13f,hair) }
        }
        part(-0.13f,2.69f + bob,0.235f,0.16f,0.105f,0.105f,hair,0f,-12f)
        part(0.08f,2.68f + bob,0.245f,0.15f,0.09f,0.105f,hair,0f,9f)
        // Face details: eye whites, irises, pupils, brows, nose and lips.
        val expression = current.expression.coerceIn(0, 99)
        val expressionPhase = expression / 99f
        val blink = if (t % 4.1f < 0.13f || (expression % 17 == 0 && t % 2.7f < 0.18f)) 0.18f else 1f
        val eyeOpen = (0.72f + ((expression * 7 % 29) / 100f)).coerceIn(0.72f, 1f)
        val eyeHeight = 0.038f * blink * eyeOpen
        val eyeWhite = 0xFFFFF5EC.toInt()
        val iris = intArrayOf(0xFF397C78.toInt(),0xFF5B4636.toInt(),0xFF405D91.toInt(),0xFF292929.toInt(),0xFF6A8E3A.toInt(),0xFF7952A0.toInt())[current.eyeColor.coerceIn(0,5)]
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
        val lipPalette = intArrayOf(0xFFB94E65.toInt(),0xFF914653.toInt(),0xFFD65B76.toInt(),0xFF8A263D.toInt(),0xFFE58C91.toInt(),0xFFB56C55.toInt())
        val lipColor = lipPalette[current.makeup.coerceIn(0,5)]
        part(0f,mouthY,0.309f,mouthWidth,mouthHeight,0.018f,lipColor)
        // Outfit finishing: collar, buttons, wrist cuffs and shoe heels.
        part(0f,1.91f + bob,0.19f,0.19f,0.045f,0.035f,lower)
        part(0f,1.58f + bob,0.251f,0.018f,0.11f,0.012f,0xFFFFD6A0.toInt())
        part(-0.52f,1.28f + bob,0.09f,0.16f,0.045f,0.035f,top)
        part(0.52f,1.28f + bob,0.09f,0.16f,0.045f,0.035f,top)
        if (shoeStyle == 4 || shoeStyle == 8) {
            part(-0.27f,0.19f,0.31f,0.045f,0.12f,0.05f,trim)
            part(0.27f,0.19f,0.31f,0.045f,0.12f,0.05f,trim)
        }
    }

    private fun part(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,color:Int,bob:Float=0f,rotation:Float=0f) {
        Matrix.setIdentityM(model,0)
        Matrix.rotateM(model,0,rotationDegrees,0f,1f,0f)
        Matrix.translateM(model,0,x,y+bob,z)
        if (rotation != 0f) Matrix.rotateM(model,0,rotation,0f,0f,1f)
        Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0)
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

