import ExpoModulesCore
import UniformTypeIdentifiers

public class ExpoBackgroundStreamerModule: Module {
  private var currentUploadId: String?
  private var activeUploadTasks: [String: URLSessionUploadTask] = [:]
  private var activeDownloadTasks: [String: URLSessionDownloadTask] = [:]
  
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
            
            // TODO: Implement actual upload logic here
            // This would typically involve:
            // 1. Setting up a URLSession upload task
            // 2. Handling progress updates
            // 3. Managing encryption
            // 4. Emitting events for progress and completion
            
            // For now, we'll just return the upload ID
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
                    self.sendEvent("error", [
                        "downloadId": taskId,
                        "error": error.localizedDescription
                    ])
                    return
                }
                
                guard let httpResponse = response as? HTTPURLResponse,
                      (200...299).contains(httpResponse.statusCode) else {
                    let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
                    let error = NSError(domain: "ExpoBackgroundStreamer", code: statusCode, userInfo: [NSLocalizedDescriptionKey: "Download failed with status code: \(statusCode)"])
                    self.sendEvent("error", [
                        "downloadId": taskId,
                        "error": error.localizedDescription
                    ])
                    return
                }
                
                guard let tempURL = tempURL else {
                    let error = NSError(domain: "ExpoBackgroundStreamer", code: 1, userInfo: [NSLocalizedDescriptionKey: "No temporary file URL received"])
                    self.sendEvent("error", [
                        "downloadId": taskId,
                        "error": error.localizedDescription
                    ])
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
                    self.sendEvent("download-complete", [
                        "downloadId": taskId
                    ])
                    
                    // Remove task from active tasks
                    self.activeDownloadTasks.removeValue(forKey: taskId)
                    
                } catch {
                    print("Error saving downloaded file: \(error)")
                    self.sendEvent("error", [
                        "downloadId": taskId,
                        "error": error.localizedDescription
                    ])
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
                self.sendEvent("download-complete", [
                    "downloadId": downloadId,
                    "cancelled": true
                ])
                
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

