package com.gamecenter.app.moduleloader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gamecenter.app.core.security.ModuleSignatureVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 模块校验器。
 * 
 * 负责验证模块 APK 的签名、完整性和兼容性。
 * 所有外置模块在加载前必须通过校验。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleVerifier {
    
    private static final String TAG = "ModuleVerifier";
    
    /** SHA-256 哈希算法名称 */
    private static final String ALGORITHM_SHA256 = "SHA-256";
    
    /** 缓冲区大小：8KB */
    private static final int BUFFER_SIZE = 8192;
    
    /** 签名验证结果：成功 */
    public static final int VERIFY_SUCCESS = 0;
    
    /** 签名验证结果：APK 文件不存在 */
    public static final int VERIFY_ERROR_FILE_NOT_FOUND = 1001;
    
    /** 签名验证结果：APK 签名验证失败 */
    public static final int VERIFY_ERROR_SIGNATURE_FAILED = 1002;
    
    /** 签名验证结果：完整性校验失败（SHA-256 不匹配） */
    public static final int VERIFY_ERROR_INTEGRITY_FAILED = 1003;
    
    /** 签名验证结果：版本不兼容 */
    public static final int VERIFY_ERROR_VERSION_INCOMPATIBLE = 1004;
    
    /** 签名验证结果：IO 错误 */
    public static final int VERIFY_ERROR_IO = 1005;
    
    /**
     * 验证结果封装类。
     */
    public static class VerifyResult {
        private final boolean success;
        private final int errorCode;
        private final String errorMessage;
        
        private VerifyResult(boolean success, int errorCode, @NonNull String errorMessage) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }
        
        public static VerifyResult success() {
            return new VerifyResult(true, VERIFY_SUCCESS, "验证成功");
        }
        
        public static VerifyResult failure(int errorCode, @NonNull String errorMessage) {
            return new VerifyResult(false, errorCode, 
                    errorMessage != null ? errorMessage : "验证失败");
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
        
        @NonNull
        public String getErrorMessage() {
            return errorMessage;
        }
    }
    
    /**
     * 完整校验：签名 + 完整性 + 兼容性。
     * 
     * @param context Android Context
     * @param apkFile 模块 APK 文件
     * @param expectedSha256 期望的 SHA-256 哈希值（可为 null，表示跳过）
     * @param expectedSize 期望的文件大小（-1 表示跳过）
     * @param minFrameworkVersion 最低框架版本要求
     * @param frameworkVersionCode 当前框架版本号
     * @return 验证结果
     */
    @NonNull
    public static VerifyResult verify(@NonNull Context context, 
                                      @NonNull File apkFile,
                                      @Nullable String expectedSha256,
                                      long expectedSize,
                                      int minFrameworkVersion,
                                      int frameworkVersionCode) {
        
        // 1. 文件存在性检查
        if (apkFile == null || !apkFile.exists() || !apkFile.isFile()) {
            Log.e(TAG, "APK 文件不存在: " + (apkFile != null ? apkFile.getAbsolutePath() : "null"));
            return VerifyResult.failure(VERIFY_ERROR_FILE_NOT_FOUND, "APK 文件不存在");
        }
        
        // 2. 文件大小校验（如果提供了期望值）
        if (expectedSize > 0 && apkFile.length() != expectedSize) {
            Log.e(TAG, "文件大小不匹配: 期望=" + expectedSize + ", 实际=" + apkFile.length());
            return VerifyResult.failure(VERIFY_ERROR_INTEGRITY_FAILED, 
                    "文件大小不匹配，可能下载不完整");
        }
        
        // 3. 签名验证
        VerifyResult signatureResult = verifySignature(context, apkFile);
        if (!signatureResult.isSuccess()) {
            Log.e(TAG, "签名验证失败: " + signatureResult.getErrorMessage());
            return signatureResult;
        }
        
        // 4. 完整性校验（SHA-256）
        if (expectedSha256 != null && !expectedSha256.isEmpty()) {
            VerifyResult integrityResult = verifyIntegrity(apkFile, expectedSha256);
            if (!integrityResult.isSuccess()) {
                Log.e(TAG, "完整性校验失败: " + integrityResult.getErrorMessage());
                return integrityResult;
            }
        }
        
        // 5. 版本兼容性检查
        if (minFrameworkVersion > 0 && frameworkVersionCode < minFrameworkVersion) {
            Log.e(TAG, "版本不兼容: 模块要求框架版本=" + minFrameworkVersion 
                    + ", 当前框架版本=" + frameworkVersionCode);
            return VerifyResult.failure(VERIFY_ERROR_VERSION_INCOMPATIBLE, 
                    "模块要求框架版本 " + minFrameworkVersion + "，当前版本过低");
        }
        
        Log.d(TAG, "模块验证成功: " + apkFile.getName());
        return VerifyResult.success();
    }
    
    /**
     * 验证 APK 签名。
     * 
     * @param context Android Context
     * @param apkFile 模块 APK 文件
     * @return 验证结果
     */
    @NonNull
    public static VerifyResult verifySignature(@NonNull Context context, 
                                               @NonNull File apkFile) {
        try {
            ModuleSignatureVerifier.Result result =
                    ModuleSignatureVerifier.INSTANCE.verify(apkFile, context);
            if (result instanceof ModuleSignatureVerifier.Result.Success) {
                Log.d(TAG, "APK v2/v3 签名与发布证书匹配: " + apkFile.getName());
                return VerifyResult.success();
            }
            String reason = result instanceof ModuleSignatureVerifier.Result.Failure
                    ? ((ModuleSignatureVerifier.Result.Failure) result).getReason()
                    : ((ModuleSignatureVerifier.Result.Warning) result).getReason();
            Log.e(TAG, "签名校验失败: " + apkFile.getName() + ", " + reason);
            return VerifyResult.failure(VERIFY_ERROR_SIGNATURE_FAILED, reason);
        } catch (Exception e) {
            Log.e(TAG, "签名验证异常: " + apkFile.getName(), e);
            return VerifyResult.failure(VERIFY_ERROR_SIGNATURE_FAILED, 
                    "签名验证异常: " + e.getMessage());
        }
    }
    
    /**
     * 验证 APK 文件完整性（SHA-256 哈希值比对）。
     * 
     * @param apkFile 模块 APK 文件
     * @param expectedSha256 期望的 SHA-256 哈希值（十六进制字符串）
     * @return 验证结果
     */
    @NonNull
    public static VerifyResult verifyIntegrity(@NonNull File apkFile, 
                                               @NonNull String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isEmpty()) {
            Log.w(TAG, "未提供期望的 SHA-256 值，跳过完整性校验");
            return VerifyResult.success();
        }
        
        try {
            String actualSha256 = calculateSha256(apkFile);
            
            if (actualSha256 == null) {
                return VerifyResult.failure(VERIFY_ERROR_IO, 
                        "计算 SHA-256 失败");
            }
            
            // 不区分大小写比较
            if (actualSha256.equalsIgnoreCase(expectedSha256)) {
                Log.d(TAG, "完整性校验通过: " + apkFile.getName());
                return VerifyResult.success();
            } else {
                Log.e(TAG, "SHA-256 不匹配: " + apkFile.getName() 
                        + ", 期望=" + expectedSha256 + ", 实际=" + actualSha256);
                return VerifyResult.failure(VERIFY_ERROR_INTEGRITY_FAILED, 
                        "文件完整性校验失败（SHA-256 不匹配）");
            }
            
        } catch (IOException e) {
            Log.e(TAG, "完整性校验 IO 异常: " + apkFile.getName(), e);
            return VerifyResult.failure(VERIFY_ERROR_IO, 
                    "完整性校验失败: " + e.getMessage());
        }
    }
    
    /**
     * 计算文件的 SHA-256 哈希值。
     * 
     * @param file 待计算的文件
     * @return SHA-256 哈希值（十六进制字符串），计算失败返回 null
     * @throws IOException 文件读取失败
     */
    @Nullable
    private static String calculateSha256(@NonNull File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(ALGORITHM_SHA256);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 算法不可用", e);
            return null;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }
    
    /**
     * 计算字节数组的 SHA-256 哈希值。
     * 
     * @param bytes 待计算的字节数组
     * @return SHA-256 哈希值（十六进制字符串），计算失败返回 null
     */
    @Nullable
    private static String calculateSha256(@NonNull byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM_SHA256);
            byte[] hashBytes = digest.digest(bytes);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 算法不可用", e);
            return null;
        }
    }
    
    /**
     * 字节数组转十六进制字符串。
     * 
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    @NonNull
    private static String bytesToHex(@NonNull byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    /**
     * 从 APK 文件中提取版本信息。
     * 
     * @param context Android Context
     * @param apkFile 模块 APK 文件
     * @return 版本号，提取失败返回 -1
     */
    public static int extractVersionCode(@NonNull Context context, 
                                        @NonNull File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), 
                    0);
            
            if (packageInfo != null) {
                return packageInfo.versionCode;
            }
        } catch (Exception e) {
            Log.e(TAG, "提取版本号失败: " + apkFile.getName(), e);
        }
        
        return -1;
    }
    
    /**
     * 从 APK 文件中提取版本名称。
     * 
     * @param context Android Context
     * @param apkFile 模块 APK 文件
     * @return 版本名称，提取失败返回 null
     */
    @Nullable
    public static String extractVersionName(@NonNull Context context, 
                                            @NonNull File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), 
                    0);
            
            if (packageInfo != null) {
                return packageInfo.versionName;
            }
        } catch (Exception e) {
            Log.e(TAG, "提取版本名称失败: " + apkFile.getName(), e);
        }
        
        return null;
    }
}
