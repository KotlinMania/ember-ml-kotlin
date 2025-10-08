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
    printf("%-3s %-3s %-7s %-7s %-5s %-7s | %-10s %-10s %-8s | %-6s %-6s\n",
           "P","C","N/1k","cap","pkt","spin","pps(M)","Gbps","dur(s)","mism","errs");
}

static void save_best_config(const char *desc, double pps, double gbps)
{
    FILE *f = fopen("optimal_config.json", "w");
    if (!f) return;
    
    // Parse the description to extract parameters
    int P=4, C=4, N=100000, cap=4096, pkt=64, spin=1024;
    sscanf(desc, "P=%d C=%d N=%d cap=%d pkt=%d spin=%d", &P, &C, &N, &cap, &pkt, &spin);
    
    fprintf(f, "{\n");
    fprintf(f, "  \"comment\": \"Auto-generated optimal configuration\",\n");
    fprintf(f, "  \"producers\": %d,\n", P);
    fprintf(f, "  \"consumers\": %d,\n", C);
    fprintf(f, "  \"per_producer\": %d,\n", N);
    fprintf(f, "  \"capacity\": %d,\n", cap);
    fprintf(f, "  \"msg_size\": 4,\n");
    fprintf(f, "  \"pkt_bytes\": %d,\n", pkt);
    fprintf(f, "  \"spin_iters\": %d,\n", spin);
    fprintf(f, "  \"performance\": {\n");
    fprintf(f, "    \"pps\": %.0f,\n", pps);
    fprintf(f, "    \"mpps\": %.3f,\n", pps/1e6);
    fprintf(f, "    \"gbps\": %.3f\n", gbps);
    fprintf(f, "  }\n");
    fprintf(f, "}\n");
    fclose(f);
}

int main(int argc, char **argv)
{
    // Extended test matrix with spin optimization focus
    int Ps[]    = {2,4,6,8,12,16};
    int Cs[]    = {2,4,6,8};
    int Nps[]   = {50000,100000,200000,500000};
    int Caps[]  = {1024,2048,4096,8192,16384};
    int Pkts[]  = {64, 256, 1500};
    int Spins[] = {0, 256, 512, 1024, 2048, 4096, 8192};

    if (argc > 1 && strcmp(argv[1], "--quick") == 0) {
        // Quick test for development
        int tmpP[] = {2,4}; memcpy(Ps, tmpP, sizeof(tmpP)); Ps[2] = 0;
        int tmpC[] = {2,4}; memcpy(Cs, tmpC, sizeof(tmpC)); Cs[2] = 0; 
        int tmpN[] = {50000,100000}; memcpy(Nps, tmpN, sizeof(tmpN)); Nps[2] = 0;
        int tmpCap[] = {1024,4096}; memcpy(Caps, tmpCap, sizeof(tmpCap)); Caps[2] = 0;
        int tmpSpin[] = {512,2048}; memcpy(Spins, tmpSpin, sizeof(tmpSpin)); Spins[2] = 0;
    }

    long rss_cap_mb = -1; 
    const char *m = getenv("OPT_MAX_RSS_MB"); 
    if (m && *m) rss_cap_mb = atol(m);

    double best_pps = 0.0; 
    char best_desc[256]="";
    double best_gbps = 0.0;
    
    printf("kcoro Performance Optimization Suite\n");
    printf("====================================\n\n");
    hdr();

    int tests_run = 0, tests_passed = 0;

    for (size_t si=0; si<sizeof(Spins)/sizeof(Spins[0]) && Spins[si] != 0; ++si) {
        for (size_t pi=0; pi<sizeof(Ps)/sizeof(Ps[0]) && Ps[pi] != 0; ++pi) {
            int P = Ps[pi];
            for (size_t ci=0; ci<sizeof(Cs)/sizeof(Cs[0]) && Cs[ci] != 0; ++ci) {
                int C = Cs[ci];
                for (size_t ni=0; ni<sizeof(Nps)/sizeof(Nps[0]) && Nps[ni] != 0; ++ni) {
                    int N = Nps[ni];
                    for (size_t ki=0; ki<sizeof(Caps)/sizeof(Caps[0]) && Caps[ki] != 0; ++ki) {
                        int Cap = Caps[ki];
                        for (size_t ti=0; ti<sizeof(Pkts)/sizeof(Pkts[0]) && Pkts[ti] != 0; ++ti) {
                            int pkt = Pkts[ti];
                            int spin = Spins[si];

                            struct rusage ru0, ru1; 
                            getrusage(RUSAGE_SELF, &ru0);
                            long rss0 = rss_bytes_proc();

                            kc_bench_config_t cfg = { 
                                .producers=P, 
                                .consumers=C, 
                                .per_producer=N, 
                                .capacity=(size_t)Cap, 
                                .msg_size=sizeof(int), 
                                .pkt_bytes=pkt,
                                .spin_iters=spin,
                                .pin_threads=0
                            };
                            
                            kc_bench_result_t out; 
                            int rc = kc_bench_run(&cfg, &out);
                            tests_run++;
                            
                            getrusage(RUSAGE_SELF, &ru1);
                            long rss1 = rss_bytes_proc();

                            double u = (ru1.ru_utime.tv_sec - ru0.ru_utime.tv_sec) + (ru1.ru_utime.tv_usec - ru0.ru_utime.tv_usec)/1e6;
                            double s = (ru1.ru_stime.tv_sec - ru0.ru_stime.tv_sec) + (ru1.ru_stime.tv_usec - ru0.ru_stime.tv_usec)/1e6;
                            double cpu_pct = out.duration_s > 0 ? 100.0 * (u + s) / out.duration_s : 0.0;
                            long maxrss_kb = ru1.ru_maxrss;
                            long drss = (rss1>0 && rss0>0) ? (rss1 - rss0) : -1;

                            if (rc == 0) {
                                printf("%-3d %-3d %-7d %-7d %-5d %-7d | %-10.3f %-10.3f %-8.3f | %-6d %-6d  [cpu=%.1f%% maxrss=%ldKB rssΔ=%ld]\n",
                                       P, C, N/1000, Cap, pkt, spin, out.pps/1e6, out.gbps, out.duration_s,
                                       out.per_prod_mismatch, out.runtime_errors, cpu_pct, maxrss_kb, drss);
                                       
                                if (out.per_prod_mismatch==0 && out.runtime_errors==0) {
                                    tests_passed++;
                                    if (rss_cap_mb < 0 || (maxrss_kb/1024) <= rss_cap_mb) {
                                        if (out.pps > best_pps) { 
                                            best_pps = out.pps; 
                                            best_gbps = out.gbps;
                                            snprintf(best_desc, sizeof(best_desc), 
                                                    "P=%d C=%d N=%d cap=%d pkt=%d spin=%d", 
                                                    P,C,N,Cap,pkt,spin); 
                                        }
                                    }
                                }
                            } else {
                                printf("%-3d %-3d %-7d %-7d %-5d %-7d | ERROR rc=%d\n", P,C,N/1000,Cap,pkt,spin,rc);
                            }
                            fflush(stdout);
                        }
                    }
                }
            }
        }
    }

    printf("\n=== OPTIMIZATION RESULTS ===\n");
    printf("Tests run: %d, Tests passed: %d (%.1f%%)\n", tests_run, tests_passed, 
           tests_run > 0 ? 100.0 * tests_passed / tests_run : 0.0);
    
    if (best_pps > 0) {
        printf("\n🏆 OPTIMAL CONFIGURATION FOUND:\n");
        printf("   %s\n", best_desc);
        printf("   Performance: %.3f Mpps (%.3f Gbps)\n", best_pps/1e6, best_gbps);
        printf("   Bandwidth @64B: %.3f Gbps\n", (best_pps*64*8)/1e9);
        
        // Save optimal config for TUI monitor
        save_best_config(best_desc, best_pps, best_gbps);
        printf("   Saved to: optimal_config.json\n");
        
        // Generate TUI monitor command
        int P=4, C=4, N=100000, cap=4096, pkt=64, spin=1024;
        sscanf(best_desc, "P=%d C=%d N=%d cap=%d pkt=%d spin=%d", &P, &C, &N, &cap, &pkt, &spin);
        printf("\n📊 To monitor this configuration with TUI:\n");
        printf("   cd ../lab/tui/chanmon && make && KCORO_BENCH_SPIN=%d ./build/kcoro_chanmon\n", spin);
        printf("   Or edit default.json with these parameters and run ./build/kcoro_chanmon\n");
        
    } else {
        printf("\n❌ No valid configuration met constraints.\n");
        printf("   Try relaxing memory limits or adjusting test parameters.\n");
    }
    
    return 0;
}
