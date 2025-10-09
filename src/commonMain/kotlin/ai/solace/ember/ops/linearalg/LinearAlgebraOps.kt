package ai.solace.ember.ops.linearalg

import ai.solace.ember.tensor.common.EmberTensor

/**
 * Linear algebra operations interface.
 *
 * This interface defines the abstract interface for linear algebra operations
 * that can be implemented by different backends.
 */
interface LinearAlgebraOps {
    
    /**
     * Solve a linear matrix equation or system of linear scalar equations.
     * 
     * @param a Coefficient matrix
     * @param b Ordinate or "dependent variable" values
     * @return Solution to the system a x = b
     */
    fun solve(a: EmberTensor, b: EmberTensor): EmberTensor
    
    /**
     * Compute the multiplicative inverse of a matrix.
     * 
     * @param a Input matrix
     * @return Multiplicative inverse of a
     */
    fun inv(a: EmberTensor): EmberTensor
    
    /**
     * Singular Value Decomposition.
     * 
     * @param a Input matrix
     * @param fullMatrices Whether to compute full or reduced SVD
     * @return Triple of (U, S, Vh) matrices
     */
    fun svd(a: EmberTensor, fullMatrices: Boolean = true): Triple<EmberTensor, EmberTensor, EmberTensor>
    
    /**
     * Compute the eigenvalues and right eigenvectors of a square array.
     * 
     * @param a Input matrix
     * @return Pair of (eigenvalues, eigenvectors)
     */
    fun eig(a: EmberTensor): Pair<EmberTensor, EmberTensor>
    
    /**
     * Compute the eigenvalues and eigenvectors of a real symmetric or complex Hermitian matrix.
     * 
     * @param a Input matrix
     * @param eigvecs Whether to compute eigenvectors in addition to eigenvalues
     * @return If eigvecs is true, pair of (eigenvalues, eigenvectors), otherwise just eigenvalues
     */
    fun eigh(a: EmberTensor, eigvecs: Boolean = true): Pair<EmberTensor, EmberTensor?>
    
    /**
     * Compute the eigenvalues of a general matrix.
     * 
     * @param a Input matrix
     * @return Eigenvalues of a
     */
    fun eigvals(a: EmberTensor): EmberTensor
    
    /**
     * Compute the determinant of an array.
     * 
     * @param a Input matrix
     * @return Determinant of a
     */
    fun det(a: EmberTensor): EmberTensor
    
    /**
     * Matrix or vector norm.
     * 
     * @param x Input array
     * @param ord Order of the norm
     * @param axis Axis along which to compute the norm
     * @param keepDims Whether to keep the reduced dimensions
     * @return Norm of x
     */
    fun norm(x: EmberTensor, ord: String? = null, axis: IntArray? = null, keepDims: Boolean = false): EmberTensor
    
    /**
     * Compute the QR factorization of a matrix.
     * 
     * @param a Input matrix
     * @param mode Determines what to return ('reduced', 'complete', 'r', 'raw')
     * @return QR factorization based on mode
     */
    fun qr(a: EmberTensor, mode: String = "reduced"): Pair<EmberTensor, EmberTensor>
    
    /**
     * Compute the Cholesky factorization of a positive-definite matrix.
     * 
     * @param a Input matrix
     * @param lower Whether to compute the lower or upper triangular Cholesky factor
     * @return Cholesky factorization of a
     */
    fun cholesky(a: EmberTensor, lower: Boolean = true): EmberTensor
    
    /**
     * Return the least-squares solution to a linear matrix equation.
     * 
     * @param a Coefficient matrix
     * @param b Ordinate or "dependent variable" values
     * @param rcond Cut-off ratio for small singular values
     * @return Least-squares solution
     */
    fun lstsq(a: EmberTensor, b: EmberTensor, rcond: Double? = null): EmberTensor
    
    /**
     * Extract a diagonal or construct a diagonal array.
     * 
     * @param v Input array
     * @param k Diagonal offset
     * @return Diagonal array
     */
    fun diag(v: EmberTensor, k: Int = 0): EmberTensor
    
    /**
     * Return specified diagonals.
     * 
     * @param a Input array
     * @param offset Offset of the diagonal from the main diagonal
     * @param axis1 Axis to be used as the first axis of the 2-D sub-arrays
     * @param axis2 Axis to be used as the second axis of the 2-D sub-arrays
     * @return Array of diagonals
     */
    fun diagonal(a: EmberTensor, offset: Int = 0, axis1: Int = 0, axis2: Int = 1): EmberTensor
    
    /**
     * Generate a random orthogonal matrix.
     * 
     * @param n The number of rows and columns in the output
     * @return Random orthogonal matrix
     */
    fun orthogonal(n: Int): EmberTensor
}