package ai.solace.ember.tensor

import ai.solace.ember.dtype.DType
import ai.solace.ember.scalar.Scalar
import ai.solace.klang.fp.CFloat32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EmberTensorTest {
    
    @Test
    fun testScalarTensor() {
        val scalar = Scalar.Float32(CFloat32.fromFloat(5.0f))
        val tensor = EmberTensor.fromScalar(scalar)
        
        assertTrue(tensor.isScalar)
        assertEquals(0, tensor.ndim)
        assertEquals(1, tensor.size)
        assertEquals(5.0f, tensor.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun test1DTensorCreation() {
        val tensor = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f))
        
        assertEquals(1, tensor.ndim)
        assertEquals(3, tensor.size)
        assertEquals(intArrayOf(3).contentToString(), tensor.shape.contentToString())
        
        val data = tensor.toFloatArray()
        assertEquals(1.0f, data[0], 0.0001f)
        assertEquals(2.0f, data[1], 0.0001f)
        assertEquals(3.0f, data[2], 0.0001f)
    }
    
    @Test
    fun test2DTensorCreation() {
        val tensor = EmberTensor.fromList2D(listOf(
            listOf(1.0f, 2.0f),
            listOf(3.0f, 4.0f),
            listOf(5.0f, 6.0f)
        ))
        
        assertEquals(2, tensor.ndim)
        assertEquals(6, tensor.size)
        assertEquals(intArrayOf(3, 2).contentToString(), tensor.shape.contentToString())
    }
    
    @Test
    fun testZerosCreation() {
        val tensor = EmberTensor.zeros(intArrayOf(2, 3))
        
        assertEquals(2, tensor.ndim)
        assertEquals(6, tensor.size)
        val data = tensor.toFloatArray()
        assertTrue(data.all { it == 0f })
    }
    
    @Test
    fun testOnesCreation() {
        val tensor = EmberTensor.ones(intArrayOf(4))
        
        assertEquals(1, tensor.ndim)
        assertEquals(4, tensor.size)
        val data = tensor.toFloatArray()
        assertTrue(data.all { it == 1f })
    }
    
    @Test
    fun testFullCreation() {
        val tensor = EmberTensor.full(intArrayOf(2, 2), 3.14f)
        
        assertEquals(4, tensor.size)
        val data = tensor.toFloatArray()
        assertTrue(data.all { it == 3.14f })
    }
    
    @Test
    fun testEyeCreation() {
        val tensor = EmberTensor.eye(3)
        
        assertEquals(intArrayOf(3, 3).contentToString(), tensor.shape.contentToString())
        val data = tensor.toFloatArray()
        
        // Check identity matrix
        assertEquals(1f, data[0], 0.0001f)
        assertEquals(0f, data[1], 0.0001f)
        assertEquals(0f, data[2], 0.0001f)
        assertEquals(0f, data[3], 0.0001f)
        assertEquals(1f, data[4], 0.0001f)
        assertEquals(0f, data[5], 0.0001f)
        assertEquals(0f, data[6], 0.0001f)
        assertEquals(0f, data[7], 0.0001f)
        assertEquals(1f, data[8], 0.0001f)
    }
    
    @Test
    fun testArangeCreation() {
        val tensor = EmberTensor.arange(0, 10, 2)
        
        assertEquals(1, tensor.ndim)
        assertEquals(5, tensor.size)
        val data = tensor.toFloatArray()
        assertEquals(floatArrayOf(0f, 2f, 4f, 6f, 8f).contentToString(), data.contentToString())
    }
    
    @Test
    fun testLinspaceCreation() {
        val tensor = EmberTensor.linspace(0.0, 4.0, 5)
        
        assertEquals(5, tensor.size)
        val data = tensor.toFloatArray()
        assertEquals(0.0f, data[0], 0.0001f)
        assertEquals(1.0f, data[1], 0.0001f)
        assertEquals(2.0f, data[2], 0.0001f)
        assertEquals(3.0f, data[3], 0.0001f)
        assertEquals(4.0f, data[4], 0.0001f)
    }
    
    @Test
    fun testAddition() {
        val a = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f))
        val b = EmberTensor.fromList(listOf(4.0f, 5.0f, 6.0f))
        val c = a + b
        
        val data = c.toFloatArray()
        assertEquals(5.0f, data[0], 0.0001f)
        assertEquals(7.0f, data[1], 0.0001f)
        assertEquals(9.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testSubtraction() {
        val a = EmberTensor.fromList(listOf(10.0f, 20.0f, 30.0f))
        val b = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f))
        val c = a - b
        
        val data = c.toFloatArray()
        assertEquals(9.0f, data[0], 0.0001f)
        assertEquals(18.0f, data[1], 0.0001f)
        assertEquals(27.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testMultiplication() {
        val a = EmberTensor.fromList(listOf(2.0f, 3.0f, 4.0f))
        val b = EmberTensor.fromList(listOf(5.0f, 6.0f, 7.0f))
        val c = a * b
        
        val data = c.toFloatArray()
        assertEquals(10.0f, data[0], 0.0001f)
        assertEquals(18.0f, data[1], 0.0001f)
        assertEquals(28.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testDivision() {
        val a = EmberTensor.fromList(listOf(10.0f, 20.0f, 30.0f))
        val b = EmberTensor.fromList(listOf(2.0f, 4.0f, 5.0f))
        val c = a / b
        
        val data = c.toFloatArray()
        assertEquals(5.0f, data[0], 0.0001f)
        assertEquals(5.0f, data[1], 0.0001f)
        assertEquals(6.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testScalarOperations() {
        val x = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f))
        
        val add = x + 10f
        assertEquals(11.0f, add.toFloatArray()[0], 0.0001f)
        
        val sub = x - 1f
        assertEquals(0.0f, sub.toFloatArray()[0], 0.0001f)
        
        val mul = x * 2f
        assertEquals(2.0f, mul.toFloatArray()[0], 0.0001f)
        
        val div = x / 2f
        assertEquals(0.5f, div.toFloatArray()[0], 0.0001f)
    }
    
    @Test
    fun testUnaryMinus() {
        val x = EmberTensor.fromList(listOf(1.0f, -2.0f, 3.0f))
        val y = -x
        
        val data = y.toFloatArray()
        assertEquals(-1.0f, data[0], 0.0001f)
        assertEquals(2.0f, data[1], 0.0001f)
        assertEquals(-3.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testTrigFunctions() {
        val x = EmberTensor.fromList(listOf(0.0f, kotlin.math.PI.toFloat() / 2))
        
        val sinX = x.sin()
        assertEquals(0.0f, sinX.toFloatArray()[0], 0.0001f)
        assertEquals(1.0f, sinX.toFloatArray()[1], 0.0001f)
        
        val cosX = x.cos()
        assertEquals(1.0f, cosX.toFloatArray()[0], 0.0001f)
        assertEquals(0.0f, cosX.toFloatArray()[1], 0.001f)
    }
    
    @Test
    fun testExpLog() {
        val x = EmberTensor.fromList(listOf(0.0f, 1.0f, 2.0f))
        
        val expX = x.exp()
        assertEquals(1.0f, expX.toFloatArray()[0], 0.0001f)
        assertEquals(2.7182f, expX.toFloatArray()[1], 0.001f)
        
        val logExp = expX.log()
        assertEquals(0.0f, logExp.toFloatArray()[0], 0.0001f)
        assertEquals(1.0f, logExp.toFloatArray()[1], 0.001f)
        assertEquals(2.0f, logExp.toFloatArray()[2], 0.001f)
    }
    
    @Test
    fun testSqrtAndSquare() {
        val x = EmberTensor.fromList(listOf(4.0f, 9.0f, 16.0f))
        
        val sqrtX = x.sqrt()
        assertEquals(2.0f, sqrtX.toFloatArray()[0], 0.0001f)
        assertEquals(3.0f, sqrtX.toFloatArray()[1], 0.0001f)
        assertEquals(4.0f, sqrtX.toFloatArray()[2], 0.0001f)
        
        val squared = sqrtX.square()
        assertEquals(4.0f, squared.toFloatArray()[0], 0.0001f)
        assertEquals(9.0f, squared.toFloatArray()[1], 0.0001f)
        assertEquals(16.0f, squared.toFloatArray()[2], 0.0001f)
    }
    
    @Test
    fun testPower() {
        val x = EmberTensor.fromList(listOf(2.0f, 3.0f, 4.0f))
        val y = x.power(2)
        
        val data = y.toFloatArray()
        assertEquals(4.0f, data[0], 0.0001f)
        assertEquals(9.0f, data[1], 0.0001f)
        assertEquals(16.0f, data[2], 0.0001f)
    }
    
    @Test
    fun testSum() {
        val x = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f, 4.0f))
        val sum = x.sum()
        
        assertTrue(sum.isScalar)
        assertEquals(10.0f, sum.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun testMean() {
        val x = EmberTensor.fromList(listOf(2.0f, 4.0f, 6.0f, 8.0f))
        val mean = x.mean()
        
        assertEquals(5.0f, mean.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun testMaxMin() {
        val x = EmberTensor.fromList(listOf(3.0f, 7.0f, 2.0f, 9.0f, 1.0f))
        
        val max = x.max()
        assertEquals(9.0f, max.toScalar().toFloat(), 0.0001f)
        
        val min = x.min()
        assertEquals(1.0f, min.toScalar().toFloat(), 0.0001f)
    }
    
    @Test
    fun testReshape() {
        val x = EmberTensor.arange(0, 12)
        val y = x.reshape(intArrayOf(3, 4))
        
        assertEquals(2, y.ndim)
        assertEquals(intArrayOf(3, 4).contentToString(), y.shape.contentToString())
        assertEquals(12, y.size)
    }
    
    @Test
    fun testReshapeError() {
        val x = EmberTensor.arange(0, 12)
        assertFailsWith<IllegalArgumentException> {
            x.reshape(intArrayOf(3, 3)) // Wrong size
        }
    }
    
    @Test
    fun testTranspose2D() {
        val x = EmberTensor.fromList2D(listOf(
            listOf(1.0f, 2.0f, 3.0f),
            listOf(4.0f, 5.0f, 6.0f)
        ))
        val y = x.transpose()
        
        assertEquals(intArrayOf(3, 2).contentToString(), y.shape.contentToString())
        val data = y.toFloatArray()
        
        // Original: [[1, 2, 3], [4, 5, 6]]
        // Transposed: [[1, 4], [2, 5], [3, 6]]
        assertEquals(1.0f, data[0], 0.0001f)
        assertEquals(4.0f, data[1], 0.0001f)
        assertEquals(2.0f, data[2], 0.0001f)
        assertEquals(5.0f, data[3], 0.0001f)
        assertEquals(3.0f, data[4], 0.0001f)
        assertEquals(6.0f, data[5], 0.0001f)
    }
    
    @Test
    fun testMatmul() {
        val a = EmberTensor.fromList2D(listOf(
            listOf(1.0f, 2.0f, 3.0f),
            listOf(4.0f, 5.0f, 6.0f)
        ))
        val b = EmberTensor.fromList2D(listOf(
            listOf(7.0f, 8.0f),
            listOf(9.0f, 10.0f),
            listOf(11.0f, 12.0f)
        ))
        val c = a.matmul(b)
        
        assertEquals(intArrayOf(2, 2).contentToString(), c.shape.contentToString())
        val data = c.toFloatArray()
        
        // [1*7 + 2*9 + 3*11, 1*8 + 2*10 + 3*12] = [58, 64]
        // [4*7 + 5*9 + 6*11, 4*8 + 5*10 + 6*12] = [139, 154]
        assertEquals(58.0f, data[0], 0.0001f)
        assertEquals(64.0f, data[1], 0.0001f)
        assertEquals(139.0f, data[2], 0.0001f)
        assertEquals(154.0f, data[3], 0.0001f)
    }
    
    @Test
    fun testMatmulSquare() {
        val a = EmberTensor.eye(3)
        val b = EmberTensor.fromList2D(listOf(
            listOf(1.0f, 2.0f, 3.0f),
            listOf(4.0f, 5.0f, 6.0f),
            listOf(7.0f, 8.0f, 9.0f)
        ))
        val c = a.matmul(b)
        
        // Identity matrix times any matrix = that matrix
        val bData = b.toFloatArray()
        val cData = c.toFloatArray()
        assertEquals(bData.contentToString(), cData.contentToString())
    }
    
    @Test
    fun testIncompatibleShapes() {
        val a = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f))
        val b = EmberTensor.fromList(listOf(1.0f, 2.0f))
        
        assertFailsWith<IllegalArgumentException> {
            a + b
        }
    }
    
    @Test
    fun testToString() {
        val x = EmberTensor.fromList(listOf(1.0f, 2.0f, 3.0f))
        val str = x.toString()
        
        assertTrue(str.contains("EmberTensor"))
        assertTrue(str.contains("shape"))
        assertTrue(str.contains("dtype"))
    }
}
