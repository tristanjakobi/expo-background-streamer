// Reexport the native module. On web, it will be resolved to ExpoBackgroundStreamerModule.web.ts
// and on native platforms to ExpoBackgroundStreamerModule.ts
import { EventSubscription } from "expo-modules-core";
import ExpoBackgroundStreamerModule from "./ExpoBackgroundStreamerModule.js";
import { ExpoBackgroundStreamerModuleEvents } from "./ExpoBackgroundStreamer.types.js";
export * from "./ExpoBackgroundStreamer.types.js";

export type OnUploadProgress = {
  type: "upload-progress";
  progress: number;
};

export type OnDownloadProgress = {
  type: "download-progress";
  progress: number;
};

export type OnUploadComplete = {
  type: "upload-complete";
  uploadId: string;
};

export type OnDownloadComplete = {
  type: "download-complete";
  downloadId: string;
};

export type ErrorEvent = {
  type: "error";
  error: string;
};

export type DebugEvent = {
  type: "debug";
  message: string;
};

export function addListener<T extends keyof ExpoBackgroundStreamerModuleEvents>(
  eventName: T,
  listener: ExpoBackgroundStreamerModuleEvents[T]
): EventSubscription {
  return ExpoBackgroundStreamerModule.addListener(eventName, listener);
}

export function removeListener(listener: EventSubscription) {
  return listener.remove();
}

export default ExpoBackgroundStreamerModule;
