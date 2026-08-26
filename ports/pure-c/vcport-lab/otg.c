#include "otg.h"

#include <string.h>

int vc_c_otg_is_path (const char *path)
{
	if (!path)
		return 0;
	return strncmp (path, VC_C_OTG_PREFIX, sizeof (VC_C_OTG_PREFIX) - 1) == 0 ? 1 : 0;
}

int vc_c_otg_ready (const char *path)
{
	(void) path;
	/* Vague: the name exists. Nothing is bound. Whole-disk Open is Android-only
	 * on experimental-otg-master. */
	return 0;
}
