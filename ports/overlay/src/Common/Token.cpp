/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.

 Phone replacement for src/Common/Token.cpp. PKCS#11 / EMV smart-card
 keyfiles are not available; file keyfiles still go through Keyfile.cpp.
*/

#include "Common/Token.h"

namespace VeraCrypt
{
	vector <shared_ptr <TokenKeyfile> > Token::GetAvailableKeyfiles (bool)
	{
		return vector <shared_ptr <TokenKeyfile> > ();
	}

	bool Token::IsKeyfilePathValid (const wstring &, bool)
	{
		return false;
	}

	list <shared_ptr <TokenInfo> > Token::GetAvailableTokens ()
	{
		return list <shared_ptr <TokenInfo> > ();
	}

	shared_ptr <TokenKeyfile> Token::getTokenKeyfile (const TokenKeyfilePath &)
	{
		return shared_ptr <TokenKeyfile> ();
	}
}
