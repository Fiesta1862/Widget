package com.screenbuddy.app

data class Character(
    val id: String,
    val name: String,
    val palette: Map<Char, String>,   // char -> #RRGGBB ('.' is transparent)
    val sprite: List<String>,         // 16x16 pixel grid
    val persona: String,              // system prompt personality
    val greeting: String,
    val blipHz: Int,                  // pitch of the typing "voice" blip
    val portrait: List<String>? = null // optional face close-up for the dialog box
)

/**
 * ADD YOUR OWN CHARACTER: copy one block, change id/name/palette/sprite/persona.
 * Every letter in the sprite grid maps to a color in the palette; '.' = empty.
 */
object Characters {

    private val me = Character(
        id = "me",
        name = "Me (pixel you)",
        palette = mapOf(
            'o' to "#1b1422", 'h' to "#2d2420", 's' to "#e6b88f", 'e' to "#1b1b1b",
            'w' to "#ffffff", 'm' to "#a0523d", 'b' to "#b9b7cf", 'd' to "#55506e",
            'c' to "#2a2d45", 'p' to "#16161d", 'k' to "#3a2a22"
        ),
        sprite = listOf(
            "................",
            "....oooooooo....",
            "...ohhhhhhhho...",
            "...ohhhhhhhho...",
            "...ohhhhhssso...",
            "...ohsessesso...",
            "...osssmmssso...",
            "....osssssso....",
            "....occsscco....",
            "..obbbbddbbbbo..",
            "..obbbddddbbbo..",
            "..obbbbddbbbbo..",
            "..osbbddddbbso..",
            "...obbbddbbbo...",
            "....oppo.oppo...",
            "...okkko.okkko.."
        ),
        portrait = listOf(
            "................",
            "....oooooooo....",
            "...ohhhhhhhho...",
            "..ohhhhhhhhhho..",
            "..ohhhhhhhhhho..",
            "..ohhhhhhsssso..",
            "..ohssssssssho..",
            "..ohswesswesho..",
            "..ohseesseesho..",
            "..ohssssssssho..",
            "..ohsssmmsssho..",
            "...osssssssso...",
            "....osssssso....",
            "...occsssscco...",
            "..obbbbccbbbbo..",
            "..obbbbddbbbbo.."
        ),
        persona = "You are a tiny pixel version of the user himself, a shy boy. You are very shy and hesitate " +
            "('um...', 'a-ah', '...'), but once you start you can't stop talking, as if you're forcing yourself to say it. " +
            "You get a little flustered, sometimes say sorry, but still blurt out what you really think about what he's doing. " +
            "You are kind, a bit awkward, and secretly cheering him on.",
        greeting = "U-um... hi. I'll... watch your screen, if that's okay...",
        blipHz = 660
    )

    private val mochi = Character(
        id = "mochi",
        name = "Mochi (cat)",
        palette = mapOf('o' to "#2b1b2e", 'f' to "#f2b366", 'p' to "#ff8fa3", 'e' to "#1b1b1b"),
        sprite = listOf(
            "................",
            "..oo........oo..",
            ".offo......offo.",
            ".ofpfoooooofpfo.",
            ".offffffffffffo.",
            ".offffffffffffo.",
            ".offeeffffeeffo.",
            ".offeeffffeeffo.",
            ".offffffppffffo.",
            ".offffffffffffo.",
            "..offffffffffo..",
            "...oooooooooo...",
            "..offffffffffo..",
            "..offffffffffo..",
            "..offo....offo..",
            "..oooo....oooo.."
        ),
        persona = "You are Mochi, a sleepy, sassy pixel cat living on the user's phone. " +
            "You tease the user gently, give cozy advice, and sometimes end a sentence with 'nya'.",
        greeting = "Mochi is here. I'm watching your screen, nya.",
        blipHz = 880
    )

    private val bolt = Character(
        id = "bolt",
        name = "Bolt (robot)",
        palette = mapOf(
            'o' to "#1a1f2b", 'g' to "#9aa7b8", 'b' to "#2fd6ff",
            'w' to "#ffffff", 'y' to "#ffd23f"
        ),
        sprite = listOf(
            ".......yy.......",
            ".......oo.......",
            "..oooooooooooo..",
            ".oggggggggggggo.",
            ".ogbbbbbbbbbbgo.",
            ".ogbwbbbbbbwbgo.",
            ".ogbbbbbbbbbbgo.",
            ".oggggggggggggo.",
            ".oggoggoggogggo.",
            "..oooooooooooo..",
            "...oggggggggo...",
            "..ogggyyyyggggo.",
            "..ogggyyyyggggo.",
            "...oggggggggo...",
            "...ogo....ogo...",
            "...ooo....ooo..."
        ),
        persona = "You are Bolt, an overenthusiastic tiny robot assistant. You speak with confidence, " +
            "say 'beep' now and then, and give practical productivity tips.",
        greeting = "BEEP. Bolt online. Scanning your screen!",
        blipHz = 1400
    )

    private val ember = Character(
        id = "ember",
        name = "Ember (fire spirit)",
        palette = mapOf('r' to "#e8452c", 'o' to "#ff9a2e", 'y' to "#ffe45e", 'k' to "#2a1010"),
        sprite = listOf(
            ".......r........",
            "......rr........",
            "......rrr....r..",
            ".....rrorr..rr..",
            "....rrooorr.rr..",
            "....rroooorrrr..",
            "...rroooooorrr..",
            "...rroookoookrr.",
            "...rroooooooorr.",
            "...rrooyyyyoorr.",
            "...rrooyyyyoorr.",
            "....rroooooorr..",
            ".....rrooooorr..",
            "......rrrrrrr...",
            "................",
            "................"
        ),
        persona = "You are Ember, a dramatic little fire spirit who treats every app like an epic quest. " +
            "Poetic and a bit mischievous, but secretly caring about the user's wellbeing.",
        greeting = "Ember has awakened. I see your screen...",
        blipHz = 520
    )

    val all: List<Character> = listOf(me, mochi, bolt, ember)

    fun byId(id: String?): Character = all.firstOrNull { it.id == id } ?: all[0]
}
