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
  addListener<T extends keyof ExpoBackgroundStreamerModuleEvents>(
    eventName: T,
    listener: ExpoBackgroundStreamerModuleEvents[T]
  ): any;
}

const module = requireNativeModule<ExpoBackgroundStreamerModule>(
  "ExpoBackgroundStreamer"
);

export default module;
export * from "./BackgroundStreamer.types";
