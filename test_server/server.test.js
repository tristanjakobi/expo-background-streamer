const request = require("supertest");
const express = require("express");
const path = require("path");
const fs = require("fs");
const app = require("./server");

describe("File Upload Tests", () => {
  const testFilePath = path.join(__dirname, "test.txt");
  const testContent = "test content for upload verification";

  beforeAll(() => {
    fs.writeFileSync(testFilePath, testContent);
  });

  afterAll(() => {
    fs.unlinkSync(testFilePath);
    const uploadsDir = path.join(__dirname, "uploads");
    if (fs.existsSync(uploadsDir)) {
      fs.readdirSync(uploadsDir).forEach((file) => {
        fs.unlinkSync(path.join(uploadsDir, file));
      });
    }
  });

  it("should upload a file and verify its contents", async () => {
    const response = await request(app)
      .post("/upload")
      .attach("file", testFilePath);

    expect(response.status).toBe(200);
    expect(response.body).toHaveProperty("filename");

    const uploadedFilePath = path.join(
      __dirname,
      "uploads",
      response.body.filename
    );
    expect(fs.existsSync(uploadedFilePath)).toBe(true);

    const uploadedContent = fs.readFileSync(uploadedFilePath, "utf8");
    expect(uploadedContent).toBe(testContent);
  });

  it("should return 400 when no file is uploaded", async () => {
    const response = await request(app).post("/upload");

    expect(response.status).toBe(400);
    expect(response.body).toHaveProperty("error");
  });
});
