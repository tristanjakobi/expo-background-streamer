import React, { useState, useEffect } from "react";
import {
  Button,
  SafeAreaView,
  ScrollView,
  Text,
  View,
  Alert,
  Platform,
} from "react-native";
import ExpoBackgroundStreamer from "../src/BackgroundStreamer";
import * as FileSystem from "expo-file-system";
import type {
  OnUploadProgress,
  OnUploadComplete,
  OnDownloadProgress,
  OnDownloadComplete,
  ErrorEvent,
  DebugEvent,
  UploadOptions,
} from "../src/BackgroundStreamer.types";
import * as Crypto from "expo-crypto";
import { Buffer } from "buffer";

const generateEncryptionKeys = async () => {
  const key = await Crypto.getRandomBytesAsync(32);
  const nonce = await Crypto.getRandomBytesAsync(16);
  return {
    key: Buffer.from(key).toString("base64"),
    nonce: Buffer.from(nonce).toString("base64"),
  };
};

export default function App() {
  const [status, setStatus] = useState("Initializing...");
  const [logs, setLogs] = useState<string[]>([]);
  const [activeTransfers, setActiveTransfers] = useState<{
    uploads: Record<string, string>;
    downloads: Record<string, string>;
  }>({ uploads: {}, downloads: {} });
  const [encryptionKeys, setEncryptionKeys] = useState<{
    key: string;
    nonce: string;
  } | null>(null);

  const addLog = (message: string) => {
    console.log(message);
    setLogs((prev) => [...prev, `${new Date().toISOString()}: ${message}`]);
  };

  // Monitor active transfers
  useEffect(() => {
    const checkActiveTransfers = async () => {
      try {
        const allTransfers =
          await ExpoBackgroundStreamer.getAllActiveTransfers();
        setActiveTransfers(allTransfers);
      } catch (error) {
        // Silently fail - don't spam logs
      }
    };

    // Check immediately
    checkActiveTransfers();

    // Then check every 2 seconds
    const interval = setInterval(checkActiveTransfers, 100);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    try {
      const subscriptions = [
        ExpoBackgroundStreamer.addListener(
          "upload-progress",
          (event: OnUploadProgress) => {
            addLog(
              `Upload progress: ${event.progress}% (${event.speed.toFixed(2)} bytes/s)`
            );
            setStatus(`Upload progress: ${event.progress}%`);
          }
        ),
        ExpoBackgroundStreamer.addListener(
          "upload-complete",
          (event: OnUploadComplete) => {
            addLog(
              `Upload complete: ${event.uploadId} (${event.duration.toFixed(2)}s)`
            );
            setStatus("Upload complete");
          }
        ),
        ExpoBackgroundStreamer.addListener(
          "download-progress",
          (event: OnDownloadProgress) => {
            addLog(
              `Download progress: ${event.progress}% (${event.speed.toFixed(2)} bytes/s)`
            );
          }
        ),
        ExpoBackgroundStreamer.addListener(
          "download-complete",
          (event: OnDownloadComplete) => {
            addLog(
              `Download complete: ${event.downloadId} (${event.duration.toFixed(2)}s)`
            );
          }
        ),
        ExpoBackgroundStreamer.addListener("error", (event: ErrorEvent) => {
          addLog(
            `Error: ${event.error}${event.code ? ` (${event.code})` : ""}`
          );
          Alert.alert("Error", event.error);
        }),
        ExpoBackgroundStreamer.addListener("debug", (event: DebugEvent) => {
          addLog(`Debug [${event.level}]: ${event.message}`);
        }),
      ];

      addLog("Event listeners set up successfully");
      setStatus("Ready");

      return () => {
        subscriptions.forEach((sub) => sub.remove());
        addLog("Event listeners cleaned up");
      };
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error occurred";
      addLog(`Error setting up listeners: ${errorMessage}`);
      setStatus(`Error: ${errorMessage}`);
    }
  }, []);

  const testUpload = async () => {
    try {
      addLog("Starting test upload...");
      setStatus("Starting upload...");

      const filePath = `${FileSystem.cacheDirectory}test.txt`;
      const testContent = "This is a test file for background upload.\n".repeat(
        100
      );

      const cacheDir = FileSystem.cacheDirectory || "";
      addLog(`Cache directory: ${cacheDir}`);
      addLog(`Document directory: ${FileSystem.documentDirectory}`);
      addLog(`Attempting to write to: ${filePath}`);

      const dirInfo = await FileSystem.getInfoAsync(cacheDir);
      addLog(
        `Cache directory exists: ${dirInfo.exists}, isDirectory: ${dirInfo.isDirectory}`
      );

      await FileSystem.writeAsStringAsync(filePath, testContent);
      const uploadedContent = await FileSystem.readAsStringAsync(filePath);
      addLog(`Upload file content length: ${uploadedContent.length}`);
      addLog(
        `Upload file content preview: ${uploadedContent.substring(0, 50)}...`
      );

      const fileInfo = await FileSystem.getInfoAsync(filePath, { size: true });
      addLog(`File size: ${fileInfo.exists ? fileInfo.size : "unknown"} bytes`);

      const uploadUrl = "http://10.0.2.2:3000/upload";
      const keys = await generateEncryptionKeys();
      setEncryptionKeys(keys);

      addLog(`Encryption enabled: true`);
      addLog(`Key length: ${Buffer.from(keys.key, "base64").length} bytes`);
      addLog(`Nonce length: ${Buffer.from(keys.nonce, "base64").length} bytes`);

      const options: UploadOptions = {
        url: uploadUrl,
        path: filePath,
        headers: { "Content-Type": "application/octet-stream" },
        encryption: {
          enabled: true,
          key: keys.key,
          nonce: keys.nonce,
        },
      };

      addLog(`Starting upload to ${uploadUrl}`);
      const uploadId = await ExpoBackgroundStreamer.startUpload(options);

      addLog(`Upload started with ID: ${uploadId}`);
      setStatus(`Upload started with ID: ${uploadId}`);
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error occurred";
      addLog(`Upload error: ${errorMessage}`);
      setStatus(`Upload error: ${errorMessage}`);
      Alert.alert("Upload Error", errorMessage);
    }
  };

  const testDownload = async () => {
    try {
      if (!encryptionKeys) {
        Alert.alert(
          "Error",
          "Please upload a file first to generate encryption keys"
        );
        return;
      }

      addLog("Starting test download...");
      setStatus("Starting download...");

      const downloadUrl =
        Platform.OS === "ios"
          ? "http://localhost:3000/download"
          : "http://10.0.2.2:3000/download";
      const dataDir = `${FileSystem.documentDirectory}data/`;
      const dirInfo = await FileSystem.getInfoAsync(dataDir);

      console.log("downloadUrl", downloadUrl);
      console.log("dataDir", dataDir);
      console.log("dirInfo", dirInfo);

      if (!dirInfo.exists) {
        await FileSystem.makeDirectoryAsync(dataDir, {
          intermediates: true,
        });
      }

      const destination = `${dataDir}downloaded.txt`;
      addLog(`Download path: ${destination}`);

      addLog(`Using existing encryption keys`);
      addLog(
        `Key length: ${Buffer.from(encryptionKeys.key, "base64").length} bytes`
      );
      addLog(
        `Nonce length: ${Buffer.from(encryptionKeys.nonce, "base64").length} bytes`
      );

      const downloadId = await ExpoBackgroundStreamer.startDownload({
        url: downloadUrl,
        path: destination.replace("file://", ""),
        headers: { Accept: "application/octet-stream" },
        encryption: {
          enabled: true,
          key: encryptionKeys.key,
          nonce: encryptionKeys.nonce,
        },
      });

      addLog(`Download started with ID: ${downloadId}`);
      setStatus(`Download started with ID: ${downloadId}`);

      // Wait a bit for download to complete and check content
      setTimeout(async () => {
        try {
          const downloadedContent =
            await FileSystem.readAsStringAsync(destination);
          addLog(`Download file content length: ${downloadedContent.length}`);
          addLog(
            `Download file content preview: ${downloadedContent.substring(0, 50)}...`
          );
        } catch (error) {
          addLog(`Error reading downloaded file: ${error}`);
        }
      }, 2000);
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error occurred";
      addLog(`Download error: ${errorMessage}`);
      setStatus(`Download error: ${errorMessage}`);
      Alert.alert("Download Error", errorMessage);
    }
  };

  const testTransferStatus = async () => {
    try {
      addLog("Getting transfer status...");

      // Get all active transfers
      const allTransfers = await ExpoBackgroundStreamer.getAllActiveTransfers();
      addLog(`All active transfers: ${JSON.stringify(allTransfers)}`);

      // Get active uploads specifically
      const activeUploads = await ExpoBackgroundStreamer.getActiveUploads();
      addLog(`Active uploads: ${JSON.stringify(activeUploads)}`);

      // Get active downloads specifically
      const activeDownloads = await ExpoBackgroundStreamer.getActiveDownloads();
      addLog(`Active downloads: ${JSON.stringify(activeDownloads)}`);

      // Check status of specific transfer (if any)
      const uploadKeys = Object.keys(activeUploads);
      if (uploadKeys.length > 0) {
        const uploadStatus = await ExpoBackgroundStreamer.getUploadStatus(
          uploadKeys[0]
        );
        addLog(`Upload ${uploadKeys[0]} status: ${uploadStatus}`);
      }

      const downloadKeys = Object.keys(activeDownloads);
      if (downloadKeys.length > 0) {
        const downloadStatus = await ExpoBackgroundStreamer.getDownloadStatus(
          downloadKeys[0]
        );
        addLog(`Download ${downloadKeys[0]} status: ${downloadStatus}`);
      }

      if (uploadKeys.length === 0 && downloadKeys.length === 0) {
        addLog("No active transfers found");
      }
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error occurred";
      addLog(`Transfer status error: ${errorMessage}`);
      Alert.alert("Transfer Status Error", errorMessage);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Background Streamer Example</Text>
        <View style={styles.group}>
          <Text style={styles.status}>{status}</Text>

          {/* Active Transfers Display */}
          <View style={styles.transfersContainer}>
            <Text style={styles.sectionHeader}>Active Transfers:</Text>
            {Object.keys(activeTransfers.uploads).length > 0 && (
              <View>
                <Text style={styles.transferType}>📤 Uploads:</Text>
                {Object.entries(activeTransfers.uploads).map(([id, status]) => (
                  <Text key={id} style={styles.transferItem}>
                    • {id.substring(0, 8)}... ({status})
                  </Text>
                ))}
              </View>
            )}
            {Object.keys(activeTransfers.downloads).length > 0 && (
              <View>
                <Text style={styles.transferType}>📥 Downloads:</Text>
                {Object.entries(activeTransfers.downloads).map(
                  ([id, status]) => (
                    <Text key={id} style={styles.transferItem}>
                      • {id.substring(0, 8)}... ({status})
                    </Text>
                  )
                )}
              </View>
            )}
            {Object.keys(activeTransfers.uploads).length === 0 &&
              Object.keys(activeTransfers.downloads).length === 0 && (
                <Text style={styles.noTransfers}>No active transfers</Text>
              )}
          </View>

          <View style={styles.buttonContainer}>
            <Button
              title="Test Upload"
              onPress={testUpload}
              disabled={status === "Initializing..."}
            />
            <Button
              title="Test Download"
              onPress={testDownload}
              disabled={status === "Initializing..."}
            />
          </View>
          <ScrollView style={styles.logs}>
            {logs.map((log, i) => (
              <Text key={i} style={styles.log}>
                {log}
              </Text>
            ))}
          </ScrollView>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = {
  header: {
    fontSize: 30,
    margin: 20,
  },
  group: {
    margin: 20,
    backgroundColor: "#fff",
    borderRadius: 10,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: "#eee",
  },
  status: {
    fontSize: 18,
    marginBottom: 20,
  },
  logs: {
    marginTop: 20,
    maxHeight: 300,
  },
  log: {
    fontSize: 12,
    fontFamily: "monospace",
    marginBottom: 4,
  },
  buttonContainer: {
    flexDirection: "row" as const,
    justifyContent: "space-around" as const,
    marginBottom: 20,
  },
  transfersContainer: {
    marginBottom: 20,
  },
  sectionHeader: {
    fontSize: 18,
    fontWeight: "bold" as const,
    marginBottom: 10,
  },
  transferType: {
    fontSize: 16,
    fontWeight: "bold" as const,
    marginBottom: 5,
  },
  transferItem: {
    fontSize: 14,
    marginBottom: 2,
  },
  noTransfers: {
    fontSize: 14,
    textAlign: "center" as const,
  },
};
