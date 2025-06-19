# Security Policy

## 🔒 Supported Versions

I provide security updates for the following versions of expo-background-streamer:

| Version | Supported |
| ------- | --------- |
| 0.4.x   | ✅        |

I don't recommend using an earlier version as those are pretty broken.

## 🚨 Reporting a Vulnerability

I try to take security seriously. If you discover a security vulnerability, please follow these steps:

### 1. Do NOT create a public issue

Please **do not** report security vulnerabilities through public GitHub issues.

### 2. Send a private report

Instead, please send an email to **tristanjakobi08@gmail.com** with:

- A description of the vulnerability
- Steps to reproduce the issue
- Potential impact
- Any suggested fixes (if you have them)

## 🛡️ Security Considerations

### File Upload Security

When using expo-background-streamer for file uploads:

- **Validate file types** on both client and server
- **Implement proper authentication** for upload endpoints
- **Use HTTPS** for all file transfers
- **Sanitize file names** to prevent path traversal attacks
- **Implement file size limits** to prevent DoS attacks

### Encryption

The module supports AES encryption for secure file transfers:

- **Generate secure keys** using `expo-crypto` or similar
- **Never hardcode encryption keys** in your application
- **Use proper key management** practices
- **Rotate keys** regularly in production

### Network Security

- **Use TLS/SSL** for all network communications
- **Validate server certificates**
- **Implement proper timeout handling**
- **Use secure authentication headers**

### Platform-Specific Security

#### iOS

- Files are stored in the app's sandbox
- Background uploads use iOS's secure background session
- Encryption keys are stored in app memory only

#### Android

- Files use Android's scoped storage where possible
- Foreground service notifications cannot be dismissed by users
- Encryption keys are cleared from memory after use

## 🔍 Security Best Practices

### For Developers Using This Library

1. **Input Validation**

   ```typescript
   // Validate file paths
   if (!filePath.startsWith(FileSystem.documentDirectory)) {
     throw new Error("Invalid file path");
   }
   ```

2. **Secure Headers**

   ```typescript
   const headers = {
     Authorization: `Bearer ${secureToken}`,
     "Content-Type": "application/octet-stream",
     "X-API-Key": process.env.API_KEY, // Never hardcode
   };
   ```

3. **Error Handling**
   ```typescript
   // Don't expose sensitive information in error messages
   ExpoBackgroundStreamer.addListener("error", (event) => {
     // Log detailed error securely
     secureLogger.error(event);
     // Show generic message to user
     showUserMessage("Upload failed. Please try again.");
   });
   ```

### For Server-Side Implementation

1. **Authentication**: Always verify user permissions
2. **File Validation**: Check file types, sizes, and content
3. **Rate Limiting**: Implement upload rate limits
4. **Virus Scanning**: Scan uploaded files for malware
5. **Logging**: Log all upload attempts for audit trails

## 📋 Security Checklist

Before deploying your app with expo-background-streamer:

- [ ] All endpoints use HTTPS
- [ ] Authentication tokens are securely stored
- [ ] File upload limits are implemented
- [ ] Error messages don't leak sensitive information
- [ ] Encryption keys are properly managed
- [ ] Server-side validation is implemented
- [ ] Rate limiting is configured
- [ ] Audit logging is enabled

## 📚 Additional Resources

- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Expo Security Guidelines](https://docs.expo.dev/guides/security/)
- [React Native Security](https://reactnative.dev/docs/security)

Thank you for helping keep expo-background-streamer and its users safe! 🙏
