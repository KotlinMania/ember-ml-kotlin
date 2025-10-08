/**
 * float64_spotcheck.c - C reference implementation for Float64 validation
 * 
 * Compile: gcc -std=c11 -o float64_spotcheck float64_spotcheck.c -lm
 * Run: ./float64_spotcheck
 * 
 * Generates reference test vectors for validating Kotlin Float64Math.
 */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <string.h>

int main(void) {
    printf("=== Float64 C Reference Test Vectors ===\n\n");
    
    // Test arithmetic operations
    struct { double a, b; } tests[] = {
        {1.0, 1.0}, {2.5, 3.5}, {5.0, 3.0}, 
        {2.0, 3.0}, {6.0, 2.0}, {-1.5, 2.5},
        {1e100, 1e100}, {1e-100, 1e-100}
    };
    
    for (size_t i = 0; i < sizeof(tests) / sizeof(tests[0]); i++) {
        double a = tests[i].a;
        double b = tests[i].b;
        
        uint64_t a_bits, b_bits;
        memcpy(&a_bits, &a, 8);
        memcpy(&b_bits, &b, 8);
        
        double add = a + b;
        double sub = a - b;
        double mul = a * b;
        double div = a / b;
        
        uint64_t add_bits, sub_bits, mul_bits, div_bits;
        memcpy(&add_bits, &add, 8);
        memcpy(&sub_bits, &sub, 8);
        memcpy(&mul_bits, &mul, 8);
        memcpy(&div_bits, &div, 8);
        
        printf("Test %zu: a=%.3e (0x%016llX) b=%.3e (0x%016llX)\n", 
            i, a, (unsigned long long)a_bits, b, (unsigned long long)b_bits);
        printf("  add=0x%016llX sub=0x%016llX mul=0x%016llX div=0x%016llX\n",
            (unsigned long long)add_bits, (unsigned long long)sub_bits,
            (unsigned long long)mul_bits, (unsigned long long)div_bits);
    }
    
    // Test Float32 <-> Float64 conversions
    printf("\n=== Float32 <-> Float64 Conversions ===\n\n");
    
    float f32_values[] = {1.0f, 2.5f, -3.14159f, 1e20f, 1e-20f};
    
    for (size_t i = 0; i < sizeof(f32_values) / sizeof(f32_values[0]); i++) {
        float f = f32_values[i];
        double d = (double)f;  // widening
        float f2 = (float)d;   // narrowing
        
        uint32_t f_bits, f2_bits;
        uint64_t d_bits;
        memcpy(&f_bits, &f, 4);
        memcpy(&d_bits, &d, 8);
        memcpy(&f2_bits, &f2, 4);
        
        printf("f32=0x%08X -> f64=0x%016llX -> f32=0x%08X\n",
            f_bits, (unsigned long long)d_bits, f2_bits);
    }
    
    return 0;
}
