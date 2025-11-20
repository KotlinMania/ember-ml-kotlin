/**
 * float16_spotcheck.c - C reference implementation for Float16 validation
 * 
 * Compile: gcc -std=c11 -o float16_spotcheck float16_spotcheck.c -lm
 * Run: ./float16_spotcheck
 * 
 * This generates reference test vectors that the Kotlin Float16Math
 * implementation should match exactly (bit-for-bit).
 */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <string.h>

typedef uint16_t float16_t;

// Convert float16 to float32
float f16_to_f32(float16_t h) {
    uint32_t sign = (h >> 15) & 0x1;
    uint32_t exp = (h >> 10) & 0x1F;
    uint32_t mant = h & 0x3FF;
    
    if (exp == 0x1F) {
        uint32_t f32_exp = 0xFF;
        uint32_t f32_mant = (mant != 0) ? (1 << 22) : 0;
        uint32_t bits = (sign << 31) | (f32_exp << 23) | f32_mant;
        float result;
        memcpy(&result, &bits, 4);
        return result;
    }
    
    if (exp == 0) {
        if (mant == 0) {
            uint32_t bits = sign << 31;
            float result;
            memcpy(&result, &bits, 4);
            return result;
        }
        uint32_t m = mant;
        int e = -14;
        while ((m & 0x400) == 0) {
            m <<= 1;
            e--;
        }
        m &= 0x3FF;
        uint32_t f32_exp = e + 127;
        uint32_t f32_mant = m << 13;
        uint32_t bits = (sign << 31) | (f32_exp << 23) | f32_mant;
        float result;
        memcpy(&result, &bits, 4);
        return result;
    }
    
    uint32_t f32_exp = exp - 15 + 127;
    uint32_t f32_mant = mant << 13;
    uint32_t bits = (sign << 31) | (f32_exp << 23) | f32_mant;
    float result;
    memcpy(&result, &bits, 4);
    return result;
}

// Convert float32 to float16 with rounding
float16_t f32_to_f16(float f) {
    uint32_t bits;
    memcpy(&bits, &f, 4);
    
    uint32_t sign = (bits >> 31) & 0x1;
    uint32_t exp = (bits >> 23) & 0xFF;
    uint32_t mant = bits & 0x7FFFFF;
    
    if (exp == 0xFF) {
        return (sign << 15) | 0x7C00 | ((mant != 0) ? 0x200 : 0);
    }
    
    if (exp == 0 && mant == 0) {
        return sign << 15;
    }
    
    int new_exp = (int)exp - 127 + 15;
    
    if (new_exp >= 31) {
        return (sign << 15) | 0x7C00;
    }
    
    if (new_exp <= 0) {
        return sign << 15;
    }
    
    uint32_t new_mant = mant >> 13;
    uint32_t round_bit = (mant >> 12) & 1;
    uint32_t sticky = mant & 0xFFF;
    
    if (round_bit && (sticky || (new_mant & 1))) {
        new_mant++;
        if (new_mant > 0x3FF) {
            new_mant = 0;
            new_exp++;
            if (new_exp >= 31) {
                return (sign << 15) | 0x7C00;
            }
        }
    }
    
    return (sign << 15) | (new_exp << 10) | new_mant;
}

int main(void) {
    printf("=== Float16 C Reference Test Vectors ===\n\n");
    
    // Test arithmetic operations
    struct { float a, b; } tests[] = {
        {1.0f, 1.0f}, {2.5f, 3.5f}, {5.0f, 3.0f}, 
        {2.0f, 3.0f}, {6.0f, 2.0f}, {-1.5f, 2.5f}
    };
    
    for (size_t i = 0; i < sizeof(tests) / sizeof(tests[0]); i++) {
        float16_t h_a = f32_to_f16(tests[i].a);
        float16_t h_b = f32_to_f16(tests[i].b);
        float a = f16_to_f32(h_a);
        float b = f16_to_f32(h_b);
        
        printf("Test %zu: a=%.3f (0x%04X) b=%.3f (0x%04X)\n", 
            i, tests[i].a, h_a, tests[i].b, h_b);
        printf("  add=0x%04X sub=0x%04X mul=0x%04X div=0x%04X\n",
            f32_to_f16(a + b), f32_to_f16(a - b),
            f32_to_f16(a * b), f32_to_f16(a / b));
    }
    
    return 0;
}
