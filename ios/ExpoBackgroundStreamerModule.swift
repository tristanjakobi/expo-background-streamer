import ExpoModulesCore
import UniformTypeIdentifiers
import CryptoKit

// Import encryption types directly since they're in the same module
public class ExpoBackgroundStreamerModule: Module {
  private var currentUploadId: String?
  private var activeUploadTasks: [String: URLSessionUploadTask] = [:]
  private var activeDownloadTasks: [String: URLSessionDownloadTask] = [:]
  private var startTimes: [String: TimeInterval] = [:]
  
  private func calculateProgressInfo(bytesWritten: Int64, totalBytes: Int64, startTime: TimeInterval) -> [String: Any] {
    let duration = Date().timeIntervalSince1970 - startTime
    let speed = duration > 0 ? Double(bytesWritten) / duration : 0
    let remainingBytes = totalBytes - bytesWritten
    let estimatedTimeRemaining = speed > 0 ? Double(remainingBytes) / speed : 0
    
    return [
      "progress": Int((Double(bytesWritten) * 100) / Double(totalBytes)),
      "bytesWritten": bytesWritten,
      "totalBytes": totalBytes,
      "speed": speed,
      "estimatedTimeRemaining": estimatedTimeRemaining
    ]
  }
  
  private func sendProgressEvent(uploadId: String, bytesWritten: Int64, totalBytes: Int64) {
    let startTime = startTimes[uploadId] ?? Date().timeIntervalSince1970
    startTimes[uploadId] = startTime
    
    let progressInfo = calculateProgressInfo(bytesWritten: bytesWritten, totalBytes: totalBytes, startTime: startTime)
    var params = progressInfo
    params["uploadId"] = uploadId
    
    sendEvent("upload-progress", params)
  }
  
  private func sendDownloadProgressEvent(downloadId: String, bytesWritten: Int64, totalBytes: Int64) {
    let startTime = startTimes[downloadId] ?? Date().timeIntervalSince1970
    startTimes[downloadId] = startTime
    
    let progressInfo = calculateProgressInfo(bytesWritten: bytesWritten, totalBytes: totalBytes, startTime: startTime)
    var params = progressInfo
    params["downloadId"] = downloadId
    
    sendEvent("download-progress", params)
  }
  
  private func sendUploadCompleteEvent(uploadId: String, response: HTTPURLResponse, responseData: Data?) {
    let startTime = startTimes.removeValue(forKey: uploadId) ?? Date().timeIntervalSince1970
    let duration = Date().timeIntervalSince1970 - startTime
    
    var params: [String: Any] = [
      "uploadId": uploadId,
      "responseCode": response.statusCode,
      "responseHeaders": response.allHeaderFields,
      "duration": duration
    ]
    
    if let data = responseData, let responseString = String(data: data, encoding: .utf8) {
      params["response"] = responseString
    }
    
    sendEvent("upload-complete", params)
  }
  
  private func sendDownloadCompleteEvent(downloadId: String, filePath: String) {
    let startTime = startTimes.removeValue(forKey: downloadId) ?? Date().timeIntervalSince1970
    let duration = Date().timeIntervalSince1970 - startTime
    
    let fileURL = URL(fileURLWithPath: filePath)
    let fileAttributes = try? FileManager.default.attributesOfItem(atPath: filePath)
    let fileSize = fileAttributes?[.size] as? Int64 ?? 0
    
    // Get MIME type using UTType
    var mimeType = "application/octet-stream"
    if let type = UTType(filenameExtension: fileURL.pathExtension) {
      mimeType = type.preferredMIMEType ?? mimeType
    }
    
    let params: [String: Any] = [
      "downloadId": downloadId,
      "filePath": filePath,
      "mimeType": mimeType,
      "totalBytes": fileSize,
      "duration": duration
    ]
    
    sendEvent("download-complete", params)
  }
  
  private func sendErrorEvent(uploadId: String? = nil, downloadId: String? = nil, error: Error, code: String? = nil, details: [String: Any]? = nil) {
    var params: [String: Any] = [
      "error": error.localizedDescription
    ]
    
    if let uploadId = uploadId {
      params["uploadId"] = uploadId
    }
    if let downloadId = downloadId {
      params["downloadId"] = downloadId
    }
    if let code = code {
      params["code"] = code
    }
    if let details = details {
      params["details"] = details
    }
    
    sendEvent("error", params)
  }
  
  private func sendDebugEvent(message: String, level: String = "info", details: [String: Any]? = nil) {
    var params: [String: Any] = [
      "message": message,
      "level": level,
      "timestamp": Date().timeIntervalSince1970 * 1000
    ]
    
    if let details = details {
      params["details"] = details
    }
    
    sendEvent("debug", params)
  }
  
  public func definition() -> ModuleDefinition {

      Name("ExpoBackgroundStreamer")

      Events("upload-progress", "download-progress", "upload-complete", "download-complete", "error", "debug")

      Function("getFileInfo") { (path: String) -> [String: Any] in
          do {
              let fileURL = URL(fileURLWithPath: path)
              let fileManager = FileManager.default
              let exists = fileManager.fileExists(atPath: path)
              
              let name = fileURL.lastPathComponent
              let `extension` = fileURL.pathExtension
              
              // Get MIME type
              var mimeType = "application/octet-stream"
              if let type = UTType(filenameExtension: `extension`) {
                  mimeType = type.preferredMIMEType ?? mimeType
              }
              
              // Get file size
              var size: Int64 = 0
              if exists {
                  let attributes = try fileManager.attributesOfItem(atPath: path)
                  size = attributes[.size] as? Int64 ?? 0
              }
              
              return [
                  "mimeType": mimeType,
                  "size": Double(size),
                  "exists": exists,
                  "name": name,
                  "extension": `extension`
              ]
          } catch {
              // Log error and return default values
              print("Error getting file info: \(error)")
              return [
                  "mimeType": "",
                  "size": 0.0,
                  "exists": false,
                  "name": "",
                  "extension": ""
              ]
          }
      }

      Function("startUpload") { (options: [String: Any]) -> String in
        do {
            // Validate required parameters
            guard let uploadUrl = options["url"] as? String else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "URL is required"])
            }
            
            guard let fileUri = options["path"] as? String else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "File path is required"])
            }
            
            // Get encryption options from nested structure
            guard let encryptionOptions = options["encryption"] as? [String: Any],
                  let encryptionKey = encryptionOptions["key"] as? String,
                  let encryptionNonce = encryptionOptions["nonce"] as? String else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "Encryption key and nonce are required"])
            }
            
            // Get optional parameters with defaults
            let method = options["method"] as? String ?? "POST"
            let headers = options["headers"] as? [String: String] ?? [:]
            
            // Convert file URI to actual file path
            let filePath: String
            if fileUri.hasPrefix("file://") {
                filePath = String(fileUri.dropFirst(7))
            } else {
                filePath = fileUri
            }
            
            // Log debug information
            print("Starting upload to \(uploadUrl)")
            print("Converted file path: \(filePath)")
            print("Method: \(method)")
            print("Headers: \(headers)")
            
            // Check if file exists
            let fileManager = FileManager.default
            guard fileManager.fileExists(atPath: filePath) else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "File does not exist: \(filePath)"])
            }
            
            // Get file size for logging
            let attributes = try fileManager.attributesOfItem(atPath: filePath)
            let fileSize = attributes[.size] as? Int64 ?? 0
            print("File exists, size: \(fileSize) bytes")
            
            // Generate a unique upload ID
            let uploadId = UUID().uuidString
            self.currentUploadId = uploadId
            
            // Create URL and request
            guard let url = URL(string: uploadUrl) else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
            }
            
            // Create request
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            
            // Add headers
            if let headers = options["headers"] as? [String: String] {
                for (key, value) in headers {
                    request.setValue(value, forHTTPHeaderField: key)
                }
            }
            
            // Handle encryption if enabled
            let inputStream: InputStream
            if let encryption = options["encryption"] as? [String: Any],
               let enabled = encryption["enabled"] as? Bool,
               enabled,
               let key = encryption["key"] as? String,
               let nonce = encryption["nonce"] as? String {
                // Create encrypted input stream
                guard let keyData = Data(base64Encoded: key),
                      let nonceData = Data(base64Encoded: nonce) else {
                    throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid encryption key or nonce"])
                }
                
                // Create ChaCha20-Poly1305 cipher
                let cipher = SymmetricKey(data: keyData)
                
                // Create encrypted input stream
                let fileStream = InputStream(fileAtPath: filePath)!
                if let encryptedStream = EncryptedInputStream(inputStream: fileStream, key: keyData, nonce: nonceData) {
                    inputStream = encryptedStream
                } else {
                    throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to create encrypted stream"])
                }
            } else {
                // Use plain input stream
                inputStream = InputStream(fileAtPath: filePath)!
            }
            
            // Create upload task
            let session = URLSession.shared
            
            // Create a custom upload task with progress tracking
            let uploadTask = session.uploadTask(with: request, fromFile: URL(fileURLWithPath: filePath)) { [weak self] data, response, error in
                guard let self = self else { return }
                
                if let error = error {
                    print("Upload error: \(error)")
                    self.sendErrorEvent(uploadId: uploadId, error: error)
                    return
                }
                
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
                    let error = NSError(domain: "ExpoBackgroundStreamer", code: statusCode, userInfo: [NSLocalizedDescriptionKey: "Upload failed with status code: \(statusCode)"])
                    self.sendErrorEvent(uploadId: uploadId, error: error)
                    return
                }
                
                // Send completion event
                self.sendUploadCompleteEvent(uploadId: uploadId, response: httpResponse, responseData: data)
                
                // Remove task from active tasks
                self.activeUploadTasks.removeValue(forKey: uploadId)
            }
            
            // Add progress tracking
            let progressObserver = uploadTask.progress.observe(\.fractionCompleted) { [weak self] progress, _ in
                guard let self = self else { return }
                let percentage = Int(progress.fractionCompleted * 100)
                self.sendProgressEvent(uploadId: uploadId, bytesWritten: 0, totalBytes: 0)
            }
            
            // Store task and start it
            activeUploadTasks[uploadId] = uploadTask
            uploadTask.resume()
            
            return uploadId
            
        } catch {
            print("Error starting upload: \(error)")
            throw error
        }
      }

      Function("cancelUpload") { (uploadId: String) -> Bool in
        do {
            // Check if this is the current upload
            if uploadId == self.currentUploadId {
                self.currentUploadId = nil
            }
            
            // Cancel the upload task if it exists
            if let task = self.activeUploadTasks[uploadId] {
                task.cancel()
                self.activeUploadTasks.removeValue(forKey: uploadId)
                print("Successfully cancelled upload: \(uploadId)")
                return true
            } else {
                print("No active upload found with ID: \(uploadId)")
                return false
            }
        } catch {
            print("Error canceling upload: \(error)")
            throw error
        }
      }

      Function("startDownload") { (options: [String: Any]) -> String in
        do {
            // Validate required parameters
            guard let urlString = options["url"] as? String else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "URL is required"])
            }
            
            guard let path = options["path"] as? String else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "Path is required"])
            }
            
            // Get optional parameters with defaults
            let method = options["method"] as? String ?? "GET"
            let headers = options["headers"] as? [String: String] ?? [:]
            let customTransferId = options["customTransferId"] as? String
            
            // Generate or use custom transfer ID
            let taskId = customTransferId ?? UUID().uuidString
            
            // Create URL and request
            guard let url = URL(string: urlString) else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = method
            
            // Add headers
            for (key, value) in headers {
                request.setValue(value, forHTTPHeaderField: key)
            }
            
            // Create download task
            let session = URLSession.shared
            let downloadTask = session.downloadTask(with: request) { [weak self] tempURL, response, error in
                guard let self = self else { return }
                
                if let error = error {
                    print("Download error: \(error)")
                    self.sendErrorEvent(downloadId: taskId, error: error)
                    return
                }
                
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
                    let error = NSError(domain: "ExpoBackgroundStreamer", code: statusCode, userInfo: [NSLocalizedDescriptionKey: "Download failed with status code: \(statusCode)"])
                    self.sendErrorEvent(downloadId: taskId, error: error)
                    return
                }
                
                guard let tempURL = tempURL else {
                    let error = NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "No temporary file URL received"])
                    self.sendErrorEvent(downloadId: taskId, error: error)
                    return
                }
                
                do {
                    // Create directory if it doesn't exist
                    let fileManager = FileManager.default
                    let directory = (path as NSString).deletingLastPathComponent
                    try fileManager.createDirectory(atPath: directory, withIntermediateDirectories: true)
                    
                    // Move file to final destination
                    if fileManager.fileExists(atPath: path) {
                        try fileManager.removeItem(atPath: path)
                    }
                    try fileManager.moveItem(at: tempURL, to: URL(fileURLWithPath: path))
                    
                    // Send completion event
                    self.sendDownloadCompleteEvent(downloadId: taskId, filePath: path)
                    
                    // Remove task from active tasks
                    self.activeDownloadTasks.removeValue(forKey: taskId)
                    
                } catch {
                    print("Error saving downloaded file: \(error)")
                    self.sendErrorEvent(downloadId: taskId, error: error)
                }
            }
            
            // Store task and start it
            activeDownloadTasks[taskId] = downloadTask
            downloadTask.resume()
            
            return taskId
            
        } catch {
            print("Error starting download: \(error)")
            throw error
        }
      }

      Function("cancelDownload") { (downloadId: String) -> Bool in
        do {
            // Get the download task
            if let task = self.activeDownloadTasks[downloadId] {
                // Cancel the task
                task.cancel()
                
                // Remove from active tasks
                self.activeDownloadTasks.removeValue(forKey: downloadId)
                
                // Send cancellation event
                self.sendDownloadCompleteEvent(downloadId: downloadId, filePath: "")
                
                print("Successfully cancelled download: \(downloadId)")
                return true
            } else {
                print("No active download found with ID: \(downloadId)")
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid download ID"])
            }
        } catch {
            print("Error canceling download: \(error)")
            throw error
        }
      }
    
  }
}

