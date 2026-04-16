#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "IkemenSffEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ========================================================
// 结构体定义区
// ========================================================
#pragma pack(push, 1)
struct SffV2Header {
    char signature[12];
    uint8_t ver0, ver1, ver2, ver3;
    uint32_t reserved;
    uint32_t reserved2;
    uint8_t compat_ver0, compat_ver1, compat_ver2, compat_ver3;
    uint32_t reserved3, reserved4;
    uint32_t offsetFirstSpriteNode; 
    uint32_t totalSprites;
    uint32_t offsetPaletteNode;
    uint32_t totalPalettes;
    uint32_t ldataOffset, ldataLength;
    uint32_t tdataOffset, tdataLength;
};
#pragma pack(pop)

// ========================================================
// 核心解码算法区
// ========================================================

// SFFv2: LZ5 暴力解压
inline void lz5_decompress_hardcore(const uint8_t* __restrict src, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; // 跳过前4字节长度
    int dst_ptr = 0;
    while (dst_ptr < dst_len) {
        uint8_t ctrl = src[src_ptr++];
        for (int i = 0; i < 8 && dst_ptr < dst_len; ++i) {
            if (ctrl & (1 << i)) {
                uint16_t pos = src[src_ptr++] | (src[src_ptr++] << 8);
                int offset = (pos >> 5) + 1;
                int count = (pos & 0x1F) + 3;
                if (count == 34) count += src[src_ptr++];
                
                for (int j = 0; j < count; ++j) {
                    dst[dst_ptr] = dst[dst_ptr - offset];
                    dst_ptr++;
                }
            } else {
                dst[dst_ptr++] = src[src_ptr++];
            }
        }
    }
}

// SFFv1: PCX 格式极速解码 (v1的图像全是PCX)
inline void pcx_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 128; // 强行跳过 128 字节的 PCX 文件头
    int dst_ptr = 0;
    while (src_ptr < src_len && dst_ptr < dst_len) {
        uint8_t byte = src[src_ptr++];
        if ((byte & 0xC0) == 0xC0) { // PCX 的 RLE 标志位
            int count = byte & 0x3F;
            uint8_t color = src[src_ptr++];
            for (int i = 0; i < count && dst_ptr < dst_len; ++i) {
                dst[dst_ptr++] = color;
            }
        } else {
            dst[dst_ptr++] = byte;
        }
    }
}

// ========================================================
// JNI 接口区
// ========================================================

// 【新增】SFFv1 专属解码接口
extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV1C(JNIEnv* env, jobject thiz, jbyteArray data, jint width, jint height, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    int dst_len = width * height;

    uint8_t* pixels = (uint8_t*)malloc(dst_len);
    
    // 调起 PCX 解码
    pcx_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, dst_len);

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        int idx = pixels[i] & 0xFF;
        if (idx == 0) {
            out_pixels[i] = 0; // 透明色
        } else {
            int p_idx = idx * 3; // v1 调色板通常是 RGB (没有 Alpha 通道，3字节一组)
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

// SFFv2 / v2.01 综合解码接口
extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV2C(JNIEnv* env, jobject thiz, jbyteArray data, jint format, jint width, jint height, jbyteArray palette) {
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    int dst_len = width * height;

    uint8_t* pixels = (uint8_t*)malloc(dst_len);

    if (format == 4) { // LZ5
        lz5_decompress_hardcore((uint8_t*)src_buf, pixels, dst_len);
    } else if (format == 0) { // RAW
        memcpy(pixels, src_buf, dst_len);
    } else if (format == 2) { // RLE8
        int src_ptr = 4; 
        int dst_ptr = 0;
        while (dst_ptr < dst_len) {
            uint8_t byte = src_buf[src_ptr++];
            if ((byte & 0xC0) == 0x40) { 
                int count = byte & 0x3F;
                uint8_t color = src_buf[src_ptr++];
                for (int i = 0; i < count && dst_ptr < dst_len; ++i) {
                    pixels[dst_ptr++] = color;
                }
            } else {
                pixels[dst_ptr++] = byte;
            }
        }
    } else if (format == 10) { // PNG
        memcpy(pixels, src_buf, dst_len);
    } else {
        memset(pixels, 0, dst_len);
    }

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        int idx = pixels[i] & 0xFF;
        if (idx == 0) {
            out_pixels[i] = 0;
        } else {
            int p_idx = idx * 4; // v2 调色板是 RGBA (4字节一组)
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
