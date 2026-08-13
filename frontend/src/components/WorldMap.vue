<script setup lang="ts">
import { onMounted, ref } from 'vue'
import worldDataUrl from '../assets/maps/natural-earth-110m-countries.geojson?url'

defineProps<{ animated?: boolean }>()

type Position = [number, number]
type Geometry = { type: 'Polygon' | 'MultiPolygon'; coordinates: Position[][] | Position[][][] }
type Feature = { geometry: Geometry | null; properties: Record<string, unknown> }

const viewBox = { width: 720, height: 360 }
const project = ([longitude, latitude]: Position) => [
  ((longitude + 180) / 360) * viewBox.width,
  ((90 - latitude) / 180) * viewBox.height,
] as const

const ringPath = (ring: Position[]) => ring.map((point, index) => {
  const [x, y] = project(point)
  return `${index === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`
}).join(' ') + 'Z'

const geometryPath = (geometry: Geometry | null) => {
  if (!geometry) return ''
  const polygons = geometry.type === 'Polygon'
    ? geometry.coordinates as Position[][]
    : (geometry.coordinates as Position[][][]).flat()
  return polygons.map(ringPath).join(' ')
}

const countries = ref<{ id: string; path: string }[]>([])

onMounted(async () => {
  const response = await fetch(worldDataUrl)
  const worldData = await response.json() as { features: Feature[] }
  countries.value = worldData.features.map(feature => ({
    id: String(feature.properties.ADM0_A3 ?? feature.properties.ISO_A3 ?? feature.properties.NAME ?? ''),
    path: geometryPath(feature.geometry),
  }))
})

const regions = [
  ['美国', 'region-us'], ['加拿大', 'region-ca'], ['欧洲', 'region-eu'], ['中国', 'region-cn'],
  ['日本', 'region-jp'], ['韩国', 'region-kr'], ['澳大利亚', 'region-au'],
] as const
</script>

<template>
  <div class="world-map" :class="{ 'world-map--animated': animated }">
    <svg viewBox="0 0 720 360" role="img" aria-label="离线世界物流网络地图">
      <defs><clipPath id="world-map-clip"><rect width="720" height="360" rx="8" /></clipPath></defs>
      <g class="world-map__graticule"><path d="M0 60h720M0 120h720M0 180h720M0 240h720M0 300h720M120 0v360M240 0v360M360 0v360M480 0v360M600 0v360" /></g>
      <g class="world-map__land" clip-path="url(#world-map-clip)"><path v-for="country in countries" :key="country.id" :d="country.path" /></g>
      <g class="world-map__routes">
        <path d="M603 118 Q362 4 124 112" /><path d="M603 118 Q488 32 376 80" />
        <path d="M376 80 Q246 14 124 112" /><path d="M124 112 Q116 93 114 81" />
        <path d="M603 118 Q678 155 662 248" /><path d="M614 105 Q484 58 376 80" />
        <path d="M639 108 Q390 0 124 112" />
      </g>
      <g class="world-map__nodes"><circle cx="124" cy="112" r="5" /><circle cx="114" cy="81" r="5" /><circle cx="376" cy="80" r="5" /><circle cx="603" cy="118" r="5" /><circle cx="639" cy="108" r="5" /><circle cx="614" cy="105" r="5" /><circle cx="662" cy="248" r="5" /></g>
    </svg>
    <span v-for="([label, className]) in regions" :key="label" class="world-map__label" :class="className"><i></i>{{ label }}</span>
  </div>
</template>

<style scoped>
.world-map { min-height: 0; aspect-ratio: 2 / 1; }
.world-map svg { height: auto; aspect-ratio: 2 / 1; }
.world-map--animated .world-map__routes path { animation: route-dash-flow 10s linear infinite; stroke-dashoffset: 0; }
.world-map--animated .world-map__routes path:nth-child(2) { animation-delay: -1.4s; }
.world-map--animated .world-map__routes path:nth-child(3) { animation-delay: -2.8s; }
.world-map--animated .world-map__routes path:nth-child(4) { animation-delay: -4.2s; }
.world-map--animated .world-map__routes path:nth-child(5) { animation-delay: -5.6s; }
.world-map--animated .world-map__routes path:nth-child(6) { animation-delay: -7s; }
.world-map--animated .world-map__routes path:nth-child(7) { animation-delay: -8.4s; }
.region-us { left: 17.2%; top: 31.2%; }.region-ca { left: 15.8%; top: 22.5%; }.region-eu { left: 52.3%; top: 22%; }
.region-cn { left: 83.7%; top: 33.2%; }.region-jp { left: 88.2%; top: 30.1%; }.region-kr { left: 84.9%; top: 30.1%; }.region-au { left: 88.6%; top: 69.2%; }
@keyframes route-dash-flow { to { stroke-dashoffset: -90; } }
@media (prefers-reduced-motion: reduce) { .world-map--animated .world-map__routes path { animation: none; } }
@media (max-width: 720px) { .region-us { left: 17.2%; }.region-ca { left: 15.8%; }.region-eu { left: 52.3%; }.region-cn { left: 83.7%; }.region-jp { left: 88.2%; }.region-kr { left: 84.9%; }.region-au { left: 86%; } }
</style>
