# expo-background-streamer 🚀

A powerful background file uploader and downloader for Expo/React Native applications with encryption support, progress tracking, and robust error handling.

[![npm version](https://badge.fury.io/js/expo-background-streamer.svg)](https://badge.fury.io/js/expo-background-streamer)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> ⚠️ **Note**: This package is actively maintained and tested with the latest Expo SDK.

## ✨ Features

- 📱 **Background transfers** - Upload and download files in the background on iOS and Android
- 🔒 **Encryption support** - Optional AES encryption for secure file transfers
- 🗜️ **Compression** - Built-in compression to reduce transfer times
- 📊 **Real-time progress** - Detailed progress tracking with speed and ETA
- 🎯 **Promise-based API** - Simple, modern JavaScript API
- 🔄 **Automatic retry** - Built-in retry logic for failed transfers
- 📝 **TypeScript support** - Full TypeScript definitions included
- 🎨 **Customizable notifications** - Native progress notifications
- 🚀 **Streaming transfers** - No temporary files needed
- 📱 **Cross-platform** - Works on both iOS and Android

## 📦 Installation

```bash
npx expo install expo-background-streamer
```

## 🚀 Quick Start

### Basic Upload

```typescript
import ExpoBackgroundStreamer from "expo-background-streamer";

const uploadId = await ExpoBackgroundStreamer.startUpload({
  url: "https://your-upload-endpoint.com/upload",
  path: "/path/to/your/file.mp4",
  headers: {
    "Content-Type": "application/octet-stream",
    Authorization: "Bearer your-token",
  },
});

console.log(`Upload started with ID: ${uploadId}`);
```

### Basic Download

```typescript
const downloadId = await ExpoBackgroundStreamer.startDownload({
  url: "https://example.com/large-file.zip",
  path: "/path/to/save/file.zip",
});

console.log(`Download started with ID: ${downloadId}`);
```

### With Progress Tracking

```typescript
// Listen for upload progress
const uploadSub = ExpoBackgroundStreamer.addListener(
  "upload-progress",
  (event) => {
    console.log(`Upload: ${event.progress}% - ${event.speed} bytes/s`);
    console.log(`ETA: ${event.estimatedTimeRemaining}s`);
  }
);

// Listen for download progress
const downloadSub = ExpoBackgroundStreamer.addListener(
  "download-progress",
  (event) => {
    console.log(`Download: ${event.progress}% - ${event.speed} bytes/s`);
  }
);

// Clean up listeners
uploadSub.remove();
downloadSub.remove();
```

## 🔐 Encryption Example

```typescript
import * as Crypto from "expo-crypto";
import { Buffer } from "buffer";

// Generate encryption keys
const key = await Crypto.getRandomBytesAsync(32);
const nonce = await Crypto.getRandomBytesAsync(16);

const uploadId = await ExpoBackgroundStreamer.startUpload({
  url: "https://your-secure-endpoint.com/upload",
  path: "/path/to/sensitive-file.pdf",
  encryption: {
    enabled: true,
    key: Buffer.from(key).toString("hex"),
    nonce: Buffer.from(nonce).toString("hex"),
  },
});
```

## 📚 API Reference

### Methods

#### `startUpload(options: UploadOptions): Promise<string>`

Starts a background upload and returns the upload ID.

```typescript
interface UploadOptions {
  url: string; // Upload endpoint URL
  path: string; // Local file path
  method?: string; // HTTP method (default: "POST")
  headers?: Record<string, string>; // HTTP headers
  customTransferId?: string; // Custom transfer ID
  appGroup?: string; // iOS app group identifier
  encryption?: EncryptionOptions; // Encryption settings
  compression?: CompressionOptions; // Compression settings
}
```

#### `startDownload(options: DownloadOptions): Promise<string>`

Starts a background download and returns the download ID.

```typescript
interface DownloadOptions {
  url: string; // Download URL
  path: string; // Local save path
  headers?: Record<string, string>; // HTTP headers
  customTransferId?: string; // Custom transfer ID
  appGroup?: string; // iOS app group identifier
  encryption?: EncryptionOptions; // Encryption settings
  compression?: CompressionOptions; // Compression settings
}
```

#### `cancelUpload(uploadId: string): Promise<void>`

Cancels an ongoing upload.

#### `cancelDownload(downloadId: string): Promise<void>`

Cancels an ongoing download.

#### `getAllActiveTransfers(): Promise<{uploads: Record<string, string>, downloads: Record<string, string>}>`

Gets all currently active transfers.

#### `getFileInfo(path: string): Promise<FileInfo>`

Gets detailed information about a file.

```typescript
interface FileInfo {
  exists: boolean;
  size: number;
  name: string;
  extension: string;
  mimeType: string;
}
```

### Encryption Options

```typescript
interface EncryptionOptions {
  enabled: boolean; // Enable/disable encryption
  key: string; // Hex-encoded encryption key (32 bytes)
  nonce: string; // Hex-encoded nonce (16 bytes)
}
```

### Compression Options

```typescript
interface CompressionOptions {
  enabled: boolean; // Enable/disable compression
}
```

### Events

Subscribe to transfer events using `addListener()`:

#### Upload Events

- **`upload-progress`** - Progress updates during upload

  ```typescript
  {
    uploadId: string;
    progress: number; // 0-100
    bytesWritten: number;
    totalBytes: number;
    speed: number; // bytes per second
    estimatedTimeRemaining: number; // seconds
  }
  ```

- **`upload-complete`** - Upload completed successfully

  ```typescript
  {
    uploadId: string;
    response: string;
    responseHeaders: Record<string, string>;
    responseCode: number;
    totalBytes: number;
    duration: number; // seconds
  }
  ```

- **`upload-cancelled`** - Upload was cancelled
  ```typescript
  {
    uploadId: string;
    bytesWritten: number;
    totalBytes: number;
    reason?: string;
  }
  ```

#### Download Events

- **`download-progress`** - Progress updates during download
- **`download-complete`** - Download completed successfully
- **`download-cancelled`** - Download was cancelled

#### Error & Debug Events

- **`error`** - Error occurred during transfer
- **`debug`** - Debug information (info, warn, error, debug levels)

## 🏗️ Platform-Specific Notes

### iOS

- Requires background fetch capability in your `app.json`:
  ```json
  {
    "expo": {
      "ios": {
        "backgroundModes": ["background-fetch", "background-processing"]
      }
    }
  }
  ```
- Supports background transfers even when app is terminated
- Progress notifications shown in notification center
- Supports iOS app groups for shared container access

### Android

- Requires foreground service permission in your `app.json`:
  ```json
  {
    "expo": {
      "android": {
        "permissions": ["FOREGROUND_SERVICE", "WAKE_LOCK"]
      }
    }
  }
  ```
- Supports background transfers with Doze mode optimization
- Progress notifications shown in notification center
- Works with Android's background execution limits

## 🔧 Development Setup

1. Clone the repository
2. Install dependencies: `npm install`
3. Build the module: `npm run build`
4. Run the example app: `cd example && npm install && npx expo start`

## 🤝 Contributing

I welcome contributions! Please see my [Contributing Guide](CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Update documentation
6. Submit a pull request

## 📄 License

MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- I was inspired by [android-upload-service](https://github.com/gotev/android-upload-service) and [VydiaRNFileUploader](https://github.com/Vydia/react-native-background-upload)
- Built with ❤️ for the Expo community

## 📞 Support

- 🐛 **Bug reports**: [GitHub Issues](https://github.com/tristanjakobi/expo-background-streamer/issues)
- 💬 **Questions**: [GitHub Discussions](https://github.com/tristanjakobi/expo-background-streamer/discussions)
- 📧 **Email**: tristanjakobi08@gmail.com

---

<div align="center">
  <strong>Built with ❤️ using Expo</strong>
</div>
