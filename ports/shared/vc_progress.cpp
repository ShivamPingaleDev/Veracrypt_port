/*
 Copyright (c) 2026 Shivam Mangesh Pingale. All rights reserved.

 Governed by the Apache License 2.0 the full text of which is
 contained in the file License.txt included in VeraCrypt binary and source
 code distribution packages.
*/

#include "vc_mobile.h"

#include <atomic>
#include <cstring>
#include <mutex>

static std::atomic<int> gProgressPercent {-1};
static char gProgressPhase[96];
static std::mutex gProgressLock;

void vc_progress_reset (void)
{
	gProgressPercent.store (-1, std::memory_order_relaxed);
	std::lock_guard<std::mutex> lock (gProgressLock);
	gProgressPhase[0] = 0;
}

void vc_progress_set (int percent, const char *phase)
{
	if (percent > 100)
		percent = 100;
	if (percent < -1)
		percent = -1;
	if (phase)
	{
		std::lock_guard<std::mutex> lock (gProgressLock);
		if (std::strcmp (gProgressPhase, phase) != 0)
		{
			std::strncpy (gProgressPhase, phase, sizeof (gProgressPhase) - 1);
			gProgressPhase[sizeof (gProgressPhase) - 1] = 0;
		}
	}
	gProgressPercent.store (percent, std::memory_order_relaxed);
}

void vc_progress_tick (int percent, const char *phase)
{
	if (percent >= 0 && percent == vc_progress_percent ())
		return;
	vc_progress_set (percent, phase);
}

int vc_progress_percent (void)
{
	return gProgressPercent.load (std::memory_order_relaxed);
}

void vc_progress_phase (char *out, size_t out_size)
{
	if (!out || out_size == 0)
		return;
	std::lock_guard<std::mutex> lock (gProgressLock);
	std::strncpy (out, gProgressPhase, out_size - 1);
	out[out_size - 1] = 0;
}
