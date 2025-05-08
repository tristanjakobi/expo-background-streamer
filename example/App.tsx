import React, { useState, useEffect } from "react";
import {
  Button,
  SafeAreaView,
  ScrollView,
  Text,
  View,
  Alert,
} from "react-native";
import ExpoBackgroundStreamer from "expo-background-streamer";

export default function App() {
  const [status, setStatus] = useState("Initializing...");

  useEffect(() => {
    try {
      console.log("Setting up upload listener...");
      const subscription = ExpoBackgroundStreamer.addListener(
        "upload-progress",
        (event) => {
          console.log("Upload progress event:", event);
          setStatus(`Upload progress: ${event.progress}%`);
        }
      );

      console.log("Upload listener set up successfully");
      setStatus("Ready");

      return () => {
        console.log("Cleaning up upload listener...");
        subscription.remove();
      };
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error occurred";
      console.error("Error in useEffect:", error);
      setStatus(`Error: ${errorMessage}`);
    }
  }, []);

  const testUpload = async () => {
    try {
      console.log("Starting test upload...");
      setStatus("Starting upload...");

      // Test file path - using a simple text file
      const filePath =
        "/data/data/com.expo.backgroundstreamer.example/files/test.txt";
      const uploadUrl = "https://httpbin.org/post"; // Using a test endpoint

      console.log("Calling startUpload with:", { filePath, uploadUrl });
      const uploadId = await ExpoBackgroundStreamer.startUpload({
        url: uploadUrl,
        path: filePath,
        headers: { "Content-Type": "text/plain" },
      });

      console.log("Upload started successfully with ID:", uploadId);
      setStatus(`Upload started with ID: ${uploadId}`);
    } catch (error: unknown) {
      const errorMessage =
        error instanceof Error ? error.message : "Unknown error occurred";
      console.error("Upload error:", error);
      setStatus(`Upload error: ${errorMessage}`);
      Alert.alert("Upload Error", errorMessage);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Background Streamer Example</Text>
        <View style={styles.group}>
          <Text style={styles.status}>{status}</Text>
          <Button
            title="Test Upload"
            onPress={testUpload}
            disabled={status === "Initializing..."}
          />
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
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
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
  view: {
    flex: 1,
    height: 200,
  },
  status: {
    fontSize: 18,
    marginBottom: 20,
  },
};
