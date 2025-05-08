# expo-background-streamer 🚀

A powerful background file uploader for Expo/React Native applications, inspired by both [android-upload-service](https://github.com/gotev/android-upload-service) and [VydiaRNFileUploader](https://github.com/Vydia/react-native-background-upload). This module provides robust background upload capabilities with progress tracking and encryption support.

> ⚠️ **Note**: This package is actively maintained and tested with the latest Expo SDK.

## Features

- 📱 Background uploads on iOS and Android
- 🔒 Optional encryption support
- 📊 Real-time progress tracking
- 🎯 Simple, Promise-based API
- 🔄 Automatic retry on failure
- 📝 TypeScript support
- 🎨 Customizable notifications
- 🚀 Streaming uploads (no temporary files needed)

## Installation

```bash
npx expo install expo-background-streamer
```

## Usage

```typescript
import ExpoBackgroundStreamer from "expo-background-streamer";

// Start an upload
const uploadId = await ExpoBackgroundStreamer.startUpload({
  url: "https://your-upload-endpoint.com/upload",
  path: "/path/to/your/file.mp4",
  method: "POST",
  headers: {
    "Content-Type": "application/octet-stream",
    Authorization: "Bearer your-token",
  },
  // Optional encryption
  encryptionKey: "your-base64-encoded-key",
  encryptionNonce: "your-base64-encoded-nonce",
});

// Listen for upload progress
const subscription = ExpoBackgroundStreamer.addListener(
  "upload-progress",
  (event) => {
    console.log(`Upload progress: ${event.progress}%`);
  }
);

// Cancel an upload
await ExpoBackgroundStreamer.cancelUpload(uploadId);

// Clean up listener
subscription.remove();
```

## API Reference

### Methods

#### `startUpload(options: UploadOptions): Promise<string>`

Starts a background upload. Returns a Promise that resolves with the upload ID.

```typescript
interface UploadOptions {
  url: string;
  path: string;
  method?: "POST" | "PUT" | "PATCH";
  headers?: Record<string, string>;
  encryptionKey?: string;
  encryptionNonce?: string;
}
```

#### `cancelUpload(uploadId: string): Promise<void>`

Cancels an ongoing upload.

#### `getFileInfo(path: string): Promise<FileInfo>`

Gets information about a file.

```typescript
interface FileInfo {
  exists: boolean;
  size: number;
  name: string;
  extension: string;
  mimeType: string;
}
```

### Events

- `upload-progress`: Fired during upload with progress percentage
- `upload-complete`: Fired when upload completes successfully
- `error`: Fired when an error occurs
- `debug`: Fired for debug logs

## Platform-Specific Notes

### iOS

- Requires background fetch capability
- Supports background uploads even when app is terminated
- Progress notifications are shown in the notification center

### Android

- Requires `FOREGROUND_SERVICE` permission
- Supports background uploads with Doze mode
- Progress notifications are shown in the notification center

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT

## Acknowledgments

- Inspired by [android-upload-service](https://github.com/gotev/android-upload-service) and [VydiaRNFileUploader](https://github.com/Vydia/react-native-background-upload)
- Built with ❤️ for the Expo community
