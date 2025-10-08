/**
 * float128_ieee754_validator.c - Validate true IEEE-754 binary128 implementation
 *
 * This program uses GCC's __float128 to generate bit-exact test vectors
 * for validating Kotlin's Float128Math implementation.
 *
 * Compile: gcc -std=c11 -o float128_ieee754_validator float128_ieee754_validator.c -lm -lquadmath
 * Run: ./float128_ieee754_validator
 */

#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <quadmath.h>

// Print __float128 as hex bit string
void print_float128_bits(const char *label, __float128 value) {
    // __float128 is 128 bits = 16 bytes
    unsigned char bytes[16];
    memcpy(bytes, &value, 16);
    
    printf("%s:\n", label);
    printf("  Bits: ");
    
    // Print as hex (big-endian for readability)
    for (int i = 15; i >= 0; i--) {
        printf("%02X", bytes[i]);
        if (i > 0 && i % 2 == 0) printf("_");
    }
    printf("\n");
    
    // Print as binary for sign, exp, mantissa breakdown
    printf("  Binary: ");
    for (int i = 15; i >= 0; i--) {
        for (int j = 7; j >= 0; j--) {
            printf("%d", (bytes[i] >> j) & 1);
        }
        if (i == 15) printf(" ");  // After sign bit
        if (i == 13) printf(" ");  // After exponent
        if (i > 0 && i % 2 == 0) printf(" ");
    }
    printf("\n");
    
    // Print value
    char buf[128];
    quadmath_snprintf(buf, sizeof(buf), "%.36Qe", value);
    printf("  Value: %s\n", buf);
}

// Test basic values
void test_basic_values() {
    printf("=== IEEE-754 Binary128 Basic Values ===\n\n");
    
    __float128 zero = 0.0Q;
    __float128 one = 1.0Q;
    __float128 minus_one = -1.0Q;
    __float128 two = 2.0Q;
    __float128 half = 0.5Q;
    
    print_float128_bits("Zero", zero);
    printf("\n");
    print_float128_bits("One", one);
    printf("\n");
    print_float128_bits("Minus One", minus_one);
    printf("\n");
    print_float128_bits("Two", two);
    printf("\n");
    print_float128_bits("Half (0.5)", half);
    printf("\n");
}

// Test special values
void test_special_values() {
    printf("\n=== Special Values ===\n\n");
    
    __float128 nan = nanq("");
    __float128 inf = 1.0Q / 0.0Q;
    __float128 neg_inf = -1.0Q / 0.0Q;
    
    print_float128_bits("NaN", nan);
    printf("\n");
    print_float128_bits("+Infinity", inf);
    printf("\n");
    print_float128_bits("-Infinity", neg_inf);
    printf("\n");
}

// Test arithmetic
void test_arithmetic() {
    printf("\n=== Arithmetic Operations ===\n\n");
    
    __float128 a = 1.5Q;
    __float128 b = 2.25Q;
    
    print_float128_bits("a = 1.5", a);
    printf("\n");
    print_float128_bits("b = 2.25", b);
    printf("\n");
    
    __float128 sum = a + b;
    __float128 diff = a - b;
    __float128 product = a * b;
    __float128 quotient = a / b;
    
    print_float128_bits("a + b", sum);
    printf("\n");
    print_float128_bits("a - b", diff);
    printf("\n");
    print_float128_bits("a * b", product);
    printf("\n");
    print_float128_bits("a / b", quotient);
    printf("\n");
}

// Test precision edge cases
void test_precision() {
    printf("\n=== Precision Tests ===\n\n");
    
    // 1/3 - classic rounding test
    __float128 third = 1.0Q / 3.0Q;
    print_float128_bits("1/3", third);
    printf("\n");
    
    // Very small number
    __float128 tiny = 1e-100Q;
    print_float128_bits("1e-100", tiny);
    printf("\n");
    
    // Very large number
    __float128 huge = 1e100Q;
    print_float128_bits("1e100", huge);
    printf("\n");
    
    // Pi to full precision
    __float128 pi = 3.1415926535897932384626433832795028841971693993751Q;
    print_float128_bits("Pi (36 digits)", pi);
    printf("\n");
    
    // Euler's number
    __float128 e = 2.7182818284590452353602874713526624977572470937000Q;
    print_float128_bits("E (36 digits)", e);
    printf("\n");
}

// Test conversions
void test_conversions() {
    printf("\n=== Conversion Tests ===\n\n");
    
    // Double -> Float128
    double d = 1.234567890123456;
    __float128 f128 = (__float128)d;
    
    printf("Double: %.17e\n", d);
    print_float128_bits("As Float128", f128);
    printf("\n");
    
    // Float128 -> Double (may lose precision)
    __float128 f128_val = 1.234567890123456789012345678901234Q;
    double d_back = (double)f128_val;
    
    print_float128_bits("Float128 (34 digits)", f128_val);
    printf("Back to Double: %.17e\n", d_back);
    printf("\n");
}

int main() {
    printf("========================================\n");
    printf("IEEE-754 Binary128 Validation Test Vectors\n");
    printf("========================================\n\n");
    
    test_basic_values();
    test_special_values();
    test_arithmetic();
    test_precision();
    test_conversions();
    
    printf("\n========================================\n");
    printf("All test vectors generated successfully\n");
    printf("========================================\n");
    
    return 0;
}
