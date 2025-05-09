const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");

// Run the expo-module build first
console.log("Running expo-module build...");
execSync("expo-module build", { stdio: "inherit" });

// Clean up duplicate module files
console.log("Cleaning up build output...");
const buildDir = path.join(__dirname, "..", "build");

// Remove ExpoBackgroundStreamer files since they're redundant
const filesToRemove = [
  "ExpoBackgroundStreamer.js",
  "ExpoBackgroundStreamer.d.ts",
  "ExpoBackgroundStreamer.js.map",
  "ExpoBackgroundStreamer.d.ts.map",
];

filesToRemove.forEach((file) => {
  const filePath = path.join(buildDir, file);
  if (fs.existsSync(filePath)) {
    fs.unlinkSync(filePath);
  }
});

// Update package.json to point to the correct entry point
const packageJsonPath = path.join(__dirname, "..", "package.json");
const packageJson = require(packageJsonPath);

// Ensure the main and types fields point to the correct files
packageJson.main = "./build/BackgroundStreamer.js";
packageJson.types = "./build/BackgroundStreamer.d.ts";

// Write the updated package.json
fs.writeFileSync(packageJsonPath, JSON.stringify(packageJson, null, 2));

console.log("Build completed successfully!");
