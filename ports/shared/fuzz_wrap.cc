/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 libFuzzer / AFL++ harness for wrap unwrap. Feeds malformed bytes into
 vc_is_wrap + vc_unwrap_file. Valid Argon2 parameters with a matching
 file size are rare; garbage fails on magic or length before the 32 MiB KDF.

   clang++ -fsanitize=fuzzer,address ... fuzz_wrap.cc -o fuzz_wrap
   ./fuzz_wrap -max_len=4096 -max_total_time=30
*/

#include "vc_mobile.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <vector>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size)
{
	if (!data)
		return 0;

	char dir[] = "/tmp/vcport-fuzz-XXXXXX";
	if (!mkdtemp(dir))
		return 0;
	char wrap[256];
	char outdir[256];
	char outpath[256];
	snprintf(wrap, sizeof(wrap), "%s/in.vcpw", dir);
	snprintf(outdir, sizeof(outdir), "%s/out", dir);

	FILE *f = fopen(wrap, "wb");
	if (!f)
	{
		rmdir(dir);
		return 0;
	}
	if (size)
		fwrite(data, 1, size, f);
	fclose(f);

	(void) vc_is_wrap(wrap);
	(void) vc_is_wrap(nullptr);
	(void) vc_unwrap_file(wrap, outdir, "fuzz-password", 13, outpath, sizeof(outpath));
	(void) vc_unwrap_file(wrap, outdir, "", 0, outpath, sizeof(outpath));
	(void) vc_unwrap_file(nullptr, outdir, "x", 1, outpath, sizeof(outpath));

	if (size > 0 && size <= 64)
	{
		char pw[80];
		int n = vc_generate_password(pw, sizeof(pw), 16 + (int) (size % 49));
		if (n > 0)
			vc_secure_wipe(pw, sizeof(pw));
	}

	unlink(wrap);
	rmdir(outdir);
	rmdir(dir);
	return 0;
}

#if !defined(LIBFUZZER)
int main(int argc, char **argv)
{
	if (argc < 2)
		return 0;
	FILE *f = fopen(argv[1], "rb");
	if (!f)
		return 0;
	if (fseek(f, 0, SEEK_END) != 0)
	{
		fclose(f);
		return 0;
	}
	long n = ftell(f);
	if (n < 0)
	{
		fclose(f);
		return 0;
	}
	rewind(f);
	std::vector<uint8_t> buf((size_t) n);
	if (n && fread(buf.data(), 1, (size_t) n, f) != (size_t) n)
	{
		fclose(f);
		return 0;
	}
	fclose(f);
	return LLVMFuzzerTestOneInput(buf.data(), buf.size());
}
#endif
