/*
 Derived from source code of TrueCrypt 7.1a, which is
 Copyright (c) 2008-2012 TrueCrypt Developers Association and which is governed
 by the TrueCrypt License 3.0.

 Modifications and additions to the original source code (contained in this file)
 and all other portions of this file are Copyright (c) 2013-2026 AM Crypto
 and are governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#include "System.h"
#include "Application.h"
#include "GraphicUserInterface.h"
#include "Xml.h"
#include "VolumeHistory.h"
#include <wx/ffile.h>
#include <vector>

namespace VeraCrypt
{
	VolumeHistory::VolumeHistory ()
	{
	}

	VolumeHistory::~VolumeHistory ()
	{
	}

	void VolumeHistory::Add (const VolumePath &newPath)
	{
		if (Gui->GetPreferences().SaveHistory)
		{
			ScopeLock lock (AccessMutex);

			VolumePathList::iterator iter = VolumePaths.begin();
			foreach (const VolumePath &path, VolumePaths)
			{
				if (newPath == path)
				{
					VolumePaths.erase (iter);
					break;
				}
				iter++;
			}

			VolumePaths.push_front (newPath);
			if (VolumePaths.size() > MaxSize)
				VolumePaths.pop_back();

			foreach (wxComboBox *comboBox, ConnectedComboBoxes)
			{
				UpdateComboBox (comboBox);
			}
		}
	}

	void VolumeHistory::Clear ()
	{
		VolumePaths.clear();
		foreach (wxComboBox *comboBox, ConnectedComboBoxes)
		{
			UpdateComboBox (comboBox);
		}

		Save();
	}

	bool VolumeHistory::ConfirmEnable ()
	{
		return Gui->AskYesNo (
			L"Saving history keeps volume paths in this window for this session only. History.xml is never written. A seized computer can still see this list. Enable for this session?",
			false, true);
	}

	static void WipeHistoryFile (const FilePath &path)
	{
		if (!path.IsFile())
			return;
		wxString native (wstring (path));
		wxFFile file (native, wxT("r+b"));
		if (file.IsOpened())
		{
			wxFileOffset len = file.Length();
			if (len > 0 && len <= 1024 * 1024)
			{
				vector <char> zeros ((size_t) len, 0);
				file.Seek (0);
				file.Write (&zeros[0], zeros.size());
				file.Flush();
			}
			file.Close();
		}
		wxRemoveFile (native);
	}

	void VolumeHistory::ConnectComboBox (wxComboBox *comboBox)
	{
		ScopeLock lock (AccessMutex);
		ConnectedComboBoxes.push_back (comboBox);

		UpdateComboBox (comboBox);
	}

	void VolumeHistory::DisconnectComboBox (wxComboBox *comboBox)
	{
		ScopeLock lock (AccessMutex);

		for (list<wxComboBox *>::iterator iter = ConnectedComboBoxes.begin(); iter != ConnectedComboBoxes.end(); ++iter)
		{
			if (comboBox == *iter)
			{
				ConnectedComboBoxes.erase (iter);
				break;
			}
		}
	}

	void VolumeHistory::Load ()
	{
		ScopeLock lock (AccessMutex);
		FilePath historyCfgPath = Application::GetConfigFilePath (GetFileName());
		WipeHistoryFile (historyCfgPath);
	}

	void VolumeHistory::Save ()
	{
		ScopeLock lock (AccessMutex);
		FilePath historyCfgPath = Application::GetConfigFilePath (GetFileName());
		WipeHistoryFile (historyCfgPath);
	}

	void VolumeHistory::UpdateComboBox (wxComboBox *comboBox)
	{
		wxString curValue = comboBox->GetValue();

		comboBox->Freeze();
		comboBox->Clear();

		foreach (const VolumePath &path, VolumePaths)
		{
			comboBox->Append (wstring (path));
		}

		comboBox->SetValue (curValue);
		comboBox->Thaw();
	}

	list <wxComboBox *> VolumeHistory::ConnectedComboBoxes;
	VolumePathList VolumeHistory::VolumePaths;
	Mutex VolumeHistory::AccessMutex;

}
