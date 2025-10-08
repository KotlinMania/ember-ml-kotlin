// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <ctype.h>
#include <sys/stat.h>

#include "../../include/kcoro_config_runtime.h"

static struct kc_runtime_config g_cfg;
static int g_cfg_loaded = 0;
static int g_cfg_loading = 0; /* guard recursive */

static void kc_cfg_set_defaults(struct kc_runtime_config *c) {
    c->chan_metrics_emit_min_ops = 1024UL;
    c->chan_metrics_emit_min_ms = 50; /* ms */
    c->chan_metrics_auto_enable = 0;
    c->chan_metrics_pipe_capacity = 64;
}

#define KC_CFG_MAX_FILE_SIZE (1 << 20) /* 1MB */

static char* kc_read_file(const char *path, size_t *len_out) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    struct stat st;
    if (stat(path, &st) != 0) {
        fclose(f);
        return NULL;
    }
    if (st.st_size > KC_CFG_MAX_FILE_SIZE) { fclose(f); errno = EFBIG; return NULL; } /* 1MB cap */
    size_t sz = (size_t)st.st_size;
    char *buf = (char*)malloc(sz + 1);
    if (!buf) { fclose(f); return NULL; }
    size_t n = fread(buf, 1, sz, f); fclose(f);
    if (n != sz) { free(buf); return NULL; }
    buf[sz] = '\0';
    if (len_out) *len_out = sz;
    return buf;
}

static const char* skip_ws(const char *s) {
    while (*s && isspace((unsigned char)*s)) {
        ++s;
    }
    return s;
}
static int match_lit(const char **sp, const char *lit) {
    const char *s = *sp;
    while (*lit) {
        if (*s != *lit)
            return 0;
        ++s;
        ++lit;
    }
    *sp = s;
    return 1;
}
static int parse_number(const char **sp, unsigned long *out_ul, long *out_l) {
    const char *s = *sp;
    int neg = 0;
    if (*s == '-') {
        neg = 1;
        ++s;
    }
    if (!isdigit((unsigned char)*s))
        return 0;
    unsigned long v = 0;
    while (isdigit((unsigned char)*s)) {
        v = v * 10 + (*s - '0');
        ++s;
    }
    *sp = s;
    if (out_ul)
        *out_ul = neg ? (unsigned long)(-(long)v) : v;
    if (out_l)
        *out_l = neg ? -(long)v : (long)v;
    return 1;
}
static int parse_bool(const char **sp, int *out) {
    const char *s = *sp;
    if (match_lit(&s, "true")) {
        *out = 1;
        *sp = s;
        return 1;
    }
    if (match_lit(&s, "false")) {
        *out = 0;
        *sp = s;
        return 1;
    }
    return 0;
}
static int parse_string_key(const char **sp, char *out, size_t outsz) {
    const char *s = *sp;
    if (*s != '"')
        return 0;
    ++s;
    size_t i = 0;
    while (*s && *s != '"' && i + 1 < outsz) {
        out[i++] = *s++;
    }
    if (*s != '"')
        return 0;
    ++s;
    out[i] = '\0';
    *sp = s;
    return 1;
}

static void kc_cfg_parse(struct kc_runtime_config *c, const char *json) {
    const char *p = skip_ws(json);
    if (*p != '{')
        return;
    ++p;
    for (;;) {
        p = skip_ws(p);
        if (*p == '}') { ++p; break; }
        char key[64];
        if (!parse_string_key(&p, key, sizeof(key))) break;
        p = skip_ws(p);
        if (*p != ':') break;
        ++p;
        p = skip_ws(p);
        if (strcmp(key, "channel") == 0) {
            if (*p != '{') break;
            ++p; /* inside channel */
            for (;;) {
                p = skip_ws(p);
                if (*p == '}') { ++p; break; }
                char k2[64];
                if (!parse_string_key(&p, k2, sizeof(k2))) break;
                p = skip_ws(p);
                if (*p != ':') break;
                ++p;
                p = skip_ws(p);
                if (strcmp(k2, "metrics") == 0) {
                    if (*p != '{') break;
                    ++p;
                    for (;;) {
                        p = skip_ws(p);
                        if (*p == '}') { ++p; break; }
                        char k3[64];
                        if (!parse_string_key(&p, k3, sizeof(k3))) break;
                        p = skip_ws(p);
                        if (*p != ':') break;
                        ++p;
                        p = skip_ws(p);
                        if (strcmp(k3, "emit_min_ops") == 0) {
                            unsigned long v; if (!parse_number(&p, &v, NULL)) break; if (v >= 1) c->chan_metrics_emit_min_ops = v;
                        } else if (strcmp(k3, "emit_min_ms") == 0) {
                            long v; if (!parse_number(&p, NULL, &v)) break; if (v >= 0) c->chan_metrics_emit_min_ms = v;
                        } else if (strcmp(k3, "auto_enable") == 0) {
                            int b; if (!parse_bool(&p, &b)) break; c->chan_metrics_auto_enable = b;
                        } else if (strcmp(k3, "pipe_capacity") == 0) {
                            unsigned long v; if (!parse_number(&p, &v, NULL)) break; if (v >= 1) c->chan_metrics_pipe_capacity = (size_t)v;
                        } else {
                            /* skip unknown scalar */
                            if (*p == '"') {
                                char tmp[64]; if (!parse_string_key(&p, tmp, sizeof(tmp))) break;
                            } else {
                                unsigned long vx; long lx; if (!parse_number(&p, &vx, &lx)) { int b; if (!parse_bool(&p, &b)) break; }
                            }
                        }
                        p = skip_ws(p);
                        if (*p == ',') { ++p; continue; }
                        if (*p == '}') { ++p; break; }
                    }
                } else {
                    /* skip unknown object */
                    if (*p == '{') {
                        int depth = 1; ++p;
                        while (*p && depth) {
                            if (*p == '{') depth++; else if (*p == '}') depth--; ++p;
                        }
                    }
                }
                p = skip_ws(p);
                if (*p == ',') { ++p; continue; }
                if (*p == '}') { ++p; break; }
            }
        } else {
            /* skip unknown top-level value */
            if (*p == '{') {
                int depth = 1; ++p;
                while (*p && depth) {
                    if (*p == '{') depth++; else if (*p == '}') depth--; ++p;
                }
            }
        }
        p = skip_ws(p);
        if (*p == ',') { ++p; continue; }
        if (*p == '}') { ++p; break; }
    }
}

static int kc_runtime_config_load(const char *path) {
    struct kc_runtime_config tmp; kc_cfg_set_defaults(&tmp);
    const char *use_path = path;
    if (!use_path || !*use_path) {
        use_path = getenv("KCORO_CONFIG");
        if (!use_path || !*use_path) use_path = "kcoro_config.json";
    }
    size_t len=0; char *buf = kc_read_file(use_path, &len);
    if (!buf) { /* keep defaults if missing */ g_cfg = tmp; g_cfg_loaded = 1; return 0; }
    kc_cfg_parse(&tmp, buf);
    free(buf);
    g_cfg = tmp; g_cfg_loaded = 1; return 0;
}

int kc_runtime_config_init(const char *path) {
    if (g_cfg_loaded) return 0;
    if (g_cfg_loading) return -EALREADY;
    g_cfg_loading = 1;
    int rc = kc_runtime_config_load(path);
    g_cfg_loading = 0;
    return rc;
}

int kc_runtime_config_reload(const char *path) {
    g_cfg_loaded = 0; return kc_runtime_config_init(path);
}

const struct kc_runtime_config* kc_runtime_config_get(void) {
    if (!g_cfg_loaded) kc_runtime_config_init(NULL);
    return &g_cfg;
}
