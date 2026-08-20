#include "preview.h"

#include <string.h>

static const char *ext_of (const char *name)
{
	const char *dot;
	const char *p;
	if (!name)
		return "";
	dot = name;
	for (p = name; *p; p++)
		if (*p == '.')
			dot = p + 1;
	return dot == name ? "" : dot;
}

static int eqi (const char *a, const char *b)
{
	for (; *a && *b; a++, b++)
	{
		char ca = *a >= 'A' && *a <= 'Z' ? (char) (*a - 'A' + 'a') : *a;
		char cb = *b >= 'A' && *b <= 'Z' ? (char) (*b - 'A' + 'a') : *b;
		if (ca != cb)
			return 0;
	}
	return *a == 0 && *b == 0;
}

VcCPreviewKind vc_c_preview_kind (const char *name)
{
	const char *e = ext_of (name);
	if (eqi (e, "jpg") || eqi (e, "jpeg") || eqi (e, "png") || eqi (e, "gif")
		|| eqi (e, "webp") || eqi (e, "bmp") || eqi (e, "heic") || eqi (e, "heif"))
		return VC_C_PREVIEW_IMAGE;
	if (eqi (e, "txt") || eqi (e, "md") || eqi (e, "json") || eqi (e, "xml")
		|| eqi (e, "csv") || eqi (e, "log") || eqi (e, "html") || eqi (e, "htm")
		|| eqi (e, "c") || eqi (e, "h") || eqi (e, "cc") || eqi (e, "cpp")
		|| eqi (e, "py") || eqi (e, "kt") || eqi (e, "swift") || eqi (e, "sh")
		|| eqi (e, "ini") || eqi (e, "cfg"))
		return VC_C_PREVIEW_TEXT;
	if (eqi (e, "pdf"))
		return VC_C_PREVIEW_PDF;
	if (eqi (e, "mp3") || eqi (e, "m4a") || eqi (e, "aac") || eqi (e, "wav")
		|| eqi (e, "ogg") || eqi (e, "flac") || eqi (e, "oga"))
		return VC_C_PREVIEW_AUDIO;
	if (eqi (e, "mp4") || eqi (e, "mkv") || eqi (e, "webm") || eqi (e, "3gp")
		|| eqi (e, "mov") || eqi (e, "m4v"))
		return VC_C_PREVIEW_VIDEO;
	return VC_C_PREVIEW_UNSUPPORTED;
}

const char *vc_c_preview_kind_name (VcCPreviewKind kind)
{
	switch (kind)
	{
	case VC_C_PREVIEW_IMAGE:
		return "image";
	case VC_C_PREVIEW_TEXT:
		return "text";
	case VC_C_PREVIEW_PDF:
		return "pdf";
	case VC_C_PREVIEW_AUDIO:
		return "audio";
	case VC_C_PREVIEW_VIDEO:
		return "video";
	default:
		return "unsupported";
	}
}
