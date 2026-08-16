/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifdef TC_MACOSX

#import <Foundation/Foundation.h>
#import <Security/Authorization.h>
#import <Security/AuthorizationTags.h>

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

#include "MacOSXAuthorization.h"
#include "Core/Unix/CoreService.h"
#include "Core/Unix/CoreServiceRequest.h"
#include "Core/CoreException.h"
#include "Platform/Platform.h"
#include "Platform/FileStream.h"
#include "Platform/SystemLog.h"
#include "Platform/Unix/Process.h"

namespace VeraCrypt
{
	static const char *ElevatedSocketArg = "--elevated-socket";
	static const char *ClientUidArg = "--client-uid";
	static const char *ClientGidArg = "--client-gid";

	static int CreateListeningSocket (char *pathOut, size_t pathOutSize)
	{
		snprintf (pathOut, pathOutSize, "/tmp/.veracrypt_elev_%d_%d", (int) getuid(), (int) getpid());
		unlink (pathOut);

		int listenFd = socket (AF_UNIX, SOCK_STREAM, 0);
		throw_sys_if (listenFd == -1);

		struct sockaddr_un addr;
		memset (&addr, 0, sizeof (addr));
		addr.sun_family = AF_UNIX;
		strncpy (addr.sun_path, pathOut, sizeof (addr.sun_path) - 1);

		if (bind (listenFd, (struct sockaddr *) &addr, sizeof (addr)) == -1)
		{
			int saved = errno;
			close (listenFd);
			unlink (pathOut);
			throw SystemException (SRC_POS, saved);
		}

		chmod (pathOut, 0600);

		if (listen (listenFd, 1) == -1)
		{
			int saved = errno;
			close (listenFd);
			unlink (pathOut);
			throw SystemException (SRC_POS, saved);
		}

		return listenFd;
	}

	static int AcceptWithTimeout (int listenFd, int timeoutMs)
	{
		struct pollfd pfd;
		pfd.fd = listenFd;
		pfd.events = POLLIN;
		pfd.revents = 0;

		int pr = poll (&pfd, 1, timeoutMs);
		if (pr == 0)
			throw ElevationFailed (SRC_POS, "Authorization Services", 1, "Timed out waiting for the elevated core service to start.");
		if (pr < 0)
			throw SystemException (SRC_POS);

		int connFd = accept (listenFd, nullptr, nullptr);
		throw_sys_if (connFd == -1);
		return connFd;
	}

	void ConnectElevatedSocket (const char *socketPath)
	{
		int fd = socket (AF_UNIX, SOCK_STREAM, 0);
		throw_sys_if (fd == -1);

		struct sockaddr_un addr;
		memset (&addr, 0, sizeof (addr));
		addr.sun_family = AF_UNIX;
		strncpy (addr.sun_path, socketPath, sizeof (addr.sun_path) - 1);

		if (connect (fd, (struct sockaddr *) &addr, sizeof (addr)) == -1)
		{
			int saved = errno;
			close (fd);
			throw SystemException (SRC_POS, saved);
		}

		if (dup2 (fd, STDIN_FILENO) == -1 || dup2 (fd, STDOUT_FILENO) == -1)
		{
			int saved = errno;
			close (fd);
			throw SystemException (SRC_POS, saved);
		}

		if (fd > STDOUT_FILENO)
			close (fd);
	}

	bool StartElevatedUsingAuthorization (const CoreServiceRequest &request)
	{
		string appPath = request.ApplicationExecutablePath;
		if (appPath.empty() || appPath[0] != '/')
		{
			std::string errorMsg;
			appPath = Process::FindSystemBinary ("veracrypt", errorMsg);
			if (appPath.empty())
				appPath = Process::FindSystemBinary ("VeraCrypt", errorMsg);
			if (appPath.empty())
				throw SystemException (SRC_POS, errorMsg.empty() ? "VeraCrypt executable path is unknown" : errorMsg);
		}

		char socketPath[104];
		int listenFd = CreateListeningSocket (socketPath, sizeof (socketPath));
		finally_do_arg2 (int, listenFd, char *, socketPath, { close (finally_arg); unlink (finally_arg2); });

		char uidStr[32];
		char gidStr[32];
		snprintf (uidStr, sizeof (uidStr), "%u", (unsigned) getuid());
		snprintf (gidStr, sizeof (gidStr), "%u", (unsigned) getgid());

		AuthorizationRef authRef = nullptr;
		OSStatus status = AuthorizationCreate (nullptr, kAuthorizationEmptyEnvironment, kAuthorizationFlagDefaults, &authRef);
		if (status != errAuthorizationSuccess || !authRef)
			return false;

		finally_do_arg (AuthorizationRef, authRef, { AuthorizationFree (finally_arg, kAuthorizationFlagDestroyRights); });

		AuthorizationItem rightItems[] = { { kAuthorizationRightExecute, 0, nullptr, 0 } };
		AuthorizationRights rights = { 1, rightItems };
		AuthorizationFlags flags = kAuthorizationFlagDefaults
			| kAuthorizationFlagInteractionAllowed
			| kAuthorizationFlagPreAuthorize
			| kAuthorizationFlagExtendRights;

		status = AuthorizationCopyRights (authRef, &rights, nullptr, flags, nullptr);
		if (status == errAuthorizationCanceled || status == errAuthorizationDenied)
			throw UserAbort (SRC_POS);
		if (status != errAuthorizationSuccess)
			throw ElevationFailed (SRC_POS, "Authorization Services", (int) status,
				"macOS refused administrator authentication. A standard user must authenticate as an administrator (Touch ID or admin user + password) in the system dialog.");

		char *args[] = {
			(char *) TC_CORE_SERVICE_NO_FORK_CMDLINE_OPTION,
			(char *) ElevatedSocketArg,
			socketPath,
			(char *) ClientUidArg,
			uidStr,
			(char *) ClientGidArg,
			gidStr,
			nullptr
		};

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
		status = AuthorizationExecuteWithPrivileges (authRef, appPath.c_str(), kAuthorizationFlagDefaults, args, nullptr);
#pragma clang diagnostic pop

		if (status == errAuthorizationCanceled || status == errAuthorizationDenied)
			throw UserAbort (SRC_POS);
		if (status != errAuthorizationSuccess)
			throw ElevationFailed (SRC_POS, "Authorization Services", (int) status,
				"Failed to start the elevated VeraCrypt core service.");

		// SecurityAgent / Touch ID can take a while; wait up to 90s for the root helper.
		int connFd = AcceptWithTimeout (listenFd, 90000);

		int readFd = dup (connFd);
		throw_sys_if (readFd == -1);

		CoreService::AdoptElevatedChannel (connFd, readFd);
		return true;
	}
}

#endif
