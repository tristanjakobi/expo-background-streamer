import Foundation
import CommonCrypto

class EncryptedInputStream: InputStream, StreamDelegate {
    private var cryptor: CCCryptorRef?
    private let sourceStream: InputStream
    private let readBuffer: NSMutableData
    private var internalBuffer: UnsafeMutablePointer<UInt8>
    private var bufferPos: Int
    private var bufferLen: Int
    private var _delegate: StreamDelegate?
    
    init?(inputStream: InputStream, key: Data, nonce: Data) {
        self.sourceStream = inputStream
        self.readBuffer = NSMutableData(length: 4096)!
        self.internalBuffer = UnsafeMutablePointer<UInt8>.allocate(capacity: 4096)
        self.bufferPos = 0
        self.bufferLen = 0
        
        super.init(data: Data())
        
        // Set self as delegate for source stream
        sourceStream.delegate = self
        
        let status = CCCryptorCreateWithMode(
            CCOperation(kCCEncrypt),
            CCMode(kCCModeCTR),
            CCAlgorithm(kCCAlgorithmAES),
            CCPadding(ccNoPadding),
            nonce.withUnsafeBytes { $0.baseAddress },
            key.withUnsafeBytes { $0.baseAddress },
            key.count,
            nil, 0, 0,
            CCModeOptions(kCCModeOptionCTR_BE),
            &cryptor
        )
        
        if status != kCCSuccess {
            print("[EncryptedInputStream] Failed to create cryptor with status: \(status)")
            return nil
        }
        
        print("[EncryptedInputStream] Successfully initialized with key length: \(key.count), nonce length: \(nonce.count)")
    }
    
    deinit {
        if let cryptor = cryptor {
            CCCryptorRelease(cryptor)
        }
        internalBuffer.deallocate()
        sourceStream.delegate = nil
    }
    
    override func open() {
        sourceStream.open()
        print("[EncryptedInputStream] Stream opened, status: \(sourceStream.streamStatus.rawValue)")
    }
    
    override func close() {
        sourceStream.close()
        print("[EncryptedInputStream] Stream closed")
    }
    
    // MARK: - StreamDelegate
    
    override var delegate: StreamDelegate? {
        get { return _delegate }
        set { _delegate = newValue }
    }
    
    func stream(_ aStream: Stream, handle eventCode: Stream.Event) {
        _delegate?.stream?(self, handle: eventCode)
    }
    
    // MARK: - InputStream Overrides
    
    override func read(_ buffer: UnsafeMutablePointer<UInt8>, maxLength len: Int) -> Int {
        if bufferPos >= bufferLen {
            let bytesRead = sourceStream.read(internalBuffer, maxLength: 4096)
            print("[EncryptedInputStream] Read \(bytesRead) bytes from source stream")
            
            if bytesRead <= 0 {
                print("[EncryptedInputStream] No more data to read or error occurred")
                return bytesRead
            }
            
            var outMoved: size_t = 0
            let status = CCCryptorUpdate(
                cryptor,
                internalBuffer,
                bytesRead,
                readBuffer.mutableBytes,
                readBuffer.length,
                &outMoved
            )
            
            if status != kCCSuccess {
                print("[EncryptedInputStream] Encryption failed with status: \(status)")
                return -1
            }
            
            print("[EncryptedInputStream] Encrypted \(outMoved) bytes")
            bufferLen = Int(outMoved)
            bufferPos = 0
        }
        
        let available = bufferLen - bufferPos
        let toCopy = min(len, available)
        memcpy(buffer, readBuffer.bytes.advanced(by: bufferPos), toCopy)
        bufferPos += toCopy
        
        print("[EncryptedInputStream] Returning \(toCopy) bytes to caller")
        return toCopy
    }
    
    override var streamStatus: Stream.Status {
        return sourceStream.streamStatus
    }
    
    override var streamError: Error? {
        return sourceStream.streamError
    }
    
    override var hasBytesAvailable: Bool {
        return sourceStream.hasBytesAvailable || bufferPos < bufferLen
    }
    
    override func getBuffer(_ buffer: UnsafeMutablePointer<UnsafeMutablePointer<UInt8>?>, length len: UnsafeMutablePointer<Int>) -> Bool {
        return false
    }
    
    override func schedule(in aRunLoop: RunLoop, forMode mode: RunLoop.Mode) {
        sourceStream.schedule(in: aRunLoop, forMode: mode)
    }
    
    override func remove(from aRunLoop: RunLoop, forMode mode: RunLoop.Mode) {
        sourceStream.remove(from: aRunLoop, forMode: mode)
    }
    
    override func property(forKey key: Stream.PropertyKey) -> Any? {
        return sourceStream.property(forKey: key)
    }
    
    override func setProperty(_ property: Any?, forKey key: Stream.PropertyKey) -> Bool {
        return sourceStream.setProperty(property, forKey: key)
    }
}

class EncryptedOutputStream: NSObject {
    private var cryptor: CCCryptorRef?
    private var outputStream: OutputStream?
    private let filePath: String
    
    init?(filePath: String, key: Data, nonce: Data) {
        // Normalize file path
        let resolvedPath: String
        if filePath.hasPrefix("file://") {
            resolvedPath = URL(string: filePath)?.path ?? filePath
        } else {
            resolvedPath = filePath
        }
        self.filePath = resolvedPath
        
        super.init()
        
        print("[EncryptedOutputStream] initWithFilePath:")
        print("  ➤ original path: \(filePath)")
        print("  ➤ resolved path: \(resolvedPath)")
        
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: resolvedPath) {
            print("[EncryptedOutputStream] File already exists, will be overwritten: \(resolvedPath)")
        }
        
        outputStream = OutputStream(toFileAtPath: filePath, append: false)
        print("[EncryptedOutputStream] Created output stream")
        
        outputStream?.open()
        print("[EncryptedOutputStream] Stream status after open: \(outputStream?.streamStatus.rawValue ?? 0)")
        
        if outputStream?.streamStatus == .error {
            print("[EncryptedOutputStream] Failed to open stream for path: \(filePath), error: \(String(describing: outputStream?.streamError))")
            return nil
        }
        
        let status = CCCryptorCreateWithMode(
            CCOperation(kCCDecrypt),
            CCMode(kCCModeCTR),
            CCAlgorithm(kCCAlgorithmAES),
            CCPadding(ccNoPadding),
            nonce.withUnsafeBytes { $0.baseAddress },
            key.withUnsafeBytes { $0.baseAddress },
            key.count,
            nil, 0, 0,
            CCModeOptions(kCCModeOptionCTR_BE),
            &cryptor
        )
        
        if status != kCCSuccess {
            print("[EncryptedOutputStream] Failed to create cryptor with status: \(status)")
            return nil
        }
        
        print("[EncryptedOutputStream] Successfully initialized")
    }
    
    func write(data: Data) throws -> Bool {
        print("[EncryptedOutputStream] Writing data of size: \(data.count)")
        
        guard let cryptor = cryptor, let outputStream = outputStream else {
            print("[EncryptedOutputStream] Missing cryptor or output stream")
            throw NSError(domain: "EncryptedOutputStream", code: -1, userInfo: [NSLocalizedDescriptionKey: "Missing cryptor or output stream"])
        }
        
        var outMoved: size_t = 0
        let outBuffer = NSMutableData(length: data.count)!
        
        let status = CCCryptorUpdate(
            cryptor,
            data.withUnsafeBytes { $0.baseAddress },
            data.count,
            outBuffer.mutableBytes,
            outBuffer.length,
            &outMoved
        )
        
        if status != kCCSuccess {
            print("[EncryptedOutputStream] Decryption failed with status: \(status)")
            throw NSError(domain: "EncryptedOutputStream", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Decryption failed"])
        }
        
        print("[EncryptedOutputStream] Successfully decrypted data, size: \(outMoved)")
        
        if !outputStream.hasSpaceAvailable {
            print("[EncryptedOutputStream] Output stream has no space available")
        }
        
        let written = outputStream.write(outBuffer.bytes.assumingMemoryBound(to: UInt8.self), maxLength: Int(outMoved))
        print("[EncryptedOutputStream] Write returned \(written)")
        
        if written <= 0 {
            print("[EncryptedOutputStream] Write failed — streamError: \(String(describing: outputStream.streamError))")
            throw outputStream.streamError ?? NSError(domain: "EncryptedOutputStream", code: -2, userInfo: [NSLocalizedDescriptionKey: "Write returned zero or failed"])
        }
        
        let fileManager = FileManager.default
        let exists = fileManager.fileExists(atPath: filePath)
        let attrs = try? fileManager.attributesOfItem(atPath: filePath)
        
        print("[EncryptedOutputStream] File written:")
        print("  ➤ path: \(filePath)")
        print("  ➤ exists: \(exists)")
        print("  ➤ size: \(attrs?[.size] ?? 0) bytes")
        
        return true
    }
    
    func close() {
        print("[EncryptedOutputStream] Closing stream and cryptor")
        if let cryptor = cryptor {
            CCCryptorRelease(cryptor)
            self.cryptor = nil
        }
        outputStream?.close()
        outputStream = nil
        print("[EncryptedOutputStream] Closed successfully")
    }
} 