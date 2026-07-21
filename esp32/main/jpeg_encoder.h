#pragma once

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct jpeg_encoder_ctx jpeg_encoder_t;

jpeg_encoder_t *jpeg_encoder_create(int width, int height, int quality);

size_t jpeg_encoder_encode_rgb888(jpeg_encoder_t *enc, const uint8_t *rgb,
                                   uint8_t *out, size_t out_max);

size_t jpeg_encoder_encode_rgb565(jpeg_encoder_t *enc, const uint16_t *rgb565,
                                   uint8_t *out, size_t out_max);

void jpeg_encoder_destroy(jpeg_encoder_t *enc);

uint8_t *jpeg_encoder_alloc_buf(size_t size, int aligned);
void jpeg_encoder_free_buf(uint8_t *buf);

size_t jpeg_encode_rgb888(const uint8_t *rgb, int width, int height,
                          uint8_t *out, size_t out_max, int quality);

#ifdef __cplusplus
}
#endif
