/**
 * C interop header for Float128 precision validation
 */

#ifndef FLOAT128_BENCHMARK_H
#define FLOAT128_BENCHMARK_H

#ifdef __cplusplus
extern "C" {
#endif

// Double-double addition: result = a + b
void compute_double_double_sum(double a_hi, double a_lo, double b_hi, double b_lo,
                                double *result_hi, double *result_lo);

// Double-double multiplication: result = a * b
void compute_double_double_mul(double a_hi, double a_lo, double b_hi, double b_lo,
                                double *result_hi, double *result_lo);

// Convert double-double to double
double compute_double_double_to_double(double hi, double lo);

#ifdef __cplusplus
}
#endif

#endif // FLOAT128_BENCHMARK_H
