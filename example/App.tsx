import React, { useState, useEffect } from "react";
import { Button, SafeAreaView, ScrollView, Text, View } from "react-native";
import ExpoBackgroundStreamer from "expo-background-streamer";

export default function App() {
  const [status, setStatus] = useState("");

  useEffect(() => {
    const subscription = ExpoBackgroundStreamer.addListener(
      "upload-progress",
      (event) => {
        setStatus(`Upload progress: ${event.progress}%`);
      }
    );

    return () => {
      subscription.remove();
    };
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Background Streamer Example</Text>
        <View style={styles.group}>
          <Text style={styles.status}>{status}</Text>
          <Button
            title="Start Upload"
            onPress={async () => {
              try {
                await ExpoBackgroundStreamer.startUpload({
                  url: "https://example.com/upload",
                  path: "/path/to/file",
                  headers: { "Content-Type": "application/octet-stream" },
                });
              } catch (error) {
                setStatus(`Error: ${error}`);
              }
            }}
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
