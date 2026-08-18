# Prefer ports/overlay/src/<rel> over ${VC_SRC}/<rel>.
# src/ stays official VeraCrypt. Phone hunks live in the overlay.
set(_VC_OVERLAY_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../overlay/src")

# Overlay headers (Common/SecurityToken.h, EMVToken.h) win over src/.
include_directories(BEFORE "${_VC_OVERLAY_ROOT}")

macro(vc_prefer_overlay listname)
	set(_vc_new "")
	foreach(_vc_f ${${listname}})
		file(RELATIVE_PATH _vc_rel "${VC_SRC}" "${_vc_f}")
		if(EXISTS "${_VC_OVERLAY_ROOT}/${_vc_rel}")
			list(APPEND _vc_new "${_VC_OVERLAY_ROOT}/${_vc_rel}")
		else()
			list(APPEND _vc_new "${_vc_f}")
		endif()
	endforeach()
	set(${listname} "${_vc_new}")
	unset(_vc_new)
	unset(_vc_f)
	unset(_vc_rel)
endmacro()

vc_prefer_overlay(VC_CRYPTO)
vc_prefer_overlay(VC_VOLUME)
vc_prefer_overlay(VC_PLATFORM)
vc_prefer_overlay(VC_COMMON)
