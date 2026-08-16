/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifndef TC_HEADER_Main_OfflineUpdate
#define TC_HEADER_Main_OfflineUpdate

#include "Platform/Platform.h"

namespace VeraCrypt
{
	struct UpdateManifest
	{
		string PortVersion;
		string UpstreamVersion;
		string Notes;
		string DownloadUrl;
		string AndroidApkSha256;
		string SourceSha256;
		bool Parsed;
	};

	class OfflineUpdate
	{
	public:
		// One-shot HTTPS GET. The session is created, used, and destroyed in
		// this call. Nothing is kept open afterwards.
		static string FetchHttps (const string &url);
		static UpdateManifest ParseManifest (const string &json);
		static int CompareVersion (const string &a, const string &b);
		static bool IsNewer (const string &remote, const string &local);

	private:
		OfflineUpdate ();
	};
}

#endif
