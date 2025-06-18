const express = require("express");
const fs = require("fs");
const path = require("path");
const morgan = require("morgan");

const app = express();
app.use(morgan("combined"));

const FILENAME = "stream.txt";

app.post("/upload", (req, res) => {
  const filepath = path.join(__dirname, "uploads", FILENAME);
  const writeStream = fs.createWriteStream(filepath);

  req.pipe(writeStream);

  writeStream.on("finish", () => {
    res.json({ success: true });
  });

  writeStream.on("error", (err) => {
    fs.unlink(filepath, () => {});
    res.status(500).json({ error: err.message });
  });
});

app.get("/download", (req, res) => {
  const filepath = path.join(__dirname, "uploads", FILENAME);

  if (!fs.existsSync(filepath)) {
    return res.status(404).json({ error: "File not found" });
  }

  res.download(filepath);
});

if (require.main === module) {
  const PORT = process.env.PORT || 3000;
  app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
  });
}

module.exports = app;
