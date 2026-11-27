/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** WebSocket base URL for order alerts, such as wss://backend.example.com/ws. Empty: same host. */
  readonly VITE_WS_URL?: string
}

declare module '*.mp3' {
  const src: string
  export default src
}
declare module '*.png' {
  const src: string
  export default src
}
declare module '*.svg' {
  const src: string
  export default src
}
