/*
 Copyright (c) 2026 Shivam Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifdef TC_MACOSX

#import <Foundation/Foundation.h>
#import <LocalAuthentication/LocalAuthentication.h>
#import <Security/Security.h>

#include "MacOSXBiometric.h"
#include "Platform/Buffer.h"

#include <cstring>

namespace VeraCrypt
{
	static NSString *const kBiometricService = @"org.veracrypt.port.volume-password";
	static const char kPayloadMagic[4] = { 'V', 'C', 'B', '1' };

	static NSString *AccountForPath (const string &volumePath)
	{
		return [NSString stringWithUTF8String: volumePath.c_str()];
	}

	static NSData *EncodePayload (shared_ptr <VolumePassword> password, int pim)
	{
		uint32 pimValue = (uint32) (pim < 0 ? 0 : pim);
		uint32 passwordSize = (uint32) password->Size();
		size_t total = 4 + 4 + 4 + passwordSize;
		vector <uint8> raw (total);
		memcpy (&raw[0], kPayloadMagic, 4);
		memcpy (&raw[4], &pimValue, 4);
		memcpy (&raw[8], &passwordSize, 4);
		if (passwordSize)
			memcpy (&raw[12], password->DataPtr(), passwordSize);
		NSData *data = [NSData dataWithBytes: &raw[0] length: total];
		if (!raw.empty())
			memset (&raw[0], 0, raw.size());
		return data;
	}

	static bool DecodePayload (NSData *data, shared_ptr <VolumePassword> &password, int &pim)
	{
		if (!data || [data length] < 12)
			return false;

		const uint8 *bytes = (const uint8 *) [data bytes];
		if (memcmp (bytes, kPayloadMagic, 4) != 0)
			return false;

		uint32 pimValue = 0;
		uint32 passwordSize = 0;
		memcpy (&pimValue, bytes + 4, 4);
		memcpy (&passwordSize, bytes + 8, 4);
		if (12 + passwordSize != [data length])
			return false;

		password = make_shared <VolumePassword> (bytes + 12, passwordSize);
		pim = (int) pimValue;
		return true;
	}

	bool MacOSXBiometric::IsAvailable ()
	{
		@autoreleasepool
		{
			LAContext *context = [[LAContext alloc] init];
			NSError *error = nil;
			if ([context canEvaluatePolicy: LAPolicyDeviceOwnerAuthenticationWithBiometrics error: &error])
				return true;
			if ([context canEvaluatePolicy: LAPolicyDeviceOwnerAuthentication error: &error])
				return true;
			return false;
		}
	}

	bool MacOSXBiometric::HasStoredPassword (const string &volumePath)
	{
		@autoreleasepool
		{
			LAContext *context = [[LAContext alloc] init];
			context.interactionNotAllowed = YES;
			NSDictionary *query = @{
				(__bridge id) kSecClass: (__bridge id) kSecClassGenericPassword,
				(__bridge id) kSecAttrService: kBiometricService,
				(__bridge id) kSecAttrAccount: AccountForPath (volumePath),
				(__bridge id) kSecReturnData: @NO,
				(__bridge id) kSecMatchLimit: (__bridge id) kSecMatchLimitOne,
				(__bridge id) kSecUseAuthenticationContext: context
			};
			OSStatus status = SecItemCopyMatching ((__bridge CFDictionaryRef) query, nullptr);
			return status == errSecSuccess || status == errSecInteractionNotAllowed;
		}
	}

	bool MacOSXBiometric::StoreVolumePassword (const string &volumePath, shared_ptr <VolumePassword> password, int pim)
	{
		if (volumePath.empty() || !password)
			return false;

		@autoreleasepool
		{
			CFErrorRef cfError = nullptr;
			SecAccessControlCreateFlags flags = kSecAccessControlBiometryCurrentSet;
			SecAccessControlRef access = SecAccessControlCreateWithFlags (
				kCFAllocatorDefault,
				kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
				flags,
				&cfError);
			if (!access)
			{
				if (cfError)
					CFRelease (cfError);
				cfError = nullptr;
				access = SecAccessControlCreateWithFlags (
					kCFAllocatorDefault,
					kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
					kSecAccessControlUserPresence,
					&cfError);
			}
			if (!access)
			{
				if (cfError)
					CFRelease (cfError);
				return false;
			}

			DeleteStoredPassword (volumePath);

			NSData *payload = EncodePayload (password, pim);
			NSDictionary *add = @{
				(__bridge id) kSecClass: (__bridge id) kSecClassGenericPassword,
				(__bridge id) kSecAttrService: kBiometricService,
				(__bridge id) kSecAttrAccount: AccountForPath (volumePath),
				(__bridge id) kSecAttrLabel: @"VeraCrypt volume password",
				(__bridge id) kSecValueData: payload,
				(__bridge id) kSecAttrAccessControl: (__bridge id) access
			};

			OSStatus status = SecItemAdd ((__bridge CFDictionaryRef) add, nullptr);
			CFRelease (access);
			return status == errSecSuccess;
		}
	}

	bool MacOSXBiometric::LoadVolumePassword (const string &volumePath, shared_ptr <VolumePassword> &password, int &pim)
	{
		@autoreleasepool
		{
			NSDictionary *query = @{
				(__bridge id) kSecClass: (__bridge id) kSecClassGenericPassword,
				(__bridge id) kSecAttrService: kBiometricService,
				(__bridge id) kSecAttrAccount: AccountForPath (volumePath),
				(__bridge id) kSecReturnData: @YES,
				(__bridge id) kSecMatchLimit: (__bridge id) kSecMatchLimitOne,
				(__bridge id) kSecUseOperationPrompt: @"Unlock the VeraCrypt volume with Touch ID"
			};

			CFTypeRef result = nullptr;
			OSStatus status = SecItemCopyMatching ((__bridge CFDictionaryRef) query, &result);
			if (status != errSecSuccess || !result)
				return false;

			NSData *data = (__bridge_transfer NSData *) result;
			return DecodePayload (data, password, pim);
		}
	}

	void MacOSXBiometric::DeleteStoredPassword (const string &volumePath)
	{
		@autoreleasepool
		{
			NSDictionary *query = @{
				(__bridge id) kSecClass: (__bridge id) kSecClassGenericPassword,
				(__bridge id) kSecAttrService: kBiometricService,
				(__bridge id) kSecAttrAccount: AccountForPath (volumePath)
			};
			SecItemDelete ((__bridge CFDictionaryRef) query);
		}
	}
}

#endif
