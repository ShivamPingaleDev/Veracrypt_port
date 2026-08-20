# Personal C lab — same libvc_mobile as the 0.3.8 phones.
SHARED := $(abspath ../shared)
ROOT := $(abspath ../..)
SRC ?= $(ROOT)/src
BUILD ?= $(TMPDIR)/vcport-vol-build
OUT := build
UNAME := $(shell uname)
ifeq ($(UNAME),Darwin)
	CXXLIB := -lc++
else
	CXXLIB := -lstdc++
endif

.PHONY: all lib test clean

all: $(OUT)/vcport-c

lib:
	cmake -S "$(SHARED)" -B "$(BUILD)" -DVC_SRC="$(SRC)" -DCMAKE_BUILD_TYPE=Release
	cmake --build "$(BUILD)" --target vc_mobile

$(OUT)/vcport-c: lib main.c preview.c otg.c preview.h otg.h
	mkdir -p "$(OUT)"
	$(CC) -O2 -Wall -Wextra -I"$(SHARED)" -o $@ main.c preview.c otg.c \
		"$(BUILD)/libvc_mobile.a" $(CXXLIB) -lpthread

test: $(OUT)/vcport-c
	test "$$($(OUT)/vcport-c preview-kind NOTE.TXT)" = text
	test "$$($(OUT)/vcport-c preview-kind photo.PNG)" = image
	test "$$($(OUT)/vcport-c preview-kind clip.mp4)" = video
	test "$$($(OUT)/vcport-c preview-kind report.docx)" = unsupported
	test "$$($(OUT)/vcport-c otg-path /tmp/volume.hc)" = not-otg
	test "$$($(OUT)/vcport-c otg-path /vcport-otg-dev/0)" = not-bound
	test "$$($(OUT)/vcport-c otg-path /proc/self/fd/7)" = not-otg
	@echo "pure-c lab PASS"

clean:
	rm -rf "$(OUT)"
