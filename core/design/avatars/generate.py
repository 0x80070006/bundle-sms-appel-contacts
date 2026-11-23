"""
Bundle: the cartoon avatars shown for contacts with no photo, and offered as a photo in the
contact editor: people, animals, robots, monsters and sci-fi characters, drawn on a 100x100
canvas from simple shapes.

Each avatar is written twice with the same geometry:
  core/design/avatars/svg/<name>.svg               the SVG (to view or edit)
  core/design/avatars/res/drawable/bundle_<name>.xml the Android vector drawable the apps use
    (a resource folder shared by core/design, for Contacts and Phone, and by apps/messages)
plus the Kotlin lists of drawables and the rule picking a contact's avatar, the same in both:
  core/design/src/main/java/bundle/core/design/CartoonAvatarArt.kt
  apps/messages/src/com/android/messaging/ui/common/components/participant/CartoonAvatarArt.kt

Run from anywhere:  python core/design/avatars/generate.py
"""
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
DESIGN = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(DESIGN))
SVG_DIR = os.path.join(HERE, "svg")
DRAWABLE_DIR = os.path.join(HERE, "res", "drawable")
KOTLIN_FILE = os.path.join(DESIGN, "src", "main", "java", "bundle", "core", "design", "CartoonAvatarArt.kt")
MESSAGES_KOTLIN_FILE = os.path.join(ROOT, "apps", "messages", "src", "com", "android", "messaging", "ui", "common",
                                    "components", "participant", "CartoonAvatarArt.kt")

# The rule picking a contact's avatar from its contact id. Contact ids follow each other, so the
# id is scrambled first, or contacts 1, 2, 3... would get avatars 1, 2, 3... (all people).
PICK_FUNCTION = """
/** The index, in a list of [size] avatars, of the one a contact with no photo shows: random but
 *  always the same for one contact ([seed] is its contact id), and the same in every app. */
internal fun cartoonAvatarIndex(seed: Long, size: Int): Int {
    var h = seed * -0x61c8864680b583ebL // 2^64 / golden ratio
    h = (h xor (h ushr 31)) * -0x4b47d5b1e9a29c6dL
    h = h xor (h ushr 29)
    return (((h % size) + size) % size).toInt()
}
"""


def n(v):
    s = ("%.2f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


# ---- shapes: each is a dict with a path and how it's painted ----

def P(d, fill=None, stroke=None, sw=0, op=1.0, rot=None):
    return {"d": d, "fill": fill, "stroke": stroke, "sw": sw, "op": op, "rot": rot}


def E(cx, cy, rx, ry, fill, op=1.0, rot=0, stroke=None, sw=0):
    d = (f"M{n(cx - rx)},{n(cy)} a{n(rx)},{n(ry)} 0 1,0 {n(2 * rx)},0 "
         f"a{n(rx)},{n(ry)} 0 1,0 {n(-2 * rx)},0 Z")
    return P(d, fill, stroke, sw, op, (rot, cx, cy) if rot else None)


def C(cx, cy, r, fill, op=1.0, stroke=None, sw=0):
    return E(cx, cy, r, r, fill, op, 0, stroke, sw)


def R(x, y, w, h, fill, rx=0, stroke=None, sw=0):
    if rx == 0:
        d = f"M{n(x)},{n(y)} H{n(x + w)} V{n(y + h)} H{n(x)} Z"
    else:
        r = rx
        d = (f"M{n(x + r)},{n(y)} H{n(x + w - r)} a{n(r)},{n(r)} 0 0,1 {n(r)},{n(r)} "
             f"V{n(y + h - r)} a{n(r)},{n(r)} 0 0,1 {n(-r)},{n(r)} H{n(x + r)} "
             f"a{n(r)},{n(r)} 0 0,1 {n(-r)},{n(-r)} V{n(y + r)} a{n(r)},{n(r)} 0 0,1 {n(r)},{n(-r)} Z")
    return P(d, fill, stroke, sw)


def line(d, color, sw):
    return P(d, None, color, sw)


DARK = "#2B2B33"
WHITE = "#FFFFFF"
BLUSH = "#F28B8B"
SKIN = {"light": "#F6D2B8", "peach": "#F3C9A5", "tan": "#E8B48F", "brown": "#C68B62", "deep": "#8D5A3B"}


def background(color):
    return R(0, 0, 100, 100, color)


def eyes(y=48, lx=42, rx=58, r=2.7):
    return [C(lx, y, r, DARK), C(rx, y, r, DARK),
            C(lx + 0.9, y - 0.9, 0.9, WHITE), C(rx + 0.9, y - 0.9, 0.9, WHITE)]


def person(bg, skin, shirt, back=(), front=(), extras=(), head=(50, 46, 21, 23),
           neck=True, ears=True, blush=(36, 64), mouth="M44,56 Q50,62 56,56"):
    s = [background(bg), P("M16,100 C16,80 30,72 50,72 C70,72 84,80 84,100 Z", shirt)]
    s += list(back)
    if neck:
        s.append(R(43, 60, 14, 14, skin, rx=3))
    if ears:
        s += [C(29, 48, 4.5, skin), C(71, 48, 4.5, skin)]
    s.append(E(*head, skin))
    s += eyes()
    if blush:
        s += [C(blush[0], 55, 3.5, BLUSH, op=0.4), C(blush[1], 55, 3.5, BLUSH, op=0.4)]
    if mouth:
        s.append(line(mouth, DARK, 2.2))
    s += list(front)
    s += list(extras)
    return s


PEOPLE = [
    # Short brown hair.
    person("#BFE3F5", SKIN["light"], "#3D7BD9", front=[
        P("M28.5,46 C27,26 37,20 50,20 C63,20 73,26 71.5,46 C68,36 62,31 54,31 C49,35 41,35 36,32 "
          "C32,35 30,40 28.5,46 Z", "#5A3A22")]),
    # Long black hair.
    person("#FFD9C7", SKIN["tan"], "#E2577D", back=[
        P("M27,46 C25,24 36,18 50,18 C64,18 75,24 73,46 L76,78 C70,80 64,78 62,70 L38,70 C36,78 30,80 24,78 Z",
          "#1F1B24")], front=[
        P("M29,44 C29,28 38,22 50,22 C62,22 71,28 71,44 C66,36 60,30 52,29 C46,34 37,38 29,44 Z", "#1F1B24")]),
    # Afro.
    person("#C9F0DC", SKIN["deep"], "#F2B632", back=[
        C(31, 36, 10, "#231A14"), C(38, 26, 11, "#231A14"), C(50, 22, 12, "#231A14"),
        C(62, 26, 11, "#231A14"), C(69, 36, 10, "#231A14"), C(28, 47, 7, "#231A14"), C(72, 47, 7, "#231A14")],
        front=[P("M30,40 C32,30 40,27 50,27 C60,27 68,30 70,40 C64,35 58,33 50,33 C42,33 36,35 30,40 Z",
                 "#231A14")]),
    # Blonde bun.
    person("#DCD3F7", SKIN["peach"], "#4CAF7A", back=[C(50, 18, 9, "#E9B949")], front=[
        P("M28.5,47 C27,27 37,21 50,21 C63,21 73,27 71.5,47 C69,38 63,32 56,30 C52,34 44,36 38,35 "
          "C33,38 30,42 28.5,47 Z", "#F2C14E")]),
    # Grandpa: white hair, glasses, moustache.
    person("#FFF0B8", SKIN["light"], "#8B5E3C", back=[P("M42,72 L50,82 L58,72 Z", WHITE)],
           mouth="M46,60.5 Q50,63 54,60.5", front=[
            E(30.5, 40, 4.5, 8, "#E6E6E6"), E(69.5, 40, 4.5, 8, "#E6E6E6"),
            E(44, 29, 5, 2.5, WHITE, op=0.5)], extras=[
            C(42, 48, 6, None, stroke=DARK, sw=1.8), C(58, 48, 6, None, stroke=DARK, sw=1.8),
            line("M48,48 L52,48", DARK, 1.8),
            line("M37,40 Q42,38 46,40", "#D0D0D0", 2), line("M54,40 Q58,38 63,40", "#D0D0D0", 2),
            P("M41,57 C44,52 48,53 50,55 C52,53 56,52 59,57 C55,58 52,58 50,57 C48,58 45,58 41,57 Z",
              "#E6E6E6")]),
    # Red hair and freckles.
    person("#FFE0B5", SKIN["peach"], "#2A9D8F", front=[
        P("M28,47 C25,28 35,18 50,19 C65,18 75,28 72,47 C70,40 67,36 64,34 L62,38 L58,32 L54,37 L50,31 "
          "L46,37 L42,32 L38,38 L36,34 C33,36 30,40 28,47 Z", "#D9572B")], extras=[
        C(37, 53, 0.9, "#C77A55", op=0.8), C(39.5, 55, 0.9, "#C77A55", op=0.8), C(35.5, 56, 0.9, "#C77A55", op=0.8),
        C(63, 53, 0.9, "#C77A55", op=0.8), C(60.5, 55, 0.9, "#C77A55", op=0.8), C(64.5, 56, 0.9, "#C77A55", op=0.8)]),
    # Headscarf.
    person("#FAD4E1", SKIN["brown"], "#7B4FA0", neck=False, ears=False, head=(50, 48, 17, 19), blush=(38, 62),
           back=[E(50, 47, 26, 28, "#7B4FA0"),
                 P("M18,100 C18,78 28,66 40,64 L60,64 C72,66 82,78 82,100 Z", "#7B4FA0")],
           extras=[line("M31,63 Q50,73 69,63", "#5E3A80", 2)]),
    # Beanie and beard.
    person("#CFD8E8", SKIN["tan"], "#3B4A5A", blush=None, mouth=None, front=[
        P("M28,40 C28,24 38,16 50,16 C62,16 72,24 72,40 Z", "#D64545"),
        R(26, 35, 48, 8, "#B83232", rx=4), C(50, 14, 5, "#F5F5F5")], extras=[
        P("M29,48 C29,62 36,72 50,72 C64,72 71,62 71,48 C68,54 64,58 58,58 C55,55 45,55 42,58 "
          "C36,58 32,54 29,48 Z", "#6B4428"),
        line("M45,61.5 Q50,64.5 55,61.5", "#3A2414", 2)]),
    # Cap.
    person("#D7F0B8", SKIN["brown"], "#2F6FB0", front=[
        P("M29,40 C29,24 38,18 50,18 C62,18 71,24 71,40 Z", "#F08A24"),
        P("M48,38 L79,38 C83,38 83,44 78,44 L48,44 Z", "#C9691A"), C(50, 18.5, 2, "#C9691A")]),
    # Purple bob.
    person("#C6EEF2", SKIN["light"], "#E76F51", back=[
        P("M26,50 C24,26 36,19 50,19 C64,19 76,26 74,50 L74,64 C70,66 66,64 65,60 L35,60 C34,64 30,66 26,64 Z",
          "#7E57C2")], front=[
        P("M30,40 C30,27 39,22 50,22 C61,22 70,27 70,40 Z", "#7E57C2")], extras=[
        C(29.5, 56, 2, "#F2C14E"), C(70.5, 56, 2, "#F2C14E")]),
]


def whiskers(color, y1=62, y2=65):
    return [line(f"M36,{y1} L24,{y1 - 2}", color, 1.2), line(f"M36,{y2} L24,{y2 + 1}", color, 1.2),
            line(f"M64,{y1} L76,{y1 - 2}", color, 1.2), line(f"M64,{y2} L76,{y2 + 1}", color, 1.2)]


def smile_y(x, y, w=5, depth=3):
    return line(f"M{x},{y} Q{x - w / 2},{y + depth} {x - w},{y + depth / 2} "
                f"M{x},{y} Q{x + w / 2},{y + depth} {x + w},{y + depth / 2}", DARK, 1.6)


ORANGE = "#F29A38"
ANIMALS = [
    # Cat.
    [background("#BDE7E0"),
     P("M25,46 L29,16 L47,32 Z", ORANGE), P("M75,46 L71,16 L53,32 Z", ORANGE),
     P("M30,38 L31.5,23 L42,32 Z", "#F7B9C4"), P("M70,38 L68.5,23 L58,32 Z", "#F7B9C4"),
     E(50, 100, 30, 14, ORANGE), E(50, 56, 28, 25, ORANGE),
     P("M45.5,31.5 L48,39 L50,31.2 Z", "#D97A1E"), P("M50,31.2 L52,39 L54.5,31.5 Z", "#D97A1E"),
     E(50, 64, 10, 7, "#FCE3C4"),
     E(40, 53, 3.2, 4, DARK), E(60, 53, 3.2, 4, DARK), C(41, 51.5, 1, WHITE), C(61, 51.5, 1, WHITE),
     C(34, 61, 3.2, BLUSH, op=0.45), C(66, 61, 3.2, BLUSH, op=0.45),
     P("M47,59 L53,59 L50,62.5 Z", "#E5677D"), smile_y(50, 62.5)] + whiskers("#7A4A1A"),
    # Dog.
    [background("#FFF1B5"),
     E(50, 100, 30, 14, "#D9A066"), E(50, 54, 23, 25, "#D9A066"),
     P("M46,31 C44,44 44,52 42,58 L58,58 C56,52 56,44 54,31 Q50,29.5 46,31 Z", "#FFF6E8"),
     E(28, 52, 8, 17, "#8A5A2B", rot=15), E(72, 52, 8, 17, "#8A5A2B", rot=-15),
     E(50, 64, 13, 10, "#FFF6E8"),
     E(41, 50, 3, 3.6, DARK), E(59, 50, 3, 3.6, DARK), C(42, 48.8, 1, WHITE), C(60, 48.8, 1, WHITE),
     P("M47.5,67 Q50,75 52.5,67 Z", "#F06A7D"),
     E(50, 59, 5, 3.5, DARK), E(48.5, 58, 1.5, 0.8, WHITE, op=0.6),
     line("M50,62.5 L50,65.5 M50,65.5 Q46,69 43,66 M50,65.5 Q54,69 57,66", DARK, 1.6)],
    # Panda.
    [background("#CDEBC0"),
     C(29, 34, 9.5, DARK), C(71, 34, 9.5, DARK), E(50, 100, 28, 12, WHITE),
     E(50, 56, 27, 24, WHITE),
     E(40, 54, 6.5, 8.5, DARK, rot=25), E(60, 54, 6.5, 8.5, DARK, rot=-25),
     C(40.5, 53, 2.4, WHITE), C(59.5, 53, 2.4, WHITE), C(41, 53, 1.2, DARK), C(59, 53, 1.2, DARK),
     C(34, 64, 3.2, "#F7A8B8", op=0.7), C(66, 64, 3.2, "#F7A8B8", op=0.7),
     E(50, 62, 4, 2.8, DARK), smile_y(50, 64.5)],
    # Fox.
    [background("#E3D7F5"),
     P("M24,46 L27,14 L46,32 Z", "#F07B2B"), P("M76,46 L73,14 L54,32 Z", "#F07B2B"),
     P("M29,38 L30,22 L40,31 Z", "#3A2A2A"), P("M71,38 L70,22 L60,31 Z", "#3A2A2A"),
     E(50, 100, 28, 13, "#F07B2B"),
     P("M22,44 C22,34 34,28 50,28 C66,28 78,34 78,44 C78,58 64,74 50,76 C36,74 22,58 22,44 Z", "#F07B2B"),
     P("M23,48 C30,54 40,58 50,60 C60,58 70,54 77,48 C74,62 62,74 50,76 C38,74 26,62 23,48 Z", "#FFF8F0"),
     E(39, 48, 3, 3.8, DARK), E(61, 48, 3, 3.8, DARK), C(40, 46.8, 1, WHITE), C(62, 46.8, 1, WHITE),
     E(50, 64, 4, 3, DARK), smile_y(50, 67, w=4, depth=2.5)],
    # Bear.
    [background("#C8DDF7"),
     C(28, 33, 10, "#9A6A44"), C(72, 33, 10, "#9A6A44"), C(28, 33, 5.5, "#C99A72"), C(72, 33, 5.5, "#C99A72"),
     E(50, 100, 30, 14, "#9A6A44"), E(50, 56, 26, 25, "#9A6A44"),
     E(50, 64, 11, 8.5, "#D9B48F"),
     C(40, 51, 3, DARK), C(60, 51, 3, DARK), C(41, 50, 1, WHITE), C(61, 50, 1, WHITE),
     C(33, 60, 3.2, BLUSH, op=0.4), C(67, 60, 3.2, BLUSH, op=0.4),
     E(50, 60, 4.5, 3.2, DARK), line("M50,63 L50,66", DARK, 1.5), smile_y(50, 66)],
    # Rabbit.
    [background("#FFD6E0"),
     E(39, 24, 7, 19, "#F4F1EE", rot=-8), E(61, 24, 7, 19, "#F4F1EE", rot=8),
     E(39, 25, 3.5, 14, "#F7B9C4", rot=-8), E(61, 25, 3.5, 14, "#F7B9C4", rot=8),
     E(50, 100, 26, 13, "#F4F1EE"), E(50, 60, 24, 22, "#F4F1EE"),
     E(41, 57, 2.8, 3.4, DARK), E(59, 57, 2.8, 3.4, DARK), C(41.9, 56, 0.9, WHITE), C(59.9, 56, 0.9, WHITE),
     C(36, 65, 3.5, "#F7A8B8", op=0.7), C(64, 65, 3.5, "#F7A8B8", op=0.7),
     R(47.6, 67.6, 4.8, 4.6, WHITE, rx=1, stroke="#C8C0B8", sw=0.8),
     E(50, 64, 2.8, 2, "#E5677D"), smile_y(50, 66, w=4.5, depth=2.5)],
    # Owl.
    [background("#BFE6EA"),
     P("M26,36 L28,16 L40,28 Z", "#8A5A3C"), P("M74,36 L72,16 L60,28 Z", "#8A5A3C"),
     E(50, 64, 28, 34, "#8A5A3C"),
     E(50, 80, 17, 16, "#D9B48F"),
     line("M43,76 q2.5,2.5 5,0 M52,76 q2.5,2.5 5,0 M47.5,83 q2.5,2.5 5,0", "#B08960", 1.4),
     E(24, 68, 6, 16, "#6E4429", rot=10), E(76, 68, 6, 16, "#6E4429", rot=-10),
     C(39, 50, 11, "#FFF8E7"), C(61, 50, 11, "#FFF8E7"),
     C(39, 50, 5.5, DARK), C(61, 50, 5.5, DARK), C(40.8, 48.2, 1.7, WHITE), C(62.8, 48.2, 1.7, WHITE),
     P("M46,58 L54,58 L50,66 Z", "#F2A33A")],
    # Frog.
    [background("#FFF4C2"),
     E(50, 100, 30, 14, "#6CC24A"), C(34, 38, 11, "#6CC24A"), C(66, 38, 11, "#6CC24A"),
     E(50, 60, 32, 24, "#6CC24A"),
     C(34, 37, 7, WHITE), C(66, 37, 7, WHITE), C(35, 38, 3.4, DARK), C(65, 38, 3.4, DARK),
     C(36, 36.8, 1, WHITE), C(66, 36.8, 1, WHITE),
     C(46, 54, 1, "#2F6B22"), C(54, 54, 1, "#2F6B22"),
     E(29, 64, 4.5, 3, "#F2989A", op=0.7), E(71, 64, 4.5, 3, "#F2989A", op=0.7),
     line("M32,60 Q50,76 68,60", "#2F6B22", 2.4)],
    # Penguin.
    [background("#CBEFF7"),
     E(50, 66, 28, 36, "#2E3A4F"),
     E(42, 54, 10, 13, WHITE), E(58, 54, 10, 13, WHITE), E(50, 68, 17, 18, WHITE),
     C(43, 52, 2.8, DARK), C(57, 52, 2.8, DARK), C(43.9, 51.1, 0.9, WHITE), C(57.9, 51.1, 0.9, WHITE),
     C(36, 60, 3, "#F7A8B8", op=0.7), C(64, 60, 3, "#F7A8B8", op=0.7),
     E(50, 60, 5.5, 3.2, "#F2A33A")],
    # Lion.
    [background("#E4F5C4"), E(50, 100, 24, 12, "#F2C14E"), C(50, 52, 28, "#C8762E")]
    + [C(50 + 25 * math.cos(math.radians(a)), 52 + 25 * math.sin(math.radians(a)), 9, "#C8762E")
       for a in range(0, 360, 30)]
    + [C(34, 36, 6, "#F2C14E"), C(66, 36, 6, "#F2C14E"), C(34, 36, 3, "#E0A13A"), C(66, 36, 3, "#E0A13A"),
       C(50, 54, 20, "#F2C14E"),
       C(43, 50, 2.6, DARK), C(57, 50, 2.6, DARK), C(43.9, 49.1, 0.9, WHITE), C(57.9, 49.1, 0.9, WHITE),
       E(50, 60, 8.5, 6, "#FBE3B0"),
       P("M47,56.5 L53,56.5 L50,59.5 Z", "#8A4B2A"), line("M50,59.5 L50,62", DARK, 1.4), smile_y(50, 62, w=4.5, depth=2.5)],
]


MORE_ANIMALS = [
    # Koala.
    [background("#DDEFE0"), C(26, 40, 13, "#9AA5B1"), C(74, 40, 13, "#9AA5B1"),
     C(26, 40, 7, "#E8D5DA"), C(74, 40, 7, "#E8D5DA"), E(50, 100, 28, 13, "#9AA5B1"), E(50, 56, 26, 24, "#9AA5B1"),
     C(39, 52, 2.8, DARK), C(61, 52, 2.8, DARK), C(39.9, 51.1, 0.9, WHITE), C(61.9, 51.1, 0.9, WHITE),
     C(33, 61, 3.2, BLUSH, op=0.45), C(67, 61, 3.2, BLUSH, op=0.45),
     E(50, 60, 6, 8.5, "#3B3B45"), smile_y(50, 69, w=4, depth=2)],
    # Pig.
    [background("#E0F2FE"), P("M28,40 L30,22 L44,34 Z", "#F4A7B9"), P("M72,40 L70,22 L56,34 Z", "#F4A7B9"),
     E(50, 100, 28, 13, "#F7B8C8"), E(50, 56, 26, 24, "#F7B8C8"),
     C(40, 50, 2.6, DARK), C(60, 50, 2.6, DARK), C(40.8, 49.2, 0.8, WHITE), C(60.8, 49.2, 0.8, WHITE),
     C(33, 60, 3.4, "#F08DA6", op=0.6), C(67, 60, 3.4, "#F08DA6", op=0.6),
     E(50, 62, 10, 7, "#F08DA6"), E(46.5, 62, 1.6, 2.4, "#B5526C"), E(53.5, 62, 1.6, 2.4, "#B5526C"),
     line("M45,71 Q50,74 55,71", DARK, 1.6)],
    # Mouse.
    [background("#FFF1D6"), C(28, 32, 12, "#B8BCC6"), C(72, 32, 12, "#B8BCC6"),
     C(28, 32, 7, "#F7B9C4"), C(72, 32, 7, "#F7B9C4"), E(50, 100, 26, 12, "#B8BCC6"), E(50, 58, 22, 22, "#B8BCC6"),
     E(50, 66, 9, 6, "#D7DAE0"), C(42, 54, 2.6, DARK), C(58, 54, 2.6, DARK), C(42.8, 53.2, 0.8, WHITE),
     C(58.8, 53.2, 0.8, WHITE), C(50, 63, 2.8, "#E5677D"), smile_y(50, 65.5, w=4, depth=2)]
    + whiskers("#7A7F8A", 64, 67),
    # Monkey.
    [background("#D9F2E6"), E(50, 100, 26, 12, "#8B5A3C"), C(25, 52, 8, "#8B5A3C"), C(75, 52, 8, "#8B5A3C"),
     C(25, 52, 4.5, "#F1C9A5"), C(75, 52, 4.5, "#F1C9A5"), E(50, 54, 25, 25, "#8B5A3C"),
     E(42, 52, 9, 10, "#F1C9A5"), E(58, 52, 9, 10, "#F1C9A5"), E(50, 64, 14, 10, "#F1C9A5"),
     C(42, 52, 2.6, DARK), C(58, 52, 2.6, DARK), C(42.8, 51.2, 0.8, WHITE), C(58.8, 51.2, 0.8, WHITE),
     C(48, 61, 1, DARK), C(52, 61, 1, DARK), line("M43,66 Q50,72 57,66", DARK, 1.8)],
    # Tiger.
    [background("#E3F2FD"), C(30, 34, 8, "#F28C28"), C(70, 34, 8, "#F28C28"),
     C(30, 34, 4, "#FFE0C2"), C(70, 34, 4, "#FFE0C2"), E(50, 100, 28, 13, "#F28C28"), E(50, 56, 27, 24, "#F28C28"),
     P("M47,32 L50,42 L53,32 Z", DARK), P("M23,50 L35,53 L23,56 Z", DARK), P("M77,50 L65,53 L77,56 Z", DARK),
     P("M25,60 L35,61 L27,64 Z", DARK), P("M75,60 L65,61 L73,64 Z", DARK),
     E(50, 64, 12, 8, "#FFF6E8"), E(40, 51, 3, 3.6, DARK), E(60, 51, 3, 3.6, DARK),
     C(41, 49.8, 1, WHITE), C(61, 49.8, 1, WHITE),
     P("M46.5,59 L53.5,59 L50,62.5 Z", "#E5677D"), smile_y(50, 62.5)],
    # Cow.
    [background("#E6F7D9"), E(50, 100, 28, 12, WHITE), E(60, 96, 7, 5, DARK),
     P("M31,33 C28,25 30,20 34,20 C34,25 35,28 39,31 Z", "#F5E6C8"),
     P("M69,33 C72,25 70,20 66,20 C66,25 65,28 61,31 Z", "#F5E6C8"),
     E(25, 46, 9, 5, WHITE, rot=-20), E(75, 46, 9, 5, WHITE, rot=20),
     E(25, 46, 5, 2.5, "#F7B8C8", rot=-20), E(75, 46, 5, 2.5, "#F7B8C8", rot=20),
     E(50, 52, 23, 24, WHITE), E(40, 38, 7, 5, DARK), E(63, 44, 5, 6, DARK),
     E(50, 66, 15, 10, "#F7B8C8"), E(44, 66, 2, 2.8, "#B5526C"), E(56, 66, 2, 2.8, "#B5526C"),
     C(41, 51, 2.8, DARK), C(59, 51, 2.8, DARK), C(41.9, 50.1, 0.9, WHITE), C(59.9, 50.1, 0.9, WHITE)],
    # Chick.
    [background("#D6EAF8"), P("M45,33 Q47,23 50,32 Q53,21 55,33 Z", "#FFC300"),
     E(24, 70, 6, 12, "#F5C518", rot=20), E(76, 70, 6, 12, "#F5C518", rot=-20),
     C(50, 62, 28, "#FFD93D"),
     C(41, 55, 3, DARK), C(59, 55, 3, DARK), C(42, 54, 1, WHITE), C(60, 54, 1, WHITE),
     C(34, 63, 3.5, BLUSH, op=0.45), C(66, 63, 3.5, BLUSH, op=0.45),
     P("M45,61 L55,61 L50,68 Z", "#F28C28")],
    # Sheep.
    [background("#FFE4EC"), E(50, 100, 28, 13, WHITE), C(50, 50, 24, WHITE)]
    + [C(50 + 22 * math.cos(math.radians(a)), 50 + 22 * math.sin(math.radians(a)), 9, WHITE) for a in range(0, 360, 36)]
    + [E(28, 54, 8, 4, "#3B3B45", rot=20), E(72, 54, 8, 4, "#3B3B45", rot=-20), E(50, 57, 14, 17, "#3B3B45"),
       C(44, 37, 7, WHITE), C(56, 37, 7, WHITE), C(50, 33, 7, WHITE),
       C(44.5, 54, 2.6, WHITE), C(55.5, 54, 2.6, WHITE), C(45, 54.5, 1.3, DARK), C(56, 54.5, 1.3, DARK),
       line("M46,64 Q50,67 54,64", "#E8E8E8", 1.5)],
    # Raccoon.
    [background("#FFF4C2"), P("M26,42 L30,20 L44,32 Z", "#8E8E99"), P("M74,42 L70,20 L56,32 Z", "#8E8E99"),
     P("M30,36 L32,26 L39,32 Z", "#3B3B45"), P("M70,36 L68,26 L61,32 Z", "#3B3B45"),
     E(50, 100, 28, 13, "#8E8E99"), E(50, 56, 27, 23, "#8E8E99"),
     E(50, 66, 13, 9, "#F2F2F2"),
     P("M24,52 C30,44 42,46 50,52 C58,46 70,44 76,52 C70,60 58,60 50,56 C42,60 30,60 24,52 Z", "#3B3B45"),
     C(40, 52, 3.2, WHITE), C(60, 52, 3.2, WHITE), C(40, 52, 1.7, DARK), C(60, 52, 1.7, DARK),
     E(50, 62, 3.5, 2.5, DARK), smile_y(50, 64.5, w=4, depth=2.5)],
    # Elephant.
    [background("#FDE2E4"), E(50, 104, 28, 12, "#9BB1C8"),
     E(25, 52, 16, 20, "#9BB1C8"), E(75, 52, 16, 20, "#9BB1C8"),
     E(25, 52, 10, 14, "#F7B9C4"), E(75, 52, 10, 14, "#F7B9C4"),
     E(50, 50, 22, 22, "#9BB1C8"),
     P("M44,56 L56,56 L55,78 C55,85 46,87 43,82 C46,82 49,80 49,76 Z", "#9BB1C8"),
     C(41, 48, 2.6, DARK), C(59, 48, 2.6, DARK), C(41.8, 47.2, 0.8, WHITE), C(59.8, 47.2, 0.8, WHITE),
     C(36, 57, 3, BLUSH, op=0.45), C(64, 57, 3, BLUSH, op=0.45)],
    # Hedgehog.
    [background("#E7F5DC"), E(50, 100, 28, 12, "#7A5230"),
     P("M16,72 L20,54 L13,47 L24,41 L20,28 L32,31 L34,18 L44,26 L50,14 L56,26 L66,18 L68,31 L80,28 L76,41 "
       "L87,47 L80,54 L84,72 Z", "#7A5230"),
     E(50, 62, 20, 18, "#F3D2B0"),
     C(43, 58, 2.6, DARK), C(57, 58, 2.6, DARK), C(43.8, 57.2, 0.8, WHITE), C(57.8, 57.2, 0.8, WHITE),
     C(38, 65, 3, BLUSH, op=0.45), C(62, 65, 3, BLUSH, op=0.45),
     C(50, 65, 3, DARK), smile_y(50, 68, w=4, depth=2)],
    # Unicorn.
    [background("#EDE7F6"), E(50, 100, 26, 12, WHITE),
     P("M27,52 C22,30 36,20 47,25 C40,30 36,40 37,54 Z", "#C77DFF"),
     P("M35,34 L36,20 L45,29 Z", WHITE), P("M65,34 L64,20 L55,29 Z", WHITE),
     E(50, 54, 22, 24, WHITE), P("M46,30 L50,7 L54,30 Z", "#FFD166"),
     line("M47.5,24 L52.5,21 M48.5,17 L51.5,15.5", "#E0A13A", 1.2),
     C(43, 31, 6, "#FF8FAB"), C(53, 30, 6, "#7CC6FE"),
     E(50, 67, 13, 9, "#FBE3F0"), E(46, 67, 1.4, 2, "#C78BA8"), E(54, 67, 1.4, 2, "#C78BA8"),
     line("M38,52 Q41,48.5 44,52", DARK, 2), line("M56,52 Q59,48.5 62,52", DARK, 2),
     C(36, 59, 3.2, BLUSH, op=0.45), C(64, 59, 3.2, BLUSH, op=0.45)],
    # Octopus.
    [background("#D6F5F5"),
     E(22, 86, 7, 16, "#B07CC6", rot=35), E(37, 90, 7, 16, "#B07CC6", rot=12),
     E(63, 90, 7, 16, "#B07CC6", rot=-12), E(78, 86, 7, 16, "#B07CC6", rot=-35),
     E(50, 50, 28, 30, "#B07CC6"),
     C(37, 30, 3, "#9A63B3"), C(62, 28, 2.5, "#9A63B3"), C(67, 39, 2, "#9A63B3"),
     C(40, 52, 5.5, WHITE), C(60, 52, 5.5, WHITE), C(41, 53, 2.8, DARK), C(61, 53, 2.8, DARK),
     C(42, 51.6, 0.9, WHITE), C(62, 51.6, 0.9, WHITE),
     C(32, 62, 3.5, BLUSH, op=0.5), C(68, 62, 3.5, BLUSH, op=0.5), line("M43,63 Q50,69 57,63", DARK, 2)],
]

# ---- robots: one template, varied parts ----

SCREEN = "#1E2A38"


def robot(bg, body, accent, head, eyes_kind, mouth_kind, antenna):
    s = [background(bg), P("M18,100 C18,84 26,76 50,76 C74,76 82,84 82,100 Z", body),
         R(40, 84, 20, 10, SCREEN, rx=2), C(45, 89, 2, accent), C(55, 89, 2, "#FFFFFF", op=0.7),
         R(44, 64, 12, 13, "#8A93A6", rx=2)]
    top = {"box": 26, "round": 23, "tv": 30, "dome": 22, "hex": 24}[head]
    if antenna == "ball":
        s += [line(f"M50,{top} L50,13", "#8A93A6", 2.5), C(50, 11, 3.8, accent)]
    elif antenna == "double":
        s += [line(f"M42,{top + 2} L35,12", "#8A93A6", 2.2), line(f"M58,{top + 2} L65,12", "#8A93A6", 2.2),
              C(35, 11, 3, accent), C(65, 11, 3, accent)]
    elif antenna == "bolt":
        s += [P(f"M50,{top + 1} L45,13 L51,13 L47,4 L57,15 L51,15 L54,{top + 1} Z", accent)]
    s += [R(20, 40, 7, 14, accent, rx=2), R(73, 40, 7, 14, accent, rx=2)]
    s += {
        "box": [R(26, 26, 48, 42, body, rx=8)],
        "round": [C(50, 47, 24, body)],
        "tv": [R(24, 30, 52, 38, body, rx=5), R(30, 35, 40, 28, SCREEN, rx=4)],
        "dome": [P("M26,66 L26,46 C26,30 37,22 50,22 C63,22 74,30 74,46 L74,66 Z", body)],
        "hex": [P("M38,24 L62,24 L76,46 L62,68 L38,68 L24,46 Z", body)],
    }[head]
    if head != "tv":
        s += [C(31, 32, 1.4, "#FFFFFF", op=0.5), C(69, 32, 1.4, "#FFFFFF", op=0.5)]
    s += {
        "round": [C(40, 45, 6, WHITE), C(60, 45, 6, WHITE), C(40, 45, 3, DARK), C(60, 45, 3, DARK)],
        "visor": [R(32, 40, 36, 10, SCREEN, rx=5), R(36, 43, 28, 4, accent, rx=2)],
        "cyclops": [C(50, 45, 9, WHITE), C(50, 45, 5, accent), C(50, 45, 2, DARK)],
        "square": [R(34, 39, 11, 11, accent, rx=1.5), R(55, 39, 11, 11, accent, rx=1.5),
                   R(36, 41, 3, 3, WHITE, rx=0.5), R(57, 41, 3, 3, WHITE, rx=0.5)],
        "happy": [line("M34,47 Q40,40 46,47", accent if head == "tv" else DARK, 3),
                  line("M54,47 Q60,40 66,47", accent if head == "tv" else DARK, 3)],
    }[eyes_kind]
    ink = accent if head == "tv" else DARK
    s += {
        "grill": [R(38, 55, 24, 8, SCREEN, rx=2), line("M44,55 V63 M50,55 V63 M56,55 V63", body, 1.5)],
        "led": [R(40, 57, 20, 4, accent, rx=2)],
        "smile": [line("M41,56 Q50,63 59,56", ink, 2.5)],
        "zigzag": [line("M38,58 L42,55 L46,58 L50,55 L54,58 L58,55 L62,58", ink, 2)],
    }[mouth_kind]
    return s


ROBOTS = [
    robot("#FFE8D6", "#9FB4C7", "#F25C54", "box", "round", "grill", "ball"),
    robot("#D6EAF8", "#F2C14E", "#3DDC97", "round", "visor", "led", "double"),
    robot("#E8F5E9", "#E76F51", "#7CF5FF", "tv", "square", "led", "double"),
    robot("#FDE2E4", "#7E8CE0", "#FFD166", "dome", "cyclops", "grill", "bolt"),
    robot("#FFF3C4", "#5FC9A8", "#FF8FAB", "hex", "happy", "smile", "ball"),
    robot("#D7F0FF", "#B0B7C3", "#FF5D5D", "box", "visor", "zigzag", "none"),
    robot("#E7F9E7", "#C77DFF", "#FFE066", "round", "square", "smile", "bolt"),
    robot("#FCE7D7", "#4D9DE0", "#F7B801", "dome", "round", "led", "ball"),
    robot("#F4E1D2", "#3D405B", "#81F7B2", "tv", "happy", "smile", "ball"),
    robot("#E0ECFF", "#F28482", "#84DCC6", "hex", "cyclops", "zigzag", "double"),
    robot("#FFF0F5", "#2EC4B6", "#FF9F1C", "box", "square", "grill", "double"),
    robot("#E6F4F1", "#B07D56", "#FFD166", "round", "round", "grill", "none"),
]

# ---- monsters: one template, varied parts ----

HORN = "#D9B26F"


def monster(bg, color, shape, eyes_kind, horns, mouth_kind, spots=None, belly=None):
    s = [background(bg)]
    if horns == "two":
        s += [P("M33,34 L25,10 L42,27 Z", HORN), P("M67,34 L75,10 L58,27 Z", HORN)]
    elif horns == "curved":
        s += [P("M31,34 C19,30 18,14 28,9 C26,20 31,26 39,28 Z", HORN),
              P("M69,34 C81,30 82,14 72,9 C74,20 69,26 61,28 Z", HORN)]
    elif horns == "antenna":
        s += [line("M42,24 L36,10", color, 3), line("M58,24 L64,10", color, 3),
              C(36, 9, 4, spots or "#FFD166"), C(64, 9, 4, spots or "#FFD166")]
    if shape == "blob":
        s.append(P("M16,100 C12,70 20,26 50,24 C80,26 88,70 84,100 Z", color))
    elif shape == "round":
        s += [R(22, 70, 56, 30, color), C(50, 54, 30, color)]
    elif shape == "tall":
        s.append(P("M27,100 L27,36 C27,22 38,16 50,16 C62,16 73,22 73,36 L73,100 Z", color))
    elif shape == "fuzzy":
        s.append(P("M16,100 C12,70 20,26 50,24 C80,26 88,70 84,100 Z", color))
        s += [C(50 + 33 * math.cos(math.radians(a)), 62 + 36 * math.sin(math.radians(a)), 7, color)
              for a in range(180, 361, 20)]
    elif shape == "drip":
        s.append(P("M20,92 L20,44 C20,26 34,20 50,20 C66,20 80,26 80,44 L80,92 C80,98 74,98 74,92 "
                   "C74,86 66,86 66,94 C66,100 58,100 58,94 L58,90 C58,84 50,84 50,92 C50,98 42,98 42,92 "
                   "C42,86 34,86 34,94 C34,100 26,100 26,94 C26,88 20,88 20,92 Z", color))
    if belly:
        s.append(E(50, 92, 18, 14, belly))
    if spots:
        s += [C(30, 70, 4, spots, op=0.55), C(70, 66, 3, spots, op=0.55), C(64, 80, 2.5, spots, op=0.55),
              C(34, 34, 2.5, spots, op=0.55)]
    if eyes_kind == "one":
        s += [C(50, 44, 11, WHITE), C(50, 45, 5.5, DARK), C(52, 43, 1.8, WHITE)]
    elif eyes_kind == "two":
        s += [C(39, 45, 7, WHITE), C(61, 45, 7, WHITE), C(40, 46, 3.4, DARK), C(60, 46, 3.4, DARK),
              C(41, 45, 1.1, WHITE), C(61, 45, 1.1, WHITE)]
    elif eyes_kind == "three":
        s += [C(35, 47, 5.5, WHITE), C(50, 38, 6.5, WHITE), C(65, 47, 5.5, WHITE),
              C(35.5, 48, 2.6, DARK), C(50.5, 39, 3, DARK), C(64.5, 48, 2.6, DARK)]
    elif eyes_kind == "sleepy":
        s += [C(39, 46, 7, WHITE), C(61, 46, 7, WHITE), C(39, 48.5, 3, DARK), C(61, 48.5, 3, DARK),
              P("M32,46 a7,7 0 0,1 14,0 Z", color), P("M54,46 a7,7 0 0,1 14,0 Z", color),
              line("M32,46 L46,46 M54,46 L68,46", DARK, 1.4)]
    elif eyes_kind == "angry":
        s += [C(39, 47, 6.5, WHITE), C(61, 47, 6.5, WHITE), C(40, 48, 3.2, DARK), C(60, 48, 3.2, DARK),
              line("M31,38 L46,43 M69,38 L54,43", DARK, 2.6)]
    if mouth_kind == "fangs":
        s += [P("M36,61 Q50,74 64,61 Z", DARK), P("M40,62 L43,68 L46,63 Z", WHITE), P("M54,63 L57,68 L60,62 Z", WHITE)]
    elif mouth_kind == "zigzag":
        s += [R(34, 59, 32, 11, DARK, rx=4),
              P("M34,60 L38,65 L42,60 L46,65 L50,60 L54,65 L58,60 L62,65 L66,60 Z", WHITE)]
    elif mouth_kind == "grin":
        s += [line("M36,61 Q50,72 64,61", DARK, 2.5), R(46, 64.5, 5, 4, WHITE, rx=1)]
    elif mouth_kind == "tongue":
        s += [P("M38,60 Q50,77 62,60 Z", DARK), E(50, 68, 5, 3.5, "#F06A7D")]
    elif mouth_kind == "o":
        s += [E(50, 64, 6, 7, DARK), E(50, 67, 3.5, 2.5, "#F06A7D")]
    return s


MONSTERS = [
    monster("#E0F7FA", "#7BC67E", "blob", "one", "two", "fangs", spots="#3E8E41"),
    monster("#FFF3E0", "#9B5DE5", "round", "three", "none", "zigzag", belly="#C9A7F5"),
    monster("#FCE4EC", "#4CC9F0", "fuzzy", "two", "none", "grin"),
    monster("#E8F5E9", "#F15BB5", "tall", "sleepy", "antenna", "tongue", spots="#FFD166"),
    monster("#EDE7F6", "#FF9F1C", "drip", "angry", "curved", "fangs"),
    monster("#FFFDE7", "#3A86FF", "blob", "two", "curved", "tongue", belly="#8EC5FF"),
    monster("#E3F2FD", "#E63946", "round", "one", "two", "grin", spots="#FFB4A2"),
    monster("#F1F8E9", "#6A4C93", "fuzzy", "three", "none", "fangs"),
    monster("#FFF0F6", "#06D6A0", "tall", "two", "antenna", "zigzag", spots="#1B9AAA"),
    monster("#E0F2F1", "#FFB703", "drip", "sleepy", "none", "o"),
    monster("#FDF2E9", "#8338EC", "blob", "angry", "two", "zigzag", belly="#B392F0"),
    monster("#EAF4FF", "#43AA8B", "round", "two", "antenna", "o", spots="#F9C74F"),
    monster("#FFF8E1", "#F94144", "fuzzy", "one", "curved", "tongue"),
]

# ---- sci-fi characters ----


def stars(*pts):
    return [C(x, y, r, WHITE, op=0.8) for x, y, r in pts]


SCIFI = [
    # Astronaut.
    [background("#22305A")] + stars((14, 18, 1.2), (84, 14, 1), (88, 60, 1.3), (10, 66, 1), (22, 40, 0.8))
    + [P("M16,100 C16,80 30,72 50,72 C70,72 84,80 84,100 Z", "#F0F0F5"), R(58, 84, 10, 7, "#E76F51", rx=1),
       R(40, 70, 20, 8, "#C9CED6", rx=3), C(50, 46, 28, "#F0F0F5"), E(50, 48, 20, 17, "#2B3A67"),
       E(41, 42, 7, 3.5, WHITE, op=0.45, rot=-25), C(59, 54, 1.8, WHITE, op=0.35),
       C(24, 46, 4, "#C9CED6"), C(76, 46, 4, "#C9CED6")],
    # Green alien.
    [background("#E0F7FA"), P("M16,100 C16,80 30,72 50,72 C70,72 84,80 84,100 Z", "#6C5CE7"),
     R(44, 62, 12, 12, "#7DDB6A", rx=3),
     line("M40,22 L33,7", "#7DDB6A", 2.5), line("M60,22 L67,7", "#7DDB6A", 2.5),
     C(33, 6, 3.5, "#FF8FAB"), C(67, 6, 3.5, "#FF8FAB"),
     P("M24,44 C24,24 36,16 50,16 C64,16 76,24 76,44 C76,60 60,72 50,72 C40,72 24,60 24,44 Z", "#7DDB6A"),
     E(39, 45, 8, 10, DARK, rot=25), E(61, 45, 8, 10, DARK, rot=-25),
     C(37, 41, 2.2, WHITE), C(59, 41, 2.2, WHITE), line("M45,61 Q50,64 55,61", DARK, 2)],
    # Grey alien.
    [background("#2D2A4A")] + stars((12, 14, 1), (88, 20, 1.2), (84, 70, 0.9), (16, 76, 1.1))
    + [P("M16,100 C16,80 30,72 50,72 C70,72 84,80 84,100 Z", "#4B4F63"), R(45, 62, 10, 12, "#B8C0CC", rx=3),
       P("M20,40 C20,18 34,10 50,10 C66,10 80,18 80,40 C80,56 60,72 50,72 C40,72 20,56 20,40 Z", "#B8C0CC"),
       E(37, 46, 9, 5, DARK, rot=25), E(63, 46, 9, 5, DARK, rot=-25),
       E(35, 44.5, 2.2, 1, WHITE, op=0.7, rot=25), E(61, 44.5, 2.2, 1, WHITE, op=0.7, rot=-25),
       line("M47,62 L53,62", DARK, 1.6)],
    # Cyborg.
    person("#E3E7EE", SKIN["light"], "#37474F", front=[
        P("M28.5,46 C27,26 37,20 50,20 L50,31 C42,32 34,36 28.5,46 Z", "#3A2A1E")], extras=[
        P("M50,23 C62,23 71,32 71,46 C71,58 64,68 50,69 Z", "#9AA5B1"),
        line("M50,23 L50,69", "#6B7480", 1.4), line("M52,36 L66,36 M52,60 L64,60", "#6B7480", 1),
        C(58, 48, 6, "#FF3B3B", op=0.3), C(58, 48, 3.2, "#FF3B3B"), C(57.2, 47.2, 1, WHITE),
        C(65, 32, 1.2, DARK), C(65, 62, 1.2, DARK), C(71, 48, 4.5, "#6B7480")]),
    # Space pilot.
    person("#DDE8F8", SKIN["tan"], "#2F6FB0", front=[
        P("M27,46 C27,24 37,17 50,17 C63,17 73,24 73,46 C70,38 64,36 50,36 C36,36 30,38 27,46 Z", "#E9ECEF"),
        R(32, 26, 36, 10, "#2B3A67", rx=5), C(41, 31, 4, "#7CC6FE"), C(59, 31, 4, "#7CC6FE"),
        C(40, 29.6, 1.2, WHITE), C(58, 29.6, 1.2, WHITE)], extras=[
        line("M29,52 Q31,62 40,62", DARK, 1.6), C(41, 62, 1.8, DARK), R(56, 82, 12, 4, "#F9C74F", rx=1)]),
    # Android.
    person("#E8E1F5", "#E3F2FD", "#FFFFFF", blush=None, front=[
        P("M28,48 C25,26 36,19 50,19 C64,19 75,26 72,48 C70,36 64,30 50,30 C36,30 30,36 28,48 Z", "#CFD8DC")],
        extras=[C(42, 48, 2.7, "#29B6F6"), C(58, 48, 2.7, "#29B6F6"), C(42.9, 47.1, 0.9, WHITE),
                C(58.9, 47.1, 0.9, WHITE), line("M33,55 L36,55 L38,58 M67,55 L64,55 L62,58", "#4FC3F7", 1),
                C(30, 44, 1.8, "#4FC3F7"), line("M38,80 L50,86 L62,80", "#4FC3F7", 1.6)]),
    # Cyberpunk.
    person("#FFE0F0", SKIN["brown"], "#2B2B33", back=[P("M40,72 L50,80 L60,72 Z", "#FF2E88")], front=[
        P("M44,32 L45,9 L50,23 L52,6 L55,23 L59,11 L57,32 Z", "#FF2E88")], extras=[
        R(31, 42, 38, 10, "#00F5D4", rx=5),
        line("M35,45 L45,45", WHITE, 1.2), C(71, 54, 1.8, "#F9C74F")]),
    # Space captain.
    person("#FFF4E0", SKIN["peach"], "#B71C1C", back=[P("M42,72 L50,80 L58,72 Z", "#1A1A2E")], front=[
        P("M29,44 C28,26 37,21 50,21 C63,21 72,26 71,44 C66,32 58,29 46,31 C38,32 32,36 29,44 Z", "#2B1D14")],
        extras=[P("M58,84 L61,80 L64,84 L62,90 L60,90 Z", "#FFD166"), line("M26,94 L36,86", "#FFD166", 2)]),
    # Space ranger.
    person("#E6F4EA", SKIN["light"], "#43AA8B", neck=False, ears=False, head=(50, 50, 16, 18), blush=(40, 60),
           back=[C(50, 46, 28, "#43AA8B"), R(47, 13, 6, 14, "#F9C74F", rx=3),
                 C(23, 48, 5, "#F9C74F"), C(77, 48, 5, "#F9C74F")],
           extras=[line("M34,34 Q50,28 66,34", "#2D7A60", 2)]),
    # Three-eyed alien.
    [background("#FFF6D6"), P("M16,100 C16,80 30,72 50,72 C70,72 84,80 84,100 Z", "#00BBF9"),
     R(44, 62, 12, 12, "#9B5DE5", rx=3),
     P("M28,46 L12,34 L29,38 Z", "#9B5DE5"), P("M72,46 L88,34 L71,38 Z", "#9B5DE5"),
     C(50, 48, 24, "#9B5DE5"),
     C(39, 49, 5, WHITE), C(61, 49, 5, WHITE), C(50, 37, 4.5, WHITE),
     C(39.5, 50, 2.4, DARK), C(60.5, 50, 2.4, DARK), C(50, 38, 2.2, DARK),
     C(34, 58, 3, "#FF8FAB", op=0.6), C(66, 58, 3, "#FF8FAB", op=0.6), line("M43,59 Q50,65 57,59", DARK, 2)],
    # Visor helmet.
    [background("#EFE6DD"), P("M16,100 C16,80 30,72 50,72 C70,72 84,80 84,100 Z", "#6B705C"),
     R(40, 72, 20, 8, "#A5A58D", rx=3), R(44, 62, 12, 12, "#8A8F96", rx=3),
     R(26, 22, 48, 46, "#C9CED6", rx=17), E(50, 46, 19, 8, SCREEN), R(36, 44, 28, 2.5, "#FF6B6B", rx=1.2),
     R(73, 36, 5, 16, "#8A8F96", rx=2.5), line("M33,30 L40,27", WHITE, 1.6), line("M44,60 L56,60", "#8A8F96", 2)],
    # Bubble-helmet alien.
    [background("#1F2A44")] + stars((12, 16, 1.1), (86, 12, 0.9), (90, 54, 1.2), (12, 60, 0.9))
    + [P("M16,100 C16,80 30,72 50,72 C70,72 84,80 84,100 Z", "#F9C74F"), R(32, 68, 36, 8, "#C9CED6", rx=4),
       line("M50,36 L50,24", "#FF9F1C", 2.2), C(50, 23, 2.8, "#43AA8B"),
       C(50, 52, 17, "#FF9F1C"), C(43, 50, 4.5, WHITE), C(57, 50, 4.5, WHITE),
       C(43.5, 51, 2.2, DARK), C(56.5, 51, 2.2, DARK), line("M45,59 Q50,63 55,59", DARK, 1.8),
       C(50, 46, 27, "#BDE0FE", op=0.35), line("M30,34 Q36,26 44,24", WHITE, 2.2)],
]


def svg(shapes):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="512" height="512">']
    for s in shapes:
        attrs = [f'd="{s["d"]}"', f'fill="{s["fill"] or "none"}"']
        if s["op"] != 1.0:
            attrs.append(f'fill-opacity="{n(s["op"])}"')
        if s["stroke"]:
            attrs += [f'stroke="{s["stroke"]}"', f'stroke-width="{n(s["sw"])}"', 'stroke-linecap="round"',
                      'stroke-linejoin="round"']
        el = f'  <path {" ".join(attrs)}/>'
        if s["rot"]:
            a, px, py = s["rot"]
            el = f'  <g transform="rotate({n(a)} {n(px)} {n(py)})">{el.strip()}</g>'
        out.append(el)
    out.append("</svg>")
    return "\n".join(out) + "\n"


def vector(shapes, name):
    out = [f'<!-- Generated by core/design/avatars/generate.py from avatars/svg/{name}.svg: edit the script. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="100dp" android:height="100dp"',
           '    android:viewportWidth="100" android:viewportHeight="100">']
    for s in shapes:
        attrs = [f'android:pathData="{s["d"]}"']
        if s["fill"]:
            attrs.append(f'android:fillColor="{s["fill"]}"')
            if s["op"] != 1.0:
                attrs.append(f'android:fillAlpha="{n(s["op"])}"')
        if s["stroke"]:
            attrs += [f'android:strokeColor="{s["stroke"]}"', f'android:strokeWidth="{n(s["sw"])}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        el = f'    <path {" ".join(attrs)}/>'
        if s["rot"]:
            a, px, py = s["rot"]
            el = (f'    <group android:rotation="{n(a)}" android:pivotX="{n(px)}" android:pivotY="{n(py)}">\n'
                  f'    {el}\n    </group>')
        out.append(el)
    out.append("</vector>")
    return "\n".join(out) + "\n"


def main():
    os.makedirs(SVG_DIR, exist_ok=True)
    os.makedirs(DRAWABLE_DIR, exist_ok=True)
    kinds = (("person", PEOPLE), ("animal", ANIMALS + MORE_ANIMALS), ("robot", ROBOTS),
             ("monster", MONSTERS), ("scifi", SCIFI))
    names = {kind: [] for kind, _ in kinds}
    for kind, items in kinds:
        for i, shapes in enumerate(items, 1):
            name = f"avatar_{kind}_{i:02d}"
            names[kind].append(name)
            with open(os.path.join(SVG_DIR, name + ".svg"), "w", newline="\n", encoding="utf-8") as f:
                f.write(svg(shapes))
            with open(os.path.join(DRAWABLE_DIR, "bundle_" + name + ".xml"), "w", newline="\n", encoding="utf-8") as f:
                f.write(vector(shapes, name))

    def ids(kind):
        return "\n".join(f"    R.drawable.bundle_{x}," for x in names[kind])

    kotlin = f"""// Generated by core/design/avatars/generate.py: edit the script, not this file.
package bundle.core.design

/** The cartoon people, drawn in avatars/svg/avatar_person_*.svg. */
internal val PEOPLE_AVATARS = intArrayOf(
{ids("person")}
)

/** The cartoon animals, drawn in avatars/svg/avatar_animal_*.svg. */
internal val ANIMAL_AVATARS = intArrayOf(
{ids("animal")}
)

/** The robots, drawn in avatars/svg/avatar_robot_*.svg. */
internal val ROBOT_AVATARS = intArrayOf(
{ids("robot")}
)

/** The monsters, drawn in avatars/svg/avatar_monster_*.svg. */
internal val MONSTER_AVATARS = intArrayOf(
{ids("monster")}
)

/** The sci-fi characters, drawn in avatars/svg/avatar_scifi_*.svg. */
internal val SCIFI_AVATARS = intArrayOf(
{ids("scifi")}
)
"""
    with open(KOTLIN_FILE, "w", newline="\n", encoding="utf-8") as f:
        f.write(kotlin + PICK_FUNCTION)

    every = "\n".join(f"    R.drawable.bundle_{x}," for kind, _ in kinds for x in names[kind])
    messages_kotlin = f"""// Generated by core/design/avatars/generate.py: edit the script, not this file.
package com.android.messaging.ui.common.components.participant

import com.android.messaging.R

/**
 * Bundle: every cartoon avatar, in the same order as Contacts and Phone (core/design), so a
 * contact with no photo shows the same one here. The drawables come from core/design/avatars/res.
 */
internal val CARTOON_AVATARS = intArrayOf(
{every}
)
{PICK_FUNCTION}"""
    if os.path.isdir(os.path.dirname(MESSAGES_KOTLIN_FILE)):
        with open(MESSAGES_KOTLIN_FILE, "w", newline="\n", encoding="utf-8") as f:
            f.write(messages_kotlin)
    print(", ".join(f"{len(v)} {k}" for k, v in names.items()), f"= {sum(len(v) for v in names.values())} avatars")


if __name__ == "__main__":
    main()
