/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef TC_HEADER_Main_PortFileWrap
#define TC_HEADER_Main_PortFileWrap

#include "System.h"

namespace VeraCrypt
{
	class PortFileWrap
	{
	public:
		static void WrapFile (wxWindow *parent);
		static void UnwrapFile (wxWindow *parent);
		static void ShareEncrypted (wxWindow *parent);
	};

#ifdef TC_MACOSX
	void MacOSXShareEncrypted (wxWindow *parent, const wxString &path);
#endif
}

#endif
