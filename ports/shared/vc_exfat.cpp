/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.

 In-app exFAT for VeraCrypt file containers. Same on-disk layout as desktop
 mkfs.exfat so a computer can open the volume. Files may be larger than 4 GiB.
*/

#include "vc_exfat.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <string>
#include <unistd.h>
#include <vector>

namespace
{

uint16_t u16 (const uint8_t *p)
{
	return (uint16_t) (p[0] | ((uint16_t) p[1] << 8));
}

uint32_t u32 (const uint8_t *p)
{
	return p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

uint64_t u64 (const uint8_t *p)
{
	return (uint64_t) u32 (p) | ((uint64_t) u32 (p + 4) << 32);
}

void put16 (uint8_t *p, uint16_t v)
{
	p[0] = (uint8_t) v;
	p[1] = (uint8_t) (v >> 8);
}

void put32 (uint8_t *p, uint32_t v)
{
	p[0] = (uint8_t) v;
	p[1] = (uint8_t) (v >> 8);
	p[2] = (uint8_t) (v >> 16);
	p[3] = (uint8_t) (v >> 24);
}

void put64 (uint8_t *p, uint64_t v)
{
	put32 (p, (uint32_t) v);
	put32 (p + 4, (uint32_t) (v >> 32));
}

uint16_t upcase16 (uint16_t c)
{
	if (c >= 'a' && c <= 'z')
		return (uint16_t) (c - 32);
	return c;
}

struct Exfat
{
	uint32_t bps;
	uint32_t spc;
	uint32_t cluster_size;
	uint32_t fat_sec;
	uint32_t fat_len;
	uint32_t heap_sec;
	uint32_t cluster_count;
	uint32_t root_cluster;
	uint32_t bitmap_cluster;
	uint64_t bitmap_bytes;
	uint64_t fat_off;
	uint64_t heap_off;
};

uint64_t cluster_off (const Exfat *g, uint32_t cluster)
{
	return g->heap_off + (uint64_t) (cluster - 2) * g->cluster_size;
}

int read_all (VcVolume *v, uint64_t off, void *buf, size_t n)
{
	return vc_read (v, off, buf, n);
}

int write_all (VcVolume *v, uint64_t off, const void *buf, size_t n)
{
	return vc_write (v, off, buf, n);
}

uint32_t fat_get (VcVolume *v, const Exfat *g, uint32_t cluster)
{
	uint8_t b[4];
	if (read_all (v, g->fat_off + (uint64_t) cluster * 4, b, 4) != VC_OK)
		return 0xFFFFFFFFu;
	return u32 (b);
}

int fat_set (VcVolume *v, const Exfat *g, uint32_t cluster, uint32_t value)
{
	uint8_t b[4];
	put32 (b, value);
	return write_all (v, g->fat_off + (uint64_t) cluster * 4, b, 4);
}

int bitmap_get (VcVolume *v, const Exfat *g, uint32_t cluster, int *used)
{
	if (cluster < 2 || cluster >= g->cluster_count + 2)
		return VC_ERR_FORMAT;
	uint32_t bit = cluster - 2;
	uint8_t b = 0;
	uint64_t off = cluster_off (g, g->bitmap_cluster) + bit / 8;
	if (read_all (v, off, &b, 1) != VC_OK)
		return VC_ERR_IO;
	*used = (b >> (bit % 8)) & 1;
	return VC_OK;
}

int bitmap_set (VcVolume *v, const Exfat *g, uint32_t cluster, int used)
{
	if (cluster < 2 || cluster >= g->cluster_count + 2)
		return VC_ERR_FORMAT;
	uint32_t bit = cluster - 2;
	uint8_t b = 0;
	uint64_t off = cluster_off (g, g->bitmap_cluster) + bit / 8;
	if (read_all (v, off, &b, 1) != VC_OK)
		return VC_ERR_IO;
	if (used)
		b = (uint8_t) (b | (1u << (bit % 8)));
	else
		b = (uint8_t) (b & ~(1u << (bit % 8)));
	return write_all (v, off, &b, 1);
}

int load_chain (VcVolume *v, const Exfat *g, uint32_t start, uint64_t bytes, int no_fat, std::vector<uint8_t> &out)
{
	out.clear ();
	if (start < 2 || bytes == 0)
		return VC_OK;
	uint64_t remain = bytes;
	uint32_t cluster = start;
	int hops = 0;
	while (remain && hops++ < (1 << 24))
	{
		uint64_t n = remain < g->cluster_size ? remain : g->cluster_size;
		size_t at = out.size ();
		out.resize (at + (size_t) n);
		if (read_all (v, cluster_off (g, cluster), &out[at], (size_t) n) != VC_OK)
			return VC_ERR_IO;
		remain -= n;
		if (remain == 0)
			break;
		if (no_fat)
			cluster++;
		else
		{
			uint32_t next = fat_get (v, g, cluster);
			if (next < 2 || next == 0xFFFFFFFFu)
				break;
			cluster = next;
		}
	}
	return remain ? VC_ERR_IO : VC_OK;
}

int write_chain (VcVolume *v, const Exfat *g, uint32_t start, const uint8_t *data, uint64_t bytes, int no_fat)
{
	uint64_t remain = bytes;
	uint32_t cluster = start;
	const uint8_t *p = data;
	int hops = 0;
	while (remain && hops++ < (1 << 24))
	{
		uint64_t n = remain < g->cluster_size ? remain : g->cluster_size;
		if (write_all (v, cluster_off (g, cluster), p, (size_t) n) != VC_OK)
			return VC_ERR_IO;
		remain -= n;
		p += n;
		if (remain == 0)
			break;
		if (no_fat)
			cluster++;
		else
		{
			uint32_t next = fat_get (v, g, cluster);
			if (next < 2 || next == 0xFFFFFFFFu)
				return VC_ERR_IO;
			cluster = next;
		}
	}
	return remain ? VC_ERR_IO : VC_OK;
}

int find_run (VcVolume *v, const Exfat *g, uint32_t need, uint32_t *first)
{
	uint32_t run = 0;
	uint32_t start = 0;
	for (uint32_t c = 2; c < g->cluster_count + 2; ++c)
	{
		int used = 1;
		int rc = bitmap_get (v, g, c, &used);
		if (rc != VC_OK)
			return rc;
		if (!used)
		{
			if (run == 0)
				start = c;
			run++;
			if (run >= need)
			{
				*first = start;
				return VC_OK;
			}
		}
		else
			run = 0;
	}
	return VC_ERR_MEMORY;
}

int mark_run (VcVolume *v, const Exfat *g, uint32_t first, uint32_t count, int used)
{
	for (uint32_t i = 0; i < count; ++i)
	{
		int rc = bitmap_set (v, g, first + i, used);
		if (rc != VC_OK)
			return rc;
		if (!used)
		{
			int fr = fat_set (v, g, first + i, 0);
			if (fr != VC_OK)
				return fr;
		}
	}
	if (used && count > 0)
	{
		for (uint32_t i = 0; i + 1 < count; ++i)
		{
			int rc = fat_set (v, g, first + i, first + i + 1);
			if (rc != VC_OK)
				return rc;
		}
		int rc = fat_set (v, g, first + count - 1, 0xFFFFFFFFu);
		if (rc != VC_OK)
			return rc;
	}
	return VC_OK;
}

int load_root_dir (VcVolume *v, const Exfat *g, std::vector<uint8_t> &dir)
{
	/* Root is FAT-chained. Read until a zero type in the last cluster, cap 8 MiB. */
	dir.clear ();
	uint32_t cluster = g->root_cluster;
	int hops = 0;
	while (cluster >= 2 && cluster != 0xFFFFFFFFu && hops++ < 4096 && dir.size () < 8 * 1024 * 1024)
	{
		size_t at = dir.size ();
		dir.resize (at + g->cluster_size);
		if (read_all (v, cluster_off (g, cluster), &dir[at], g->cluster_size) != VC_OK)
			return VC_ERR_IO;
		uint32_t next = fat_get (v, g, cluster);
		if (next < 2)
			break;
		cluster = next;
	}
	return dir.empty () ? VC_ERR_FORMAT : VC_OK;
}

int save_root_prefix (VcVolume *v, const Exfat *g, const std::vector<uint8_t> &dir)
{
	uint32_t cluster = g->root_cluster;
	size_t off = 0;
	while (off < dir.size () && cluster >= 2 && cluster != 0xFFFFFFFFu)
	{
		size_t n = g->cluster_size;
		if (off + n > dir.size ())
			n = dir.size () - off;
		std::vector<uint8_t> chunk (g->cluster_size, 0);
		memcpy (chunk.data (), &dir[off], n);
		if (write_all (v, cluster_off (g, cluster), chunk.data (), g->cluster_size) != VC_OK)
			return VC_ERR_IO;
		off += g->cluster_size;
		if (off >= dir.size ())
			break;
		uint32_t next = fat_get (v, g, cluster);
		if (next < 2)
		{
			uint32_t extra = 0;
			int rc = find_run (v, g, 1, &extra);
			if (rc != VC_OK)
				return rc;
			rc = mark_run (v, g, extra, 1, 1);
			if (rc != VC_OK)
				return rc;
			rc = fat_set (v, g, cluster, extra);
			if (rc != VC_OK)
				return rc;
			cluster = extra;
		}
		else
			cluster = next;
	}
	return VC_OK;
}

int parse_boot (VcVolume *v, Exfat *g)
{
	std::vector<uint8_t> boot (512);
	if (read_all (v, 0, boot.data (), 512) != VC_OK)
		return VC_ERR_IO;
	if (memcmp (&boot[3], "EXFAT   ", 8) != 0)
		return 0;
	uint8_t bps_shift = boot[108];
	uint8_t spc_shift = boot[109];
	if (bps_shift != 9 || spc_shift > 12)
		return VC_ERR_FORMAT;
	g->bps = 512;
	g->spc = 1u << spc_shift;
	g->cluster_size = g->bps * g->spc;
	g->fat_sec = u32 (&boot[80]);
	g->fat_len = u32 (&boot[84]);
	g->heap_sec = u32 (&boot[88]);
	g->cluster_count = u32 (&boot[92]);
	g->root_cluster = u32 (&boot[96]);
	if (g->cluster_count < 1 || g->root_cluster < 2 || boot[104] != 0x00 || boot[105] != 0x01)
		return VC_ERR_FORMAT;
	g->fat_off = (uint64_t) g->fat_sec * g->bps;
	g->heap_off = (uint64_t) g->heap_sec * g->bps;
	g->bitmap_cluster = 0;
	g->bitmap_bytes = 0;

	std::vector<uint8_t> dir;
	int rc = load_root_dir (v, g, dir);
	if (rc != VC_OK)
		return rc;
	for (size_t i = 0; i + 32 <= dir.size (); i += 32)
	{
		if (dir[i] == 0)
			break;
		if (dir[i] == 0x81)
		{
			g->bitmap_cluster = u32 (&dir[i + 20]);
			g->bitmap_bytes = u64 (&dir[i + 24]);
			break;
		}
	}
	if (g->bitmap_cluster < 2)
		return VC_ERR_FORMAT;
	return 1;
}

int load_geom (VcVolume *v, Exfat *g)
{
	int rc = parse_boot (v, g);
	if (rc == 1)
		return VC_OK;
	if (rc == 0)
		return VC_ERR_FORMAT;
	return rc;
}

size_t utf8_to_utf16 (const char *src, uint16_t *out, size_t max)
{
	size_t n = 0;
	const uint8_t *p = (const uint8_t *) src;
	while (*p && n + 1 < max)
	{
		uint32_t c = *p++;
		if (c < 0x80)
			out[n++] = (uint16_t) c;
		else if ((c & 0xE0) == 0xC0 && *p)
		{
			c = ((c & 0x1F) << 6) | (*p++ & 0x3F);
			out[n++] = (uint16_t) c;
		}
		else if ((c & 0xF0) == 0xE0 && p[0] && p[1])
		{
			c = ((c & 0x0F) << 12) | ((p[0] & 0x3F) << 6) | (p[1] & 0x3F);
			p += 2;
			out[n++] = (uint16_t) c;
		}
		else
			out[n++] = '?';
	}
	out[n] = 0;
	return n;
}

size_t utf16_to_utf8 (const uint16_t *src, int count, char *out, size_t out_size)
{
	size_t o = 0;
	if (!out_size)
		return 0;
	for (int i = 0; i < count && src[i]; ++i)
	{
		uint32_t c = src[i];
		if (c < 0x80)
		{
			if (o + 1 >= out_size)
				break;
			out[o++] = (char) c;
		}
		else if (c < 0x800)
		{
			if (o + 2 >= out_size)
				break;
			out[o++] = (char) (0xC0 | (c >> 6));
			out[o++] = (char) (0x80 | (c & 0x3F));
		}
		else
		{
			if (o + 3 >= out_size)
				break;
			out[o++] = (char) (0xE0 | (c >> 12));
			out[o++] = (char) (0x80 | ((c >> 6) & 0x3F));
			out[o++] = (char) (0x80 | (c & 0x3F));
		}
	}
	out[o] = 0;
	return o;
}

uint16_t name_hash (const uint16_t *name, size_t len)
{
	uint16_t hash = 0;
	for (size_t i = 0; i < len; ++i)
	{
		uint16_t c = upcase16 (name[i]);
		hash = (uint16_t) (((hash << 15) | (hash >> 1)) + (c & 0xFF));
		hash = (uint16_t) (((hash << 15) | (hash >> 1)) + (c >> 8));
	}
	return hash;
}

int names_equal (const uint16_t *a, size_t na, const char *b)
{
	uint16_t wb[256];
	size_t nb = utf8_to_utf16 (b, wb, 256);
	if (na != nb)
		return 0;
	for (size_t i = 0; i < na; ++i)
	{
		if (upcase16 (a[i]) != upcase16 (wb[i]))
			return 0;
	}
	return 1;
}

uint32_t dos_stamp ()
{
	time_t now = time (nullptr);
	struct tm tmv;
	struct tm *pt = gmtime (&now);
	if (pt)
		tmv = *pt;
	else
		memset (&tmv, 0, sizeof (tmv));
	int year = tmv.tm_year + 1900;
	if (year < 1980)
		year = 1980;
	return ((uint32_t) (year - 1980) << 25)
		| ((uint32_t) (tmv.tm_mon + 1) << 21)
		| ((uint32_t) tmv.tm_mday << 16)
		| ((uint32_t) tmv.tm_hour << 11)
		| ((uint32_t) tmv.tm_min << 5)
		| ((uint32_t) (tmv.tm_sec / 2));
}

uint16_t set_checksum (const uint8_t *set, size_t bytes)
{
	uint16_t sum = 0;
	for (size_t i = 0; i < bytes; ++i)
	{
		if (i == 2 || i == 3)
			continue;
		sum = (uint16_t) (((sum << 15) | (sum >> 1)) + set[i]);
	}
	return sum;
}

int parse_dir (const uint8_t *dir, size_t bytes, VcDirEntry *entries, int max_entries, int skip)
{
	int count = 0;
	int seen = 0;
	if (skip < 0)
		skip = 0;
	for (size_t i = 0; i + 32 <= bytes && count < max_entries; )
	{
		uint8_t type = dir[i];
		if (type == 0)
			break;
		if ((type & 0x80) == 0)
		{
			i += 32;
			continue;
		}
		if (type != 0x85)
		{
			i += 32;
			continue;
		}
		uint8_t secondary = dir[i + 1];
		size_t set = 32u * (1u + secondary);
		if (i + set > bytes)
			break;
		if (secondary < 2 || dir[i + 32] != 0xC0)
		{
			i += 32;
			continue;
		}
		uint16_t name16[256];
		memset (name16, 0, sizeof (name16));
		size_t nlen = dir[i + 32 + 3];
		size_t got = 0;
		for (uint8_t s = 1; s < secondary && got < nlen; ++s)
		{
			const uint8_t *e = dir + i + 32u * (1u + s);
			if (e[0] != 0xC1)
				break;
			for (int k = 0; k < 15 && got < nlen; ++k)
				name16[got++] = u16 (e + 2 + k * 2);
		}
		if (seen++ < skip)
		{
			i += set;
			continue;
		}
		VcDirEntry *e = &entries[count];
		memset (e, 0, sizeof (*e));
		utf16_to_utf8 (name16, (int) got, e->name, sizeof (e->name));
		uint16_t attr = u16 (dir + i + 4);
		e->is_dir = (attr & 0x10) ? 1 : 0;
		e->size = u64 (dir + i + 32 + 24);
		e->first_cluster = u32 (dir + i + 32 + 20);
		uint32_t ts = u32 (dir + i + 12);
		e->dos_date = (uint16_t) (ts >> 16);
		e->dos_time = (uint16_t) (ts & 0xFFFF);
		count++;
		i += set;
	}
	return count;
}

struct Found
{
	VcDirEntry entry;
	size_t offset;
	size_t set_bytes;
	int no_fat;
};

int find_in_dir (const uint8_t *dir, size_t bytes, const char *name, Found *out)
{
	for (size_t i = 0; i + 32 <= bytes; )
	{
		uint8_t type = dir[i];
		if (type == 0)
			break;
		if (type != 0x85)
		{
			i += 32;
			continue;
		}
		uint8_t secondary = dir[i + 1];
		size_t set = 32u * (1u + secondary);
		if (i + set > bytes)
			break;
		if (secondary < 2 || dir[i + 32] != 0xC0)
		{
			i += 32;
			continue;
		}
		uint16_t name16[256];
		memset (name16, 0, sizeof (name16));
		size_t nlen = dir[i + 32 + 3];
		size_t got = 0;
		for (uint8_t s = 1; s < secondary && got < nlen; ++s)
		{
			const uint8_t *e = dir + i + 32u * (1u + s);
			if (e[0] != 0xC1)
				break;
			for (int k = 0; k < 15 && got < nlen; ++k)
				name16[got++] = u16 (e + 2 + k * 2);
		}
		if (names_equal (name16, got, name))
		{
			memset (out, 0, sizeof (*out));
			utf16_to_utf8 (name16, (int) got, out->entry.name, sizeof (out->entry.name));
			uint16_t attr = u16 (dir + i + 4);
			out->entry.is_dir = (attr & 0x10) ? 1 : 0;
			out->entry.size = u64 (dir + i + 32 + 24);
			out->entry.first_cluster = u32 (dir + i + 32 + 20);
			out->offset = i;
			out->set_bytes = set;
			out->no_fat = (dir[i + 32 + 1] & 0x02) ? 1 : 0;
			return VC_OK;
		}
		i += set;
	}
	return VC_ERR_FORMAT;
}

int is_root (const char *path)
{
	return !path || !path[0] || (path[0] == '/' && path[1] == 0);
}

const char *basename_of (const char *path)
{
	const char *s = path ? path : "";
	const char *slash = strrchr (s, '/');
	return slash ? slash + 1 : s;
}

int parent_of (const char *path, char *out, size_t out_sz)
{
	if (!out || out_sz < 2)
		return VC_ERR_ARGUMENT;
	const char *s = path ? path : "";
	const char *slash = strrchr (s, '/');
	if (!slash || slash == s)
	{
		out[0] = '/';
		out[1] = 0;
		return VC_OK;
	}
	size_t n = (size_t) (slash - s);
	if (n >= out_sz)
		return VC_ERR_ARGUMENT;
	memcpy (out, s, n);
	out[n] = 0;
	return VC_OK;
}

int name_bad (const char *name)
{
	if (!name || !name[0] || strcmp (name, ".") == 0 || strcmp (name, "..") == 0)
		return 1;
	if (strchr (name, '/') || strchr (name, '\\'))
		return 1;
	return 0;
}

int find_path (VcVolume *v, const Exfat *g, const char *path, Found *out)
{
	if (is_root (path))
		return VC_ERR_ARGUMENT;
	if (strstr (path, ".."))
		return VC_ERR_ARGUMENT;
	std::vector<uint8_t> dir;
	int rc = load_root_dir (v, g, dir);
	if (rc != VC_OK)
		return rc;
	std::string rest = path[0] == '/' ? path + 1 : path;
	while (!rest.empty ())
	{
		size_t slash = rest.find ('/');
		std::string part = slash == std::string::npos ? rest : rest.substr (0, slash);
		if (part.empty ())
			return VC_ERR_ARGUMENT;
		Found hit;
		rc = find_in_dir (dir.data (), dir.size (), part.c_str (), &hit);
		if (rc != VC_OK)
			return rc;
		if (slash == std::string::npos)
		{
			*out = hit;
			return VC_OK;
		}
		if (!hit.entry.is_dir)
			return VC_ERR_UNSUPPORTED;
		if (hit.entry.first_cluster < 2)
		{
			dir.clear ();
			return VC_ERR_FORMAT;
		}
		rc = load_chain (v, g, hit.entry.first_cluster, hit.entry.size ? hit.entry.size : g->cluster_size,
			hit.no_fat, dir);
		if (rc != VC_OK)
			return rc;
		rest = rest.substr (slash + 1);
	}
	return VC_ERR_FORMAT;
}

void build_file_set (uint8_t *set, size_t set_bytes, const uint16_t *name, size_t nlen,
	int is_dir, uint32_t first, uint64_t size, int no_fat)
{
	memset (set, 0, set_bytes);
	uint8_t secondary = (uint8_t) (set_bytes / 32 - 1);
	set[0] = 0x85;
	set[1] = secondary;
	put16 (set + 4, is_dir ? 0x10 : 0x20);
	uint32_t ts = dos_stamp ();
	put32 (set + 8, ts);
	put32 (set + 12, ts);
	put32 (set + 16, ts);
	set[32] = 0xC0;
	set[33] = (uint8_t) (0x01 | (no_fat ? 0x02 : 0));
	set[35] = (uint8_t) nlen;
	put16 (set + 36, name_hash (name, nlen));
	put64 (set + 40, size);
	put32 (set + 52, first);
	put64 (set + 56, size);
	size_t got = 0;
	for (uint8_t s = 0; s < secondary - 1; ++s)
	{
		uint8_t *e = set + 64 + s * 32;
		e[0] = 0xC1;
		for (int k = 0; k < 15; ++k)
		{
			uint16_t c = got < nlen ? name[got++] : 0;
			put16 (e + 2 + k * 2, c);
		}
	}
	put16 (set + 2, set_checksum (set, set_bytes));
}

int insert_set (VcVolume *v, const Exfat *g, std::vector<uint8_t> &dir, const uint8_t *set, size_t set_bytes,
	const Found *into = nullptr)
{
	size_t slot = dir.size ();
	for (size_t i = 0; i + set_bytes <= dir.size (); i += 32)
	{
		int free = 1;
		for (size_t k = 0; k < set_bytes; k += 32)
		{
			uint8_t t = dir[i + k];
			if (t != 0 && (t & 0x80) != 0)
			{
				free = 0;
				break;
			}
		}
		if (free)
		{
			slot = i;
			break;
		}
		if (dir[i] == 0)
		{
			slot = i;
			break;
		}
	}
	if (slot + set_bytes > dir.size ())
	{
		if (into && into->entry.first_cluster >= 2)
			return VC_ERR_MEMORY;
		dir.resize (slot + set_bytes + g->cluster_size, 0);
	}
	memcpy (&dir[slot], set, set_bytes);
	if (into && into->entry.first_cluster >= 2)
	{
		uint64_t cap = into->entry.size ? into->entry.size : g->cluster_size;
		if (dir.size () > cap)
			return VC_ERR_MEMORY;
		return write_chain (v, g, into->entry.first_cluster, dir.data (), dir.size (), into->no_fat);
	}
	return save_root_prefix (v, g, dir);
}

int copy_file (VcVolume *v, const Exfat *g, const Found *f, void *buffer, size_t buffer_size, size_t *out_size, FILE *dest)
{
	if (f->entry.is_dir)
		return VC_ERR_UNSUPPORTED;
	uint64_t remaining = f->entry.size;
	if (remaining == 0)
		return VC_OK;
	if (f->entry.first_cluster < 2)
		return VC_ERR_FORMAT;
	uint32_t cluster = f->entry.first_cluster;
	size_t written = 0;
	std::vector<uint8_t> chunk (g->cluster_size);
	int hops = 0;
	while (remaining && hops++ < (1 << 24))
	{
		size_t n = remaining < g->cluster_size ? (size_t) remaining : g->cluster_size;
		if (read_all (v, cluster_off (g, cluster), chunk.data (), n) != VC_OK)
			return VC_ERR_IO;
		if (dest)
		{
			if (fwrite (chunk.data (), 1, n, dest) != n)
				return VC_ERR_IO;
		}
		else if (buffer)
		{
			size_t room = buffer_size > written ? buffer_size - written : 0;
			size_t copy = n < room ? n : room;
			if (copy)
				memcpy ((uint8_t *) buffer + written, chunk.data (), copy);
			written += copy;
			if (out_size)
				*out_size = written;
			if (written >= buffer_size)
				return VC_OK;
		}
		remaining -= n;
		if (f->entry.size)
			vc_progress_tick ((int) (((f->entry.size - remaining) * 100ull) / f->entry.size), "Reading file");
		if (remaining == 0)
			break;
		if (f->no_fat)
			cluster++;
		else
		{
			uint32_t next = fat_get (v, g, cluster);
			if (next < 2)
				break;
			cluster = next;
		}
	}
	if (out_size && !dest)
		*out_size = written;
	return remaining ? VC_ERR_IO : VC_OK;
}

uint32_t boot_checksum (const uint8_t *sectors, size_t n)
{
	uint32_t checksum = 0;
	for (size_t i = 0; i < n; ++i)
	{
		if (i == 106 || i == 107 || i == 112)
			continue;
		checksum = ((checksum << 31) | (checksum >> 1)) + sectors[i];
	}
	return checksum;
}

int file_size64 (FILE *f, uint64_t *out)
{
	if (!f || !out)
		return -1;
	if (fseeko (f, 0, SEEK_END) != 0)
		return -1;
	off_t sz = ftello (f);
	if (sz < 0)
		return -1;
	*out = (uint64_t) sz;
	if (fseeko (f, 0, SEEK_SET) != 0)
		return -1;
	return 0;
}

} // namespace

int vc_exfat_probe (VcVolume *volume)
{
	if (!volume)
		return VC_ERR_ARGUMENT;
	uint8_t oem[8];
	if (vc_read (volume, 3, oem, 8) != VC_OK)
		return VC_ERR_IO;
	return memcmp (oem, "EXFAT   ", 8) == 0 ? 1 : 0;
}

int vc_exfat_format (VcVolume *volume, uint64_t data_bytes)
{
	if (!volume || data_bytes < 2ull * 1024ull * 1024ull)
		return VC_ERR_ARGUMENT;
	const uint32_t bps = 512;
	uint64_t total_sec = data_bytes / bps;
	if (total_sec < 4096)
		return VC_ERR_ARGUMENT;

	uint8_t spc_shift = 3; /* 4 KiB */
	if (data_bytes >= 32ull * 1024ull * 1024ull * 1024ull)
		spc_shift = 8; /* 128 KiB */
	else if (data_bytes >= 256ull * 1024ull * 1024ull)
		spc_shift = 5; /* 16 KiB */
	uint32_t spc = 1u << spc_shift;
	uint32_t cluster_size = bps * spc;
	uint32_t fat_offset = 24;
	uint32_t fat_len = 1;
	uint32_t heap_sec = 0;
	uint32_t cluster_count = 0;
	for (int i = 0; i < 8; ++i)
	{
		heap_sec = (fat_offset + fat_len + spc - 1) / spc * spc;
		if (heap_sec >= total_sec)
			return VC_ERR_ARGUMENT;
		cluster_count = (uint32_t) ((total_sec - heap_sec) / spc);
		if (cluster_count < 16)
			return VC_ERR_ARGUMENT;
		uint32_t need = (uint32_t) (((uint64_t) (cluster_count + 2) * 4 + bps - 1) / bps);
		if (need <= fat_len)
			break;
		fat_len = need;
	}

	uint64_t bitmap_bytes = ((uint64_t) cluster_count + 7) / 8;
	uint32_t bitmap_clusters = (uint32_t) ((bitmap_bytes + cluster_size - 1) / cluster_size);
	if (bitmap_clusters == 0)
		bitmap_clusters = 1;
	const uint32_t upcase_bytes = 128 * 1024;
	uint32_t upcase_clusters = (upcase_bytes + cluster_size - 1) / cluster_size;
	uint32_t root_clusters = 1;
	uint32_t used = bitmap_clusters + upcase_clusters + root_clusters;
	if (used + 2 >= cluster_count)
		return VC_ERR_ARGUMENT;
	uint32_t bitmap_cluster = 2;
	uint32_t upcase_cluster = bitmap_cluster + bitmap_clusters;
	uint32_t root_cluster = upcase_cluster + upcase_clusters;

	std::vector<uint8_t> boot (12 * 512, 0);
	boot[0] = 0xEB;
	boot[1] = 0x76;
	boot[2] = 0x90;
	memcpy (&boot[3], "EXFAT   ", 8);
	put64 (&boot[64], 0);
	put64 (&boot[72], total_sec);
	put32 (&boot[80], fat_offset);
	put32 (&boot[84], fat_len);
	put32 (&boot[88], heap_sec);
	put32 (&boot[92], cluster_count);
	put32 (&boot[96], root_cluster);
	uint32_t serial = (uint32_t) (data_bytes ^ 0x56435054u);
	put32 (&boot[100], serial);
	boot[104] = 0x00;
	boot[105] = 0x01;
	boot[108] = 9;
	boot[109] = spc_shift;
	boot[110] = 1;
	boot[111] = 0x80;
	boot[112] = 0xFF;
	boot[510] = 0x55;
	boot[511] = 0xAA;
	for (int s = 1; s <= 8; ++s)
	{
		boot[s * 512 + 510] = 0x55;
		boot[s * 512 + 511] = 0xAA;
	}
	uint32_t sum = boot_checksum (boot.data (), 11 * 512);
	for (int i = 0; i < 128; ++i)
		put32 (&boot[11 * 512 + i * 4], sum);

	if (write_all (volume, 0, boot.data (), boot.size ()) != VC_OK)
		return VC_ERR_IO;
	if (write_all (volume, 12 * 512, boot.data (), boot.size ()) != VC_OK)
		return VC_ERR_IO;

	std::vector<uint8_t> fat ((size_t) fat_len * bps, 0);
	put32 (&fat[0], 0xFFFFFFF8u);
	put32 (&fat[4], 0xFFFFFFFFu);
	auto chain = [&] (uint32_t first, uint32_t count)
	{
		for (uint32_t i = 0; i < count; ++i)
		{
			uint32_t val = (i + 1 == count) ? 0xFFFFFFFFu : first + i + 1;
			put32 (&fat[(first + i) * 4], val);
		}
	};
	chain (bitmap_cluster, bitmap_clusters);
	chain (upcase_cluster, upcase_clusters);
	chain (root_cluster, root_clusters);
	if (write_all (volume, (uint64_t) fat_offset * bps, fat.data (), fat.size ()) != VC_OK)
		return VC_ERR_IO;

	uint64_t heap_off = (uint64_t) heap_sec * bps;
	std::vector<uint8_t> bitmap ((size_t) bitmap_clusters * cluster_size, 0);
	for (uint32_t c = 0; c < used; ++c)
		bitmap[c / 8] = (uint8_t) (bitmap[c / 8] | (1u << (c % 8)));
	if (write_all (volume, heap_off + (uint64_t) (bitmap_cluster - 2) * cluster_size, bitmap.data (), bitmap.size ()) != VC_OK)
		return VC_ERR_IO;

	std::vector<uint8_t> up ((size_t) upcase_clusters * cluster_size, 0);
	for (uint32_t i = 0; i < 65536; ++i)
		put16 (&up[i * 2], upcase16 ((uint16_t) i));
	uint32_t up_sum = 0;
	for (uint32_t i = 0; i < upcase_bytes; ++i)
		up_sum = ((up_sum << 31) | (up_sum >> 1)) + up[i];
	if (write_all (volume, heap_off + (uint64_t) (upcase_cluster - 2) * cluster_size, up.data (), up.size ()) != VC_OK)
		return VC_ERR_IO;

	std::vector<uint8_t> root (cluster_size, 0);
	root[0] = 0x81;
	put32 (&root[20], bitmap_cluster);
	put64 (&root[24], bitmap_bytes);
	root[32] = 0x82;
	put32 (&root[32 + 4], up_sum);
	put32 (&root[32 + 20], upcase_cluster);
	put64 (&root[32 + 24], upcase_bytes);
	root[64] = 0x83;
	root[65] = 7;
	const uint16_t label[] = { 'V', 'C', ' ', 'P', 'o', 'r', 't' };
	for (int i = 0; i < 7; ++i)
		put16 (&root[66 + i * 2], label[i]);
	if (write_all (volume, heap_off + (uint64_t) (root_cluster - 2) * cluster_size, root.data (), root.size ()) != VC_OK)
		return VC_ERR_IO;
	return VC_OK;
}

int vc_exfat_list_dir_from (VcVolume *volume, const char *path, VcDirEntry *entries, int max_entries, int skip)
{
	if (!volume || !entries || max_entries <= 0 || skip < 0)
		return VC_ERR_ARGUMENT;
	if (path && strstr (path, ".."))
		return VC_ERR_ARGUMENT;
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> dir;
	if (is_root (path))
		rc = load_root_dir (volume, &g, dir);
	else
	{
		Found hit;
		rc = find_path (volume, &g, path, &hit);
		if (rc != VC_OK)
			return rc;
		if (!hit.entry.is_dir)
			return VC_ERR_UNSUPPORTED;
		if (hit.entry.first_cluster < 2)
			return 0;
		rc = load_chain (volume, &g, hit.entry.first_cluster,
			hit.entry.size ? hit.entry.size : g.cluster_size, hit.no_fat, dir);
	}
	if (rc != VC_OK)
		return rc;
	return parse_dir (dir.data (), dir.size (), entries, max_entries, skip);
}

int vc_exfat_export (VcVolume *volume, const char *path, const char *dest_path)
{
	if (!volume || !path || !dest_path)
		return VC_ERR_ARGUMENT;
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	Found hit;
	rc = find_path (volume, &g, path, &hit);
	if (rc != VC_OK)
		return rc;
	FILE *out = fopen (dest_path, "wb");
	if (!out)
		return VC_ERR_IO;
	rc = copy_file (volume, &g, &hit, nullptr, 0, nullptr, out);
	if (fclose (out) != 0 && rc == VC_OK)
		rc = VC_ERR_IO;
	return rc;
}

int vc_exfat_read_file (VcVolume *volume, const char *path, void *buffer, size_t buffer_size, size_t *out_size)
{
	if (out_size)
		*out_size = 0;
	if (!volume || !path || !buffer)
		return VC_ERR_ARGUMENT;
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	Found hit;
	rc = find_path (volume, &g, path, &hit);
	if (rc != VC_OK)
		return rc;
	return copy_file (volume, &g, &hit, buffer, buffer_size, out_size, nullptr);
}

int vc_exfat_import (VcVolume *volume, const char *dest_dir, const char *src_path, const char *dest_name)
{
	if (!src_path || !src_path[0])
		return VC_ERR_ARGUMENT;
	const char *name = dest_name && dest_name[0] ? dest_name : basename_of (src_path);
	name = basename_of (name);
	if (name_bad (name))
		return VC_ERR_ARGUMENT;
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;

	Found parent = {};
	std::vector<uint8_t> dir;
	const int in_root = is_root (dest_dir);
	if (in_root)
		rc = load_root_dir (volume, &g, dir);
	else
	{
		rc = find_path (volume, &g, dest_dir, &parent);
		if (rc != VC_OK)
			return rc;
		if (!parent.entry.is_dir)
			return VC_ERR_UNSUPPORTED;
		uint64_t bytes = parent.entry.size ? parent.entry.size : g.cluster_size;
		rc = load_chain (volume, &g, parent.entry.first_cluster, bytes, parent.no_fat, dir);
	}
	if (rc != VC_OK)
		return rc;

	FILE *in = fopen (src_path, "rb");
	if (!in)
		return VC_ERR_IO;
	uint64_t size = 0;
	if (file_size64 (in, &size) != 0)
	{
		fclose (in);
		return VC_ERR_IO;
	}

	Found exists;
	if (find_in_dir (dir.data (), dir.size (), name, &exists) == VC_OK)
	{
		fclose (in);
		return VC_ERR_FORMAT;
	}

	uint32_t need = size == 0 ? 0 : (uint32_t) ((size + g.cluster_size - 1) / g.cluster_size);
	uint32_t first = 0;
	if (need)
	{
		rc = find_run (volume, &g, need, &first);
		if (rc != VC_OK)
		{
			fclose (in);
			return rc;
		}
		rc = mark_run (volume, &g, first, need, 1);
		if (rc != VC_OK)
		{
			fclose (in);
			return rc;
		}
		std::vector<uint8_t> chunk (g.cluster_size);
		uint64_t left = size;
		uint32_t cluster = first;
		vc_progress_set (0, "Copying into volume");
		while (left)
		{
			size_t n = left < g.cluster_size ? (size_t) left : g.cluster_size;
			memset (chunk.data (), 0, chunk.size ());
			if (fread (chunk.data (), 1, n, in) != n)
			{
				fclose (in);
				return VC_ERR_IO;
			}
			if (write_all (volume, cluster_off (&g, cluster), chunk.data (), g.cluster_size) != VC_OK)
			{
				fclose (in);
				return VC_ERR_IO;
			}
			left -= n;
			if (size)
				vc_progress_tick ((int) (((size - left) * 100ull) / size), "Copying into volume");
			cluster++;
		}
	}
	fclose (in);

	uint16_t name16[256];
	size_t nlen = utf8_to_utf16 (name, name16, 256);
	if (nlen == 0)
		return VC_ERR_ARGUMENT;
	uint8_t name_entries = (uint8_t) ((nlen + 14) / 15);
	size_t set_bytes = 32u * (2u + name_entries);
	std::vector<uint8_t> set (set_bytes);
	build_file_set (set.data (), set_bytes, name16, nlen, 0, first, size, 1);
	return insert_set (volume, &g, dir, set.data (), set_bytes, in_root ? nullptr : &parent);
}

int vc_exfat_delete (VcVolume *volume, const char *path)
{
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	Found hit;
	rc = find_path (volume, &g, path, &hit);
	if (rc != VC_OK)
		return rc;
	if (hit.entry.is_dir)
		return VC_ERR_UNSUPPORTED;
	char parent[512];
	rc = parent_of (path, parent, sizeof (parent));
	if (rc != VC_OK)
		return rc;
	Found into = {};
	std::vector<uint8_t> dir;
	const int in_root = is_root (parent);
	if (in_root)
		rc = load_root_dir (volume, &g, dir);
	else
	{
		rc = find_path (volume, &g, parent, &into);
		if (rc != VC_OK)
			return rc;
		if (!into.entry.is_dir)
			return VC_ERR_UNSUPPORTED;
		uint64_t bytes = into.entry.size ? into.entry.size : g.cluster_size;
		rc = load_chain (volume, &g, into.entry.first_cluster, bytes, into.no_fat, dir);
	}
	if (rc != VC_OK)
		return rc;
	Found inDir;
	rc = find_in_dir (dir.data (), dir.size (), basename_of (path), &inDir);
	if (rc != VC_OK)
		return rc;
	for (size_t i = 0; i < inDir.set_bytes; i += 32)
		dir[inDir.offset + i] = (uint8_t) (dir[inDir.offset + i] & 0x7F);
	if (hit.entry.first_cluster >= 2 && hit.entry.size)
	{
		uint32_t count = (uint32_t) ((hit.entry.size + g.cluster_size - 1) / g.cluster_size);
		rc = mark_run (volume, &g, hit.entry.first_cluster, count, 0);
		if (rc != VC_OK)
			return rc;
	}
	if (in_root)
		return save_root_prefix (volume, &g, dir);
	uint64_t cap = into.entry.size ? into.entry.size : g.cluster_size;
	if (dir.size () > cap)
		return VC_ERR_MEMORY;
	return write_chain (volume, &g, into.entry.first_cluster, dir.data (), dir.size (), into.no_fat);
}

int vc_exfat_mkdir (VcVolume *volume, const char *parent_dir, const char *name)
{
	if (!is_root (parent_dir) || name_bad (name))
		return VC_ERR_ARGUMENT;
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> dir;
	rc = load_root_dir (volume, &g, dir);
	if (rc != VC_OK)
		return rc;
	Found exists;
	if (find_in_dir (dir.data (), dir.size (), name, &exists) == VC_OK)
		return VC_ERR_FORMAT;
	uint32_t first = 0;
	rc = find_run (volume, &g, 1, &first);
	if (rc != VC_OK)
		return rc;
	rc = mark_run (volume, &g, first, 1, 1);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> empty (g.cluster_size, 0);
	if (write_all (volume, cluster_off (&g, first), empty.data (), empty.size ()) != VC_OK)
		return VC_ERR_IO;
	uint16_t name16[256];
	size_t nlen = utf8_to_utf16 (name, name16, 256);
	uint8_t name_entries = (uint8_t) ((nlen + 14) / 15);
	size_t set_bytes = 32u * (2u + name_entries);
	std::vector<uint8_t> set (set_bytes);
	build_file_set (set.data (), set_bytes, name16, nlen, 1, first, g.cluster_size, 1);
	return insert_set (volume, &g, dir, set.data (), set_bytes);
}

int vc_exfat_rmdir (VcVolume *volume, const char *path)
{
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	Found hit;
	rc = find_path (volume, &g, path, &hit);
	if (rc != VC_OK)
		return rc;
	if (!hit.entry.is_dir)
		return VC_ERR_UNSUPPORTED;
	if (hit.entry.first_cluster >= 2)
	{
		std::vector<uint8_t> child;
		rc = load_chain (volume, &g, hit.entry.first_cluster, hit.entry.size, hit.no_fat, child);
		if (rc != VC_OK)
			return rc;
		for (size_t i = 0; i + 32 <= child.size (); i += 32)
		{
			if (child[i] == 0)
				break;
			if (child[i] == 0x85)
				return VC_ERR_UNSUPPORTED;
		}
		uint32_t count = (uint32_t) ((hit.entry.size + g.cluster_size - 1) / g.cluster_size);
		if (count == 0)
			count = 1;
		rc = mark_run (volume, &g, hit.entry.first_cluster, count, 0);
		if (rc != VC_OK)
			return rc;
	}
	std::vector<uint8_t> dir;
	rc = load_root_dir (volume, &g, dir);
	if (rc != VC_OK)
		return rc;
	for (size_t i = 0; i < hit.set_bytes; i += 32)
		dir[hit.offset + i] = (uint8_t) (dir[hit.offset + i] & 0x7F);
	return save_root_prefix (volume, &g, dir);
}

int vc_exfat_rename (VcVolume *volume, const char *path, const char *new_name)
{
	if (name_bad (new_name))
		return VC_ERR_ARGUMENT;
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	Found hit;
	rc = find_path (volume, &g, path, &hit);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> dir;
	rc = load_root_dir (volume, &g, dir);
	if (rc != VC_OK)
		return rc;
	Found clash;
	if (find_in_dir (dir.data (), dir.size (), new_name, &clash) == VC_OK)
		return VC_ERR_FORMAT;
	for (size_t i = 0; i < hit.set_bytes; i += 32)
		dir[hit.offset + i] = (uint8_t) (dir[hit.offset + i] & 0x7F);
	uint16_t name16[256];
	size_t nlen = utf8_to_utf16 (new_name, name16, 256);
	uint8_t name_entries = (uint8_t) ((nlen + 14) / 15);
	size_t set_bytes = 32u * (2u + name_entries);
	std::vector<uint8_t> set (set_bytes);
	build_file_set (set.data (), set_bytes, name16, nlen, hit.entry.is_dir,
		hit.entry.first_cluster, hit.entry.size, hit.no_fat);
	return insert_set (volume, &g, dir, set.data (), set_bytes);
}

int vc_exfat_wipe_free (VcVolume *volume)
{
	Exfat g;
	int rc = load_geom (volume, &g);
	if (rc != VC_OK)
		return rc;
	std::vector<uint8_t> z (g.cluster_size, 0);
	uint32_t free_n = 0;
	for (uint32_t c = 2; c < g.cluster_count + 2; ++c)
	{
		int used = 1;
		rc = bitmap_get (volume, &g, c, &used);
		if (rc != VC_OK)
			return rc;
		if (!used)
			++free_n;
	}
	vc_progress_set (0, "Wiping free space");
	if (free_n == 0)
	{
		vc_progress_set (100, "Wiping free space");
		return VC_OK;
	}
	uint32_t done = 0;
	for (uint32_t c = 2; c < g.cluster_count + 2; ++c)
	{
		int used = 1;
		rc = bitmap_get (volume, &g, c, &used);
		if (rc != VC_OK)
			return rc;
		if (used)
			continue;
		if (write_all (volume, cluster_off (&g, c), z.data (), z.size ()) != VC_OK)
			return VC_ERR_IO;
		++done;
		vc_progress_tick ((int) ((done * 100ull) / free_n), "Wiping free space");
	}
	vc_progress_set (100, "Wiping free space");
	return VC_OK;
}
