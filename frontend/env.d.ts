/// <reference types="vite/client" />
/// <reference types="vite/client" />

declare module '*.geojson' {
  const value: {
    features: unknown[]
  }
  export default value
}

declare module '*.geojson?url' {
  const value: string
  export default value
}
