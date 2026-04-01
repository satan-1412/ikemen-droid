package org.libsdl.app;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class AssetExtractor {

    public static long getAssetCRC(AssetManager assets, String path) {
        try (InputStream is = assets.open(path);
             CheckedInputStream cis = new CheckedInputStream(is, new CRC32())) {
            byte[] buffer = new byte[16384];
            while (cis.read(buffer) >= 0) {}
            return cis.getChecksum().getValue();
        } catch (IOException e) {
            return -1; // File doesn't exist in APK
        }
    }

    public static long getFileCRC(File file) {
        if (!file.exists()) return -2;
        try (InputStream is = new FileInputStream(file);
             CheckedInputStream cis = new CheckedInputStream(is, new CRC32())) {
            byte[] buffer = new byte[16384];
            while (cis.read(buffer) >= 0) {}
            return cis.getChecksum().getValue();
        } catch (IOException e) {
            return -3;
        }
    }

    // 替换原有的 extractAll 方法
    public static void extractAll(AssetManager assets, File targetDir) throws IOException {
        Log.i("AssetExtractor", "开始执行动态资源释放（无需 manifest.txt）...");
        // 从 assets 根目录 "" 开始递归扫描
        copyAssetFolder(assets, "", targetDir);
        Log.i("AssetExtractor", "动态资源释放完成！");
    }

    // 新增的递归扫描核心逻辑
    private static void copyAssetFolder(AssetManager assets, String currentPath, File targetDir) {
        try {
            // list() 方法会返回当前路径下的所有文件和文件夹的名称
            String[] files = assets.list(currentPath);
            
            // 如果返回的数组有内容，说明这是一个文件夹（或者是根目录）
            if (files != null && files.length > 0) {
                for (String file : files) {
                    // 安卓系统底层有时会自动在 assets 根目录注入一些系统文件夹（如 images, webkit）
                    // 我们在根目录扫描时，可以直接跳过它们，也可以选择性保留。这里做了基础过滤。
                    if (currentPath.isEmpty() && (file.equals("images") || file.equals("sounds") || file.equals("webkit") || file.equals("kuhana"))) {
                        continue; 
                    }

                    // 拼接下一级的路径
                    String nextPath = currentPath.isEmpty() ? file : currentPath + "/" + file;
                    // 递归调用，继续往深处扫
                    copyAssetFolder(assets, nextPath, targetDir);
                }
            } else {
                // 如果返回的数组为空，说明 currentPath 是一个具体的文件
                if (currentPath.isEmpty() || currentPath.equals("manifest.txt")) {
                    return; // 忽略根目录自身和废弃的清单文件
                }

                File outFile = new File(targetDir, currentPath);
                File parentDir = outFile.getParentFile();
                
                // 确保父目录存在
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    Log.e("AssetExtractor", "无法创建目录: " + parentDir.getAbsolutePath());
                }

                // 开始复制文件流
                try (InputStream in = assets.open(currentPath);
                     OutputStream out = new FileOutputStream(outFile)) {

                    byte[] buffer = new byte[16384];
                    int read;
                    long totalRead = 0;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        totalRead += read;
                    }
                    out.flush();
                    Log.d("AssetExtractor", "成功释放: " + currentPath + " (" + totalRead + " bytes)");
                } catch (IOException e) {
                    Log.e("AssetExtractor", "释放文件失败: " + currentPath, e);
                }
            }
        } catch (IOException e) {
            Log.e("AssetExtractor", "扫描路径失败: " + currentPath, e);
        }
    }
}
