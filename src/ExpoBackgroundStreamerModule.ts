import { requireNativeModule } from "expo-modules-core";
import {
  DownloadOptions,
  ExpoBackgroundStreamerModuleEvents,
  FileInfo,
  UploadOptions,
} from "./ExpoBackgroundStreamer.types.js";

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

const ExpoBackgroundStreamerModule =
  requireNativeModule<ExpoBackgroundStreamerModule>("ExpoBackgroundStreamer");

export default ExpoBackgroundStreamerModule;
