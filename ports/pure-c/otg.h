#ifndef VC_C_OTG_H
#define VC_C_OTG_H

/* Sketch of experimental-otg-master USB slots. No SCSI, no bind, no Open. */

#define VC_C_OTG_PREFIX "/vcport-otg-dev/"

int vc_c_otg_is_path (const char *path);
int vc_c_otg_ready (const char *path);

#endif
