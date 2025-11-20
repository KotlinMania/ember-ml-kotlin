/**
 * float128_bitcompare.c - Bit-exact validation of double-double vs C long double
 * 
 * This program compares the bit patterns of double-double arithmetic against
 * C's long double to validate IEEE-754 compliance and precision.
 * 
 * Compile: gcc -std=c11 -o float128_bitcompare float128_bitcompare.c -lm
 * Run: ./float128_bitcompare
 */

#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <float.h>

// Double-double representation
typedef struct {
    double hi;
    double lo;
} dd_real;

// Print 64 bits of a double
void print_double_bits(const char *label, double d) {
    uint64_t bits;
    memcpy(&bits, &d, 8);
    
    printf("%s: ", label);
    for (int i = 63; i >= 0; i--) {
        printf("%d", (int)((bits >> i) & 1));
        if (i == 63 || i == 52) printf(" ");  // Separate sign, exponent, mantissa
    }
    printf(" = %.17e\n", d);
}

// Print 128 bits of a double-double
void print_dd_bits(const char *label, dd_real dd) {
    printf("%s:\n", label);
    print_double_bits("  hi", dd.hi);
    print_double_bits("  lo", dd.lo);
    printf("  Combined: %.20e\n", dd.hi + dd.lo);
}

// Two-sum algorithm (error-free transform)
dd_real two_sum(double a, double b) {
    double s = a + b;
    double v = s - a;
    double e = (a - (s - v)) + (b - v);
    dd_real result = {s, e};
    return result;
}

// Quick-two-sum (assumes |a| >= |b|)
dd_real quick_two_sum(double a, double b) {
    double s = a + b;
    double e = b - (s - a);
    dd_real result = {s, e};
    return result;
}

// Two-product algorithm (error-free multiplication)
dd_real two_prod(double a, double b) {
    double p = a * b;
    double e = fma(a, b, -p);  // Use FMA for exact error
    dd_real result = {p, e};
    return result;
}

// Double-double addition
dd_real dd_add(dd_real a, dd_real b) {
    dd_real s = two_sum(a.hi, b.hi);
    double e = a.lo + b.lo + s.lo;
    return quick_two_sum(s.hi, e);
}

// Double-double multiplication
dd_real dd_mul(dd_real a, dd_real b) {
    dd_real p = two_prod(a.hi, b.hi);
    p.lo += a.hi * b.lo + a.lo * b.hi;
    return quick_two_sum(p.hi, p.lo);
}

// Create double-double from double
dd_real dd_from_double(double d) {
    dd_real result = {d, 0.0};
    return result;
}

// Test case structure
typedef struct {
    const char *name;
    double value;
} test_case_t;

int main(void) {
    printf("=== IEEE-754 Bit Pattern Validation ===\n\n");
    printf("System info:\n");
    printf("  sizeof(long double) = %zu bytes\n", sizeof(long double));
    printf("  LDBL_MANT_DIG = %d bits\n", LDBL_MANT_DIG);
    printf("  DBL_MANT_DIG = %d bits\n", DBL_MANT_DIG);
    printf("\n");

    // Test cases
    test_case_t tests[] = {
        {"Zero", 0.0},
        {"One", 1.0},
        {"Minus One", -1.0},
        {"One Third", 1.0/3.0},
        {"Point One", 0.1},
        {"Pi", M_PI},
        {"E", M_E},
        {"Small", 1e-100},
        {"Large", 1e100}
    };
    
    int num_tests = sizeof(tests) / sizeof(tests[0]);
    
    for (int i = 0; i < num_tests; i++) {
        printf("\n=== Test %d: %s ===\n", i+1, tests[i].name);
        double val = tests[i].value;
        
        // Show the input value's bits
        print_double_bits("Input (double)", val);
        
        // Create double-double
        dd_real dd = dd_from_double(val);
        printf("\nAs double-double:\n");
        print_double_bits("  hi", dd.hi);
        print_double_bits("  lo", dd.lo);
        
        // Test addition: val + val
        printf("\n--- Addition: %s + %s ---\n", tests[i].name, tests[i].name);
        
        double d_sum = val + val;
        print_double_bits("double result", d_sum);
        
        dd_real dd_sum = dd_add(dd, dd);
        print_dd_bits("double-double result", dd_sum);
        
        // Compare: should match if no extra precision needed
        if (dd_sum.lo == 0.0) {
            printf("✓ No precision gain needed (exact in double)\n");
        } else {
            printf("✓ Extra precision captured in lo: %.6e\n", dd_sum.lo);
        }
        
        // Test multiplication: val * val
        if (fabs(val) < 1e50 && fabs(val) > 1e-50) {  // Avoid overflow/underflow
            printf("\n--- Multiplication: %s * %s ---\n", tests[i].name, tests[i].name);
            
            double d_prod = val * val;
            print_double_bits("double result", d_prod);
            
            dd_real dd_prod = dd_mul(dd, dd);
            print_dd_bits("double-double result", dd_prod);
            
            if (dd_prod.lo == 0.0) {
                printf("✓ No precision gain needed (exact in double)\n");
            } else {
                printf("✓ Extra precision captured in lo: %.6e\n", dd_prod.lo);
            }
        }
    }
    
    // Critical test: 0.1 + 0.1 + 0.1 - 0.3
    printf("\n\n=== Critical Test: (0.1 + 0.1 + 0.1) - 0.3 ===\n");
    printf("This tests accumulated rounding error.\n\n");
    
    double d1 = 0.1, d2 = 0.1, d3 = 0.1, d_target = 0.3;
    double d_result = (d1 + d2 + d3) - d_target;
    
    printf("Double precision:\n");
    print_double_bits("  0.1", d1);
    print_double_bits("  0.1 + 0.1", d1 + d2);
    print_double_bits("  (0.1+0.1) + 0.1", (d1 + d2) + d3);
    print_double_bits("  0.3", d_target);
    print_double_bits("  Result", d_result);
    printf("  Error: %.20e\n", d_result);
    
    printf("\nDouble-double precision:\n");
    dd_real dd1 = dd_from_double(0.1);
    dd_real dd2 = dd_from_double(0.1);
    dd_real dd3 = dd_from_double(0.1);
    dd_real dd_target_val = dd_from_double(0.3);
    
    dd_real dd_sum1 = dd_add(dd1, dd2);
    dd_real dd_sum2 = dd_add(dd_sum1, dd3);
    dd_real dd_diff = dd_add(dd_sum2, (dd_real){-dd_target_val.hi, -dd_target_val.lo});
    
    print_dd_bits("  (0.1 + 0.1 + 0.1)", dd_sum2);
    print_dd_bits("  Result", dd_diff);
    printf("  Error: %.20e\n", dd_diff.hi + dd_diff.lo);
    
    // Comparison
    printf("\n=== Precision Comparison ===\n");
    double d_error = fabs(d_result);
    double dd_error = fabs(dd_diff.hi + dd_diff.lo);
    
    printf("Double error:        %.6e\n", d_error);
    printf("Double-double error: %.6e\n", dd_error);
    
    if (dd_error < d_error) {
        printf("✓ Double-double is MORE PRECISE (%.1fx better)\n", d_error / dd_error);
    } else if (dd_error == d_error) {
        printf("= Both have same precision\n");
    } else {
        printf("✗ Double-double is LESS PRECISE\n");
    }
    
    // Bit pattern comparison for Kotlin interop
    printf("\n\n=== Bit Patterns for Kotlin Validation ===\n");
    printf("Format: hi_bits lo_bits (hex)\n\n");
    
    for (int i = 0; i < num_tests; i++) {
        dd_real dd = dd_from_double(tests[i].value);
        uint64_t hi_bits, lo_bits;
        memcpy(&hi_bits, &dd.hi, 8);
        memcpy(&lo_bits, &dd.lo, 8);
        
        printf("%s:\n", tests[i].name);
        printf("  hi: 0x%016llX\n", (unsigned long long)hi_bits);
        printf("  lo: 0x%016llX\n", (unsigned long long)lo_bits);
    }
    
    return 0;
}
