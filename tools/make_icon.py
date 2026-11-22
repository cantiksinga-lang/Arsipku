"""Membuat icon GarasiKu (kunci inggris di atas gradasi biru) untuk semua densitas."""
from PIL import Image, ImageDraw
import os

S = 1024
W = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)
RES = 'android/app/src/main/res'


def wrench_layer():
    layer = Image.new('RGBA', (S, S), CLEAR)
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((300, 468, 770, 556), radius=44, fill=W)   # gagang
    d.ellipse((175, 387, 425, 637), fill=W)                        # kepala
    d.rectangle((120, 466, 300, 558), fill=CLEAR)                  # mulut kunci
    d.ellipse((698, 440, 842, 584), fill=W)                        # cincin ujung
    d.ellipse((740, 482, 800, 542), fill=CLEAR)                    # lubang cincin
    return layer.rotate(45, resample=Image.BICUBIC)


def place(layer, scale):
    """Potong ke bentuk kunci, skala agar sisi terpanjang = S*scale, taruh di tengah."""
    box = layer.getbbox()
    l = layer.crop(box)
    target = int(S * scale)
    k = target / max(l.width, l.height)
    l = l.resize((max(1, int(l.width * k)), max(1, int(l.height * k))), Image.LANCZOS)
    c = Image.new('RGBA', (S, S), CLEAR)
    c.alpha_composite(l, ((S - l.width) // 2, (S - l.height) // 2))
    return c


def gradient():
    g = Image.new('RGBA', (S, S))
    d = ImageDraw.Draw(g)
    top, bot = (24, 160, 224), (8, 88, 168)
    for y in range(S):
        t = y / (S - 1)
        col = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)) + (255,)
        d.line([(0, y), (S, y)], fill=col)
    return g


wr = wrench_layer()

# icon lama (API < 26): kotak membulat
base = gradient()
base.alpha_composite(place(wr, 0.62))
mask = Image.new('L', (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=230, fill=255)
legacy = Image.new('RGBA', (S, S), CLEAR)
legacy.paste(base, (0, 0), mask)

# foreground adaptive icon (zona aman 66%)
fg = place(wr, 0.50)

legacy_sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
fg_sizes = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}

for dens, px in legacy_sizes.items():
    p = f'{RES}/mipmap-{dens}'
    os.makedirs(p, exist_ok=True)
    legacy.resize((px, px), Image.LANCZOS).save(f'{p}/ic_launcher.png')
for dens, px in fg_sizes.items():
    p = f'{RES}/mipmap-{dens}'
    os.makedirs(p, exist_ok=True)
    fg.resize((px, px), Image.LANCZOS).save(f'{p}/ic_launcher_foreground.png')

legacy.save('icon_preview.png')
print('Icon dibuat')
