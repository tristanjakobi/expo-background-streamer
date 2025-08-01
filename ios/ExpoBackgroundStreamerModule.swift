import ExpoModulesCore
import UniformTypeIdentifiers
import CryptoKit
import CommonCrypto
import Foundation

// Import encryption types directly since they're in the same module
public class ExpoBackgroundStreamerModule: Module {
  private var currentUploadId: String?
  var activeUploadTasks: [String: URLSessionUploadTask] = [:]
  var activeDownloadTasks: [String: URLSessionDownloadTask] = [:]
  private var startTimes: [String: TimeInterval] = [:]
  var downloadSessions: [String: URLSession] = [:]
  private var transferStatus: [String: String] = [:]
  
  private func calculateProgressInfo(bytesWritten: Int64, totalBytes: Int64, startTime: TimeInterval) -> [String: Any] {
    let duration = Date().timeIntervalSince1970 - startTime
    let speed = duration > 0 ? Double(bytesWritten) / duration : 0
    let remainingBytes = totalBytes - bytesWritten
    let estimatedTimeRemaining = speed > 0 ? Double(remainingBytes) / speed : 0
    
    // Calculate progress as a percentage between 0 and 100
    let progress = totalBytes > 0 ? min(100, max(0, Int((Double(bytesWritten) * 100) / Double(totalBytes)))) : 0
    
    return [
      "progress": progress,
      "bytesWritten": bytesWritten,
      "totalBytes": totalBytes,
      "speed": speed,
      "estimatedTimeRemaining": estimatedTimeRemaining
    ]
  }
  
  func sendProgressEvent(uploadId: String, bytesWritten: Int64, totalBytes: Int64) {
    let startTime = startTimes[uploadId] ?? Date().timeIntervalSince1970
    startTimes[uploadId] = startTime
    
    let progressInfo = calculateProgressInfo(bytesWritten: bytesWritten, totalBytes: totalBytes, startTime: startTime)
    var params = progressInfo
    params["uploadId"] = uploadId
    
    sendEvent("upload-progress", params)
  }
  
  func sendDownloadProgressEvent(downloadId: String, bytesWritten: Int64, totalBytes: Int64) {
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
    
    transferStatus[uploadId] = "completed"
    
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
  
  func sendDownloadCompleteEvent(downloadId: String, filePath: String) {
    let startTime = startTimes.removeValue(forKey: downloadId) ?? Date().timeIntervalSince1970
    let duration = Date().timeIntervalSince1970 - startTime
    
    transferStatus[downloadId] = "completed"
    
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
  
  func sendErrorEvent(uploadId: String? = nil, downloadId: String? = nil, error: Error, code: String? = nil, details: [String: Any]? = nil) {
    var params: [String: Any] = [
      "error": error.localizedDescription
    ]
    
    if let uploadId = uploadId {
      params["uploadId"] = uploadId
      transferStatus[uploadId] = "error"
    }
    if let downloadId = downloadId {
      params["downloadId"] = downloadId
      transferStatus[downloadId] = "error"
    }
    if let code = code {
      params["code"] = code
    }
    if let details = details {
      params["details"] = details
    }
    
    sendEvent("error", params)
  }
  
  func sendDebugEvent(message: String, level: String = "info", details: [String: Any]? = nil) {
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
            
            // Log file information
            let fileManager = FileManager.default
            guard fileManager.fileExists(atPath: filePath) else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "File does not exist: \(filePath)"])
            }
            
            let attributes = try fileManager.attributesOfItem(atPath: filePath)
            let fileSize = attributes[.size] as? Int64 ?? 0
            let fileCreationDate = attributes[.creationDate] as? Date
            let fileModificationDate = attributes[.modificationDate] as? Date
            
            print("[ExpoBackgroundStreamer] File details:")
            print("  ➤ Path: \(filePath)")
            print("  ➤ Size: \(fileSize) bytes")
            print("  ➤ Created: \(fileCreationDate?.description ?? "unknown")")
            print("  ➤ Modified: \(fileModificationDate?.description ?? "unknown")")
            
            // Log encryption details
            if let keyData = Data(base64Encoded: encryptionKey),
               let nonceData = Data(base64Encoded: encryptionNonce) {
                print("[ExpoBackgroundStreamer] Encryption details:")
                print("  ➤ Key length: \(keyData.count) bytes")
                print("  ➤ Nonce length: \(nonceData.count) bytes")
            }
            
            // Log request details
            print("[ExpoBackgroundStreamer] Upload request:")
            print("  ➤ URL: \(uploadUrl)")
            print("  ➤ Method: \(method)")
            print("  ➤ Headers: \(headers)")
            
            // Generate a unique upload ID
            let uploadId = UUID().uuidString
            self.currentUploadId = uploadId
            print("[ExpoBackgroundStreamer] Generated upload ID: \(uploadId)")
            
            // Create URL and request
            guard let url = URL(string: uploadUrl) else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"])
            }
            
            // Create request
            var request = URLRequest(url: url)
            request.httpMethod = method
            
            // Add headers
            for (key, value) in headers {
                request.setValue(value, forHTTPHeaderField: key)
            }
            
            // Get file size and set content length
            request.setValue("\(fileSize)", forHTTPHeaderField: "Content-Length")
            
            var uploadStream: InputStream
            var uploadSize: Int64
            if let encryption = options["encryption"] as? [String: Any],
               let enabled = encryption["enabled"] as? Bool,
               enabled {
                print("[ExpoBackgroundStreamer] Using EncryptedInputStream for chunked upload")
                guard let keyData = Data(base64Encoded: encryptionKey),
                      let nonceData = Data(base64Encoded: encryptionNonce),
                      let fileStream = InputStream(fileAtPath: filePath),
                      let encryptedStream = EncryptedInputStream(inputStream: fileStream, key: keyData, nonce: nonceData) else {
                    throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to create encrypted input stream"])
                }
                uploadStream = encryptedStream
                let attr = try FileManager.default.attributesOfItem(atPath: filePath)
                uploadSize = attr[.size] as? Int64 ?? 0
            } else {
                guard let fileStream = InputStream(fileAtPath: filePath) else {
                    throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to open file stream"])
                }
                uploadStream = fileStream
                let attr = try FileManager.default.attributesOfItem(atPath: filePath)
                uploadSize = attr[.size] as? Int64 ?? 0
            }
            
            // Create upload task with delegate (streaming)
            let session = URLSession.shared
            let delegate = UploadTaskDelegate(uploadId: uploadId, module: self, totalBytesExpectedToSend: uploadSize)
            let uploadTask = session.uploadTask(withStreamedRequest: request)
            uploadTask.setValue(delegate, forKey: "delegate")
            // Store stream for delegate to provide
            delegate.provideStream = { uploadStream }
            // Store task and start it
            activeUploadTasks[uploadId] = uploadTask
            transferStatus[uploadId] = "uploading"
            uploadTask.resume()
            print("[ExpoBackgroundStreamer] Upload task started (streamed)")
            
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
                self.transferStatus[uploadId] = "cancelled"
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
            guard let urlString = options["url"] as? String,
                  let url = URL(string: urlString),
                  let path = options["path"] as? String else {
                throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Missing required parameters: url and path"])
            }
            
            // Create request
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            
            // Add headers
            if let headers = options["headers"] as? [String: String] {
                for (key, value) in headers {
                    request.setValue(value, forHTTPHeaderField: key)
                }
            }
            
            // Create download task with delegate
            let downloadId = UUID().uuidString
            let delegate = DownloadTaskDelegate(downloadId: downloadId, module: self, path: path)
            
            // Create a new session for this download
            let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)
            downloadSessions[downloadId] = session
            
            // Store encryption options in delegate if enabled
            if let encryption = options["encryption"] as? [String: Any],
               let enabled = encryption["enabled"] as? Bool,
               enabled {
                guard let key = encryption["key"] as? String,
                      let nonce = encryption["nonce"] as? String else {
                    throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Missing encryption key or nonce"])
                }
                
                delegate.encryptionEnabled = true
                delegate.encryptionKey = key
                delegate.encryptionNonce = nonce
                print("[ExpoBackgroundStreamer] Download encryption enabled with key: \(key.prefix(8))..., nonce: \(nonce.prefix(8))...")
            } else {
                print("[ExpoBackgroundStreamer] Download encryption not enabled")
            }
            
            let downloadTask = session.downloadTask(with: request)
            
            // Store task and start it
            activeDownloadTasks[downloadId] = downloadTask
            transferStatus[downloadId] = "downloading"
            downloadTask.resume()
            print("[ExpoBackgroundStreamer] Download task started with ID: \(downloadId), encryption: \(delegate.encryptionEnabled ? "enabled" : "disabled")")
            
            return downloadId
            
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
                self.transferStatus[downloadId] = "cancelled"
                
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

      Function("getActiveUploads") { () -> [String: String] in
        var result: [String: String] = [:]
        // Add currently uploading transfers
        for uploadId in self.activeUploadTasks.keys {
          result[uploadId] = self.transferStatus[uploadId] ?? "uploading"
        }
        // Add failed transfers that are still being tracked
        for (uploadId, status) in self.transferStatus {
          if status == "error" {
            result[uploadId] = status
          }
        }
        return result
      }

      Function("getActiveDownloads") { () -> [String: String] in
        var result: [String: String] = [:]
        // Add currently downloading transfers
        for downloadId in self.activeDownloadTasks.keys {
          result[downloadId] = self.transferStatus[downloadId] ?? "downloading"
        }
        // Add failed transfers that are still being tracked
        for (downloadId, status) in self.transferStatus {
          if status == "error" {
            result[downloadId] = status
          }
        }
        return result
      }

      Function("getUploadStatus") { (uploadId: String) -> String? in
        if self.activeUploadTasks[uploadId] != nil {
          return self.transferStatus[uploadId] ?? "uploading"
        } else {
          return self.transferStatus[uploadId]
        }
      }

      Function("getDownloadStatus") { (downloadId: String) -> String? in
        if self.activeDownloadTasks[downloadId] != nil {
          return self.transferStatus[downloadId] ?? "downloading"
        } else {
          return self.transferStatus[downloadId]
        }
      }

      Function("getAllActiveTransfers") { () -> [String: Any] in
        var uploads: [String: String] = [:]
        var downloads: [String: String] = [:]
        
        // Add currently uploading transfers
        for uploadId in self.activeUploadTasks.keys {
          uploads[uploadId] = self.transferStatus[uploadId] ?? "uploading"
        }
        // Add failed upload transfers that are still being tracked
        for (uploadId, status) in self.transferStatus {
          if status == "error" && self.activeUploadTasks[uploadId] == nil {
            uploads[uploadId] = status
          }
        }
        
        // Add currently downloading transfers
        for downloadId in self.activeDownloadTasks.keys {
          downloads[downloadId] = self.transferStatus[downloadId] ?? "downloading"
        }
        // Add failed download transfers that are still being tracked
        for (downloadId, status) in self.transferStatus {
          if status == "error" && self.activeDownloadTasks[downloadId] == nil {
            downloads[downloadId] = status
          }
        }
        
        return [
          "uploads": uploads,
          "downloads": downloads
        ]
      }
    
  }
}

@available(iOS 13.0, *)
class UploadTaskDelegate: NSObject, URLSessionTaskDelegate, URLSessionDataDelegate, @unchecked Sendable {
    private let uploadId: String
    private let module: ExpoBackgroundStreamerModule
    private let totalBytesExpectedToSend: Int64
    private var totalBytesSent: Int64 = 0
    var provideStream: (() -> InputStream)?
    
    init(uploadId: String, module: ExpoBackgroundStreamerModule, totalBytesExpectedToSend: Int64) {
        self.uploadId = uploadId
        self.module = module
        self.totalBytesExpectedToSend = totalBytesExpectedToSend
        super.init()
        module.sendDebugEvent(message: "UploadTaskDelegate initialized for uploadId: \(uploadId), expected bytes: \(totalBytesExpectedToSend)")
    }
    
    // Provide the encrypted stream for uploadTask(withStreamedRequest:)
    func urlSession(_ session: URLSession, task: URLSessionTask, needNewBodyStream completionHandler: @escaping (InputStream?) -> Void) {
        completionHandler(provideStream?())
    }
    
    func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
        self.totalBytesSent = totalBytesSent
        
        // Simple progress calculation based only on bytes sent
        let progress = min(100, max(0, Int((Double(totalBytesSent) * 100) / Double(self.totalBytesExpectedToSend))))
        let speed = Double(bytesSent) / 1024 // KB/s
        
        print("[ExpoBackgroundStreamer] Raw values - bytesSent: \(bytesSent), totalBytesSent: \(totalBytesSent), expected: \(self.totalBytesExpectedToSend)")
        print("[ExpoBackgroundStreamer] Upload progress: \(progress)% (\(totalBytesSent) bytes)")
        print("[ExpoBackgroundStreamer] Upload speed: \(speed) KB/s")
        
        module.sendProgressEvent(uploadId: uploadId, bytesWritten: totalBytesSent, totalBytes: self.totalBytesExpectedToSend)
    }
    
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error = error {
            print("[ExpoBackgroundStreamer] Upload task failed: \(error.localizedDescription)")
            module.sendErrorEvent(uploadId: uploadId, error: error)
        } else {
            print("[ExpoBackgroundStreamer] Upload task completed successfully")
            print("  ➤ Total bytes sent: \(totalBytesSent)")
            print("  ➤ Expected bytes: \(totalBytesExpectedToSend)")
        }
    }
}

class DownloadTaskDelegate: NSObject, URLSessionDownloadDelegate {
    let downloadId: String
    let module: ExpoBackgroundStreamerModule
    let path: String
    var encryptionEnabled = false
    var encryptionKey: String?
    var encryptionNonce: String?
    
    init(downloadId: String, module: ExpoBackgroundStreamerModule, path: String) {
        self.downloadId = downloadId
        self.module = module
        self.path = path
        super.init()
        module.sendDebugEvent(message: "DownloadTaskDelegate initialized for downloadId: \(downloadId)")
    }
    
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        do {
            print("[ExpoBackgroundStreamer] Download completed, saving file...")
            print("[ExpoBackgroundStreamer] Encryption enabled: \(encryptionEnabled)")
            print("[ExpoBackgroundStreamer] Saving to path: \(path)")
            
            // Create directory if it doesn't exist
            let fileManager = FileManager.default
            let directory = (path as NSString).deletingLastPathComponent
            try fileManager.createDirectory(atPath: directory, withIntermediateDirectories: true)
            
            // Handle encryption if enabled
            if encryptionEnabled {
                print("[ExpoBackgroundStreamer] Decrypting downloaded file using AES/CTR/NoPadding stream")
                guard let key = encryptionKey,
                      let nonce = encryptionNonce,
                      let keyData = Data(base64Encoded: key),
                      let nonceData = Data(base64Encoded: nonce) else {
                    print("[ExpoBackgroundStreamer] Failed to parse encryption key/nonce")
                    throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid encryption key or nonce"])
                }
                guard let inputStream = InputStream(url: location),
                      let encryptedOutputStream = EncryptedOutputStream(filePath: path, key: keyData, nonce: nonceData) else {
                    print("[ExpoBackgroundStreamer] Failed to create encrypted output stream")
                    throw NSError(domain: "ExpoBackgroundStreamer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to create encrypted output stream"])
                }
                inputStream.open()
                defer { inputStream.close() }
                let bufferSize = 4096
                let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
                defer { buffer.deallocate() }
                var totalBytesRead = 0
                while true {
                    let bytesRead = inputStream.read(buffer, maxLength: bufferSize)
                    if bytesRead <= 0 { break }
                    let chunk = Data(bytes: buffer, count: bytesRead)
                    _ = try encryptedOutputStream.write(data: chunk)
                    totalBytesRead += bytesRead
                    print("[ExpoBackgroundStreamer] Decrypted chunk: \(bytesRead) bytes, total: \(totalBytesRead) bytes")
                }
                encryptedOutputStream.close()
                print("[ExpoBackgroundStreamer] Successfully decrypted file, total bytes: \(totalBytesRead)")
                // Verify file exists and is readable
                if fileManager.fileExists(atPath: path) {
                    let attributes = try fileManager.attributesOfItem(atPath: path)
                    print("[ExpoBackgroundStreamer] Decrypted file size: \(attributes[.size] ?? 0) bytes")
                    try fileManager.setAttributes([.posixPermissions: 0o644], ofItemAtPath: path)
                } else {
                    print("[ExpoBackgroundStreamer] Warning: Decrypted file not found at path: \(path)")
                }
            } else {
                print("[ExpoBackgroundStreamer] Encryption not enabled, moving file directly")
                // If file exists at destination, remove it
                if fileManager.fileExists(atPath: path) {
                    try fileManager.removeItem(atPath: path)
                }
                
                // Move downloaded file to final destination
                try fileManager.moveItem(at: location, to: URL(fileURLWithPath: path))
                try fileManager.setAttributes([.posixPermissions: 0o644], ofItemAtPath: path)
                print("[ExpoBackgroundStreamer] Successfully moved file to: \(path)")
            }
            
            // Send completion event
            module.sendDownloadCompleteEvent(downloadId: downloadId, filePath: path)
            
            // Clean up
            module.activeDownloadTasks.removeValue(forKey: downloadId)
            module.downloadSessions.removeValue(forKey: downloadId)
            session.finishTasksAndInvalidate()
            
        } catch {
            print("[ExpoBackgroundStreamer] Error saving downloaded file: \(error)")
            module.sendErrorEvent(downloadId: downloadId, error: error)
        }
    }
    
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        let progress = min(100, max(0, Int((Double(totalBytesWritten) * 100) / Double(totalBytesExpectedToWrite))))
        print("[ExpoBackgroundStreamer] Download progress: \(progress)% (\(totalBytesWritten)/\(totalBytesExpectedToWrite) bytes)")
        module.sendDownloadProgressEvent(downloadId: downloadId, bytesWritten: totalBytesWritten, totalBytes: totalBytesExpectedToWrite)
    }
    
    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error = error {
            print("[ExpoBackgroundStreamer] Download error: \(error)")
            module.sendErrorEvent(downloadId: downloadId, error: error)
        }
    }
}

