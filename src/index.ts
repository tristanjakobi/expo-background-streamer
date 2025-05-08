// Reexport the native module. On web, it will be resolved to ExpoBackgroundStreamerModule.web.ts
// and on native platforms to ExpoBackgroundStreamerModule.ts
export { default } from './ExpoBackgroundStreamerModule';
export { default as ExpoBackgroundStreamerView } from './ExpoBackgroundStreamerView';
export * from  './ExpoBackgroundStreamer.types';
