export type OnLoadEventPayload = {
  url: string;
};

export type BackgroundStreamerEvent = {
  type:
    | "upload-progress"
    | "download-progress"
    | "upload-complete"
    | "download-complete"
    | "error"
    | "debug";
  progress?: number;
  uploadId?: string;
  downloadId?: string;
  error?: string;
  message?: string;
};

export interface ExpoBackgroundStreamerModuleEvents {
  "upload-progress": (event: OnUploadProgress) => void;
  "download-progress": (event: OnDownloadProgress) => void;
  "upload-complete": (event: OnUploadComplete) => void;
  "download-complete": (event: OnDownloadComplete) => void;
  "upload-cancelled": (event: OnUploadCancelled) => void;
  "download-cancelled": (event: OnDownloadCancelled) => void;
  error: (event: ErrorEvent) => void;
  debug: (event: DebugEvent) => void;
  [key: string]: (event: any) => void;
}

export type OnUploadProgress = {
  uploadId: string;
  progress: number;
  bytesWritten: number;
  totalBytes: number;
  speed: number; // bytes per second
  estimatedTimeRemaining: number; // seconds
};

export type OnDownloadProgress = {
  downloadId: string;
  progress: number;
  bytesWritten: number;
  totalBytes: number;
  speed: number; // bytes per second
  estimatedTimeRemaining: number; // seconds
};

export type OnUploadComplete = {
  uploadId: string;
  response: string;
  responseHeaders: Record<string, string>;
  responseCode: number;
  totalBytes: number;
  duration: number; // seconds
};

export type OnDownloadComplete = {
  downloadId: string;
  filePath: string;
  totalBytes: number;
  duration: number; // seconds
  mimeType: string;
};

export type OnUploadCancelled = {
  uploadId: string;
  bytesWritten: number;
  totalBytes: number;
  reason?: string;
};

export type OnDownloadCancelled = {
  downloadId: string;
  bytesWritten: number;
  totalBytes: number;
  reason?: string;
};

export type ErrorEvent = {
  uploadId?: string;
  downloadId?: string;
  error: string;
  code?: string;
  details?: Record<string, any>;
};

export type DebugEvent = {
  message: string;
  level: "info" | "warn" | "error" | "debug";
  timestamp: number;
  details?: Record<string, any>;
};

export type ChangeEventPayload = {
  value: string;
};

export interface FileInfo {
  mimeType: string;
  size: number;
  exists: boolean;
  name: string;
  extension: string;
}

export interface EncryptionOptions {
  enabled: boolean;
  key: string;
  nonce: string;
}

export interface CompressionOptions {
  enabled: boolean;
}

export interface BaseTransferOptions {
  url: string;
  path: string;
  method?: string;
  headers?: Record<string, string>;
  customTransferId?: string;
  appGroup?: string;
  encryption?: EncryptionOptions;
  compression?: CompressionOptions;
}

export interface UploadOptions extends BaseTransferOptions {
  // Additional upload-specific options can be added here
}

export interface DownloadOptions extends BaseTransferOptions {
  // Additional download-specific options can be added here
}
