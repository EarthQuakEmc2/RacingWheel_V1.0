#!/usr/bin/env python3
"""Porsche, McLaren, Ferrari, and Mercedes wheel templates.

Coordinates are pixel centers on the source photo. Radius is in source pixels.
A rotary consumes two programmable buttons (+/-). Each template stays within
4–18 button slots. `manufacturer` is optional for older MOZA files.
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
UPLOADS = Path("/home/ubuntu/.cursor/projects/agent-repos-RacingWheel-V1-0/uploads")
OUT = ROOT / "app" / "src" / "main" / "assets" / "wheels"
WEB = ROOT / "website" / "public" / "wheels"
ALIGN = ROOT / "docs" / "align"
DATA = ROOT / "website" / "src" / "data" / "wheels.ts"

# id, label, kind, color, x, y, r
B = "button"
R = "rotary"


def C(i, label, kind, color, x, y, r):
    return (i, label, kind, color, float(x), float(y), float(r))


TEMPLATES = [
    # --- Porsche ---
    {
        "id": "porsche_gt3r_screen",
        "name": "Porsche GT3 R screen",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "gt3",
        "src": "porsche_01",
        "image": "porsche_gt3r_screen.png",
        "blurb": "GT3 R screen wheel. Brake bias, flags, and the four face rotaries.",
        "controls": [
            C("br_m", "BR-", B, "#E10600", 128.5, 84.5, 11),
            C("fcy", "FCY", B, "#F5C400", 157.1, 96.5, 12),
            C("rad", "RAD", B, "#FF3355", 171.3, 122.9, 11),
            C("drk", "DRK", B, "#FF6A00", 170.6, 152.8, 11),
            C("f_m", "F-", B, "#C040C8", 166.8, 214.0, 11),
            C("br_p", "BR+", B, "#E10600", 420.5, 83.0, 11),
            C("pit", "PIT", B, "#F5C400", 393.1, 94.0, 12),
            C("ok", "OK", B, "#2F80ED", 378.3, 121.5, 11),
            C("wip", "WIP", B, "#3DDC57", 379.1, 152.0, 11),
            C("f_p", "F+", B, "#00E5FF", 383.8, 213.0, 11),
            C("tc_con", "TC/CON", R, "#FF3355", 210.9, 205.4, 18),
            C("tc_r", "TC/R", R, "#FF3355", 338.5, 204.5, 18),
            C("map", "MAP", R, "#FF3355", 247.2, 242.4, 18),
            C("wiper_k", "WIPER", R, "#FF3355", 302.4, 241.9, 18),
        ],
    },
    {
        "id": "porsche_gt3r_grid",
        "name": "Porsche GT3 R GRID",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "gt3",
        "src": "porsche_02",
        "image": "porsche_gt3r_grid.png",
        "blurb": "GT3 R screen with the GRID dash. Same face map as the color screen wheel.",
        "controls": [
            C("br_m", "BR-", B, "#E10600", 139, 85, 11),
            C("fcy", "FCY", B, "#C6E000", 165.6, 97.3, 13),
            C("rad", "RAD", B, "#3DDC57", 179.9, 124.9, 11),
            C("drk", "DRK", B, "#FF6A00", 180, 156, 11),
            C("f_m", "F-", B, "#E10600", 179, 214, 11),
            C("br_p", "BR+", B, "#E10600", 432.9, 85.2, 11),
            C("pit", "PIT", B, "#C040C8", 405.4, 96.2, 13),
            C("ok", "OK", B, "#2F80ED", 390.0, 126.0, 11),
            C("wip", "WIP", B, "#3DDC57", 390.0, 156.8, 11),
            C("f_p", "F+", B, "#2F80ED", 393.2, 221.7, 11),
            C("tc_con", "TC/CON", R, "#C8CDD4", 222.8, 212.9, 18),
            C("tc_r", "TC/R", R, "#C8CDD4", 350.4, 211.8, 18),
            C("map", "MAP", R, "#C8CDD4", 258.0, 249.2, 18),
            C("wiper_k", "WIPER", R, "#C8CDD4", 313.0, 249.2, 18),
        ],
    },
    {
        "id": "porsche_gt3r_color",
        "name": "Porsche GT3 R screen color",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "gt3",
        "src": "porsche_03",
        "image": "porsche_gt3r_color.png",
        "blurb": "Night GT3 R screen. Colored faces on the same button stack.",
        "controls": [
            C("br_m", "BR-", B, "#C040C8", 184.4, 78.8, 13),
            C("fcy", "FCY", B, "#F5C400", 220.3, 95.4, 14),
            C("rad", "RAD", B, "#2F80ED", 239.0, 130.0, 13),
            C("drk", "DRK", B, "#00E5E5", 238.0, 168.5, 13),
            C("f_m", "F-", B, "#00E5E5", 233.8, 247.3, 13),
            C("br_p", "BR+", B, "#C040C8", 556.8, 78.8, 13),
            C("pit", "PIT", B, "#F5C400", 521.8, 93.0, 14),
            C("ok", "OK", B, "#2F80ED", 503.0, 128.0, 13),
            C("wip", "WIP", B, "#3DDC57", 508, 170, 13),
            C("f_p", "F+", B, "#C040C8", 509.2, 246.0, 13),
            C("tc_con", "TC/CON", R, "#E10600", 290.0, 236.5, 22),
            C("tc_r", "TC/R", R, "#2F80ED", 452.0, 234.5, 22),
            C("map", "MAP", R, "#C040C8", 337.1, 283.6, 22),
            C("wiper_k", "WIPER", R, "#00E5E5", 406, 284, 22),
        ],
    },
    {
        "id": "porsche_rsr_f",
        "name": "Porsche 911 RSR F-style",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "gt",
        "src": "porsche_04",
        "image": "porsche_rsr_f.png",
        "blurb": "Yellow 911 RSR plate with the F mark, face buttons, and five rotaries.",
        "controls": [
            C("grn", "GRN", B, "#3DDC57", 81, 75, 14),
            C("b1", "B1", B, "#1A1A1A", 117, 69, 11),
            C("b2", "B2", B, "#1A1A1A", 152, 62, 11),
            C("b3", "B3", B, "#1A1A1A", 403, 61, 11),
            C("b4", "B4", B, "#1A1A1A", 440, 66, 11),
            C("red", "RED", B, "#E10600", 475, 74, 14),
            C("red_l", "L-RED", B, "#E10600", 191, 143, 11),
            C("red_r", "R-RED", B, "#E10600", 365, 143, 11),
            C("k1", "K1", R, "#E10600", 233, 133, 14),
            C("k2", "K2", R, "#E10600", 329, 134, 14),
            C("tc", "TC", R, "#2F80ED", 278, 177, 15),
            C("k3", "K3", R, "#E10600", 246, 255, 14),
            C("k4", "K4", R, "#E10600", 310, 255, 14),
        ],
    },
    {
        "id": "porsche_911_rsr",
        "name": "Porsche 911 RSR yellow",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "gt",
        "src": "porsche_05",
        "image": "porsche_911_rsr.png",
        "blurb": "Yellow 911 RSR with the gear window, side buttons, and two bottom encoders.",
        "controls": [
            C("grn", "GRN", B, "#3DDC57", 86, 69, 13),
            C("b1", "B1", B, "#1A1A1A", 123, 62, 11),
            C("b2", "B2", B, "#1A1A1A", 159, 57, 11),
            C("b4", "B4", B, "#1A1A1A", 375, 50, 11),
            C("b5", "B5", B, "#1A1A1A", 410, 56, 11),
            C("b6", "B6", B, "#1A1A1A", 447, 60, 11),
            C("b7", "B7", B, "#1A1A1A", 483, 68, 11),
            C("red", "RED", B, "#E10600", 372, 136, 12),
            C("b8", "B8", B, "#1A1A1A", 197, 136, 12),
            C("b9", "B9", B, "#1A1A1A", 156, 167, 12),
            C("wht", "WHT", B, "#F3F1EC", 300, 252, 11),
            C("enc_l", "ENC-", R, "#1A1A1A", 250, 255, 14),
            C("enc_r", "ENC+", R, "#1A1A1A", 345, 255, 14),
        ],
    },
    {
        "id": "porsche_gt_dash",
        "name": "Porsche GT dash",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "gt",
        "src": "porsche_06",
        "image": "porsche_gt_dash.png",
        "blurb": "Screen GT wheel. P, OT, DRS, N, and the three center encoders.",
        "controls": [
            C("p", "P", B, "#E10600", 184.0, 323.2, 20),
            C("ot", "OT", B, "#00E5E5", 236.7, 325.1, 22),
            C("r", "R", B, "#E040FB", 276.4, 368.8, 22),
            C("drs", "DRS", B, "#00E5E5", 762.2, 325.2, 22),
            C("n", "N", B, "#E10600", 815.2, 323.5, 20),
            C("mark", "MARK", B, "#E040FB", 723.1, 367.3, 22),
            C("p1", "+1", B, "#F5C400", 288.6, 558.5, 20),
            C("oil", "OIL", B, "#00E5E5", 287.2, 603.8, 20),
            C("p10", "+10", B, "#F5C400", 710.0, 558.7, 20),
            C("ok", "OK", B, "#00E5E5", 711.8, 603.6, 20),
            C("blu", "BLU", R, "#2F80ED", 397.6, 573.7, 30),
            C("yel", "YEL", R, "#F5C400", 500.0, 570.4, 30),
            C("grn", "GRN", R, "#3DDC57", 601.2, 574.8, 30),
        ],
    },
    {
        "id": "porsche_fanatec_gt3r",
        "name": "Fanatec 911 GT3 R round",
        "manufacturer": "Porsche",
        "maker": "Fanatec",
        "style": "gt",
        "src": "porsche_07",
        "image": "porsche_fanatec_gt3r.png",
        "blurb": "Round Fanatec 911 GT3 R. Labeled pods, TC and MAP encoders.",
        "controls": [
            C("wiper", "WIPER", B, "#3DDC57", 282.9, 196.3, 12),
            C("high", "HIGH", B, "#00E5E5", 261.6, 230.2, 12),
            C("minus", "−", B, "#C8CDD4", 210, 215, 12),
            C("radio", "RADIO", B, "#E10600", 250.1, 341.1, 12),
            C("dash", "DASH", B, "#1A1A1A", 290, 345, 11),
            C("reset", "RESET", B, "#E10600", 263.0, 378.0, 12),
            C("fuel", "FUEL", B, "#F5C400", 284.1, 411.3, 12),
            C("pit", "PIT", B, "#FF6A00", 551.2, 197.9, 12),
            C("disp", "DISPLAY", B, "#1A1A1A", 575, 230, 11),
            C("plus", "+", B, "#C8CDD4", 630, 215, 12),
            C("alarm", "ALARM", B, "#00E5E5", 582.8, 342.9, 12),
            C("rev", "REVERSE", B, "#3DDC57", 569.5, 379.4, 12),
            C("yel", "YEL", B, "#F5C400", 547.6, 412.7, 12),
            C("tc", "TC CON", R, "#E10600", 330, 445, 22),
            C("map", "MAP", R, "#3DDC57", 520, 450, 22),
        ],
    },
    {
        "id": "porsche_gt3r",
        "name": "Porsche GT3 R",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "gt",
        "src": "porsche_08",
        "image": "porsche_gt3r.png",
        "blurb": "GT3 R face with ignition, the Xbox cluster, and four rotaries.",
        "controls": [
            C("ign", "IGN", B, "#E10600", 95, 70, 14),
            C("start", "START", B, "#E10600", 140, 70, 14),
            C("rt", "RT", B, "#E10600", 185, 70, 14),
            C("rss", "RSS", B, "#E10600", 195, 155, 13),
            C("r1", "R1", B, "#E10600", 255, 155, 11),
            C("x", "X", B, "#2F80ED", 445, 65, 14),
            C("y", "Y", B, "#F5C400", 490, 70, 14),
            C("b", "B", B, "#E10600", 530, 75, 14),
            C("a", "A", B, "#3DDC57", 455, 165, 14),
            C("pause", "II", B, "#E8E6E3", 355, 300, 12),
            C("k1", "K1", R, "#FF6A00", 230, 220, 16),
            C("k2", "K2", R, "#FF6A00", 290, 215, 16),
            C("k3", "K3", R, "#FF6A00", 350, 215, 16),
            C("k4", "K4", R, "#FF6A00", 400, 220, 16),
        ],
    },
    {
        "id": "porsche_gt3_cup",
        "name": "Porsche 911 GT3 Cup",
        "manufacturer": "Porsche",
        "maker": "Porsche",
        "style": "cup",
        "src": "porsche_09",
        "image": "porsche_gt3_cup.png",
        "blurb": "Cup wheel. HS, RAD, DRK, PIT, and the two glowing encoders.",
        "controls": [
            C("hs", "HS", B, "#2F80ED", 105.9, 109.4, 12),
            C("rad", "RAD", B, "#3DDC57", 136.2, 129.9, 11),
            C("drk_l", "DRK", B, "#6C8CFF", 166.1, 150.2, 11),
            C("ack", "ACK", B, "#E10600", 190.9, 253.7, 11),
            C("str", "STR", B, "#C040C8", 195.8, 289.4, 12),
            C("pit", "PIT", B, "#E10600", 515.2, 108.8, 11),
            C("wip", "WIP", B, "#2F80ED", 486.3, 129.1, 12),
            C("yel", "YEL", B, "#F5C400", 457.4, 149.8, 11),
            C("drk_r", "DRK", B, "#6C8CFF", 436.5, 252.0, 11),
            C("exs", "EXS", B, "#C040C8", 432.4, 288.3, 12),
            C("tc", "TC", R, "#E10600", 246.1, 170.6, 28),
            C("abs", "ABS", R, "#2F80ED", 379.9, 171.9, 28),
        ],
    },
    # --- McLaren ---
    {
        "id": "mclaren_artura",
        "name": "McLaren Artura",
        "manufacturer": "McLaren",
        "maker": "McLaren",
        "style": "gt",
        "src": "mclaren_01",
        "image": "mclaren_artura.png",
        "blurb": "Artura screen wheel. BB, flash, and the two numbered dials.",
        "controls": [
            C("lim", "LIM", B, "#F5C400", 157, 52, 15),
            C("time", "TIME", B, "#00E5E5", 245, 72, 15),
            C("bb_m", "BB-", B, "#C040C8", 250, 155, 14),
            C("blu_l", "BLU", B, "#2F80ED", 248, 195, 14),
            C("fl_l", "FLSH", B, "#F5C400", 274, 239, 14),
            C("minus", "−", B, "#2F80ED", 250, 285, 13),
            C("horn_l", "HORN", B, "#FF6A00", 247, 330, 14),
            C("pwr", "PWR", B, "#E10600", 620, 60, 14),
            C("mic", "MIC", B, "#3DDC57", 660, 54, 14),
            C("bb_p", "BB+", B, "#C040C8", 560, 150, 14),
            C("blu_r", "BLU", B, "#2F80ED", 573, 195, 14),
            C("fl_r", "FLSH", B, "#F5C400", 547, 239, 14),
            C("plus", "+", B, "#2F80ED", 570, 283, 13),
            C("horn_r", "HORN", B, "#FF6A00", 573, 326, 14),
            C("map", "MAP", R, "#E07070", 355, 285, 26),
            C("tc", "TC", R, "#2F80ED", 475, 285, 26),
        ],
    },
    {
        "id": "mclaren_fanatec_gt3",
        "name": "Fanatec McLaren GT3",
        "manufacturer": "McLaren",
        "maker": "Fanatec",
        "style": "gt3",
        "src": "mclaren_02",
        "image": "mclaren_fanatec_gt3.png",
        "blurb": "Fanatec GT3. N, P, brake, TCS, ABS, and the center encoder.",
        "controls": [
            C("wiper", "WIPER", B, "#FF6A00", 233, 51, 12),
            C("n", "N", B, "#E8E6E3", 262, 68, 14),
            C("pg_up", "PG UP", B, "#1A1A1A", 318, 78, 11),
            C("pg_dn", "PG DN", B, "#1A1A1A", 305, 98, 11),
            C("flash", "FLASH", B, "#E10600", 280, 179, 11),
            C("radio", "RADIO", B, "#E10600", 255, 205, 11),
            C("drs", "DRS", B, "#FF6A00", 579, 51, 12),
            C("p", "P", B, "#E8E6E3", 548, 68, 16),
            C("brk_p", "BRK+", B, "#E10600", 512, 82, 11),
            C("brk_m", "BRK-", B, "#E10600", 512, 118, 11),
            C("ok", "OK", B, "#E10600", 530, 148, 11),
            C("esc", "ESC", B, "#E10600", 532, 179, 11),
            C("tcs", "TCS", R, "#C8CDD4", 348, 108, 16),
            C("abs", "ABS", R, "#C8CDD4", 468, 108, 16),
            C("ctr", "CTR", R, "#3EC6FF", 408, 168, 18),
        ],
    },
    {
        "id": "mclaren_gt3_diy",
        "name": "McLaren GT3 DIY",
        "manufacturer": "McLaren",
        "maker": "McLaren",
        "style": "gt3",
        "src": "mclaren_03",
        "image": "mclaren_gt3_diy.png",
        "blurb": "DIY GT3. Pit, talk, wipe, and the four colored rotaries.",
        "controls": [
            C("grn_l", "GRN", B, "#3DDC57", 175, 61, 18),
            C("blk", "BLK", B, "#1A1A1A", 228, 95, 16),
            C("pit", "PIT", B, "#E8E6E3", 300, 145, 14),
            C("talk", "TALK", B, "#F5C400", 230, 210, 14),
            C("left", "LEFT", B, "#E10600", 270, 284, 16),
            C("grn_r", "GRN", B, "#3DDC57", 820, 55, 18),
            C("wipe", "WIPE", B, "#E8E6E3", 750, 150, 14),
            C("cam", "CAM", B, "#E8E6E3", 740, 230, 14),
            C("right", "RIGHT", B, "#E10600", 687, 274, 16),
            C("head", "HEAD", B, "#F5C400", 760, 430, 14),
            C("red_k", "RED", R, "#E10600", 360, 160, 22),
            C("blu_k", "BLU", R, "#2F80ED", 460, 160, 22),
            C("yel_k", "YEL", R, "#F5C400", 560, 155, 22),
            C("slv", "SLV", R, "#C8CDD4", 490, 270, 22),
        ],
    },
    {
        "id": "mclaren_gt3_okx",
        "name": "McLaren GT3 OKX",
        "manufacturer": "McLaren",
        "maker": "McLaren",
        "style": "gt3",
        "src": "mclaren_04",
        "image": "mclaren_gt3_okx.png",
        "blurb": "OKX livery. N, P, the face buttons, and three rotaries.",
        "controls": [
            C("lsb", "LSB", B, "#1A1A1A", 95, 42, 12),
            C("n", "N", B, "#1A1A1A", 148, 62, 14),
            C("red_l", "L-RED", B, "#E10600", 188, 78, 10),
            C("y", "Y", B, "#F5C400", 148, 112, 12),
            C("x", "X", B, "#00E5E5", 178, 158, 12),
            C("rsb", "RSB", B, "#1A1A1A", 475, 42, 12),
            C("p", "P", B, "#1A1A1A", 430, 62, 14),
            C("red_r", "R-RED", B, "#E10600", 378, 78, 10),
            C("b", "B", B, "#E10600", 418, 118, 12),
            C("a", "A", B, "#3DDC57", 405, 164, 12),
            C("k_l", "K-L", R, "#1A1A1A", 228, 100, 16),
            C("k_r", "K-R", R, "#1A1A1A", 348, 100, 16),
            C("ctr", "CTR", R, "#3EC6FF", 278, 148, 18),
        ],
    },
    {
        "id": "mclaren_gtr_lm",
        "name": "McLaren GTR LM",
        "manufacturer": "McLaren",
        "maker": "McLaren",
        "style": "gt",
        "src": "mclaren_05",
        "image": "mclaren_gtr_lm.png",
        "blurb": "GTR LM. Talk through Aux on the columns, plus the orange dials.",
        "controls": [
            C("talk", "Talk", B, "#1A1A1A", 250, 115, 16),
            C("ok", "OK", B, "#1A1A1A", 250, 175, 16),
            C("am", "A/M", B, "#1A1A1A", 250, 230, 16),
            C("page", "Page", B, "#1A1A1A", 250, 285, 16),
            C("units", "Units", B, "#1A1A1A", 250, 340, 16),
            C("plc", "PLC", B, "#1A1A1A", 640, 70, 16),
            C("flash", "Flash", B, "#1A1A1A", 640, 115, 16),
            C("n", "N", B, "#1A1A1A", 640, 175, 16),
            C("launch", "Launch", B, "#1A1A1A", 640, 230, 16),
            C("aux", "Aux", B, "#1A1A1A", 640, 340, 16),
            C("dial_l", "L-DIAL", R, "#FF6A00", 340, 162, 28),
            C("dial_r", "R-DIAL", R, "#FF6A00", 532, 162, 28),
        ],
    },
    {
        "id": "mclaren_f1_mcl",
        "name": "McLaren F1 MCL",
        "manufacturer": "McLaren",
        "maker": "McLaren",
        "style": "formula",
        "src": "mclaren_06",
        "image": "mclaren_f1_mcl.png",
        "blurb": "Color MCL. OT, N, PLS, C, and the strategy rotaries.",
        "controls": [
            C("p10", "+10", B, "#C040C8", 115, 45, 12),
            C("ot", "OT", B, "#F5C400", 166, 108, 11),
            C("x", "X", B, "#E10600", 145, 155, 11),
            C("pc", "PC", B, "#FF6A00", 148, 186, 11),
            C("n", "N", B, "#3DDC57", 165, 225, 11),
            C("p1", "+1", B, "#C040C8", 445, 45, 12),
            C("pls", "PLS", B, "#E8E6E3", 400, 100, 14),
            C("r", "R", B, "#E10600", 411, 148, 11),
            C("c", "C", B, "#00E5E5", 411, 186, 12),
            C("acs", "ACS", B, "#E8E6E3", 400, 230, 14),
            C("mix", "MIX", R, "#C040C8", 210, 195, 16),
            C("strat", "STRAT", R, "#E8E6E3", 280, 205, 24),
            C("h", "H", R, "#E8E6E3", 355, 205, 24),
        ],
    },
    {
        "id": "mclaren_f1_mcl_2",
        "name": "McLaren F1 MCL 2",
        "manufacturer": "McLaren",
        "maker": "McLaren",
        "style": "formula",
        "src": "mclaren_07",
        "image": "mclaren_f1_mcl_2.png",
        "blurb": "Second MCL. +10, N, OT, PLS, D, and the DRY rotary.",
        "controls": [
            C("p10", "+10", B, "#C040C8", 155, 65, 15),
            C("n", "N", B, "#3DDC57", 213, 139, 15),
            C("ot", "OT", B, "#F5C400", 209, 201, 15),
            C("x", "X", B, "#E8E6E3", 210, 255, 15),
            C("minus", "−", B, "#E8E6E3", 230, 310, 16),
            C("p1", "+1", B, "#C040C8", 668, 65, 15),
            C("pls", "PLS", B, "#F5C400", 619, 139, 15),
            C("red", "RED", B, "#E10600", 623, 202, 15),
            C("d", "D", B, "#E8E6E3", 625, 252, 15),
            C("plus", "+", B, "#E8E6E3", 640, 310, 16),
            C("c", "C", R, "#6C4CFF", 320, 300, 26),
            C("dry", "DRY", R, "#E8E6E3", 410, 300, 26),
            C("h", "H", R, "#E8E6E3", 510, 300, 26),
        ],
    },
    {
        "id": "mclaren_ascher_dark",
        "name": "Ascher McLaren GT dark",
        "manufacturer": "McLaren",
        "maker": "Ascher",
        "style": "gt",
        "src": "mclaren_08",
        "image": "mclaren_ascher_dark.png",
        "blurb": "Dark Ascher GT. LIM, BB, and the red and blue dials.",
        "controls": [
            C("lim", "LIM", B, "#E8E6E3", 175, 55, 16),
            C("flg", "FLG", B, "#E8E6E3", 230, 70, 14),
            C("bb_m", "BB-", B, "#E8E6E3", 250, 165, 14),
            C("blu_l", "BLU", B, "#2F80ED", 225, 216, 15),
            C("arr_l", "◀", B, "#E8E6E3", 230, 270, 15),
            C("minus", "−", B, "#E8E6E3", 230, 315, 14),
            C("horn_l", "HORN", B, "#E8E6E3", 230, 360, 14),
            C("pwr", "PWR", B, "#E8E6E3", 640, 50, 14),
            C("mic", "MIC", B, "#E8E6E3", 690, 55, 14),
            C("bb_p", "BB+", B, "#E8E6E3", 575, 165, 14),
            C("blu_r", "BLU", B, "#2F80ED", 598, 213, 15),
            C("arr_r", "▶", B, "#E8E6E3", 575, 265, 15),
            C("plus", "+", B, "#E8E6E3", 600, 315, 14),
            C("horn_r", "HORN", B, "#E8E6E3", 600, 360, 14),
            C("map", "MAP", R, "#E10600", 355, 340, 26),
            C("tc", "TC", R, "#2F80ED", 485, 335, 26),
        ],
    },
    # --- Ferrari ---
    {
        "id": "ferrari_road_red",
        "name": "Ferrari Road red",
        "manufacturer": "Ferrari",
        "maker": "Ferrari",
        "style": "road",
        "src": "ferrari_01",
        "image": "ferrari_road_red.png",
        "blurb": "Red road wheel. Indicators, lights, engine start, wipers, manettino.",
        "controls": [
            C("back", "BACK", B, "#1A1A1A", 155, 265, 14),
            C("light", "LIGHTS", B, "#1A1A1A", 155, 345, 14),
            C("ok", "OK", B, "#2F80ED", 210, 355, 13),
            C("start", "ENGINE", B, "#E10600", 239, 387, 16),
            C("wiper", "WIPER", B, "#1A1A1A", 300, 400, 14),
            C("phone", "PHONE", B, "#1A1A1A", 345, 400, 12),
            C("light_r", "LIGHTS", B, "#1A1A1A", 470, 340, 14),
            C("manettino", "MANETTINO", R, "#E10600", 420, 385, 22),
        ],
    },
    {
        "id": "ferrari_sf1000",
        "name": "Thrustmaster SF1000",
        "manufacturer": "Ferrari",
        "maker": "Thrustmaster",
        "style": "formula",
        "src": "ferrari_02",
        "image": "ferrari_sf1000.png",
        "blurb": "SF1000. N, P, K1, K2, SHA, and the center manettino.",
        "controls": [
            C("n", "N", B, "#F5C400", 147, 88, 24),
            C("radio", "RADIO", B, "#E10600", 257, 142, 16),
            C("drink", "DRINK", B, "#E8E6E3", 210, 175, 14),
            C("k1", "K1", B, "#F5C400", 230, 351, 16),
            C("sha", "SHA", B, "#C040C8", 285, 412, 16),
            C("p", "P", B, "#FF6A00", 840, 85, 24),
            C("p1", "+1", B, "#F5C400", 780, 145, 14),
            C("m10", "−10", B, "#F5C400", 800, 190, 14),
            C("s1", "S1", B, "#F5C400", 701, 279, 16),
            C("k2", "K2", B, "#F5C400", 757, 350, 16),
            C("oil", "OIL", B, "#F5C400", 800, 410, 16),
            C("manettino", "MANETTINO", R, "#F5C400", 494, 363, 28),
            C("enc_l", "ENC-L", R, "#C8CDD4", 370, 400, 20),
            C("enc_r", "ENC-R", R, "#C8CDD4", 620, 400, 20),
        ],
    },
    {
        "id": "ferrari_488_gt3",
        "name": "Thrustmaster 488 GT3",
        "manufacturer": "Ferrari",
        "maker": "Thrustmaster",
        "style": "gt3",
        "src": "ferrari_03",
        "image": "ferrari_488_gt3.png",
        "blurb": "488 GT3. Pit, flash, drink, washer, MIX and MAP.",
        "controls": [
            C("radio", "RADIO", B, "#C8CDD4", 120, 80, 12),
            C("ac", "A/C", B, "#F5C400", 133, 105, 11),
            C("flash", "FLASH", B, "#F5C400", 142, 138, 11),
            C("n", "N", B, "#3DDC57", 155, 214, 12),
            C("marker", "MARKER", B, "#E8E6E3", 140, 250, 12),
            C("pit", "PIT", B, "#E10600", 453, 87, 12),
            C("launch", "LAUNCH", B, "#FF6A00", 427, 106, 11),
            C("drink", "DRINK", B, "#00E5E5", 413, 176, 12),
            C("washer", "WASHER", B, "#2F80ED", 404, 214, 12),
            C("fcy", "FCY", B, "#E8E6E3", 410, 250, 12),
            C("mix", "MIX", R, "#00E5E5", 185, 285, 22),
            C("map", "MAP", R, "#E10600", 366, 283, 22),
        ],
    },
    {
        "id": "ferrari_f1_2012",
        "name": "Ferrari F1 2012-style",
        "manufacturer": "Ferrari",
        "maker": "Ferrari",
        "style": "formula",
        "src": "ferrari_04",
        "image": "ferrari_f1_2012.png",
        "blurb": "2012-style F1 plate. K, PL, the manettino, and the labeled stack.",
        "controls": [
            C("k", "K", B, "#3DDC57", 125, 41, 12),
            C("pl", "PL", B, "#E10600", 528, 41, 12),
            C("n", "N", B, "#E8E6E3", 150, 155, 12),
            C("pit", "PIT", B, "#E10600", 110, 220, 12),
            C("drs", "DRS", B, "#F5C400", 458, 169, 12),
            C("ok", "OK", B, "#2F80ED", 474, 343, 12),
            C("start", "START", B, "#E8E6E3", 330, 390, 12),
            C("mix", "MIX", R, "#E10600", 220, 250, 20),
            C("map", "MAP", R, "#F5C400", 326, 227, 26),
            C("trq", "TRQ", R, "#FF6A00", 430, 250, 20),
            C("grp", "GRP", R, "#E10600", 326, 320, 18),
        ],
    },
    {
        "id": "ferrari_sf_f1",
        "name": "Ferrari SF F1 carbon",
        "manufacturer": "Ferrari",
        "maker": "Ferrari",
        "style": "formula",
        "src": "ferrari_05",
        "image": "ferrari_sf_f1.png",
        "blurb": "Carbon SF wheel. N, P, BS, BB, and the center dial.",
        "controls": [
            C("c", "C", B, "#E10600", 171, 42, 12),
            C("n", "N", B, "#00E5E5", 91, 62, 12),
            C("p", "P", B, "#E10600", 561, 64, 12),
            C("bs", "BS", B, "#E8E6E3", 160, 230, 14),
            C("bb", "BB", B, "#E8E6E3", 500, 230, 14),
            C("r", "R", B, "#E10600", 140, 300, 14),
            C("oil", "OIL", B, "#F5C400", 530, 300, 14),
            C("manettino", "MANETTINO", R, "#F5C400", 329, 222, 26),
            C("enc_l", "ENC-L", R, "#C8CDD4", 230, 270, 18),
            C("enc_r", "ENC-R", R, "#C8CDD4", 430, 270, 18),
        ],
    },
    {
        "id": "ferrari_road_blue",
        "name": "Ferrari Road blue",
        "manufacturer": "Ferrari",
        "maker": "Ferrari",
        "style": "road",
        "src": "ferrari_06",
        "image": "ferrari_road_blue.png",
        "blurb": "Blue road wheel. Lights, engine start, wipers, phone, manettino.",
        "controls": [
            C("light", "LIGHTS", B, "#1A1A1A", 170, 345, 14),
            C("ok", "OK", B, "#2F80ED", 200, 360, 13),
            C("start", "ENGINE", B, "#E10600", 240, 395, 16),
            C("wiper", "WIPER", B, "#1A1A1A", 310, 400, 14),
            C("phone", "PHONE", B, "#1A1A1A", 360, 400, 12),
            C("light_r", "LIGHTS", B, "#1A1A1A", 500, 345, 14),
            C("manettino", "MANETTINO", R, "#E10600", 450, 385, 22),
        ],
    },
    {
        "id": "ferrari_458_black",
        "name": "Ferrari 458 black",
        "manufacturer": "Ferrari",
        "maker": "Ferrari",
        "style": "road",
        "src": "ferrari_07",
        "image": "ferrari_458_black.png",
        "blurb": "Black 458. Indicators, lights, engine start, and the manettino.",
        "controls": [
            C("ind_l", "IND", B, "#1A1A1A", 100, 260, 12),
            C("light", "LIGHTS", B, "#1A1A1A", 100, 310, 14),
            C("ok", "OK", B, "#2F80ED", 155, 340, 12),
            C("start", "ENGINE", B, "#E10600", 182, 372, 16),
            C("light_r", "LIGHTS", B, "#1A1A1A", 430, 310, 12),
            C("wiper", "WIPER", B, "#1A1A1A", 440, 350, 12),
            C("manettino", "MANETTINO", R, "#E10600", 390, 360, 20),
        ],
    },
    # --- Mercedes ---
    {
        "id": "mercedes_amg_f1",
        "name": "Mercedes-AMG F1 W-series",
        "manufacturer": "Mercedes",
        "maker": "Mercedes-AMG",
        "style": "formula",
        "src": "mercedes_01",
        "image": "mercedes_amg_f1.png",
        "blurb": "AMG Petronas wheel. DRS, N, BB, STRAT and HPP.",
        "controls": [
            C("drs", "DRS", B, "#F5C400", 113, 96, 14),
            C("p10", "+10", B, "#C040C8", 157, 105, 14),
            C("n", "N", B, "#00E5E5", 195, 128, 14),
            C("dr", "DR", B, "#FF6A00", 209, 236, 14),
            C("x", "X", B, "#E10600", 202, 287, 14),
            C("bb_m", "BB-", B, "#2F80ED", 200, 335, 14),
            C("ot", "OT", B, "#E10600", 640, 85, 14),
            C("p1", "+1", B, "#C040C8", 579, 104, 14),
            C("pl", "PL", B, "#F5C400", 541, 128, 14),
            C("wht", "WHT", B, "#E8E6E3", 530, 240, 12),
            C("radio", "RADIO", B, "#3DDC57", 545, 265, 14),
            C("bb_p", "BB+", B, "#1A1A1A", 545, 330, 14),
            C("strat", "STRAT", R, "#F5C400", 250, 335, 28),
            C("mix", "MIX", R, "#6C4CFF", 390, 330, 26),
            C("hpp", "HPP", R, "#00E5E5", 490, 330, 28),
        ],
    },
    {
        "id": "mercedes_cube_gt3",
        "name": "Mercedes-AMG GT3 (Cube Controls)",
        "manufacturer": "Mercedes",
        "maker": "Cube Controls",
        "style": "gt3",
        "src": "mercedes_02",
        "image": "mercedes_cube_gt3.png",
        "blurb": "Cube Controls GT. R, P, TC, ABS, mic, and the arrow pad.",
        "controls": [
            C("r", "R", B, "#E8E6E3", 100, 57, 13),
            C("pl", "PL", B, "#E8E6E3", 179, 67, 12),
            C("red_l", "L-RED", B, "#E10600", 190, 179, 12),
            C("mic", "MIC", B, "#E8E6E3", 237, 237, 13),
            C("arr_l", "◀", B, "#E8E6E3", 208, 286, 12),
            C("arr_d", "▼", B, "#E8E6E3", 181, 337, 12),
            C("p", "P", B, "#E8E6E3", 625, 55, 13),
            C("fl", "FL", B, "#E8E6E3", 543, 65, 12),
            C("red_r", "R-RED", B, "#E10600", 533, 178, 13),
            C("mute", "MUTE", B, "#E8E6E3", 485, 237, 12),
            C("ok", "OK", B, "#E8E6E3", 514, 287, 12),
            C("arr_r", "▶", B, "#E8E6E3", 543, 338, 12),
            C("tc", "TC", R, "#C8CDD4", 258, 120, 16),
            C("abs", "ABS", R, "#C8CDD4", 467, 130, 16),
        ],
    },
    {
        "id": "mercedes_gt_diy",
        "name": "Mercedes GT DIY",
        "manufacturer": "Mercedes",
        "maker": "Mercedes",
        "style": "gt",
        "src": "mercedes_03",
        "image": "mercedes_gt_diy.png",
        "blurb": "DIY GT. Radio, neutral, chrono, flash, start, and the page dials.",
        "controls": [
            C("radio", "RADIO", B, "#F5C400", 127, 67, 20),
            C("neutral", "NEUTRAL", B, "#1A1A1A", 175, 125, 16),
            C("chrono", "CHRONO", B, "#E8E6E3", 209, 268, 18),
            C("flash", "FLASH", B, "#2F80ED", 584, 70, 20),
            C("blk", "BLK", B, "#1A1A1A", 510, 220, 14),
            C("start", "START", B, "#E10600", 488, 271, 18),
            C("page_l", "PAGE", R, "#1A1A1A", 180, 175, 18),
            C("page_r", "PAGE", R, "#1A1A1A", 520, 165, 18),
        ],
    },
    {
        "id": "mercedes_f1_2014",
        "name": "Mercedes F1 2014-style",
        "manufacturer": "Mercedes",
        "maker": "Mercedes",
        "style": "formula",
        "src": "mercedes_04",
        "image": "mercedes_f1_2014.png",
        "blurb": "2014-style plate. Boost, neutral, limiter, and the face rotaries.",
        "controls": [
            C("boost", "BOOST", B, "#E10600", 78, 107, 22),
            C("neutral", "NEUTRAL", B, "#3DDC57", 153, 94, 18),
            C("eb", "EB", B, "#E10600", 191, 226, 14),
            C("bpf", "BPF", B, "#F5C400", 174, 290, 16),
            C("limiter", "LIMITER", B, "#F5C400", 564, 105, 18),
            C("reset", "RESET", B, "#F5C400", 513, 375, 14),
            C("mix", "MIX", R, "#F5C400", 248, 304, 28),
            C("torque", "TORQUE", R, "#E10600", 391, 304, 28),
        ],
    },
    {
        "id": "mercedes_road_carbon",
        "name": "Mercedes-AMG Road Carbon",
        "manufacturer": "Mercedes",
        "maker": "Mercedes-AMG",
        "style": "road",
        "src": "mercedes_05",
        "image": "mercedes_road_carbon.png",
        "blurb": "Carbon road wheel. Left and right spoke switches: pads, volume, voice, cruise.",
        "controls": [
            C("pad_l", "PAD", B, "#1A1A1A", 125, 235, 14),
            C("ok_l", "OK", B, "#1A1A1A", 145, 255, 12),
            C("vol", "VOL", B, "#1A1A1A", 110, 275, 12),
            C("voice", "VOICE", B, "#1A1A1A", 160, 270, 12),
            C("pad_r", "PAD", B, "#1A1A1A", 300, 235, 14),
            C("cruise", "CRUISE", B, "#1A1A1A", 325, 258, 12),
            C("phone", "PHONE", B, "#1A1A1A", 285, 275, 12),
        ],
    },
    {
        "id": "mercedes_road_led",
        "name": "Mercedes-AMG Road LED",
        "manufacturer": "Mercedes",
        "maker": "Mercedes-AMG",
        "style": "road",
        "src": "mercedes_06",
        "image": "mercedes_road_led.png",
        "blurb": "LED road wheel. Spoke touch pads, volume, voice, and cruise.",
        "controls": [
            C("back", "BACK", B, "#1A1A1A", 130, 200, 12),
            C("ok", "OK", B, "#1A1A1A", 155, 220, 12),
            C("vol", "VOL", B, "#1A1A1A", 125, 245, 12),
            C("voice", "VOICE", B, "#1A1A1A", 170, 240, 12),
            C("menu", "MENU", B, "#1A1A1A", 410, 205, 12),
            C("cruise", "CRUISE", B, "#1A1A1A", 435, 230, 12),
            C("phone", "PHONE", B, "#1A1A1A", 395, 245, 12),
        ],
    },
]

BRANDS = ["Porsche", "McLaren", "Audi", "BMW", "Ferrari", "Mercedes", "Alfa Romeo", "KTM"]


def find_src(prefix: str) -> Path:
    hits = sorted(UPLOADS.glob(f"{prefix}_*.png"))
    if not hits:
        raise SystemExit(f"missing upload {prefix}")
    return hits[0]


def slots(controls) -> int:
    n = 0
    for c in controls:
        n += 2 if c[2] == R else 1
    return n


def build_controls(raw, w, h):
    button_n = 1
    slot = 0
    out = []
    for cid, label, kind, color, x, y, r in raw:
        entry = {
            "id": cid,
            "label": label,
            "kind": kind,
            "shape": "circle",
            "cx": round(x / w, 5),
            "cy": round(y / h, 5),
            "r": round(r / w, 5),
            "color": color,
        }
        if kind == B:
            entry["defaultButton"] = button_n
            button_n += 1
        else:
            entry["positions"] = 12
            entry["knobSlot"] = slot
            entry["decButton"] = button_n
            button_n += 1
            entry["incButton"] = button_n
            button_n += 1
            slot += 1
        out.append(entry)
    return out, button_n - 1


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    WEB.mkdir(parents=True, exist_ok=True)
    ALIGN.mkdir(parents=True, exist_ok=True)
    DATA.parent.mkdir(parents=True, exist_ok=True)
    cards: dict[str, list] = {b: [] for b in BRANDS}
    for spec in TEMPLATES:
        used = slots(spec["controls"])
        if not 4 <= used <= 18:
            raise SystemExit(f"{spec['id']} uses {used} button slots")
        src = find_src(spec["src"])
        im = Image.open(src).convert("RGBA")
        w, h = im.size
        controls, outputs = build_controls(spec["controls"], w, h)
        for c in controls:
            if not (0 < c["cx"] < 1 and 0 < c["cy"] < 1):
                raise SystemExit(f"{spec['id']} {c['id']} out of frame")
        doc = {
            "schema": "racing-wheel-pro.template/v1",
            "id": spec["id"],
            "name": spec["name"],
            "maker": spec["maker"],
            "manufacturer": spec["manufacturer"],
            "style": spec["style"],
            "image": spec["image"],
            "imageWidth": w,
            "imageHeight": h,
            "blurb": spec["blurb"],
            "hitSlop": 0.008,
            "outputButtons": outputs,
            "controls": controls,
        }
        (OUT / f"{spec['id']}.json").write_text(json.dumps(doc, indent=2) + "\n")
        im.save(OUT / spec["image"])
        shutil.copyfile(OUT / spec["image"], WEB / spec["image"])
        vis = im.copy()
        dr = ImageDraw.Draw(vis)
        for c in controls:
            cx, cy = c["cx"] * w, c["cy"] * h
            rad = max(4.0, c["r"] * w)
            col = c["color"]
            dr.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], outline=col, width=2)
            dr.line([(cx - 5, cy), (cx + 5, cy)], fill="#FFFFFF")
            dr.line([(cx, cy - 5), (cx, cy + 5)], fill="#FFFFFF")
            dr.text((cx + rad + 2, cy - 8), c["label"], fill="#FFE14A")
        vis.save(ALIGN / f"align_{spec['id']}.png")
        face = sum(1 for c in controls if c["kind"] == B)
        rot = sum(1 for c in controls if c["kind"] == R)
        cards[spec["manufacturer"]].append(
            {
                "id": spec["id"],
                "name": spec["name"],
                "image": spec["image"],
                "blurb": spec["blurb"],
                "buttons": face,
                "rotaries": rot,
            }
        )
        print(f"{spec['id']}: {w}x{h} buttons={face} rotaries={rot} slots={outputs}")

    lines = [
        "export type MakerWheel = {",
        "  id: string",
        "  name: string",
        "  image: string",
        "  blurb: string",
        "  buttons: number",
        "  rotaries: number",
        "}",
        "",
        "export const BRAND_ORDER = [",
    ]
    for b in BRANDS:
        lines.append(f"  {json.dumps(b)},")
    lines += ["] as const", "", "export const WHEELS: Record<string, MakerWheel[]> = {"]
    for b in BRANDS:
        lines.append(f"  {json.dumps(b)}: [")
        for card in cards[b]:
            lines.append(
                "    { "
                + f"id: {json.dumps(card['id'])}, name: {json.dumps(card['name'])}, "
                + f"image: {json.dumps(card['image'])}, blurb: {json.dumps(card['blurb'])}, "
                + f"buttons: {card['buttons']}, rotaries: {card['rotaries']} "
                + "},"
            )
        lines.append("  ],")
    lines.append("}")
    lines.append("")
    DATA.write_text("\n".join(lines))
    print("wrote", DATA)


if __name__ == "__main__":
    main()
