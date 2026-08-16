/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef TC_HEADER_Core_MacOSXAuthorization
#define TC_HEADER_Core_MacOSXAuthorization

#ifdef TC_MACOSX

namespace VeraCrypt
{
	// Native macOS administrator authentication (username picker + password + Touch ID).
	// Replaces sudo -S, which authenticates only the current user and therefore
	// rejects a standard user who types a valid administrator password.
	bool StartElevatedUsingAuthorization (class CoreServiceRequest const &request);
	void ConnectElevatedSocket (const char *socketPath);
}

#endif

#endif
