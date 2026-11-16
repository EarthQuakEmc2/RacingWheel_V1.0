export type MakerWheel = {
  id: string
  name: string
  image: string
  blurb: string
  buttons: number
  rotaries: number
}

export const BRAND_ORDER = [
  "Porsche",
  "McLaren",
  "Audi",
  "BMW",
  "Ferrari",
  "Mercedes",
  "Alfa Romeo",
  "KTM",
] as const

export const WHEELS: Record<string, MakerWheel[]> = {
  "Porsche": [
    { id: "porsche_gt3r_screen", name: "Porsche GT3 R screen", image: "porsche_gt3r_screen.png", blurb: "GT3 R screen wheel. Brake bias, flags, and the four face rotaries.", buttons: 10, rotaries: 4 },
    { id: "porsche_gt3r_grid", name: "Porsche GT3 R GRID", image: "porsche_gt3r_grid.png", blurb: "GT3 R screen with the GRID dash. Same face map as the color screen wheel.", buttons: 10, rotaries: 4 },
    { id: "porsche_gt3r_color", name: "Porsche GT3 R screen color", image: "porsche_gt3r_color.png", blurb: "Night GT3 R screen. Colored faces on the same button stack.", buttons: 10, rotaries: 4 },
    { id: "porsche_rsr_f", name: "Porsche 911 RSR F-style", image: "porsche_rsr_f.png", blurb: "Yellow 911 RSR plate with the F mark, face buttons, and five rotaries.", buttons: 8, rotaries: 5 },
    { id: "porsche_911_rsr", name: "Porsche 911 RSR yellow", image: "porsche_911_rsr.png", blurb: "Yellow 911 RSR with the gear window, side buttons, and two bottom encoders.", buttons: 11, rotaries: 2 },
    { id: "porsche_gt_dash", name: "Porsche GT dash", image: "porsche_gt_dash.png", blurb: "Screen GT wheel. P, OT, DRS, N, and the three center encoders.", buttons: 10, rotaries: 3 },
    { id: "porsche_fanatec_gt3r", name: "Fanatec 911 GT3 R round", image: "porsche_fanatec_gt3r.png", blurb: "Round Fanatec 911 GT3 R. Labeled pods, TC and MAP encoders.", buttons: 13, rotaries: 2 },
    { id: "porsche_gt3r", name: "Porsche GT3 R", image: "porsche_gt3r.png", blurb: "GT3 R face with ignition, the Xbox cluster, and four rotaries.", buttons: 10, rotaries: 4 },
    { id: "porsche_gt3_cup", name: "Porsche 911 GT3 Cup", image: "porsche_gt3_cup.png", blurb: "Cup wheel. HS, RAD, DRK, PIT, and the two glowing encoders.", buttons: 10, rotaries: 2 },
  ],
  "McLaren": [
    { id: "mclaren_artura", name: "McLaren Artura", image: "mclaren_artura.png", blurb: "Artura screen wheel. BB, flash, and the two numbered dials.", buttons: 14, rotaries: 2 },
    { id: "mclaren_fanatec_gt3", name: "Fanatec McLaren GT3", image: "mclaren_fanatec_gt3.png", blurb: "Fanatec GT3. N, P, brake, TCS, ABS, and the center encoder.", buttons: 12, rotaries: 3 },
    { id: "mclaren_gt3_diy", name: "McLaren GT3 DIY", image: "mclaren_gt3_diy.png", blurb: "DIY GT3. Pit, talk, wipe, and the four colored rotaries.", buttons: 10, rotaries: 4 },
    { id: "mclaren_gt3_okx", name: "McLaren GT3 OKX", image: "mclaren_gt3_okx.png", blurb: "OKX livery. N, P, the face buttons, and three rotaries.", buttons: 10, rotaries: 3 },
    { id: "mclaren_gtr_lm", name: "McLaren GTR LM", image: "mclaren_gtr_lm.png", blurb: "GTR LM. Talk through Aux on the columns, plus the orange dials.", buttons: 10, rotaries: 2 },
    { id: "mclaren_f1_mcl", name: "McLaren F1 MCL", image: "mclaren_f1_mcl.png", blurb: "Color MCL. OT, N, PLS, C, and the strategy rotaries.", buttons: 10, rotaries: 3 },
    { id: "mclaren_f1_mcl_2", name: "McLaren F1 MCL 2", image: "mclaren_f1_mcl_2.png", blurb: "Second MCL. +10, N, OT, PLS, D, and the DRY rotary.", buttons: 10, rotaries: 3 },
    { id: "mclaren_ascher_dark", name: "Ascher McLaren GT dark", image: "mclaren_ascher_dark.png", blurb: "Dark Ascher GT. LIM, BB, and the red and blue dials.", buttons: 14, rotaries: 2 },
  ],
  "Audi": [
  ],
  "BMW": [
  ],
  "Ferrari": [
    { id: "ferrari_road_red", name: "Ferrari Road red", image: "ferrari_road_red.png", blurb: "Red road wheel. Indicators, lights, engine start, wipers, manettino.", buttons: 7, rotaries: 1 },
    { id: "ferrari_sf1000", name: "Thrustmaster SF1000", image: "ferrari_sf1000.png", blurb: "SF1000. N, P, K1, K2, SHA, and the center manettino.", buttons: 11, rotaries: 3 },
    { id: "ferrari_488_gt3", name: "Thrustmaster 488 GT3", image: "ferrari_488_gt3.png", blurb: "488 GT3. Pit, flash, drink, washer, MIX and MAP.", buttons: 10, rotaries: 2 },
    { id: "ferrari_f1_2012", name: "Ferrari F1 2012-style", image: "ferrari_f1_2012.png", blurb: "2012-style F1 plate. K, PL, the manettino, and the labeled stack.", buttons: 7, rotaries: 4 },
    { id: "ferrari_sf_f1", name: "Ferrari SF F1 carbon", image: "ferrari_sf_f1.png", blurb: "Carbon SF wheel. N, P, BS, BB, and the center dial.", buttons: 7, rotaries: 3 },
    { id: "ferrari_road_blue", name: "Ferrari Road blue", image: "ferrari_road_blue.png", blurb: "Blue road wheel. Lights, engine start, wipers, phone, manettino.", buttons: 6, rotaries: 1 },
    { id: "ferrari_458_black", name: "Ferrari 458 black", image: "ferrari_458_black.png", blurb: "Black 458. Indicators, lights, engine start, and the manettino.", buttons: 6, rotaries: 1 },
  ],
  "Mercedes": [
    { id: "mercedes_amg_f1", name: "Mercedes-AMG F1 W-series", image: "mercedes_amg_f1.png", blurb: "AMG Petronas wheel. DRS, N, BB, STRAT and HPP.", buttons: 12, rotaries: 3 },
    { id: "mercedes_cube_gt3", name: "Mercedes-AMG GT3 (Cube Controls)", image: "mercedes_cube_gt3.png", blurb: "Cube Controls GT. R, P, TC, ABS, mic, and the arrow pad.", buttons: 12, rotaries: 2 },
    { id: "mercedes_gt_diy", name: "Mercedes GT DIY", image: "mercedes_gt_diy.png", blurb: "DIY GT. Radio, neutral, chrono, flash, start, and the page dials.", buttons: 6, rotaries: 2 },
    { id: "mercedes_f1_2014", name: "Mercedes F1 2014-style", image: "mercedes_f1_2014.png", blurb: "2014-style plate. Boost, neutral, limiter, and the face rotaries.", buttons: 6, rotaries: 2 },
    { id: "mercedes_road_carbon", name: "Mercedes-AMG Road Carbon", image: "mercedes_road_carbon.png", blurb: "Carbon road wheel. Left and right spoke switches: pads, volume, voice, cruise.", buttons: 7, rotaries: 0 },
    { id: "mercedes_road_led", name: "Mercedes-AMG Road LED", image: "mercedes_road_led.png", blurb: "LED road wheel. Spoke touch pads, volume, voice, and cruise.", buttons: 7, rotaries: 0 },
  ],
  "Alfa Romeo": [
  ],
  "KTM": [
  ],
}
