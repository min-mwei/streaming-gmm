package com.github.gradientgmm.optim.algorithms

import com.github.gradientgmm.optim.regularization.Regularizer
import com.github.gradientgmm.optim.weights.{WeightsTransformation,SoftmaxWeightTransformation}
import com.github.gradientgmm.components.{UpdatableGaussianComponent, AcceleratedGradientUtils}

import breeze.linalg.{DenseMatrix => BDM, DenseVector => BDV, Vector => BV, sum}


import org.apache.spark.mllib.linalg.{Vector => SV}
import org.apache.spark.rdd.RDD

/**
  * Optimizer interface that contains base hyperparameters and their getters and setters.
  * Optimization algorithms like Stochastic Gradient Ascent are implementations of this trait
  */
trait Optimizer extends Serializable{

/**
  * Optional regularization term
  */
	protected var regularizer: Option[Regularizer] = None

/**
  * Ascent procedure's learning rate
  */
	protected var learningRate: Double = 0.9

/**
  * Rate at which the learning rate is decreased as the number of iterations grow.
  * After {{{t}}} iterations the learning rate will be {{{shrinkageRate^t * learningRate}}}
  */
  protected var shrinkageRate: Double = 0.95

/**
  * Minimum allowed learning rate. Once this lower bound is reached the learning rate will not
  * shrink anymore
  */
	protected var minLearningRate: Double = 1e-2

/**
  * Minibatch size for each iteration in the ascent procedure. If {{{None}}}, it performs
  * full-batch optimization
  */
	protected var batchSize: Option[Int] = None

/**
  * Error tolerance in log-likelihood for the stopping criteria
  */
	protected var convergenceTol: Double = 1e-6

/**
  * Maximum number of iterations allowed
  */
	protected var maxIter: Int = 100

/**
  * Calculates the mapping from and to the weights' Simplex (see [[https://en.wikipedia.org/wiki/Simplex]]) and the transformation's gradient
  */
  private[gradientgmm] var weightsOptimizer: WeightsTransformation = new SoftmaxWeightTransformation()


 /**
  * Shrink {learningRate} by {shrinkageRate}
  *
  */
	def updateLearningRate: Unit = {
		learningRate = math.max(shrinkageRate*learningRate,minLearningRate)
	}

/**
  * Use the {fromSimplex} method from [[com.github.gradientgmm.optim.weights.WeightsTransformation]]
  *
  * @param weights mixture weights
  */
	def fromSimplex(weights: BDV[Double]): BDV[Double] = {
		weightsOptimizer.fromSimplex(weights)
	}

/**
  * Use the {toSimplex} method from [[com.github.gradientgmm.optim.weights.WeightsTransformation]]
  *
  * @param real vector
  * @return valid mixture weight vector
  */
	def toSimplex(weights: BDV[Double]): BDV[Double] = {
		weightsOptimizer.toSimplex(weights)
	}


/**
  * Computes the full loss gradient for Gaussian parameters
  * @param dist Mixture component
  * @param point outer product of an augmented data point x => [x 1]
  * @param w prior responsibility of {point} by {dist}
  */
	def gaussianGradient(dist: UpdatableGaussianComponent, point: BDM[Double], w: Double): BDM[Double] = {

		regularizer match{
			case None => basicGaussianGradient(dist.paramMat,point,w) 
			case Some(_) => basicGaussianGradient(dist.paramMat,point,w) +
				regularizer.get.gaussianGradient(dist)
		}

	}

/**
  * Computes the loss gradient of the mixture component without regularization term 
  */
	protected def basicGaussianGradient(paramMat: BDM[Double], point: BDM[Double], w: Double): BDM[Double] = {

		(point - paramMat) * 0.5 * w
	}

/**
  * Computes the loss gradient of the weights vector without regularization term 
  */
	protected def basicWeightsGradient(posteriors: BDV[Double], weights: BDV[Double]): BDV[Double] = {

		weightsOptimizer.gradient(posteriors,weights)
	}
/**
  * Computes the full loss gradient of the weights vector 
  */
	def weightsGradient(posteriors: BDV[Double], weights: BDV[Double]): BDV[Double] = {

		var grads = regularizer match {
			case None => basicWeightsGradient(posteriors,weights)
			case Some(_) => basicWeightsGradient(posteriors,weights) +
		 			regularizer.get.weightsGradient(weights)
		}

		grads(weights.length - 1) = 0.0

		grads

	}

/**
  * Evaluate regularization term for one of the model's components
  *
  * @param dist Mixture component
  * @return regularization term value
  */
	def evaluateRegularizationTerm(dist: UpdatableGaussianComponent): Double = {

		regularizer match{
			case None => 0
			case Some(_) => regularizer.get.evaluateDist(dist)
		}

	}

  /**
  * Evaluate regularization term for the model's weights vector
  *
  * @param weight weights vector
  * @return regularization term value
  */
  def evaluateRegularizationTerm(weights: BDV[Double]): Double = {

    regularizer match{
      case None => 0
      case Some(_) => regularizer.get.evaluateWeights(weights)
    }

  }


/**
  * Compute full updates for the model's parameters. Usually this has the form X_t + alpha * direction(X_t)
  * but it differs for some algorithms, e.g. Nesterov's gradient ascent.
  *
  * @param current Current parameter values
  * @param grad Current batch gradient
  * @param utils Wrapper for accelerated gradient ascent utilities
  * @param ops Deffinitions for algebraic operations for the apropiate data structure, e.g. vector or matrix.
  * .
  * @return updated parameter values
  */


  def getUpdate[A](current: A, grad:A, utils: AcceleratedGradientUtils[A])(implicit ops: ParameterOperations[A]): A = {
    
    ops.sum(current,ops.rescale(direction(grad,utils)(ops),learningRate))

  }

/**
  * compute the ascent direction.
  *
  * @param grad Current batch gradient
  * @param utils Wrapper for accelerated gradient ascent utilities
  * @param ops Deffinitions for algebraic operations for the apropiate data structure, e.g. vector or matrix.
  * .
  * @return updated parameter values
  */

	def direction[A](grad:A, utils: AcceleratedGradientUtils[A])(ops: ParameterOperations[A]): A


	def setLearningRate(learningRate: Double): this.type = { 
		require(learningRate > 0 , "learning rate must be positive")
		this.learningRate = learningRate
		this
	}

	def getLearningRate: Double = { 
		this.learningRate
	}

	def setMinLearningRate(m: Double): this.type = { 
		require(m >= 0, "minLearningRate rate must be in positive")
		minLearningRate = m
		this
	}

	def getMinLearningRate: Double = { 
		minLearningRate
	}

	def setShrinkageRate(s: Double): this.type = { 
		require(s > 0 &&  s <= 1.0, "learning rate must be in (0,1]")
		shrinkageRate = s
		this
	}

	def getBatchSize: Option[Int] = batchSize

	def setBatchSize(n: Int): this.type = {
		require(n>0,"n must be a positive integer")
		batchSize = Option(n)
		this
	}

	def getShrinkageRate: Double = { 
		shrinkageRate
	}

    def getConvergenceTol: Double = convergenceTol

	def setConvergenceTol(x: Double): this.type = {
		require(x>0,"convergenceTol must be positive")
		convergenceTol = x
		this
	}


	def setMaxIter(m: Int): this.type = {
		require(m > 0 ,s"maxIter needs to be a positive integer; got ${m}")
		maxIter = m
		this
	}

	def getMaxIter: Int = {
		maxIter
	}

  def setWeightsOptimizer(t: WeightsTransformation): this.type = {
    weightsOptimizer = t
    this
  }

  def setRegularizer(r: Regularizer): this.type = {
    regularizer = Option(r)
    this
  }

  override def toString: String = {

    val reg: String = if(regularizer.isDefined){
      s"Regularization term: ${regularizer.toString}"
    }else{
      "Regularization term: None"
    }

    val output = s"Algorithm: ${this.getClass} \n ${reg} \n Weight transformation: ${weightsOptimizer.toString}"
    output
  }

}