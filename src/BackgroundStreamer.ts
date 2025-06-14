import { requireNativeModule } from "expo-modules-core";
import type {
  DownloadOptions,
  ExpoBackgroundStreamerModuleEvents,
  FileInfo,
  UploadOptions,
} from "./BackgroundStreamer.types";

interface ExpoBackgroundStreamerModule {
  getFileInfo(path: string): Promise<FileInfo>;
  startUpload(options: UploadOptions): Promise<string>;
  cancelUpload(uploadId: string): Promise<boolean>;
  startDownload(options: DownloadOptions): Promise<string>;
  cancelDownload(downloadId: string): Promise<boolean>;
}

const module = requireNativeModule<ExpoBackgroundStreamerModule>(
  "ExpoBackgroundStreamer"
);

const listeners = new Map<
  keyof ExpoBackgroundStreamerModuleEvents,
  Set<Function>
>();

export default {
  ...module,
  addListener: <T extends keyof ExpoBackgroundStreamerModuleEvents>(
    eventName: T,
    listener: ExpoBackgroundStreamerModuleEvents[T]
  ) => {
    if (!listeners.has(eventName)) {
      listeners.set(eventName, new Set());
    }
    listeners.get(eventName)?.add(listener);

    return {
      remove: () => {
        listeners.get(eventName)?.delete(listener);
      },
    };
  },
};

export * from "./BackgroundStreamer.types";
