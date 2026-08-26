#ifndef VC_C_PREVIEW_H
#define VC_C_PREVIEW_H

/* Same kinds as experimental-otg-master InAppPreview. No decode here. */

typedef enum VcCPreviewKind
{
	VC_C_PREVIEW_IMAGE = 0,
	VC_C_PREVIEW_TEXT,
	VC_C_PREVIEW_PDF,
	VC_C_PREVIEW_AUDIO,
	VC_C_PREVIEW_VIDEO,
	VC_C_PREVIEW_UNSUPPORTED
} VcCPreviewKind;

VcCPreviewKind vc_c_preview_kind (const char *name);
const char *vc_c_preview_kind_name (VcCPreviewKind kind);

#endif
