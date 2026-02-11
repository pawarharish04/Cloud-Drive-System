# Security Configuration & Hardening Guide

## 1. S3 Bucket Security
We enforce a "Private by Default" policy.

### Bucket Policy
Ensure **Block Public Access** is enabled for the bucket.
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "DenyPublicRead",
            "Effect": "Deny",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::your-bucket-name/*",
            "Condition": {
                "Bool": {
                    "aws:SecureTransport": "false"
                }
            }
        }
    ]
}
```
*Note: The application uses Presigned URLs, so the bucket itself must remain private. Only the IAM Role of the service has access.*

### Encryption
We enforce **Server-Side Encryption (SSE-S3)** (AES-256) on all uploads.
The application sets `.serverSideEncryption(ServerSideEncryption.AES256)` on every `initiateMultipartUpload` call.

### Lifecycle Rules
To prevent orphaned data and cost leakage:
- **Rule Name**: `CleanupIncompleteMultipartUploads`
- **Action**: Abort incomplete multipart uploads after **7 days**.
- **Scope**: Whole bucket.

## 2. Secure Download Flow
A strict "Token Exchange" pattern is used instead of public links.

1.  **Client Request**: `GET /files/{fileId}/download` with Auth Token.
2.  **Authentication**: Gateway validates JWT and injects `X-User-Id`.
3.  **Authorization**: Service checks `FileMetadata.owner == X-User-Id`.
4.  **Token Generation**: Service generates a **Presigned URL** valid for **10 minutes**.
5.  **Access**: Client uses the temporary URL to download directly from S3.

## 3. Data Privacy & Integrity
- **No IDOR**: Access is checked against the authenticated user ID on every request.
- **No Leakage**: Internal S3 Keys and Upload IDs are never exposed in public listing APIs (only File IDs).
- **Audit**: All access attempts (successful or denied) are logged with User ID and File ID.

## 4. Secrets Management
- AWS Credentials are INJECTED via Environment Variables (`SPRING_CLOUD_AWS_CREDENTIALS_ACCESS-KEY`).
- No hardcoded secrets in source code.
