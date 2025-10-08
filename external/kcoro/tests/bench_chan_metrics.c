// SPDX-License-Identifier: BSD-3-Clause
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <stdbool.h>

#include "kcoro_bench.h"
#include "kcoro.h"

static double monotonic_sec(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double)ts.tv_sec + (double)ts.tv_nsec * 1e-9;
}

static void usage(const char *prog) {
    fprintf(stderr,
            "Usage: %s [-d duration_sec] [-i interval_sec] [-p producers] "
            "[-c consumers] [-n packets_per_cycle] [-s packet_size_bytes] [-o output.jsonl]\n",
            prog);
}

int main(int argc, char **argv) {
    double duration = 5.0;
    double interval = 0.5;
    kc_bench_params_t params = {
        .kind = KC_BUFFERED,
        .capacity = 1024,
        .producers = 4,
        .consumers = 4,
        .packets_per_cycle = 512,
        .spin_iters = 0,
        .packet_size = 1024,
        .pointer_mode = 1,
    };

    int opt;
    const char *out_path = NULL;
    while ((opt = getopt(argc, argv, "d:i:p:c:n:s:o:h")) != -1) {
        switch (opt) {
        case 'd': duration = atof(optarg); break;
        case 'i': interval = atof(optarg); break;
        case 'p': params.producers = atoi(optarg); break;
        case 'c': params.consumers = atoi(optarg); break;
        case 'n': params.packets_per_cycle = atoi(optarg); break;
        case 's': params.packet_size = strtoul(optarg, NULL, 10); params.pointer_mode = 1; break;
        case 'o': out_path = optarg; break;
        case 'h': default: usage(argv[0]); return 1;
        }
    }
    if (duration <= 0.0) duration = 5.0;
    if (interval <= 0.0) interval = 0.5;

    kc_bench_handle_t *handle = NULL;
    kc_chan_t *chan = NULL;
    if (kc_bench_chan_start(&params, &handle, &chan) != 0) {
        fprintf(stderr, "kc_bench_chan_start failed\n");
        return 1;
    }

    FILE *out_file = NULL;
    if (out_path) {
        out_file = fopen(out_path, "w");
        if (!out_file) {
            perror("fopen output");
            kc_bench_chan_stop(handle);
            return 1;
        }
    }

    struct kc_chan_snapshot prev = {0}, curr = {0};
    kc_chan_snapshot(chan, &prev);

    double start = monotonic_sec();
    double end = start + duration;
    double total_packets = 0.0;
    double total_gbps = 0.0;
    unsigned long total_rv_matches = 0;
    unsigned long total_rv_cancels = 0;
    unsigned long total_rv_zdesc = 0;
    int samples = 0;

    while (1) {
        if (monotonic_sec() >= end) break;
        struct timespec req = {
            .tv_sec = (time_t)interval,
            .tv_nsec = (long)((interval - (time_t)interval) * 1e9)
        };
        nanosleep(&req, NULL);
        if (kc_chan_snapshot(chan, &curr) != 0) {
            fprintf(stderr, "kc_chan_snapshot failed\n");
            break;
        }
        struct kc_chan_rate_sample sample;
        kc_chan_compute_rate(&prev, &curr, &sample);
        double pps = sample.sends_per_sec;
        double gbps = (sample.bytes_sent_per_sec * 8.0) / 1e9;
        if (out_file) {
            fprintf(out_file,
                    "{\"interval_sec\":%.6f,\"pps\":%.3f,\"gbps\":%.6f,"
                    "\"delta_packets\":%lu,\"delta_bytes\":%lu,"
                    "\"delta_rv_matches\":%lu,\"delta_rv_cancels\":%lu,\"delta_rv_zdesc\":%lu}\n",
                    sample.interval_sec,
                    pps,
                    gbps,
                    sample.delta_sends,
                    sample.delta_bytes_sent,
                    sample.delta_rv_matches,
                    sample.delta_rv_cancels,
                    sample.delta_rv_zdesc_matches);
            fflush(out_file);
        } else {
            printf("interval %.3fs: packets/s %.2f, Gbps %.3f, rv_matches %lu, rv_cancels %lu, rv_zdesc %lu\n",
                   sample.interval_sec, pps, gbps,
                   sample.delta_rv_matches,
                   sample.delta_rv_cancels,
                   sample.delta_rv_zdesc_matches);
        }
        total_packets += pps;
        total_gbps += gbps;
        total_rv_matches += sample.delta_rv_matches;
        total_rv_cancels += sample.delta_rv_cancels;
        total_rv_zdesc += sample.delta_rv_zdesc_matches;
        samples++;
        prev = curr;
    }

    kc_bench_chan_stop(handle);
    if (out_file) fclose(out_file);

    if (samples > 0) {
        printf("AVERAGE packets/s %.2f, Gbps %.3f, rv_matches/sample %.2f, rv_cancels/sample %.2f, rv_zdesc/sample %.2f\n",
               total_packets / samples,
               total_gbps / samples,
               samples ? (double)total_rv_matches / samples : 0.0,
               samples ? (double)total_rv_cancels / samples : 0.0,
               samples ? (double)total_rv_zdesc / samples : 0.0);
    }

    return 0;
}
