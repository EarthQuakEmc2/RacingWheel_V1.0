/** A practical set of official Assetto Corsa car folders. Any other folder name is accepted as a custom car. */

export type CarInfo = { id: string; name: string };

export const CARS: CarInfo[] = [
  { id: 'abarth500', name: 'Abarth 500' },
  { id: 'bmw_1m', name: 'BMW 1M' },
  { id: 'bmw_m3_e30', name: 'BMW M3 E30' },
  { id: 'bmw_m3_e92', name: 'BMW M3 E92' },
  { id: 'bmw_m3_gt2', name: 'BMW M3 GT2' },
  { id: 'bmw_z4', name: 'BMW Z4' },
  { id: 'bmw_z4_gt3', name: 'BMW Z4 GT3' },
  { id: 'ferrari_312t', name: 'Ferrari 312 T' },
  { id: 'ferrari_458', name: 'Ferrari 458 Italia' },
  { id: 'ferrari_599xxevo', name: 'Ferrari 599XX Evo' },
  { id: 'ferrari_f40', name: 'Ferrari F40' },
  { id: 'ferrari_laferrari', name: 'LaFerrari' },
  { id: 'ks_alfa_romeo_giulia_qv', name: 'Alfa Romeo Giulia Quadrifoglio' },
  { id: 'ks_audi_r8_lms', name: 'Audi R8 LMS' },
  { id: 'ks_ferrari_488_gt3', name: 'Ferrari 488 GT3' },
  { id: 'ks_ferrari_sf15t', name: 'Ferrari SF15-T' },
  { id: 'ks_lamborghini_huracan_gt3', name: 'Lamborghini Huracán GT3' },
  { id: 'ks_mazda_mx5_cup', name: 'Mazda MX-5 Cup' },
  { id: 'ks_mclaren_650s_gt3', name: 'McLaren 650S GT3' },
  { id: 'ks_mercedes_amg_gt3', name: 'Mercedes-AMG GT3' },
  { id: 'ks_nissan_gtr', name: 'Nissan GT-R' },
  { id: 'ks_nissan_gtr_gt3', name: 'Nissan GT-R GT3' },
  { id: 'ks_porsche_911_gt3_r_2016', name: 'Porsche 911 GT3 R 2016' },
  { id: 'ks_porsche_911_gt3_rs', name: 'Porsche 911 GT3 RS' },
  { id: 'ks_porsche_918_spyder', name: 'Porsche 918 Spyder' },
  { id: 'ks_porsche_919_hybrid_2015', name: 'Porsche 919 Hybrid 2015' },
  { id: 'ks_toyota_supra_mkiv', name: 'Toyota Supra MKIV' },
  { id: 'ktm_xbow_r', name: 'KTM X-Bow R' },
  { id: 'lotus_2_eleven', name: 'Lotus 2-Eleven' },
  { id: 'lotus_49', name: 'Lotus 49' },
  { id: 'lotus_98t', name: 'Lotus 98T' },
  { id: 'lotus_elise_sc', name: 'Lotus Elise SC' },
  { id: 'lotus_evora_gte', name: 'Lotus Evora GTE' },
  { id: 'lotus_evora_gx', name: 'Lotus Evora GX' },
  { id: 'lotus_exige_s', name: 'Lotus Exige S' },
  { id: 'lotus_exige_v6_cup', name: 'Lotus Exige V6 Cup' },
  { id: 'mclaren_mp412c', name: 'McLaren MP4-12C' },
  { id: 'mclaren_mp412c_gt3', name: 'McLaren MP4-12C GT3' },
  { id: 'mercedes_sls', name: 'Mercedes SLS AMG' },
  { id: 'mercedes_sls_gt3', name: 'Mercedes SLS AMG GT3' },
  { id: 'pagani_zonda_r', name: 'Pagani Zonda R' },
];

export function carLabel(id: string) {
  return CARS.find((car) => car.id === id)?.name ?? id;
}
