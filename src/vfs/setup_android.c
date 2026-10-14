/*
 * This software is licensed under the terms of the MIT License.
 * See COPYING for further information.
 * ---
 * Copyright (c) 2011-2026, Lukas Weber <laochailan@web.de>.
 * Copyright (c) 2012-2026, Andrei Alexeyev <akari@taisei-project.org>.
 */

#include "setup.h"
#include "version.h"

#include "memory/scratch.h"
#include "util/strbuf.h"
#include "log.h"
#include "public.h"

#include <SDL3/SDL.h>
#include <string.h>

static bool extract_apk_asset(const char *asset_name, const char *dest_dir) {
	char dest_path[PATH_MAX];
	snprintf(dest_path, sizeof(dest_path), "%s/%s", dest_dir, asset_name);

	SDL_IOStream *src = SDL_IOFromFile(asset_name, "rb");
	if(!src) {
		log_warn("Asset '%s' not found in APK: %s", asset_name, SDL_GetError());
		return false;
	}

	SDL_IOStream *dst = SDL_IOFromFile(dest_path, "wb");
	if(!dst) {
		log_error("Failed to open destination '%s': %s", dest_path, SDL_GetError());
		SDL_CloseIO(src);
		return false;
	}

	char buffer[65536];
	size_t bytes_read;
	bool success = true;

	while((bytes_read = SDL_ReadIO(src, buffer, sizeof(buffer))) > 0) {
		if(SDL_WriteIO(dst, buffer, bytes_read) != bytes_read) {
			log_error("Failed writing to '%s': %s", dest_path, SDL_GetError());
			success = false;
			break;
		}
	}

	SDL_CloseIO(src);
	SDL_CloseIO(dst);

	if(success) {
		log_info("Successfully extracted APK asset '%s' to '%s'", asset_name, dest_path);
	}
	return success;
}

void vfs_setup(CallChain next) {
	vfs_init();

	const char *pref_path = SDL_GetPrefPath("", "taisei");
	if(!pref_path) {
		log_fatal("SDL_GetPrefPath failed: %s", SDL_GetError());
	}

	StringBuffer buf = { acquire_scratch_arena() };
	strbuf_printf(&buf, "%sdata", pref_path);
	const char *data_path = strbuf_commit(&buf);

	strbuf_printf(&buf, "%sstorage", pref_path);
	const char *storage_path = strbuf_commit(&buf);

	strbuf_printf(&buf, "%scache", pref_path);
	const char *cache_path = strbuf_commit(&buf);

	SDL_CreateDirectory(data_path);
	SDL_CreateDirectory(storage_path);
	SDL_CreateDirectory(cache_path);
	SDL_free((void*)pref_path);

	char version_file[PATH_MAX];
	snprintf(version_file, sizeof(version_file), "%s/version.txt", data_path);

	bool need_extraction = true;
	SDL_IOStream *vf = SDL_IOFromFile(version_file, "rb");
	if(vf) {
		char installed_version[128] = {0};
		size_t r = SDL_ReadIO(vf, installed_version, sizeof(installed_version) - 1);
		SDL_CloseIO(vf);

		while(r > 0 && (installed_version[r-1] == '\r' || installed_version[r-1] == '\n' || installed_version[r-1] == ' ')) {
			installed_version[--r] = '\0';
		}

		if(!strcmp(installed_version, TAISEI_VERSION)) {
			need_extraction = false;
			log_info("Asset version matches (%s), skipping APK asset extraction", TAISEI_VERSION);
		} else {
			log_info("Asset version mismatch ('%s' vs current '%s'), re-extracting assets", installed_version, TAISEI_VERSION);
		}
	} else {
		log_info("No existing asset version file found at '%s', extracting APK assets", version_file);
	}

	if(need_extraction) {
		static const char *const assets_to_extract[] = {
			"00-taisei.zip",
			"01-es-shaders.zip",
			"10-l10n.zip",
			NULL
		};

		for(const char *const *p = assets_to_extract; *p; ++p) {
			extract_apk_asset(*p, data_path);
		}

		SDL_IOStream *vf_out = SDL_IOFromFile(version_file, "wb");
		if(vf_out) {
			SDL_WriteIO(vf_out, TAISEI_VERSION, strlen(TAISEI_VERSION));
			SDL_CloseIO(vf_out);
		} else {
			log_warn("Failed to write asset version file '%s': %s", version_file, SDL_GetError());
		}
	}

	VfsSetupFixedPaths paths = {
		.res_path = data_path,
		.storage_path = storage_path,
		.cache_path = cache_path,
	};
	vfs_setup_fixedpaths(&paths);

	release_scratch_arena(buf.arena);
	run_call_chain(&next, NULL);
}
