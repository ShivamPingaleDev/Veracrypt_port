#ifndef VCEDU_TOY_VOLUME_H
#define VCEDU_TOY_VOLUME_H

#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

int toy_write (const char *path, const char *password, const uint8_t *plain, size_t plain_len);
int toy_open (const char *path, const char *password, uint8_t **plain, size_t *plain_len);

#endif
