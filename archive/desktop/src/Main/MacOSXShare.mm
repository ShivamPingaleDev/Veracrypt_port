/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#ifdef TC_MACOSX

#import <AppKit/AppKit.h>
#include "Main/PortFileWrap.h"

namespace VeraCrypt
{
	void MacOSXShareEncrypted (wxWindow *parent, const wxString &path)
	{
		@autoreleasepool
		{
			NSString *nsPath = [NSString stringWithUTF8String: path.ToUTF8 ()];
			if (!nsPath)
				return;
			NSURL *url = [NSURL fileURLWithPath: nsPath];
			NSView *view = nil;
			if (parent && parent->GetHandle())
				view = (__bridge NSView *) parent->GetHandle();
			if (view)
			{
				NSSharingServicePicker *picker = [[NSSharingServicePicker alloc] initWithItems: @[url]];
				[picker showRelativeToRect: NSMakeRect (0, 0, 1, 1) ofView: view preferredEdge: NSRectEdgeMinY];
			}
			else
			{
				[[NSWorkspace sharedWorkspace] activateFileViewerSelectingURLs: @[url]];
			}
		}
	}
}

#endif
