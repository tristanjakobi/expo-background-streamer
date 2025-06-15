import React, { useState, useEffect } from "react";
import {
  Button,
  SafeAreaView,
  ScrollView,
  Text,
  View,
  Alert,
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
  const [encryptionKeys, setEncryptionKeys] = useState<{
    key: string;
    nonce: string;
  } | null>(null);

  const addLog = (message: string) => {
    console.log(message);
    setLogs((prev) => [...prev, `${new Date().toISOString()}: ${message}`]);
  };

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

      const uploadUrl = "http://localhost:3000/upload";
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

      const downloadUrl = "http://localhost:3000/download";
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

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Background Streamer Example</Text>
        <View style={styles.group}>
          <Text style={styles.status}>{status}</Text>
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
};
