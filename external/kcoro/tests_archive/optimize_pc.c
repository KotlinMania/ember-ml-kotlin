// SPDX-License-Identifier: BSD-3-Clause
#define _POSIX_C_SOURCE 200112L
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <sys/resource.h>
#include <time.h>

#include "../include/kcoro_bench.h"

static inline double now_sec(void){ struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts); return ts.tv_sec + ts.tv_nsec/1e9; }

static long rss_bytes_proc(void)
{
    FILE *f = fopen("/proc/self/statm", "r");
    if (!f) return -1;
    unsigned long size_pages=0, rss_pages=0;
    if (fscanf(f, "%lu %lu", &size_pages, &rss_pages) != 2) { fclose(f); return -1; }
    fclose(f);
    long ps = sysconf(_SC_PAGESIZE);
    return (rss_pages > 0 && ps > 0) ? (long)rss_pages * ps : -1;
}

static void hdr(void)
{
    printf("%-3s %-3s %-7s %-7s %-5s | %-10s %-10s %-8s | %-6s %-6s\n",
           "P","C","N/1k","cap","pkt","pps(M)","Gbps","dur(s)","mism","errs");
}

int main(int argc, char **argv)
{
    int Ps[]    = {2,4,6,8};
    int Cs[]    = {2,4,6};
    int Nps[]   = {100000,200000,500000};
    int Caps[]  = {1024,4096,8192,16384};
    int Pkts[]  = {64, 1500};
    int Spins[] = {256,1024,4096,8192};

    if (argc > 1 && strcmp(argv[1], "--small") == 0) {
        int tmpP[] = {2,4}; Ps[0]=tmpP[0]; Ps[1]=tmpP[1]; Ps[2]=Ps[3]=0;
    }

    long rss_cap_mb = -1; const char *m = getenv("OPT_MAX_RSS_MB"); if (m && *m) rss_cap_mb = atol(m);

    double best_pps = 0.0; char best_desc[128]="";
    hdr();

    for (size_t si=0; si<sizeof(Spins)/sizeof(Spins[0]); ++si) {
        if (Spins[si] == 0) continue;
        char spinbuf[32]; snprintf(spinbuf, sizeof(spinbuf), "%d", Spins[si]);
        setenv("KCORO_BENCH_SPIN", spinbuf, 1);

        for (size_t pi=0; pi<sizeof(Ps)/sizeof(Ps[0]); ++pi) {
            int P = Ps[pi]; if (P <= 0) continue;
            for (size_t ci=0; ci<sizeof(Cs)/sizeof(Cs[0]); ++ci) {
                int C = Cs[ci]; if (C <= 0) continue;
                for (size_t ni=0; ni<sizeof(Nps)/sizeof(Nps[0]); ++ni) {
                    int N = Nps[ni];
                    for (size_t ki=0; ki<sizeof(Caps)/sizeof(Caps[0]); ++ki) {
                        int Cap = Caps[ki];
                        for (size_t ti=0; ti<sizeof(Pkts)/sizeof(Pkts[0]); ++ti) {
                            int pkt = Pkts[ti];

                            struct rusage ru0, ru1; getrusage(RUSAGE_SELF, &ru0);
                            long rss0 = rss_bytes_proc();

                            kc_bench_config_t cfg = { .producers=P, .consumers=C, .per_producer=N, .capacity=(size_t)Cap, .msg_size=sizeof(int), .pkt_bytes=pkt };
                            kc_bench_result_t out; int rc = kc_bench_run(&cfg, &out);
                            getrusage(RUSAGE_SELF, &ru1);
                            long rss1 = rss_bytes_proc();

                            double u = (ru1.ru_utime.tv_sec - ru0.ru_utime.tv_sec) + (ru1.ru_utime.tv_usec - ru0.ru_utime.tv_usec)/1e6;
                            double s = (ru1.ru_stime.tv_sec - ru0.ru_stime.tv_sec) + (ru1.ru_stime.tv_usec - ru0.ru_stime.tv_usec)/1e6;
                            double cpu_pct = out.duration_s > 0 ? 100.0 * (u + s) / out.duration_s : 0.0;
                            long maxrss_kb = ru1.ru_maxrss;
                            long drss = (rss1>0 && rss0>0) ? (rss1 - rss0) : -1;

                            if (rc == 0) {
                                printf("%-3d %-3d %-7d %-7d %-5d | %-10.3f %-10.3f %-8.3f | %-6d %-6d  [spin=%d cpu=%.1f%% maxrss=%ldKB rssΔ=%ld]\n",
                                       P, C, N/1000, Cap, pkt, out.pps/1e6, out.gbps, out.duration_s,
                                       out.per_prod_mismatch, out.runtime_errors, Spins[si], cpu_pct, maxrss_kb, drss);
                                if (out.per_prod_mismatch==0 && out.runtime_errors==0) {
                                    if (rss_cap_mb < 0 || (maxrss_kb/1024) <= rss_cap_mb) {
                                        if (out.pps > best_pps) { best_pps = out.pps; snprintf(best_desc, sizeof(best_desc), "P=%d C=%d N=%d cap=%d pkt=%d spin=%d", P,C,N,Cap,pkt,Spins[si]); }
                                    }
                                }
                            } else {
                                printf("%-3d %-3d %-7d %-7d %-5d | ERROR rc=%d\n", P,C,N/1000,Cap,pkt,rc);
                            }
                            fflush(stdout);
                        }
                    }
                }
            }
        }
    }

    if (best_pps > 0) {
        printf("\n[opt] Best by PPS: %s -> %.3f Mpps (%.3f Gbps @64B)\n", best_desc, best_pps/1e6, (best_pps*64*8)/1e9);
    } else {
        printf("\n[opt] No valid configuration met constraints.\n");
    }
    return 0;
}
