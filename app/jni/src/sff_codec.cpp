#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "IkemenSffEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==============================================================================
// 🚨 算法 1：LZ5 解压引擎 (对应 SFFv2 的 Format 4)
// 完美修复 kfm 34 字节截断导致的大面积花屏 BUG
// ==============================================================================
inline void lz5_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while (dst_ptr < dst_len && src_ptr < src_len) {
        uint8_t ctrl = src[src_ptr++];
        for (int i = 0; i < 8 && dst_ptr < dst_len && src_ptr < src_len; ++i) {
            if (ctrl & (1 << i)) {
                if (src_ptr + 1 >= src_len) break;
                uint16_t pos = src[src_ptr] | (src[src_ptr + 1] << 8);
                src_ptr += 2;
                int offset = (pos >> 5) + 1;
                int count = (pos & 0x1F) + 3;
                if (count == 34) {
                    int c;
                    do {
                        if (src_ptr >= src_len) break;
                        c = src[src_ptr++];
                        count += c;
                    } while (c == 255);
                }
                for (int j = 0; j < count && dst_ptr < dst_len; ++j) {
                    int copy_idx = dst_ptr - offset;
                    dst[dst_ptr++] = (copy_idx >= 0) ? dst[copy_idx] : 0;
                }
            } else {
                dst[dst_ptr++] = src[src_ptr++];
            }
        }
    }
}

// ==============================================================================
// 🚨 算法 2：RLE5 解压引擎 (对应 SFFv2 的 Format 3)
// 适配系统 UI 专属压缩，并完美填平 Elecbyte 的 +8 暗坑
// ==============================================================================
inline void rle5_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while(src_ptr < src_len && dst_ptr < dst_len) {
        uint8_t ctrl = src[src_ptr++];
        uint16_t color = ctrl & 0x1F; // 低 5 位代表颜色索引
        uint16_t count = ctrl >> 5;   // 高 3 位代表长度
        
        // 🔥 Elecbyte 暗坑：如果长度为 0，则读取下一字节并强行 +8
        if (count == 0) {
            if (src_ptr >= src_len) break;
            count = src[src_ptr++] + 8; 
        }
        for (int i = 0; i < count && dst_ptr < dst_len; i++) {
            dst[dst_ptr++] = color;
        }
    }
}

// ==============================================================================
// 🚨 算法 3：RLE8 解压引擎 (对应 SFFv2 的 Format 2)
// 完美修复纯色大地图撕裂黑屏，填平 Elecbyte 的 +64 暗坑
// ==============================================================================
inline void rle8_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while (dst_ptr < dst_len && src_ptr < src_len) {
        uint8_t byte = src[src_ptr++];
        if ((byte & 0xC0) == 0x40) { // 判断高两位是否为 01
            int count = byte & 0x3F;
            
            // 🔥 Elecbyte 暗坑：如果低 6 位为 0，则读取下一字节并强行 +64
            if (count == 0) {
                if (src_ptr >= src_len) break;
                count = src[src_ptr++] + 64; 
            }
            if (src_ptr >= src_len) break;
            uint8_t color = src[src_ptr++];
            for (int i = 0; i < count && dst_ptr < dst_len; ++i) {
                dst[dst_ptr++] = color;
            }
        } else {
            dst[dst_ptr++] = byte;
        }
    }
}

// ==============================================================================
// 🚨 算法 4：PCX 极速解码引擎 (对应 SFFv1 也就是所有老版 MUGEN 角色)
// 彻底修复奇数宽度图像（如旧版KFM）行移位导致的全面崩溃
// ==============================================================================
inline void pcx_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len, int width, int height) {
    if (src_len < 128) return;
    
    // 🔥 从 PCX 头部第 66 字节读取真实的“每行物理字节数”
    int bytes_per_line = src[66] | (src[67] << 8); 
    if (bytes_per_line == 0) bytes_per_line = width; // 极端情况保底
    
    int src_ptr = 128; // PCX 数据从 128 字节后开始
    for (int y = 0; y < height && src_ptr < src_len; ++y) {
        int x = 0;
        while (x < bytes_per_line && src_ptr < src_len) {
            uint8_t byte = src[src_ptr++];
            if ((byte & 0xC0) == 0xC0) {
                int count = byte & 0x3F;
                if (src_ptr >= src_len) break;
                uint8_t color = src[src_ptr++];
                for (int i = 0; i < count && x < bytes_per_line; ++i, ++x) {
                    // 严格判断，丢弃奇数宽度的对齐废字节
                    if (x < width && (y * width + x) < dst_len) {
                        dst[y * width + x] = color;
                    }
                }
            } else {
                if (x < width && (y * width + x) < dst_len) {
                    dst[y * width + x] = byte;
                }
                x++;
            }
        }
    }
}


// ==============================================================================
// 桥接层 1：处理 SFFv2 的全格式分发
// ==============================================================================
extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV2C(JNIEnv* env, jobject thiz, jbyteArray data, jint format, jint width, jint height, jint colorDepth, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    
    int bytes_per_pixel = (colorDepth == 32) ? 4 : ((colorDepth == 24) ? 3 : 1);
    int raw_len = width * height * bytes_per_pixel;
    
    // 读取 SFFv2 头部的预期长度
    uint32_t expected_len = raw_len;
    if (src_len >= 4 && (format == 2 || format == 3 || format == 4)) {
        expected_len = (src_buf[0] & 0xFF) | ((src_buf[1] & 0xFF) << 8) | ((src_buf[2] & 0xFF) << 16) | ((src_buf[3] & 0xFF) << 24);
    }
    
    uint32_t alloc_len = (expected_len > (uint32_t)raw_len) ? expected_len : raw_len;
    uint8_t* pixels = (uint8_t*)malloc(alloc_len + 1024); // 追加 Padding 区防越界
    memset(pixels, 0, alloc_len + 1024);

    // 路由到各大解压沙箱 (格式 10,11,12 为 PNG，已在 Java 层通过安卓硬件解码)
    if (format == 4) {
        lz5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    } else if (format == 3) {
        rle5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    } else if (format == 2) {
        rle8_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    } else if (format == 0) { 
        // 算法 5：纯 RAW 数据，无压缩直接拷贝
        int copy_len = (src_len < alloc_len) ? src_len : alloc_len;
        memcpy(pixels, src_buf, copy_len);
    }

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    int dst_len = width * height;
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    // 像素转换矩阵
    for (int i = 0; i < dst_len; ++i) {
        if (colorDepth == 32) {
            int r = pixels[i * 4] & 0xFF;
            int g = pixels[i * 4 + 1] & 0xFF;
            int b = pixels[i * 4 + 2] & 0xFF;
            int a = pixels[i * 4 + 3] & 0xFF;
            out_pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        } else if (colorDepth == 24) {
            int r = pixels[i * 3] & 0xFF;
            int g = pixels[i * 3 + 1] & 0xFF;
            int b = pixels[i * 3 + 2] & 0xFF;
            out_pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        } else {
            // 8 位色表渲染，带 Alpha 通道支持 (修复黑边)
            int idx = pixels[i] & 0xFF;
            int p_idx = idx * 4; 
            int r = pal_buf[p_idx] & 0xFF;
            int g = pal_buf[p_idx + 1] & 0xFF;
            int b = pal_buf[p_idx + 2] & 0xFF;
            int a = pal_buf[p_idx + 3] & 0xFF; 
            out_pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    env->ReleasePrimitiveArrayCritical(result, out_pixels, 0);
    env->ReleaseByteArrayElements(palette, pal_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    free(pixels);

    return result;
}

// ==============================================================================
// 桥接层 2：处理 SFFv1 的分发
// ==============================================================================
extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV1C(JNIEnv* env, jobject thiz, jbyteArray data, jint width, jint height, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    int dst_len = width * height;
    
    uint8_t* pixels = (uint8_t*)malloc(dst_len);
    memset(pixels, 0, dst_len);
    
    // 传入 width 和 height，启动带行对齐保护的 PCX 解码器
    pcx_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, dst_len, width, height);

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        int idx = pixels[i] & 0xFF;
        if (idx == 0) {
            out_pixels[i] = 0; // 索引 0 强制透明
        } else {
            int p_idx = idx * 3;
            int r = pal_buf[p_idx] & 0xFF;
            int g = pal_buf[p_idx + 1] & 0xFF;
            int b = pal_buf[p_idx + 2] & 0xFF;
            out_pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
    }

    env->ReleasePrimitiveArrayCritical(result, out_pixels, 0);
    env->ReleaseByteArrayElements(palette, pal_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    free(pixels);

    return result;
}
