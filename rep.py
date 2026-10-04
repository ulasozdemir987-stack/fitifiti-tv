import re

with open('app/src/main/java/com/fitifiti/tv/ui/components/AnimatedBrandLogo.kt', 'rb') as f:
    text = f.read().decode('utf-8')

old = r'GLYPHS\.forEach \{ g ->[\s\S]*?w / 2, FLOOR_Y\)\) \{\s*when \(g\.ch\) \{[\s\S]*?\}\s*\}\s*\}'

new = '''
                val bA = if (live && liveColors.size >= 2) Brush.verticalGradient(listOf(liveColors[0], liveColors[1])) else TXT_A
                val bB = if (live && liveColors.size >= 3) Brush.verticalGradient(liveColors) else TXT_B
                GLYPHS.forEachIndexed { i, g ->
                    val brush = if (g.half == 0) bA else bB
                    val tap = g.tap.maxOfOrNull { tapAmount(t, it) } ?: 0f
                    val w = if (g.ch == '\u0131') 31f else if (g.ch == 'f') 42f else 46f
                    
                    var eqScale = 1f
                    if (live && g.ch == '\u0131' && i < 6) { // first three 'i's (indices 1, 3, 5)
                        val idx = (i - 1) / 2 // 0, 1, 2
                        val delay = idx * 0.18f
                        val eqTime = maxOf(0f, t - delay)
                        eqScale = 0.7f + 1.2f * (0.5f - 0.5f * kotlin.math.cos(eqTime * Math.PI / 1.05f)).toFloat()
                    }
                    
                    withTransform({ scale(1f + 0.06f * tap, (1f - 0.18f * tap) * eqScale, Offset(g.x + w / 2, FLOOR_Y)) }) {
                        when (g.ch) {
                            '\u0131' -> drawRoundRect(brush, Offset(g.x + 7.5f, I_TOP), Size(16f, X_HEIGHT), CornerRadius(2.6f))
                            'f' -> drawText(f, brush, Offset(g.x, FLOOR_Y - f.firstBaseline))
                            else -> drawText(tt, brush, Offset(g.x, FLOOR_Y - tt.firstBaseline))
                        }
                    }
                }
'''

text = re.sub(old, new.strip(), text)

with open('app/src/main/java/com/fitifiti/tv/ui/components/AnimatedBrandLogo.kt', 'wb') as f:
    f.write(text.encode('utf-8'))
