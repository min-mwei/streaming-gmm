package com.github.nestorsag.gradientgmm.optim.algorithms

import com.github.nestorsag.gradientgmm.components.AcceleratedGradientUtils

/**
  * Compute gradient ascent directions with momentum
  */

class MomentumGradientAscent extends Optimizer {

/**
  * exponential smoothing parameter. See ''Goh, "Why Momentum Really Works", Distill, 2017. http://doi.org/10.23915/distill.00006''
  */
	var beta = 0.5
	
	def setBeta(beta: Double): this.type = { 
		require(beta > 0 , "beta must be positive")
		this.beta = beta
		this
	}

	def getBeta: Double = { 
		this.beta
	}

	def direction[A](grad:A, utils: AcceleratedGradientUtils[A])(ops: ParameterOperations[A]): A = {
		
		if(!utils.momentum.isDefined){
			utils.initializeMomentum
		}
		
		utils.updateMomentum(
			ops.sum(
				ops.rescale(utils.momentum.get,beta),
				grad))

		utils.momentum.get
	}


}