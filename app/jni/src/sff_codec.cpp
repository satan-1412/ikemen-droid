#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "IkemenSffEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 🚨 终极防越界 LZ5 解压沙箱 (完美修复 kfm 的 34 字节截断 BUG)
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

// 🚨 新增：Format 3 (RLE5) 极速解码沙箱 (完美适配 SFFv2 系统 UI 专属压缩)
inline void rle5_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while(src_ptr < src_len && dst_ptr < dst_len) {
        uint8_t ctrl = src[src_ptr++];
        uint16_t color = ctrl & 0x1F; // 低 5 位是颜色
        uint16_t count = ctrl >> 5;   // 高 3 位是长度
        if (count == 0) {
            if (src_ptr >= src_len) break;
            count = src[src_ptr++];
        }
        for (int i = 0; i < count && dst_ptr < dst_len; i++) {
            dst[dst_ptr++] = color;
        }
    }
}

// RLE8 解压沙箱 (完美修复版)
inline void rle8_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while (dst_ptr < dst_len && src_ptr < src_len) {
        uint8_t byte = src[src_ptr++];
        if ((byte & 0xC0) == 0x40) { 
            int count = byte & 0x3F;
            // 🚨 核心修复：RLE8 格式如果低6位为0，必须再读取下一字节作为长度，否则大量图像撕裂
            if (count == 0) {
                if (src_ptr >= src_len) break;
                count = src[src_ptr++];
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

extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV2C(JNIEnv* env, jobject thiz, jbyteArray data, jint format, jint width, jint height, jint colorDepth, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    
    int bytes_per_pixel = (colorDepth == 32) ? 4 : ((colorDepth == 24) ? 3 : 1);
    int raw_len = width * height * bytes_per_pixel;
    
    // 🚨核心修复：SFFv2 压缩格式自带 4 字节的【实际解压长度】头
    // 必须按照这个真实长度分配内存，否则遇到 Padding(行对齐填充) 直接越界崩溃！
    uint32_t expected_len = raw_len;
    if (src_len >= 4 && (format == 2 || format == 3 || format == 4)) {
        expected_len = (src_buf[0] & 0xFF) | ((src_buf[1] & 0xFF) << 8) | ((src_buf[2] & 0xFF) << 16) | ((src_buf[3] & 0xFF) << 24);
    }
    uint32_t alloc_len = (expected_len > (uint32_t)raw_len) ? expected_len : raw_len;
    uint8_t* pixels = (uint8_t*)malloc(alloc_len + 1024); // 追加 1KB 溢出安全 Padding 区
    memset(pixels, 0, alloc_len + 1024);

    // 全格式兼容解码矩阵
    if (format == 4) { // LZ5
        lz5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    } else if (format == 3) { // 🚨新增：RLE5 格式
        rle5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    } else if (format == 2) { // RLE8
        rle8_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    } else if (format == 0) { // RAW
        int copy_len = (src_len < alloc_len) ? src_len : alloc_len;
        memcpy(pixels, src_buf, copy_len);
    }

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    int dst_len = width * height;
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

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
            int idx = pixels[i] & 0xFF;
            int p_idx = idx * 4; // 🚨 V2 色表现在是4字节 (RGBA)
            int r = pal_buf[p_idx] & 0xFF;
            int g = pal_buf[p_idx + 1] & 0xFF;
            int b = pal_buf[p_idx + 2] & 0xFF;
            int a = pal_buf[p_idx + 3] & 0xFF; // 🚨 读取真实的Alpha透明度，完美修复 UI 黑框
            
            // SFFv2：透明度完全由色表的Alpha通道决定，动态支持半透明UI
            out_pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    env->ReleasePrimitiveArrayCritical(result, out_pixels, 0);
    env->ReleaseByteArrayElements(palette, pal_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    free(pixels);

    return result;
}

// SFFv1 PCX 极速解码器
inline void pcx_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 128; 
    int dst_ptr = 0;
    while (src_ptr < src_len && dst_ptr < dst_len) {
        uint8_t byte = src[src_ptr++];
        if ((byte & 0xC0) == 0xC0) {
            int count = byte & 0x3F;
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

extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV1C(JNIEnv* env, jobject thiz, jbyteArray data, jint width, jint height, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    int dst_len = width * height;
    uint8_t* pixels = (uint8_t*)malloc(dst_len);
    memset(pixels, 0, dst_len);
    
    pcx_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, dst_len);

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        int idx = pixels[i] & 0xFF;
        if (idx == 0) {
            out_pixels[i] = 0;
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
