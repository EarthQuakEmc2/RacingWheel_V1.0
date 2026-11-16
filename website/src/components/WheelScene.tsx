import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { useLayoutEffect, useRef, useState, type MutableRefObject, type RefObject } from 'react'
import * as THREE from 'three'
import type { SteerBus } from '../lib/steer'

type WheelBuild = {
  group: THREE.Group
  leds: THREE.MeshStandardMaterial[]
  dashCtx: CanvasRenderingContext2D
  dashTex: THREE.CanvasTexture
  dashW: number
  dashH: number
  lastDeg: number
  dispose: () => void
}

const LED_COLORS = [
  '#2f80ed',
  '#7c5cff',
  '#e040fb',
  '#e10600',
  '#ff5a1f',
  '#f5c518',
  '#9be34a',
  '#3ddc57',
  '#9be34a',
  '#f5c518',
  '#ff5a1f',
  '#e10600',
  '#e040fb',
  '#7c5cff',
  '#00e5ff',
]

function canvasTexture(draw: (ctx: CanvasRenderingContext2D, size: number) => void, size: number) {
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2d context unavailable')
  draw(ctx, size)
  const tex = new THREE.CanvasTexture(canvas)
  tex.wrapS = THREE.RepeatWrapping
  tex.wrapT = THREE.RepeatWrapping
  tex.anisotropy = 4
  return tex
}

function carbonTexture() {
  const tex = canvasTexture((ctx, size) => {
    const block = 16
    const tow = 4
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const bx = Math.floor(x / block)
        const by = Math.floor(y / block)
        const horizontal = (bx + by) % 2 === 0
        const stripe = horizontal ? y % tow : x % tow
        let v = stripe === 0 ? 92 : stripe === 1 ? 58 : stripe === 2 ? 34 : 70
        if ((horizontal ? bx : by) % 2 === 0) v += 12
        ctx.fillStyle = `rgb(${v},${v + 2},${v + 6})`
        ctx.fillRect(x, y, 1, 1)
      }
    }
  }, 128)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.repeat.set(5, 5)
  return tex
}

function gripBump() {
  const tex = canvasTexture((ctx, size) => {
    ctx.fillStyle = '#7a7a7a'
    ctx.fillRect(0, 0, size, size)
    for (let y = 10; y < size; y += 16) {
      const ox = (Math.floor(y / 16) % 2) * 8
      for (let x = 8; x < size; x += 16) {
        ctx.fillStyle = '#d0d0d0'
        ctx.beginPath()
        ctx.arc(x + ox, y, 2.4, 0, Math.PI * 2)
        ctx.fill()
      }
    }
  }, 256)
  tex.colorSpace = THREE.NoColorSpace
  tex.repeat.set(5, 2)
  return tex
}

function roundedRect(w: number, h: number, r: number) {
  const shape = new THREE.Shape()
  const x = -w / 2
  const y = -h / 2
  shape.moveTo(x + r, y)
  shape.lineTo(x + w - r, y)
  shape.absarc(x + w - r, y + r, r, -Math.PI / 2, 0, false)
  shape.lineTo(x + w, y + h - r)
  shape.absarc(x + w - r, y + h - r, r, 0, Math.PI / 2, false)
  shape.lineTo(x + r, y + h)
  shape.absarc(x + r, y + h - r, r, Math.PI / 2, Math.PI, false)
  shape.lineTo(x, y + r)
  shape.absarc(x + r, y + r, r, Math.PI, Math.PI * 1.5, false)
  return shape
}

function rimGeometry() {
  const rx = 1.22
  const ry = 0.9
  const power = 3.15
  const segments = 160
  const radial = 12
  const positions: number[] = []
  const normals: number[] = []
  const uvs: number[] = []
  const indices: number[] = []

  const point = (t: number) => {
    const a = t * Math.PI * 2
    const c = Math.cos(a)
    const s = Math.sin(a)
    const e = 2 / power
    return [Math.sign(c) * rx * Math.abs(c) ** e, Math.sign(s) * ry * Math.abs(s) ** e] as const
  }

  for (let i = 0; i <= segments; i++) {
    const t = i / segments
    const [px, py] = point(t)
    const [qx, qy] = point(t + 0.004)
    let tx = qx - px
    let ty = qy - py
    const len = Math.hypot(tx, ty) || 1
    tx /= len
    ty /= len
    const nx = ty
    const ny = -tx
    const a = t * Math.PI * 2
    const grip = Math.abs(Math.cos(a)) ** 2.15
    const radius = 0.07 + grip * 0.1

    for (let j = 0; j <= radial; j++) {
      const v = (j / radial) * Math.PI * 2
      const cv = Math.cos(v)
      const sv = Math.sin(v)
      positions.push(px + nx * cv * radius, py + ny * cv * radius, sv * radius)
      normals.push(nx * cv, ny * cv, sv)
      uvs.push(t * 6, j / radial)
    }
  }

  for (let i = 0; i < segments; i++) {
    for (let j = 0; j < radial; j++) {
      const a = i * (radial + 1) + j
      const b = (i + 1) * (radial + 1) + j
      const c = b + 1
      const d = a + 1
      indices.push(a, b, d, b, c, d)
    }
  }

  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geo.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3))
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2))
  geo.setIndex(indices)
  return geo
}

function drawDash(build: WheelBuild, deg: number) {
  const { dashCtx: ctx, dashW: w, dashH: h } = build
  ctx.fillStyle = '#07090c'
  ctx.fillRect(0, 0, w, h)
  LED_COLORS.forEach((color, i) => {
    const dist = Math.abs(i - (LED_COLORS.length - 1) / 2)
    const on = Math.abs(deg) + 7 > dist * 7.2
    ctx.globalAlpha = on ? 1 : 0.2
    ctx.fillStyle = color
    const x = 28 + i * ((w - 56) / LED_COLORS.length)
    ctx.fillRect(x, 16, (w - 56) / LED_COLORS.length - 6, 8)
  })
  ctx.globalAlpha = 1
  ctx.fillStyle = '#f4f1ea'
  ctx.font = '600 78px Oswald, sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  const label = Math.abs(deg) < 1 ? '0°' : `${deg > 0 ? 'R' : 'L'} ${Math.abs(deg)}°`
  ctx.fillText(label, w / 2, h / 2 + 6)
  ctx.fillStyle = '#f5c518'
  ctx.font = '500 16px "IBM Plex Mono", monospace'
  ctx.fillText('STEER', w / 2, h - 58)
  ctx.fillStyle = '#1c2228'
  ctx.fillRect(48, h - 34, w - 96, 8)
  const mid = w / 2
  const extent = ((w - 96) / 2) * Math.max(-1, Math.min(1, deg / 48))
  ctx.fillStyle = Math.abs(deg) > 34 ? '#e10600' : '#f5c518'
  ctx.fillRect(extent >= 0 ? mid : mid + extent, h - 34, Math.abs(extent), 8)
  build.dashTex.needsUpdate = true
}

function createWheel(): WheelBuild {
  const group = new THREE.Group()
  const geos: THREE.BufferGeometry[] = []
  const mats: THREE.Material[] = []
  const textures: THREE.Texture[] = []

  const add = (mesh: THREE.Mesh) => {
    geos.push(mesh.geometry)
    const list = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    list.forEach((m) => mats.push(m))
    group.add(mesh)
    return mesh
  }

  const carbonMap = carbonTexture()
  textures.push(carbonMap)
  const carbon = new THREE.MeshPhysicalMaterial({
    map: carbonMap,
    roughness: 0.42,
    metalness: 0.62,
    clearcoat: 0.45,
    clearcoatRoughness: 0.28,
    color: '#ffffff',
  })
  mats.push(carbon)

  const bump = gripBump()
  textures.push(bump)
  const rubber = new THREE.MeshStandardMaterial({
    color: '#141414',
    roughness: 0.92,
    metalness: 0.04,
    bumpMap: bump,
    bumpScale: 0.07,
  })
  mats.push(rubber)

  const metal = new THREE.MeshStandardMaterial({
    color: '#8d949c',
    roughness: 0.32,
    metalness: 0.86,
  })
  mats.push(metal)

  const darkMetal = new THREE.MeshStandardMaterial({
    color: '#1a1d22',
    roughness: 0.4,
    metalness: 0.72,
  })
  mats.push(darkMetal)

  const rim = new THREE.Mesh(rimGeometry(), rubber)
  geos.push(rim.geometry)
  group.add(rim)

  const plateGeo = new THREE.ExtrudeGeometry(roundedRect(1.58, 1.24, 0.12), {
    depth: 0.08,
    bevelEnabled: true,
    bevelThickness: 0.015,
    bevelSize: 0.015,
    bevelSegments: 2,
    curveSegments: 8,
  })
  plateGeo.center()
  const plate = new THREE.Mesh(plateGeo, carbon)
  plate.position.z = 0.03
  add(plate)

  const spokeGeo = new THREE.BoxGeometry(0.12, 0.34, 0.06)
  const topSpoke = new THREE.Mesh(spokeGeo, carbon)
  topSpoke.position.set(0, 0.74, 0)
  add(topSpoke)
  const bottomSpoke = new THREE.Mesh(spokeGeo, carbon)
  bottomSpoke.position.set(0, -0.74, 0)
  add(bottomSpoke)

  const chin = new THREE.Mesh(new THREE.BoxGeometry(0.62, 0.1, 0.05), carbon)
  chin.position.set(0, -0.7, 0.02)
  add(chin)

  const bezel = new THREE.Mesh(new THREE.BoxGeometry(0.7, 0.38, 0.03), darkMetal)
  bezel.position.set(0, 0.2, 0.09)
  add(bezel)

  const dashW = 512
  const dashH = 280
  const dashCanvas = document.createElement('canvas')
  dashCanvas.width = dashW
  dashCanvas.height = dashH
  const dashCtx = dashCanvas.getContext('2d')
  if (!dashCtx) throw new Error('2d context unavailable')
  const dashTex = new THREE.CanvasTexture(dashCanvas)
  dashTex.colorSpace = THREE.SRGBColorSpace
  textures.push(dashTex)
  const screenMat = new THREE.MeshBasicMaterial({ map: dashTex, toneMapped: false })
  mats.push(screenMat)
  const screen = new THREE.Mesh(new THREE.PlaneGeometry(0.64, 0.32), screenMat)
  screen.position.set(0, 0.2, 0.112)
  add(screen)

  const housing = new THREE.Mesh(new THREE.BoxGeometry(1.18, 0.09, 0.04), darkMetal)
  housing.position.set(0, 0.5, 0.08)
  add(housing)

  const leds: THREE.MeshStandardMaterial[] = []
  LED_COLORS.forEach((color, i) => {
    const mat = new THREE.MeshStandardMaterial({
      color,
      emissive: color,
      emissiveIntensity: 0.08,
      roughness: 0.35,
      metalness: 0.05,
    })
    leds.push(mat)
    mats.push(mat)
    const led = new THREE.Mesh(new THREE.BoxGeometry(0.055, 0.035, 0.03), mat)
    const x = -0.52 + (i * 1.04) / (LED_COLORS.length - 1)
    led.position.set(x, 0.5, 0.11)
    add(led)
  })

  const buttonRing = new THREE.MeshStandardMaterial({
    color: '#121418',
    roughness: 0.45,
    metalness: 0.6,
  })
  mats.push(buttonRing)

  const addButton = (x: number, y: number, z: number, color: string, radius = 0.046) => {
    const capMat = new THREE.MeshStandardMaterial({
      color,
      emissive: color,
      emissiveIntensity: 0.28,
      roughness: 0.4,
      metalness: 0.12,
    })
    mats.push(capMat)
    const capGeo = new THREE.CylinderGeometry(radius, radius * 0.96, 0.035, 18)
    capGeo.rotateX(Math.PI / 2)
    const cap = new THREE.Mesh(capGeo, capMat)
    cap.position.set(x, y, z)
    add(cap)
    const ringGeo = new THREE.TorusGeometry(radius + 0.01, 0.007, 8, 18)
    const ring = new THREE.Mesh(ringGeo, buttonRing)
    ring.position.set(x, y, z - 0.01)
    add(ring)
  }

  ;[
    [-0.68, 0.36, '#f5c518', 0.048],
    [-0.74, 0.16, '#00e5ff', 0.044],
    [0.68, 0.36, '#e040fb', 0.048],
    [0.74, 0.16, '#3ddc57', 0.044],
    [-0.46, -0.02, '#ff4b4b', 0.042],
    [-0.26, 0.04, '#f5c518', 0.038],
    [0.26, 0.04, '#4c9dff', 0.038],
    [0.46, -0.02, '#3ddc57', 0.042],
  ].forEach(([x, y, color, radius]) => addButton(Number(x), Number(y), 0.12, String(color), Number(radius)))

  addButton(-1.2, 0.34, 0.1, '#e10600', 0.042)
  addButton(1.2, 0.34, 0.1, '#f5c518', 0.042)
  addButton(-1.16, -0.02, 0.12, '#00e5ff', 0.038)
  addButton(1.16, -0.02, 0.12, '#e040fb', 0.038)

  const addRotary = (x: number, y: number, color: string) => {
    const baseGeo = new THREE.CylinderGeometry(0.105, 0.112, 0.045, 22)
    baseGeo.rotateX(Math.PI / 2)
    const base = new THREE.Mesh(baseGeo, metal)
    base.position.set(x, y, 0.1)
    add(base)
    const ringMat = new THREE.MeshStandardMaterial({
      color,
      emissive: color,
      emissiveIntensity: 0.45,
      roughness: 0.32,
      metalness: 0.2,
    })
    mats.push(ringMat)
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.09, 0.012, 8, 22), ringMat)
    ring.position.set(x, y, 0.125)
    add(ring)
    const capGeo = new THREE.CylinderGeometry(0.06, 0.06, 0.028, 18)
    capGeo.rotateX(Math.PI / 2)
    const cap = new THREE.Mesh(capGeo, darkMetal)
    cap.position.set(x, y, 0.132)
    add(cap)
    const tickMat = new THREE.MeshStandardMaterial({
      color: '#f4f1ea',
      emissive: '#f4f1ea',
      emissiveIntensity: 0.2,
      roughness: 0.4,
    })
    mats.push(tickMat)
    const tick = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.04, 0.012), tickMat)
    tick.position.set(x, y + 0.04, 0.15)
    add(tick)
  }

  addRotary(-0.38, -0.4, '#3ddc57')
  addRotary(0, -0.48, '#e10600')
  addRotary(0.38, -0.4, '#f5c518')

  ;[-1, 1].forEach((side) => {
    const geo = new THREE.CylinderGeometry(0.05, 0.05, 0.14, 14)
    geo.rotateZ(Math.PI / 2)
    const thumb = new THREE.Mesh(geo, metal)
    thumb.position.set(side * 1.02, -0.4, 0.05)
    add(thumb)
  })

  const badgeGeo = new THREE.BoxGeometry(0.28, 0.1, 0.012)
  const badge = new THREE.Mesh(badgeGeo, darkMetal)
  badge.position.set(0, -0.12, 0.11)
  add(badge)

  ;[-1, 1].forEach((side) => {
    const shape = new THREE.Shape()
    const s = side
    shape.moveTo(0.02 * s, 0.2)
    shape.lineTo(0.32 * s, 0.06)
    shape.lineTo(0.34 * s, -0.2)
    shape.lineTo(0.1 * s, -0.26)
    shape.lineTo(0.02 * s, -0.04)
    shape.closePath()
    const geo = new THREE.ExtrudeGeometry(shape, { depth: 0.025, bevelEnabled: true, bevelThickness: 0.008, bevelSize: 0.008, bevelSegments: 1 })
    const paddle = new THREE.Mesh(geo, darkMetal)
    paddle.position.set(side * 0.82, 0.02, -0.12)
    add(paddle)
  })

  ;[
    [-0.62, 0.48],
    [0.62, 0.48],
    [-0.62, -0.5],
    [0.62, -0.5],
  ].forEach(([x, y]) => {
    const geo = new THREE.CylinderGeometry(0.018, 0.018, 0.02, 10)
    geo.rotateX(Math.PI / 2)
    const screw = new THREE.Mesh(geo, metal)
    screw.position.set(x, y, 0.1)
    add(screw)
  })

  const build: WheelBuild = {
    group,
    leds,
    dashCtx,
    dashTex,
    dashW,
    dashH,
    lastDeg: 999,
    dispose: () => {},
  }
  drawDash(build, 0)

  let alive = true
  void document.fonts?.ready.then(() => {
    if (alive) build.lastDeg = 999
  })

  build.dispose = () => {
    alive = false
    new Set(geos).forEach((g) => g.dispose())
    new Set(mats).forEach((m) => m.dispose())
    textures.forEach((t) => t.dispose())
  }
  return build
}

function paint(build: WheelBuild, radians: number, reduced: boolean) {
  const deg = (radians * 180) / Math.PI
  const mag = Math.abs(deg)
  build.leds.forEach((mat, i) => {
    const dist = Math.abs(i - (build.leds.length - 1) / 2)
    const on = reduced ? dist < 1.2 : mag + 7 > dist * 7.2
    mat.emissiveIntensity = on ? 1.4 : 0.05
  })
  const rounded = Math.round(deg)
  if (rounded !== build.lastDeg) {
    build.lastDeg = rounded
    drawDash(build, rounded)
  }
}

function Scene({
  bus,
  apiRef,
  onReady,
}: {
  bus: RefObject<SteerBus>
  apiRef: MutableRefObject<(() => void) | null>
  onReady: () => void
}) {
  const [build, setBuild] = useState<WheelBuild | null>(null)
  const spin = useRef<THREE.Group>(null)
  const invalidate = useThree((s) => s.invalidate)

  useLayoutEffect(() => {
    const next = createWheel()
    setBuild(next)
    onReady()
    return () => next.dispose()
  }, [onReady])

  useLayoutEffect(() => {
    apiRef.current = invalidate
    return () => {
      apiRef.current = null
    }
  }, [apiRef, invalidate])

  useFrame(() => {
    const b = bus.current
    if (!b || !spin.current || !build) return
    spin.current.rotation.z = -b.current
    paint(build, b.current, b.reduced)
  })

  return (
    <>
      <hemisphereLight args={['#d7e3f1', '#2a1214', 0.72]} />
      <ambientLight intensity={0.28} />
      <directionalLight position={[3.2, 4.2, 6.5]} intensity={2.5} color="#fff6ee" />
      <directionalLight position={[-4.2, 1.2, 3]} intensity={0.55} color="#9ec0ff" />
      <pointLight position={[0, -0.4, 2.6]} intensity={1.6} color="#ffd2cc" distance={8} />
      <group rotation={[0.42, 0, 0]} position={[0, -0.02, 0]}>
        <group ref={spin}>{build ? <primitive object={build.group} /> : null}</group>
      </group>
    </>
  )
}

export default function WheelScene({
  bus,
  apiRef,
  onReady,
}: {
  bus: RefObject<SteerBus>
  apiRef: MutableRefObject<(() => void) | null>
  onReady: () => void
}) {
  return (
    <Canvas
      frameloop="demand"
      dpr={[1, 1.6]}
      camera={{ position: [0, 0.12, 5.15], fov: 30, near: 0.1, far: 30 }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      onCreated={({ gl }) => {
        gl.setClearColor(0x000000, 0)
        gl.toneMapping = THREE.ACESFilmicToneMapping
        gl.toneMappingExposure = 1.12
      }}
    >
      <Scene bus={bus} apiRef={apiRef} onReady={onReady} />
    </Canvas>
  )
}
