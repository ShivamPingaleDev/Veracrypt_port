/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#include "System.h"
#include "OfflineUpdate.h"
#include "PortVersion.h"
#include "Platform/Unix/Process.h"
#include "Platform/Exception.h"

#include <cctype>
#include <sstream>
#include <vector>

namespace VeraCrypt
{
	static string JsonStringField (const string &json, const string &key)
	{
		string needle = "\"" + key + "\"";
		size_t pos = json.find (needle);
		if (pos == string::npos)
			return string();
		pos = json.find (':', pos);
		if (pos == string::npos)
			return string();
		pos = json.find ('"', pos);
		if (pos == string::npos)
			return string();
		size_t end = json.find ('"', pos + 1);
		if (end == string::npos)
			return string();
		return json.substr (pos + 1, end - pos - 1);
	}

	string OfflineUpdate::FetchHttps (const string &url)
	{
		if (url.compare (0, 8, "https://") != 0)
			throw ParameterIncorrect (SRC_POS);

		std::string errorMsg;
		string curlPath = Process::FindSystemBinary ("curl", errorMsg);
		if (curlPath.empty())
			throw SystemException (SRC_POS, "curl is required for a one-shot update check");

		list <string> args;
		args.push_back ("-fsSL");
		args.push_back ("--proto");
		args.push_back ("=https");
		args.push_back ("--tlsv1.2");
		args.push_back ("--max-time");
		args.push_back ("20");
		args.push_back ("--retry");
		args.push_back ("0");
		args.push_back ("--no-sessionid");
		args.push_back ("-A");
		args.push_back ("VCPort-OfflineUpdate/0.1");
		args.push_back (url);

		// curl exits after the response. No keep-alive session is retained.
		return Process::Execute (curlPath, args, 25000);
	}

	UpdateManifest OfflineUpdate::ParseManifest (const string &json)
	{
		UpdateManifest m;
		m.Parsed = false;
		m.PortVersion = JsonStringField (json, "port_version");
		m.UpstreamVersion = JsonStringField (json, "upstream_version");
		m.Notes = JsonStringField (json, "notes");
		m.DownloadUrl = JsonStringField (json, "download_url");
		if (m.DownloadUrl.empty())
			m.DownloadUrl = JsonStringField (json, "macos_url");
		m.Parsed = !m.PortVersion.empty();
		return m;
	}

	int OfflineUpdate::CompareVersion (const string &a, const string &b)
	{
		vector <int> pa, pb;
		auto parse = [](const string &s, vector <int> &out)
		{
			int n = 0;
			bool inNum = false;
			for (size_t i = 0; i <= s.size(); ++i)
			{
				char c = i < s.size() ? s[i] : '.';
				if (isdigit ((unsigned char) c))
				{
					n = n * 10 + (c - '0');
					inNum = true;
				}
				else if (inNum)
				{
					out.push_back (n);
					n = 0;
					inNum = false;
				}
			}
		};
		parse (a, pa);
		parse (b, pb);
		size_t n = pa.size() > pb.size() ? pa.size() : pb.size();
		pa.resize (n, 0);
		pb.resize (n, 0);
		for (size_t i = 0; i < n; ++i)
		{
			if (pa[i] < pb[i])
				return -1;
			if (pa[i] > pb[i])
				return 1;
		}
		return 0;
	}

	bool OfflineUpdate::IsNewer (const string &remote, const string &local)
	{
		return CompareVersion (remote, local) > 0;
	}
}
