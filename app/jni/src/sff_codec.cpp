#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "IkemenSffEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==============================================================================
// 🚨 算法 1：LZ5 解压引擎 (100% 还原 Elecbyte 官方位移逻辑)
// ==============================================================================
inline void lz5_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while (dst_ptr < dst_len && src_ptr < src_len) {
        uint8_t ctrl = src[src_ptr++];
        for (int i = 0; i < 8 && dst_ptr < dst_len && src_ptr < src_len; ++i) {
            if ((ctrl & (1 << i)) == 0) { // 0 代表直接拷贝
                dst[dst_ptr++] = src[src_ptr++];
            } else { // 1 代表 LZ 字典回溯
                if (src_ptr + 1 >= src_len) break;
                uint16_t val = src[src_ptr] | (src[src_ptr + 1] << 8);
                src_ptr += 2;
                
                int offset = (val >> 5) + 1;
                int count = (val & 0x1F) + 3;
                
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
            }
        }
    }
}

// ==============================================================================
// 🚨 算法 2：RLE5 解压引擎 (Format 3)
// ==============================================================================
inline void rle5_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while(src_ptr < src_len && dst_ptr < dst_len) {
        uint8_t ctrl = src[src_ptr++];
        uint16_t color = ctrl & 0x1F; 
        uint16_t count = ctrl >> 5;   
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
// 🚨 算法 3：RLE8 解压引擎 (移除画蛇添足的 0x80 透明判定，恢复纯净官方算法)
// ==============================================================================
inline void rle8_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while (dst_ptr < dst_len && src_ptr < src_len) {
        uint8_t byte = src[src_ptr++];
        if ((byte & 0xC0) == 0x40) { // 01xxxxxx 代表行程长度
            int count = byte & 0x3F;
            if (count == 0) {
                if (src_ptr >= src_len) break;
                count = src[src_ptr++] + 64; 
            }
            if (src_ptr >= src_len) break;
            uint8_t color = src[src_ptr++];
            for (int i = 0; i < count && dst_ptr < dst_len; ++i) dst[dst_ptr++] = color;
        } else { // 其余所有字节全部为正常像素颜色 (包含透明)
            dst[dst_ptr++] = byte; 
        }
    }
}

// ==============================================================================
// 🚨 算法 4：PCX 极速解码引擎 (Format 1)
// ==============================================================================
inline void pcx_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len, int width, int height) {
    if (src_len < 128) return;
    int bpl = src[66] | (src[67] << 8); 
    if (bpl == 0) bpl = width; 
    int src_ptr = 128; 
    for (int y = 0; y < height && src_ptr < src_len; ++y) {
        int x = 0;
        while (x < bpl && src_ptr < src_len) {
            uint8_t byte = src[src_ptr++];
            if ((byte & 0xC0) == 0xC0) {
                int count = byte & 0x3F;
                if (src_ptr >= src_len) break;
                uint8_t color = src[src_ptr++];
                for (int i = 0; i < count && x < bpl; ++i, ++x) {
                    if (x < width && (y * width + x) < dst_len) dst[y * width + x] = color;
                }
            } else {
                if (x < width && (y * width + x) < dst_len) dst[y * width + x] = byte;
                x++;
            }
        }
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV2C(JNIEnv* env, jobject thiz, jbyteArray data, jint format, jint width, jint height, jint colorDepth, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    
    int bytes_per_pixel = (colorDepth == 32) ? 4 : ((colorDepth == 24) ? 3 : 1);
    int raw_len = width * height * bytes_per_pixel;
    
    uint32_t expected_len = raw_len;
    if (src_len >= 4 && (format == 2 || format == 3 || format == 4)) {
        expected_len = (src_buf[0] & 0xFF) | ((src_buf[1] & 0xFF) << 8) | ((src_buf[2] & 0xFF) << 16) | ((src_buf[3] & 0xFF) << 24);
    }
    
    uint32_t alloc_len = (expected_len > (uint32_t)raw_len) ? expected_len : raw_len;
    uint8_t* pixels = (uint8_t*)malloc(alloc_len + 1024); 
    memset(pixels, 0, alloc_len + 1024);

    if (format == 4) lz5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    else if (format == 3) rle5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    else if (format == 2) rle8_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    else if (format == 0) {
        // 🔥 修复点：Format 0 绝对不能跳过前 4 个字节，它没有解压长度头！
        memcpy(pixels, src_buf, (src_len < alloc_len) ? src_len : alloc_len);
    }

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    int dst_len = width * height;
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        if (colorDepth == 32) {
            int r = pixels[i * 4] & 0xFF; int g = pixels[i * 4 + 1] & 0xFF; int b = pixels[i * 4 + 2] & 0xFF; int a = pixels[i * 4 + 3] & 0xFF;
            out_pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        } else if (colorDepth == 24) {
            int r = pixels[i * 3] & 0xFF; int g = pixels[i * 3 + 1] & 0xFF; int b = pixels[i * 3 + 2] & 0xFF;
            out_pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        } else {
            int idx = pixels[i] & 0xFF;
            int p_idx = idx * 4; 
            int r = pal_buf[p_idx] & 0xFF; int g = pal_buf[p_idx + 1] & 0xFF; int b = pal_buf[p_idx + 2] & 0xFF; int a = pal_buf[p_idx + 3] & 0xFF;
            if (idx == 0) a = 0; else if (a == 0) a = 255; 
            out_pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    env->ReleasePrimitiveArrayCritical(result, out_pixels, 0);
    env->ReleaseByteArrayElements(palette, pal_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    free(pixels);

    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV1C(JNIEnv* env, jobject thiz, jbyteArray data, jint width, jint height, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    int dst_len = width * height;
    
    if (dst_len <= 0) {
        env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
        return env->NewIntArray(0);
    }

    uint8_t* pixels = (uint8_t*)malloc(dst_len);
    memset(pixels, 0, dst_len);
    pcx_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, dst_len, width, height);

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        int idx = pixels[i] & 0xFF;
        if (idx == 0) {
            out_pixels[i] = 0; 
        } else {
            int p_idx = idx * 3;
            int r = pal_buf[p_idx] & 0xFF; int g = pal_buf[p_idx + 1] & 0xFF; int b = pal_buf[p_idx + 2] & 0xFF;
            out_pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
    }

    env->ReleasePrimitiveArrayCritical(result, out_pixels, 0);
    env->ReleaseByteArrayElements(palette, pal_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    free(pixels);

    return result;
}