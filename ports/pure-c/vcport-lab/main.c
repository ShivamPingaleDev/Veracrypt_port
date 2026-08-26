/*
 Personal C lab. Uses libvc_mobile like the phones. Not a product.
*/

#include "otg.h"
#include "preview.h"
#include "vc_mobile.h"

#include <stdio.h>
#include <string.h>

static int cmd_preview (const char *name)
{
	printf ("%s\n", vc_c_preview_kind_name (vc_c_preview_kind (name)));
	return 0;
}

static int cmd_otg (const char *path)
{
	if (!vc_c_otg_is_path (path))
	{
		printf ("not-otg\n");
		return 0;
	}
	if (vc_c_otg_ready (path))
		printf ("bound\n");
	else
		printf ("not-bound\n");
	return 0;
}

static int cmd_open_list (const char *path, const char *password)
{
	VcOpenOptions o;
	VcDirEntry entries[32];
	int err = 0;
	int n;
	int i;
	VcVolume *vol;

	memset (&o, 0, sizeof (o));
	o.path = path;
	o.password = password;
	o.password_len = strlen (password);
	o.pim = 1;
	vc_runtime_start ();
	vol = vc_open (&o, &err);
	if (!vol)
	{
		fprintf (stderr, "open failed: %d\n", err);
		return 1;
	}
	n = vc_list_dir (vol, "/", entries, 32);
	if (n < 0)
		n = 0;
	for (i = 0; i < n; i++)
		printf ("%s%s\n", entries[i].name, entries[i].is_dir ? "/" : "");
	vc_close (vol);
	return 0;
}

int main (int argc, char **argv)
{
	if (argc >= 3 && strcmp (argv[1], "preview-kind") == 0)
		return cmd_preview (argv[2]);
	if (argc >= 3 && strcmp (argv[1], "otg-path") == 0)
		return cmd_otg (argv[2]);
	if (argc >= 4 && strcmp (argv[1], "list") == 0)
		return cmd_open_list (argv[2], argv[3]);
	fprintf (stderr,
		"vcport-c lab (same libvc_mobile as 0.3.8 phones)\n"
		"  preview-kind NAME\n"
		"  otg-path PATH\n"
		"  list CONTAINER PASSWORD\n"
		"No /proc/self/fd. No whole-disk USB. See OTG Master on experimental-otg-master.\n");
	return 2;
}
