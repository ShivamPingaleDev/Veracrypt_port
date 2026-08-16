/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Token stubs for Android/iOS. Smart-card keyfiles are not available on mobile.
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
