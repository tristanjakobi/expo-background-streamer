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
  getActiveUploads(): Promise<Record<string, string>>;
  getActiveDownloads(): Promise<Record<string, string>>;
  getUploadStatus(uploadId: string): Promise<string | null>;
  getDownloadStatus(downloadId: string): Promise<string | null>;
  getAllActiveTransfers(): Promise<{
    uploads: Record<string, string>;
    downloads: Record<string, string>;
  }>;
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
