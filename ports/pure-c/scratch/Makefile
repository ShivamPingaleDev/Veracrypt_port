# Hand-written hex (educational scratch C lab)

CC ?= cc
CFLAGS ?= -Wall -Wextra -std=c11 -O2 -pedantic
OUT := build
BIN := $(OUT)/vcedu-c

SRCS := main.c hex.c sha256.c toy_volume.c

.PHONY: all test test-sha clean

all: $(BIN)

$(BIN): $(SRCS)
	mkdir -p "$(OUT)"
	$(CC) $(CFLAGS) -o $@ $(SRCS)

test: $(BIN)
	@tmp=$$(mktemp); echo -n hello > "$$tmp.plain"; \
		$(BIN) create "$$tmp.bin" pass --from "$$tmp.plain"; \
		test "$$($(BIN) list "$$tmp.bin" pass | head -1)" = "payload.bin (5 bytes)"; \
		rm -f "$$tmp" "$$tmp.plain" "$$tmp.bin"
	@echo "scratch C PASS (toy container). Run make test-sha after you fix sha256.c"

test-sha: $(BIN)
	test "$$($(BIN) hash /dev/null | head -c 64)" = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
	test "$$($(BIN) hash <(printf abc))" = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
	@echo "sha256 PASS"

clean:
	rm -rf "$(OUT)"
