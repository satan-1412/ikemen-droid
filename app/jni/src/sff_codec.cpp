#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "IkemenEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==============================================================================
// 🚨 算法 1：LZ5 解压引擎 (Elecbyte 专属魔改 LZ77 架构)
// ==============================================================================
inline void lz5_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; // 严格跳过 4 字节的解压长度头
    int dst_ptr = 0;
    while (dst_ptr < dst_len && src_ptr < src_len) {
        uint8_t ctrl = src[src_ptr++];
        for (int i = 0; i < 8 && dst_ptr < dst_len && src_ptr < src_len; ++i) {
            if ((ctrl & (1 << i)) == 0) { 
                dst[dst_ptr++] = src[src_ptr++];
            } else { 
                if (src_ptr + 1 >= src_len) break;
                uint8_t d0 = src[src_ptr++];
                uint8_t d1 = src[src_ptr++];
                
                int offset = ((d1 & 0x3F) << 8) | d0; 
                int count = (d1 >> 6) + 3;
                
                if (count == 6) { 
                    int c;
                    do {
                        if (src_ptr >= src_len) break;
                        c = src[src_ptr++];
                        count += c;
                    } while (c == 255);
                }
                
                int copy_idx = dst_ptr - offset - 1; 
                for (int j = 0; j < count && dst_ptr < dst_len; ++j) {
                    dst[dst_ptr++] = (copy_idx >= 0 && copy_idx < dst_len) ? dst[copy_idx] : 0;
                    copy_idx++; 
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
        uint8_t color = ctrl & 0x1F; 
        int count = ctrl >> 5;   
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
// 🚨 算法 3：RLE8 解压引擎 (Format 2) —— 终极修复偏色与位移错乱！
// ==============================================================================
inline void rle8_decompress_hardcore(const uint8_t* __restrict src, int src_len, uint8_t* __restrict dst, int dst_len) {
    int src_ptr = 4; 
    int dst_ptr = 0;
    while (dst_ptr < dst_len && src_ptr < src_len) {
        uint8_t b = src[src_ptr++];
        
        if ((b & 0xC0) == 0x40) { 
            int count = b & 0x3F;
            if (count == 0) {
                if (src_ptr >= src_len) break;
                count = src[src_ptr++] + 64; 
            }
            if (src_ptr >= src_len) break;
            uint8_t color = src[src_ptr++];
            for (int i = 0; i < count && dst_ptr < dst_len; ++i) dst[dst_ptr++] = color;
            
        } else if ((b & 0xC0) == 0x80) { 
            int count = b & 0x3F;
            if (count == 0) {
                if (src_ptr >= src_len) break;
                count = src[src_ptr++] + 64; 
            }
            for (int i = 0; i < count && dst_ptr < dst_len; ++i) dst[dst_ptr++] = 0; 
            
        } else { 
            dst[dst_ptr++] = b; 
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

// ==============================================================================
// 🎵 算法 5：SND 文件闪电扫描器 (全链表解析，极速定点音频数据)
// ==============================================================================
extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_scanSndC(JNIEnv* env, jobject thiz, jbyteArray data) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);

    // 长度保护和 "ElecbyteSnd" 魔法签名校验
    if (src_len < 24 || memcmp(src_buf, "ElecbyteSnd", 11) != 0) {
        env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
        return env->NewIntArray(0);
    }

    uint32_t num_sounds = ((uint8_t)src_buf[16]) | (((uint8_t)src_buf[17]) << 8) | (((uint8_t)src_buf[18]) << 16) | (((uint8_t)src_buf[19]) << 24);
    uint32_t first_offset = ((uint8_t)src_buf[20]) | (((uint8_t)src_buf[21]) << 8) | (((uint8_t)src_buf[22]) << 16) | (((uint8_t)src_buf[23]) << 24);

    // 第一遍：死循环防护与有效链表节点统计
    int count = 0;
    uint32_t tmp_offset = first_offset;
    while (tmp_offset > 0 && tmp_offset + 16 <= (uint32_t)src_len && count < 99999) {
        uint32_t next_offset = ((uint8_t)src_buf[tmp_offset]) | (((uint8_t)src_buf[tmp_offset+1]) << 8) | (((uint8_t)src_buf[tmp_offset+2]) << 16) | (((uint8_t)src_buf[tmp_offset+3]) << 24);
        count++;
        if (next_offset == 0 || next_offset == tmp_offset) break;
        tmp_offset = next_offset;
    }

    // [Group, Item, 数据起始Offset, 音频Length] -> 每个文件 4 个 int
    jintArray result = env->NewIntArray(count * 4);
    jint* out_data = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    // 第二遍：精准摘取
    uint32_t current_offset = first_offset;
    for (int i = 0; i < count; ++i) {
        if (current_offset <= 0 || current_offset + 16 > (uint32_t)src_len) break;

        uint32_t next_offset = ((uint8_t)src_buf[current_offset]) | (((uint8_t)src_buf[current_offset+1]) << 8) | (((uint8_t)src_buf[current_offset+2]) << 16) | (((uint8_t)src_buf[current_offset+3]) << 24);
        uint32_t length      = ((uint8_t)src_buf[current_offset+4]) | (((uint8_t)src_buf[current_offset+5]) << 8) | (((uint8_t)src_buf[current_offset+6]) << 16) | (((uint8_t)src_buf[current_offset+7]) << 24);
        
        // MUGEN 的 Group 和 Item 有时为负数，必须强制转换为 signed 32-bit int
        int group = (int)(((uint8_t)src_buf[current_offset+8]) | (((uint8_t)src_buf[current_offset+9]) << 8) | (((uint8_t)src_buf[current_offset+10]) << 16) | (((uint8_t)src_buf[current_offset+11]) << 24));
        int item  = (int)(((uint8_t)src_buf[current_offset+12]) | (((uint8_t)src_buf[current_offset+13]) << 8) | (((uint8_t)src_buf[current_offset+14]) << 16) | (((uint8_t)src_buf[current_offset+15]) << 24));

        out_data[i * 4 + 0] = group;
        out_data[i * 4 + 1] = item;
        out_data[i * 4 + 2] = current_offset + 16; // 跨过 16 字节的 Sub-header，直达 WAV 裸数据
        out_data[i * 4 + 3] = length;              

        if (next_offset == 0 || next_offset == current_offset) break;
        current_offset = next_offset;
    }

    env->ReleasePrimitiveArrayCritical(result, out_data, 0);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_org_libsdl_app_DesktopSystemView_decodeSffV2C(JNIEnv* env, jobject thiz, jbyteArray data, jint format, jint width, jint height, jint colorDepth, jbyteArray palette) {
    jsize src_len = env->GetArrayLength(data);
    jbyte* src_buf = env->GetByteArrayElements(data, NULL);
    
    int bytes_per_pixel = (colorDepth == 32) ? 4 : ((colorDepth == 24) ? 3 : 1);
    int raw_len = width * height * bytes_per_pixel;
    
    uint32_t expected_len = raw_len;
    if (src_len >= 4 && (format == 0 || format == 2 || format == 3 || format == 4)) {
        expected_len = (src_buf[0] & 0xFF) | ((src_buf[1] & 0xFF) << 8) | ((src_buf[2] & 0xFF) << 16) | ((src_buf[3] & 0xFF) << 24);
    }
    
    uint32_t alloc_len = (expected_len > (uint32_t)raw_len) ? expected_len : raw_len;
    uint8_t* pixels = (uint8_t*)malloc(alloc_len + 1024); 
    memset(pixels, 0, alloc_len + 1024);

    if (format == 4) lz5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    else if (format == 3) rle5_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    else if (format == 2) rle8_decompress_hardcore((uint8_t*)src_buf, src_len, pixels, alloc_len);
    else if (format == 0) {
        int copy_len = src_len - 4;
        if (copy_len > alloc_len) copy_len = alloc_len;
        if (copy_len > 0) memcpy(pixels, src_buf + 4, copy_len);
    }

    jbyte* pal_buf = env->GetByteArrayElements(palette, NULL);
    int pal_len = env->GetArrayLength(palette); 
    int dst_len = width * height;
    
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        if (colorDepth == 32) {
            int p_idx = i * 4;
            if (p_idx + 3 < alloc_len) {
                int r = pixels[p_idx] & 0xFF; int g = pixels[p_idx + 1] & 0xFF; int b = pixels[p_idx + 2] & 0xFF; int a = pixels[p_idx + 3] & 0xFF;
                out_pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        } else if (colorDepth == 24) {
            int p_idx = i * 3;
            if (p_idx + 2 < alloc_len) {
                int r = pixels[p_idx] & 0xFF; int g = pixels[p_idx + 1] & 0xFF; int b = pixels[p_idx + 2] & 0xFF;
                out_pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        } else {
            int idx = pixels[i] & 0xFF;
            int p_idx = idx * 4; 
            
            if (p_idx + 3 < pal_len) {
                int r = pal_buf[p_idx] & 0xFF; int g = pal_buf[p_idx + 1] & 0xFF; int b = pal_buf[p_idx + 2] & 0xFF; int a = pal_buf[p_idx + 3] & 0xFF;
                if (idx == 0) a = 0; else if (a == 0) a = 255; 
                out_pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
            } else {
                out_pixels[i] = (idx == 0) ? 0 : 0xFF00FF00; 
            }
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
    int pal_len = env->GetArrayLength(palette);
    jintArray result = env->NewIntArray(dst_len);
    jint* out_pixels = (jint*)env->GetPrimitiveArrayCritical(result, 0);

    for (int i = 0; i < dst_len; ++i) {
        int idx = pixels[i] & 0xFF;
        if (idx == 0) {
            out_pixels[i] = 0; 
        } else {
            int p_idx = idx * 3;
            if (p_idx + 2 < pal_len) {
                int r = pal_buf[p_idx] & 0xFF; int g = pal_buf[p_idx + 1] & 0xFF; int b = pal_buf[p_idx + 2] & 0xFF;
                out_pixels[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            } else {
                out_pixels[i] = 0xFF00FF00; 
            }
        }
    }

    env->ReleasePrimitiveArrayCritical(result, out_pixels, 0);
    env->ReleaseByteArrayElements(palette, pal_buf, JNI_ABORT);
    env->ReleaseByteArrayElements(data, src_buf, JNI_ABORT);
    free(pixels);

    return result;
}
