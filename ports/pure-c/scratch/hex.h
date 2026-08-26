#ifndef VCEDU_HEX_H
#define VCEDU_HEX_H

#include <stddef.h>
#include <stdint.h>

size_t hex_encode (const uint8_t *in, size_t in_len, char *out, size_t out_cap);
int hex_decode (const char *in, uint8_t *out, size_t out_cap, size_t *out_len);

#endif
