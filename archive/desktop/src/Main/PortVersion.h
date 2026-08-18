/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef TC_HEADER_Main_PortVersion
#define TC_HEADER_Main_PortVersion

/*
 * Compile-time pin. Copied from ports/version.json by
 * ports/scripts/sync_source_pin.py. The desktop "Check for updates" menu
 * reads VC_PORT_UPDATE_MANIFEST_URL (our tree) and VC_PORT_UPSTREAM_RELEASES
 * (official VeraCrypt GitHub latest). Neither call installs software.
 */
#define VC_PORT_VERSION			"0.3.1"
#define VC_PORT_UPSTREAM_VERSION	"1.26.29"
#define VC_PORT_UPSTREAM_COMMIT	"b48e31f5b47da7d41025e3f0e02751675e15005a"
#define VC_PORT_UPSTREAM_TAG		"VeraCrypt_1.26.29"
#define VC_PORT_UPSTREAM_GIT		"https://github.com/veracrypt/VeraCrypt.git"
#define VC_PORT_UPSTREAM_RELEASES	"https://api.github.com/repos/veracrypt/VeraCrypt/releases/latest"
#define VC_PORT_SOURCE_REPO		"https://github.com/ShivamPingaleDev/Veracrypt_port"
#define VC_PORT_UPDATE_MANIFEST_URL	"https://raw.githubusercontent.com/ShivamPingaleDev/Veracrypt_port/master/ports/version.json"

#endif
