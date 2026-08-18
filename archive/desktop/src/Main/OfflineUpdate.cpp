/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

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
#include <cstring>
#include <sstream>
#include <vector>

namespace VeraCrypt
{
	static const char *kGithubStatus = "https://www.githubstatus.com/api/v2/status.json";

	static bool UrlAllowed (const string &url)
	{
		return url == VC_PORT_UPDATE_MANIFEST_URL
			|| url == VC_PORT_UPSTREAM_RELEASES
			|| url == kGithubStatus;
	}

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
		if (!UrlAllowed (url))
			throw ParameterIncorrect (SRC_POS);

		std::string errorMsg;
		string curlPath = Process::FindSystemBinary ("curl", errorMsg);
		if (curlPath.empty())
			throw SystemException (SRC_POS, "curl is required for a one-shot update check");

		list <string> args;
		args.push_back ("-fsS");
		args.push_back ("--proto");
		args.push_back ("=https");
		args.push_back ("--tlsv1.2");
		args.push_back ("--max-time");
		args.push_back ("8");
		args.push_back ("--max-redirs");
		args.push_back ("0");
		args.push_back ("--max-filesize");
		args.push_back ("65536");
		args.push_back ("--retry");
		args.push_back ("0");
		args.push_back ("--no-sessionid");
		args.push_back ("-A");
		args.push_back (string ("VCPort-OfflineUpdate/") + VC_PORT_VERSION);
		args.push_back (url);

		// curl exits after the response. No keep-alive session is retained.
		return Process::Execute (curlPath, args, 20000);
	}

	static bool ValidSha256 (const string &hex)
	{
		if (hex.empty())
			return true;
		if (hex.size() != 64)
			return false;
		for (size_t i = 0; i < hex.size(); ++i)
		{
			char c = hex[i];
			if (!isxdigit ((unsigned char) c))
				return false;
		}
		return true;
	}

	static bool ValidHttpsUrl (const string &url)
	{
		return url.empty() || url.compare (0, 8, "https://") == 0;
	}

	UpdateManifest OfflineUpdate::ParseManifest (const string &json)
	{
		UpdateManifest m;
		m.Parsed = false;
		m.PortVersion = JsonStringField (json, "port_version");
		m.UpstreamVersion = JsonStringField (json, "upstream_version");
		m.UpstreamCommit = JsonStringField (json, "upstream_commit");
		m.Notes = JsonStringField (json, "notes");
		m.DownloadUrl = JsonStringField (json, "download_url");
		if (m.DownloadUrl.empty())
			m.DownloadUrl = JsonStringField (json, "macos_url");
		m.AndroidApkSha256 = JsonStringField (json, "android_apk_sha256");
		m.SourceSha256 = JsonStringField (json, "source_sha256");
		if (m.PortVersion.empty())
			return m;
		if (!ValidSha256 (m.AndroidApkSha256) || !ValidSha256 (m.SourceSha256))
			return m;
		if (!ValidHttpsUrl (m.DownloadUrl))
			return m;
		m.Parsed = true;
		return m;
	}

	string OfflineUpdate::VersionFromVeraCryptTag (const string &tag)
	{
		string t = tag;
		const char *prefixes[] = { "VeraCrypt_", "VeraCrypt-", "VeraCrypt ", nullptr };
		for (int i = 0; prefixes[i]; ++i)
		{
			size_t n = strlen (prefixes[i]);
			if (t.size() >= n && t.compare (0, n, prefixes[i]) == 0)
			{
				t = t.substr (n);
				break;
			}
		}
		while (!t.empty() && isspace ((unsigned char) t[0]))
			t.erase (t.begin());
		size_t sp = t.find (' ');
		if (sp != string::npos)
			t = t.substr (0, sp);
		return t;
	}

	string OfflineUpdate::ParseGithubReleaseTag (const string &json)
	{
		return JsonStringField (json, "tag_name");
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
