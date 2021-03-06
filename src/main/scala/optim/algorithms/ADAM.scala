package com.github.gradientgmm.optim

import com.github.gradientgmm.components.AcceleratedGradientUtils

import breeze.numerics.sqrt

/**
  * Implementation of ADAM. See ''Adam: A Method for Stochastic Optimization. Kingma, Diederik P.; Ba, Jimmy, 2014''
  
  * Using it is NOT recommended; you should use SGD or its accelerated versions instead.
  */

@deprecated("ADAM can be unstable for GMM problems and should not be used", "gradientgmm >= 1.4")
class ADAM extends Optimizer {

/**
  * iteration counter
  */
	var t: Double = 1.0

/**
  * offset term to avoid division by zero in the main direction calculations
  */
	var eps = 1e-8

/**
  * Exponential smoothing parameter for the first moment estimator 
  */
	var beta1 = 0.5

/**
  * Exponential smoothing parameter for the second non-central moment estimator 
  */
	var beta2 = 0.1
	
	def setBeta1(beta1: Double): this.type = { 
		require(beta1 >= 0 & beta1 < 1, "beta1 must be in [0,1)")
		this.beta1 = beta1
		this
	}

	def getBeta1: Double = { 
		this.beta1
	}

	def setBeta2(beta2: Double): this.type = { 
		require(beta2 >= 0 & beta2 < 1 , "beta2 must be in [0,1)")
		this.beta2 = beta2
		this
	}

	def getBeta2: Double = { 
		this.beta2
	}

/**
  * Reset iterator counter
  */
	def reset: Unit = {
		t = 0.0
	}

	def setEps(x: Double): Unit = {
		require(x>=0,"x should be nonnegative")
		eps = x
	}

	def getEps: Double = eps


	def direction[A](grad:A, utils: AcceleratedGradientUtils[A])(ops: ParameterOperations[A]): A = {

		t += 1.0

		if(!utils.momentum.isDefined){
			utils.initializeMomentum
		}

		if(!utils.adamInfo.isDefined){
			utils.initializeAdamInfo
		}
		
		utils.setMomentum(
			ops.sum(
				ops.rescale(utils.momentum.get,beta1), 
				ops.rescale(grad,1.0-beta1)))

		utils.setAdamInfo(
			ops.sum(
				ops.rescale(utils.adamInfo.get,beta2), 
				ops.rescale(ops.ewProd(grad,grad),1.0-beta2)))

		val alpha_t = math.sqrt(1.0 - math.pow(beta2,t))/(1.0 - math.pow(beta1,t))
		val epsHat = eps * math.sqrt(1.0 - math.pow(beta2,t))

		ops.rescale(
			ops.ewDiv(
				utils.momentum.get,
				ops.sumScalar(ops.ewSqrt(utils.adamInfo.get),epsHat)),
			alpha_t)

	}

}