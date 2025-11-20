/**
 * Benchmark: CFloat128 (double-double) vs native long double precision
 * 
 * This C program acts as a precision judge for Kotlin's CFloat128 implementation.
 * It compares:
 * 1. Native long double (80-bit on x86, 64-bit on most ARM/Mac)
 * 2. Double-double arithmetic (106-bit mantissa precision)
 * 3. Simple double precision (53-bit mantissa)
 * 
 * Test cases include operations that expose precision differences:
 * - 1/3 representation
 * - Sum of many small numbers (catastrophic cancellation)
 * - Product of near-unity values
 * - Compensated summation (Kahan)
 */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <float.h>
#include <time.h>

// ============================================================================
// Double-Double Arithmetic (QD library algorithms)
// ============================================================================

typedef struct {
    double hi;
    double lo;
} dd_real;

// Error-free transformation: split a double for exact multiplication
#define SPLIT_CONST 134217729.0  // 2^27 + 1

static inline void split_double(double a, double *hi, double *lo) {
    double temp = SPLIT_CONST * a;
    *hi = temp - (temp - a);
    *lo = a - *hi;
}

// Error-free transformation: two-sum
static inline dd_real two_sum(double a, double b) {
    double s = a + b;
    double v = s - a;
    double err = (a - (s - v)) + (b - v);
    dd_real result = {s, err};
    return result;
}

// Error-free transformation: quick-two-sum (assumes |a| >= |b|)
static inline dd_real quick_two_sum(double a, double b) {
    double s = a + b;
    double err = b - (s - a);
    dd_real result = {s, err};
    return result;
}

// Error-free transformation: two-product
static inline dd_real two_prod(double a, double b) {
    double p = a * b;
    double a_hi, a_lo, b_hi, b_lo;
    split_double(a, &a_hi, &a_lo);
    split_double(b, &b_hi, &b_lo);
    double err = ((a_hi * b_hi - p) + a_hi * b_lo + a_lo * b_hi) + a_lo * b_lo;
    dd_real result = {p, err};
    return result;
}

// Double-double addition
static inline dd_real dd_add(dd_real a, dd_real b) {
    dd_real s = two_sum(a.hi, b.hi);
    double lo_sum = a.lo + b.lo + s.lo;
    return quick_two_sum(s.hi, lo_sum);
}

// Double-double addition with scalar
static inline dd_real dd_add_d(dd_real a, double b) {
    dd_real s = two_sum(a.hi, b);
    double lo_sum = a.lo + s.lo;
    return quick_two_sum(s.hi, lo_sum);
}

// Double-double multiplication with scalar
static inline dd_real dd_mul_d(dd_real a, double b) {
    dd_real p = two_prod(a.hi, b);
    double lo_term = a.lo * b + p.lo;
    return quick_two_sum(p.hi, lo_term);
}

// Double-double multiplication
static inline dd_real dd_mul(dd_real a, dd_real b) {
    dd_real p = two_prod(a.hi, b.hi);
    dd_real result = p;
    
    // Add products: a.hi * b.lo
    dd_real t1 = two_prod(a.hi, b.lo);
    result = dd_add(result, t1);
    
    // Add products: a.lo * b.hi
    dd_real t2 = two_prod(a.lo, b.hi);
    result = dd_add(result, t2);
    
    // Add products: a.lo * b.lo (can skip for performance, adds ~4 bits)
    dd_real t3 = two_prod(a.lo, b.lo);
    result = dd_add(result, t3);
    
    return result;
}

// Convert to double
static inline double dd_to_double(dd_real a) {
    return a.hi + a.lo;
}

// Create from double
static inline dd_real dd_from_double(double x) {
    dd_real result = {x, 0.0};
    return result;
}

// Print with high precision
static void dd_print(const char* label, dd_real x) {
    printf("%s: hi=%.17e, lo=%.17e, combined=%.17e\n", 
           label, x.hi, x.lo, x.hi + x.lo);
}

// ============================================================================
// Test Cases
// ============================================================================

// Test 1: Represent 1/3 (infinite binary expansion)
void test_one_third() {
    printf("\n=== Test 1: Representing 1/3 ===\n");
    
    // Native long double
    long double ld_third = 1.0L / 3.0L;
    printf("long double:    %.40Lf\n", ld_third);
    
    // Simple double
    double d_third = 1.0 / 3.0;
    printf("double:         %.40f\n", d_third);
    
    // Double-double
    dd_real one = dd_from_double(1.0);
    dd_real three = dd_from_double(3.0);
    
    // Compute 1/3 using double-double
    // We'll use iterative refinement: x_{n+1} = x_n + x_n(1 - 3*x_n)
    dd_real dd_third = dd_from_double(1.0 / 3.0);  // Initial guess
    for (int i = 0; i < 3; i++) {
        dd_real temp = dd_mul_d(dd_third, 3.0);
        dd_real error = dd_add_d(one, -dd_to_double(temp));
        dd_real correction = dd_mul(dd_third, error);
        dd_third = dd_add(dd_third, correction);
    }
    
    printf("double-double:  %.40f\n", dd_to_double(dd_third));
    dd_print("  ", dd_third);
    
    // Error analysis
    long double ld_error = fabsl(ld_third - (1.0L/3.0L));
    double d_error = fabs(d_third - (1.0/3.0));
    double dd_error = fabs(dd_to_double(dd_third) - (1.0/3.0));
    
    printf("\nRelative errors:\n");
    printf("  long double:    %.2Le\n", ld_error / (1.0L/3.0L));
    printf("  double:         %.2e\n", d_error / (1.0/3.0));
    printf("  double-double:  %.2e\n", dd_error / (1.0/3.0));
}

// Test 2: Catastrophic cancellation
void test_cancellation() {
    printf("\n=== Test 2: Catastrophic Cancellation ===\n");
    printf("Computing: (1 + 1e-16) - 1\n");
    
    // Native long double
    long double ld_result = (1.0L + 1e-16L) - 1.0L;
    printf("long double:    %.20Le\n", ld_result);
    
    // Simple double
    double d_result = (1.0 + 1e-16) - 1.0;
    printf("double:         %.20e\n", d_result);
    
    // Double-double (should preserve precision)
    dd_real dd_one = dd_from_double(1.0);
    dd_real dd_eps = dd_from_double(1e-16);
    dd_real dd_sum = dd_add(dd_one, dd_eps);
    dd_real dd_result = dd_add_d(dd_sum, -1.0);
    printf("double-double:  %.20e\n", dd_to_double(dd_result));
    dd_print("  ", dd_result);
}

// Test 3: Sum of many small numbers
void test_summation() {
    printf("\n=== Test 3: Summation of Many Small Numbers ===\n");
    printf("Computing: sum of 1e-8 repeated 100,000,000 times\n");
    printf("Expected: 1.0\n");
    
    const int n = 100000000;
    const double small = 1e-8;
    
    // Simple double summation
    clock_t start = clock();
    double d_sum = 0.0;
    for (int i = 0; i < n; i++) {
        d_sum += small;
    }
    clock_t end = clock();
    double d_time = (double)(end - start) / CLOCKS_PER_SEC;
    
    // Kahan summation (compensated)
    start = clock();
    double k_sum = 0.0;
    double k_c = 0.0;
    for (int i = 0; i < n; i++) {
        double y = small - k_c;
        double t = k_sum + y;
        k_c = (t - k_sum) - y;
        k_sum = t;
    }
    end = clock();
    double k_time = (double)(end - start) / CLOCKS_PER_SEC;
    
    // Double-double summation
    start = clock();
    dd_real dd_sum = dd_from_double(0.0);
    dd_real dd_small = dd_from_double(small);
    for (int i = 0; i < n; i++) {
        dd_sum = dd_add(dd_sum, dd_small);
    }
    end = clock();
    double dd_time = (double)(end - start) / CLOCKS_PER_SEC;
    
    printf("\nResults:\n");
    printf("  double:            %.15f (error: %.2e, time: %.3fs)\n", 
           d_sum, fabs(d_sum - 1.0), d_time);
    printf("  Kahan (double):    %.15f (error: %.2e, time: %.3fs)\n", 
           k_sum, fabs(k_sum - 1.0), k_time);
    printf("  double-double:     %.15f (error: %.2e, time: %.3fs)\n", 
           dd_to_double(dd_sum), fabs(dd_to_double(dd_sum) - 1.0), dd_time);
    
    printf("\nPerformance:\n");
    printf("  Kahan overhead:         %.1fx slower than simple double\n", k_time / d_time);
    printf("  double-double overhead: %.1fx slower than simple double\n", dd_time / d_time);
}

// Test 4: Product of many near-unity values
void test_product() {
    printf("\n=== Test 4: Product of Near-Unity Values ===\n");
    printf("Computing: product of (1 + 1e-8) repeated 100,000 times\n");
    
    const int n = 100000;
    const double near_one = 1.0 + 1e-8;
    
    // Simple double
    double d_prod = 1.0;
    for (int i = 0; i < n; i++) {
        d_prod *= near_one;
    }
    
    // Double-double
    dd_real dd_prod = dd_from_double(1.0);
    dd_real dd_near_one = dd_from_double(near_one);
    for (int i = 0; i < n; i++) {
        dd_prod = dd_mul(dd_prod, dd_near_one);
    }
    
    // Expected: (1 + 1e-8)^100000 ≈ e^(100000 * 1e-8) ≈ e^0.001 ≈ 1.001000500167
    double expected = exp(n * 1e-8);
    
    printf("\nResults:\n");
    printf("  Expected:       %.15f\n", expected);
    printf("  double:         %.15f (error: %.2e)\n", d_prod, fabs(d_prod - expected));
    printf("  double-double:  %.15f (error: %.2e)\n", 
           dd_to_double(dd_prod), fabs(dd_to_double(dd_prod) - expected));
}

// ============================================================================
// Kotlin Interop Functions
// ============================================================================

// Functions that Kotlin can call via C interop to get reference values

void compute_double_double_sum(double a_hi, double a_lo, double b_hi, double b_lo,
                                double *result_hi, double *result_lo) {
    dd_real a = {a_hi, a_lo};
    dd_real b = {b_hi, b_lo};
    dd_real result = dd_add(a, b);
    *result_hi = result.hi;
    *result_lo = result.lo;
}

void compute_double_double_mul(double a_hi, double a_lo, double b_hi, double b_lo,
                                double *result_hi, double *result_lo) {
    dd_real a = {a_hi, a_lo};
    dd_real b = {b_hi, b_lo};
    dd_real result = dd_mul(a, b);
    *result_hi = result.hi;
    *result_lo = result.lo;
}

double compute_double_double_to_double(double hi, double lo) {
    dd_real x = {hi, lo};
    return dd_to_double(x);
}

// ============================================================================
// Main
// ============================================================================

int main() {
    printf("========================================\n");
    printf("Float128 Precision Benchmark\n");
    printf("========================================\n");
    
    printf("\nPlatform Info:\n");
    printf("  sizeof(long double): %zu bytes\n", sizeof(long double));
    printf("  LDBL_MANT_DIG:       %d bits\n", LDBL_MANT_DIG);
    printf("  sizeof(double):      %zu bytes\n", sizeof(double));
    printf("  DBL_MANT_DIG:        %d bits\n", DBL_MANT_DIG);
    printf("  Double-double:       ~106 bits mantissa (2 * 53)\n");
    
    test_one_third();
    test_cancellation();
    test_summation();
    test_product();
    
    printf("\n========================================\n");
    printf("Conclusion:\n");
    printf("========================================\n");
    printf("Double-double provides consistent ~106-bit precision\n");
    printf("across all platforms, unlike long double which varies.\n");
    printf("\nThis makes it ideal for:\n");
    printf("  - Cross-platform reproducibility\n");
    printf("  - High-precision accumulation\n");
    printf("  - Compensated arithmetic\n");
    printf("========================================\n");
    
    return 0;
}
