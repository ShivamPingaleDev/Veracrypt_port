/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef TC_HEADER_Main_MacOSXBiometric
#define TC_HEADER_Main_MacOSXBiometric

#ifdef TC_MACOSX

#include "Platform/Platform.h"
#include "Volume/VolumePassword.h"

namespace VeraCrypt
{
	class MacOSXBiometric
	{
	public:
		static bool IsAvailable ();
		static bool HasStoredPassword (const string &volumePath);
		static bool StoreVolumePassword (const string &volumePath, shared_ptr <VolumePassword> password, int pim);
		static bool LoadVolumePassword (const string &volumePath, shared_ptr <VolumePassword> &password, int &pim);
		static void DeleteStoredPassword (const string &volumePath);

	private:
		MacOSXBiometric ();
	};
}

#endif

#endif
