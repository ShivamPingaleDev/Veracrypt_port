/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#include "System.h"
#include "Main/Application.h"
#include "Main/GraphicUserInterface.h"
#include "Main/LanguageStrings.h"
#include "Main/PortFileWrap.h"

#include "vc_mobile.h"

#include <wx/filename.h>
#include <wx/valtext.h>

namespace VeraCrypt
{
	namespace
	{
		wxString Utf8 (const char *s)
		{
			return wxString::FromUTF8 (s ? s : "");
		}

		string ToUtf8 (const wxString &s)
		{
			return string (s.ToUTF8 ());
		}

		wxString AskPassword (wxWindow *parent, const wxString &title)
		{
			wxDialog dlg (parent, wxID_ANY, title, wxDefaultPosition, wxDefaultSize, wxDEFAULT_DIALOG_STYLE);
			wxBoxSizer *root = new wxBoxSizer (wxVERTICAL);
			wxStaticText *hint = new wxStaticText (&dlg, wxID_ANY, LangString["WRAP_PASSWORD_HINT"]);
			hint->Wrap (420);
			root->Add (hint, 0, wxALL | wxEXPAND, 10);
			wxTextCtrl *password = new wxTextCtrl (&dlg, wxID_ANY, wxEmptyString, wxDefaultPosition, wxDefaultSize, wxTE_PASSWORD);
			root->Add (password, 0, wxLEFT | wxRIGHT | wxEXPAND, 10);
			wxBoxSizer *buttons = new wxBoxSizer (wxHORIZONTAL);
			wxButton *generate = new wxButton (&dlg, wxID_HIGHEST + 1, LangString["IDM_GENERATE_WRAP_PASSWORD"]);
			wxButton *ok = new wxButton (&dlg, wxID_OK);
			wxButton *cancel = new wxButton (&dlg, wxID_CANCEL);
			buttons->Add (generate, 0, wxRIGHT, 8);
			buttons->AddStretchSpacer (1);
			buttons->Add (ok, 0, wxRIGHT, 8);
			buttons->Add (cancel, 0);
			root->Add (buttons, 0, wxALL | wxEXPAND, 10);
			dlg.SetSizerAndFit (root);
			password->SetFocus();

			generate->Bind (wxEVT_BUTTON, [password](wxCommandEvent&) {
				char buf[80];
				int n = vc_generate_password (buf, sizeof (buf), 24);
				if (n == 24)
					password->ChangeValue (Utf8 (buf));
				vc_secure_wipe (buf, sizeof (buf));
			});

			if (dlg.ShowModal() != wxID_OK)
				return wxEmptyString;
			return password->GetValue();
		}

		void WipeWxString (wxString &s)
		{
			for (size_t i = 0; i < s.length(); ++i)
				s[i] = wxT(' ');
			s.clear();
		}
	}

	void PortFileWrap::WrapFile (wxWindow *parent)
	{
		wxFileDialog in (parent, LangString["WRAP_SELECT_FILE"], wxEmptyString, wxEmptyString,
			wxFileSelectorDefaultWildcardStr, wxFD_OPEN | wxFD_FILE_MUST_EXIST);
		if (in.ShowModal() != wxID_OK)
			return;

		wxFileName srcName (in.GetPath());
		wxString destDefault = srcName.GetFullName() + L".vcpw";
		wxFileDialog out (parent, LangString["WRAP_SAVE_AS"], srcName.GetPath(), destDefault,
			L"Wrapped file (*.vcpw)|*.vcpw", wxFD_SAVE | wxFD_OVERWRITE_PROMPT);
		if (out.ShowModal() != wxID_OK)
			return;

		wxString password = AskPassword (parent, LangString["WRAP_PASSWORD_TITLE"]);
		if (password.empty())
			return;
		if (password.length() < 16)
		{
			Gui->ShowWarning (LangString["WRAP_PASSWORD_TOO_SHORT"]);
			WipeWxString (password);
			return;
		}

		string src = ToUtf8 (in.GetPath());
		string dest = ToUtf8 (out.GetPath());
		string orig = ToUtf8 (srcName.GetFullName());
		string pw = ToUtf8 (password);
		WipeWxString (password);

		wxBusyCursor busy;
		int rc = vc_wrap_file (src.c_str(), dest.c_str(), pw.c_str(), pw.size(), orig.c_str());
		vc_secure_wipe (&pw[0], pw.size());
		if (rc != VC_OK)
		{
			Gui->ShowError (LangString["WRAP_FAILED"]);
			return;
		}
		Gui->ShowInfo (LangString["WRAP_OK"]);
	}

	void PortFileWrap::UnwrapFile (wxWindow *parent)
	{
		wxFileDialog in (parent, LangString["UNWRAP_SELECT_FILE"], wxEmptyString, wxEmptyString,
			L"Wrapped file (*.vcpw)|*.vcpw|All files|*", wxFD_OPEN | wxFD_FILE_MUST_EXIST);
		if (in.ShowModal() != wxID_OK)
			return;

		string src = ToUtf8 (in.GetPath());
		if (!vc_is_wrap (src.c_str()))
		{
			Gui->ShowWarning (LangString["UNWRAP_NOT_WRAP"]);
			return;
		}

		wxDirDialog dir (parent, LangString["UNWRAP_SELECT_DIR"]);
		if (dir.ShowModal() != wxID_OK)
			return;

		wxString password = AskPassword (parent, LangString["UNWRAP_PASSWORD_TITLE"]);
		if (password.empty())
			return;

		string destDir = ToUtf8 (dir.GetPath());
		string pw = ToUtf8 (password);
		WipeWxString (password);
		char outPath[1024];
		wxBusyCursor busy;
		int rc = vc_unwrap_file (src.c_str(), destDir.c_str(), pw.c_str(), pw.size(), outPath, sizeof (outPath));
		vc_secure_wipe (&pw[0], pw.size());
		if (rc == VC_ERR_PASSWORD)
		{
			Gui->ShowError (LangString["UNWRAP_WRONG_PASSWORD"]);
			return;
		}
		if (rc != VC_OK)
		{
			Gui->ShowError (LangString["UNWRAP_FAILED"]);
			return;
		}
		wxString msg = LangString["UNWRAP_OK"];
		msg.Replace (L"{0}", Utf8 (outPath));
		Gui->ShowInfo (msg);
	}

	void PortFileWrap::ShareEncrypted (wxWindow *parent)
	{
		wxFileDialog in (parent, LangString["SHARE_SELECT_FILE"], wxEmptyString, wxEmptyString,
			L"Encrypted container (*.hc;*.tc;*.vera;*.vcpw)|*.hc;*.tc;*.vera;*.vcpw|All files|*",
			wxFD_OPEN | wxFD_FILE_MUST_EXIST);
		if (in.ShowModal() != wxID_OK)
			return;

		Gui->ShowInfo (LangString["SHARE_ENCRYPTED_HINT"]);
#ifdef TC_MACOSX
		MacOSXShareEncrypted (parent, in.GetPath());
#else
		wxFileName name (in.GetPath());
		wxLaunchDefaultApplication (name.GetPath());
#endif
	}
}
