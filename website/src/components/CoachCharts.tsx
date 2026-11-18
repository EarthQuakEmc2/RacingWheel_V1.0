import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, BarElement, Tooltip, Legend, Filler } from 'chart.js'
import { Bar, Line } from 'react-chartjs-2'
import type { Sample } from '../lib/trace'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, BarElement, Tooltip, Legend, Filler)

const ink = '#f3f1ec'
const muted = '#8e99a3'
const grid = '#2c343d'

function options(yLabel: string) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false as const,
    interaction: { mode: 'index' as const, intersect: false },
    plugins: {
      legend: { labels: { color: ink, boxWidth: 12, font: { family: 'IBM Plex Sans', size: 12 } } },
      tooltip: { backgroundColor: '#14181d', borderColor: grid, borderWidth: 1, titleColor: ink, bodyColor: muted },
    },
    scales: {
      x: {
        title: { display: true, text: 'Distance (m)', color: muted },
        ticks: { color: muted, maxTicksLimit: 8 },
        grid: { color: grid },
      },
      y: {
        title: { display: true, text: yLabel, color: muted },
        ticks: { color: muted },
        grid: { color: grid },
      },
    },
  }
}

function distance(sample: Sample, lengthM: number) {
  return Math.round(sample.pos * lengthM)
}

export function PedalChart({ samples, lengthM, compare }: { samples: Sample[]; lengthM: number; compare?: Sample[] | null }) {
  const labels = samples.map((sample) => distance(sample, lengthM))
  return (
    <Line
      data={{
        labels,
        datasets: [
          { label: 'Throttle', data: samples.map((sample) => Math.round(sample.gas * 100)), borderColor: '#3ddc57', pointRadius: 0, borderWidth: 1.6, tension: 0.15 },
          { label: 'Brake', data: samples.map((sample) => Math.round(sample.brake * 100)), borderColor: '#e10600', pointRadius: 0, borderWidth: 1.6, tension: 0.15 },
          ...(compare
            ? [
                { label: 'Best throttle', data: compare.map((sample) => Math.round(sample.gas * 100)), borderColor: 'rgba(61,220,87,0.35)', pointRadius: 0, borderWidth: 1.2, borderDash: [4, 3] },
                { label: 'Best brake', data: compare.map((sample) => Math.round(sample.brake * 100)), borderColor: 'rgba(225,6,0,0.35)', pointRadius: 0, borderWidth: 1.2, borderDash: [4, 3] },
              ]
            : []),
        ],
      }}
      options={options('%')}
    />
  )
}

export function SpeedChart({ samples, lengthM, compare }: { samples: Sample[]; lengthM: number; compare?: Sample[] | null }) {
  return (
    <Line
      data={{
        labels: samples.map((sample) => distance(sample, lengthM)),
        datasets: [
          { label: 'Speed', data: samples.map((sample) => Math.round(sample.speed)), borderColor: '#f5c518', backgroundColor: 'rgba(245,197,24,0.08)', fill: true, pointRadius: 0, borderWidth: 1.6, tension: 0.15 },
          ...(compare
            ? [{ label: 'Best lap', data: compare.map((sample) => Math.round(sample.speed)), borderColor: 'rgba(245,197,24,0.4)', pointRadius: 0, borderWidth: 1.2, borderDash: [4, 3] }]
            : []),
        ],
      }}
      options={options('km/h')}
    />
  )
}

export function SteerChart({ samples, lengthM }: { samples: Sample[]; lengthM: number }) {
  return (
    <Line
      data={{
        labels: samples.map((sample) => distance(sample, lengthM)),
        datasets: [
          { label: 'Steering', data: samples.map((sample) => Math.round(sample.steer)), borderColor: '#4c9dff', pointRadius: 0, borderWidth: 1.6, tension: 0.1 },
        ],
      }}
      options={options('deg')}
    />
  )
}

export function SteerSplit({ samples }: { samples: Sample[] }) {
  let left = 0
  let straight = 0
  let right = 0
  for (const sample of samples) {
    if (sample.steer < -5) left += 1
    else if (sample.steer > 5) right += 1
    else straight += 1
  }
  const total = Math.max(1, samples.length)
  const pct = (count: number) => Math.round((100 * count) / total)
  return (
    <Bar
      data={{
        labels: ['Left', 'Straight', 'Right'],
        datasets: [
          {
            label: '% of lap',
            data: [pct(left), pct(straight), pct(right)],
            backgroundColor: ['#4c9dff', '#8e99a3', '#e10600'],
            borderWidth: 0,
          },
        ],
      }}
      options={{
        ...options('% of samples'),
        plugins: { ...options('% of samples').plugins, legend: { display: false } },
      }}
    />
  )
}
