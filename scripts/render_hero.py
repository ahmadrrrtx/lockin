#!/usr/bin/env python3
"""Re-render website/assets/hero-mockup.png (brand artwork, no external tools).

Usage:  python3 scripts/render_hero.py
Requires: Pillow (pip install pillow)
Design tokens: branding/SPEC.md — edit colors here to match a rebrand.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter

SC = 2
W, H = 1600*SC, 1000*SC
BG, SOFT = (250,250,248), (241,241,238)
CHAR, PANEL = (22,23,28), (32,33,39)
BONE, AMBER = (241,238,231), (232,163,75)
GRAY, INK, MUTED, OK, WHITE = (60,62,70), (20,21,26), (139,142,151), (47,143,83), (255,255,255)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_R = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
def f(sz, bold=True): return ImageFont.truetype(FONT if bold else FONT_R, sz)

img = Image.new("RGBA", (W, H), BG + (255,))
d = ImageDraw.Draw(img)
d.ellipse([W*0.42, -H*0.25, W*1.25, H*0.95], fill=SOFT + (255,))

def hourglass(dr, cx, cy, s, glass=BONE, sand=AMBER):
    k = s/108
    def P(x, y): return (cx + (x-54)*k, cy + (y-54)*k)
    def bez(p0,c1,c2,p1,n=40):
        pts=[]
        for i in range(n+1):
            t=i/n; mt=1-t
            pts.append((mt**3*p0[0]+3*mt*mt*t*c1[0]+3*mt*t*t*c2[0]+t**3*p1[0],
                        mt**3*p0[1]+3*mt*mt*t*c1[1]+3*mt*t*t*c2[1]+t**3*p1[1]))
        return pts
    dr.rounded_rectangle([*P(34,27), *P(74,33.5)], radius=3*k, fill=glass)
    dr.rounded_rectangle([*P(34,74.5), *P(74,81)], radius=3*k, fill=glass)
    upper=[P(37,33.5)]+[P(*q) for q in bez((37,33.5),(37,45),(47,51.5),(54,54))] + \
          [P(*q) for q in bez((54,54),(61,51.5),(71,45),(71,33.5))]
    dr.polygon(upper, fill=glass)
    lower=[P(37,74.5)]+[P(*q) for q in bez((37,74.5),(37,63),(47,57),(54,54))] + \
          [P(*q) for q in bez((54,54),(61,57),(71,63),(71,74.5))]
    dr.polygon(lower, fill=glass)
    mound=[P(45.5,70)]+[P(*q) for q in bez((45.5,70),(48,63.5),(60,63.5),(62.5,70))]+[P(45.5,70)]
    dr.polygon(mound, fill=sand)

def rrect(dr, box, r, fill):
    dr.rounded_rectangle(box, radius=r, fill=fill)

def shadow_paste(base, layer, xy, blur=28, alpha=70):
    sh = Image.new("RGBA", base.size, (0,0,0,0))
    a = layer.split()[-1].point(lambda p: int(p*alpha/255))
    sh.paste(Image.new("RGBA", layer.size, (10,10,14,255)), xy, a)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    base.alpha_composite(sh); base.alpha_composite(layer, xy)

def phone_active():
    w,h = 430*SC, 860*SC
    L = Image.new("RGBA", (w,h), (0,0,0,0)); dr = ImageDraw.Draw(L)
    rrect(dr, [0,0,w-1,h-1], 54*SC, CHAR)
    m = 10*SC; sw, sh = w-2*m, h-2*m
    rrect(dr, [m,m,m+sw,m+sh], 46*SC, PANEL)
    dr.text((m+34*SC, m+26*SC), "9:41", font=f(20*SC, False), fill=MUTED)
    dr.ellipse([m+sw-58*SC, m+26*SC, m+sw-34*SC, m+50*SC], outline=MUTED, width=3*SC)
    chip = [m+sw//2-128*SC, m+96*SC, m+sw//2+128*SC, m+146*SC]
    rrect(dr, chip, 25*SC, (44,46,54))
    dr.text((chip[0]+24*SC, chip[1]+13*SC), "SESSION ACTIVE", font=f(21*SC), fill=AMBER)
    cx, cy, R = m+sw//2, m+340*SC, 128*SC
    dr.ellipse([cx-R,cy-R,cx+R,cy+R], outline=(58,60,68), width=18*SC)
    dr.arc([cx-R,cy-R,cx+R,cy+R], start=-90, end=168, fill=AMBER, width=18*SC)
    hourglass(dr, cx, cy-14*SC, 120*SC)
    t = f(74*SC); txt = "47:12"
    tb = dr.textbbox((0,0), txt, font=t)
    dr.text((cx-(tb[2]-tb[0])//2, cy+R-26*SC), txt, font=t, fill=BONE)
    dr.text((cx-70*SC, cy+R+64*SC), "remaining", font=f(24*SC, False), fill=MUTED)
    y = m+560*SC
    for i in range(3):
        row=[m+34*SC, y+i*74*SC, m+sw-34*SC, y+i*74*SC+60*SC]
        rrect(dr, row, 18*SC, (40,42,49))
        dr.rounded_rectangle([row[0]+14*SC, row[1]+14*SC, row[0]+60*SC, row[1]+46*SC],
                             radius=10*SC, fill=GRAY)
        lx, ly = row[0]+26*SC, row[1]+30*SC
        dr.rounded_rectangle([lx-9*SC, ly-2*SC, lx+9*SC, ly+16*SC], radius=3*SC, fill=BONE)
        dr.arc([lx-7*SC, ly-16*SC, lx+7*SC, ly+2*SC], start=180, end=360, fill=BONE, width=4*SC)
        dr.text((row[0]+80*SC, row[1]+16*SC), ["Social","Video","Games"][i],
                font=f(24*SC, False), fill=BONE)
    dr.text((m+34*SC, m+sh-70*SC), "no early unlock", font=f(24*SC), fill=AMBER)
    return L

def phone_block():
    w,h = 390*SC, 800*SC
    L = Image.new("RGBA", (w,h), (0,0,0,0)); dr = ImageDraw.Draw(L)
    rrect(dr, [0,0,w-1,h-1], 52*SC, CHAR)
    m = 10*SC
    rrect(dr, [m,m,w-m,h-m], 44*SC, (26,27,33))
    hourglass(dr, w//2, h*0.30, 190*SC)
    txt=f(56*SC); s="Not yet."
    tb=dr.textbbox((0,0), s, font=txt)
    dr.text((w//2-(tb[2]-tb[0])//2, h*0.47), s, font=txt, fill=BONE)
    s2="Session ends in 47 min"; t2=f(26*SC, False)
    tb2=dr.textbbox((0,0), s2, font=t2)
    dr.text((w//2-(tb2[2]-tb2[0])//2, h*0.47+78*SC), s2, font=t2, fill=MUTED)
    bar=[m+50*SC, h*0.66, w-m-50*SC, h*0.66+18*SC]
    rrect(dr, bar, 9*SC, (48,50,58))
    rrect(dr, [bar[0], bar[1], bar[0]+(bar[2]-bar[0])*0.63, bar[3]], 9*SC, AMBER)
    s3="You committed to this."; t3=f(24*SC, False)
    tb3=dr.textbbox((0,0), s3, font=t3)
    dr.text((w//2-(tb3[2]-tb3[0])//2, h*0.66+52*SC), s3, font=t3, fill=BONE)
    return L

A = phone_active().rotate(-7, expand=True, resample=Image.BICUBIC)
B = phone_block().rotate(6, expand=True, resample=Image.BICUBIC)
shadow_paste(img, B, (int(W*0.10), int(H*0.10)), blur=34, alpha=60)
shadow_paste(img, A, (int(W*0.44), int(H*0.045)), blur=40, alpha=85)

d2 = ImageDraw.Draw(img)
# STRICT chip — width sized from the text so it never clips
chip_label = "STRICT MODE"
ct = f(26*SC); cb = d2.textbbox((0,0), chip_label, font=ct)
cw = (cb[2]-cb[0]) + 62*SC + 30*SC
ch = [int(W*0.055), int(H*0.78), int(W*0.055)+cw, int(H*0.78)+74*SC]
rrect(d2, ch, 37*SC, WHITE + (255,))
d2.ellipse([ch[0]+24*SC, ch[1]+26*SC, ch[0]+46*SC, ch[1]+48*SC], fill=OK)
d2.text((ch[0]+62*SC, ch[1]+21*SC), chip_label, font=ct, fill=INK)

d2.ellipse([int(W*0.36), int(H*0.06), int(W*0.36)+40*SC, int(H*0.06)+40*SC], fill=AMBER)
d2.ellipse([int(W*0.90), int(H*0.82), int(W*0.90)+120*SC, int(H*0.82)+120*SC],
           outline=AMBER, width=6*SC)

img.resize((W//SC, H//SC), Image.LANCZOS).convert("RGB").save(
    "website/assets/hero-mockup.png", "PNG", optimize=True)
print("saved website/assets/hero-mockup.png")
